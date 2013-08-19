package uk.ac.open.kmi.parking.server;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.Iterator;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.ontoware.rdf2go.RDF2Go;
import org.ontoware.rdf2go.model.Model;
import org.ontoware.rdf2go.model.Statement;
import org.ontoware.rdf2go.model.Syntax;
import org.ontoware.rdf2go.model.node.BlankNode;
import org.ontoware.rdf2go.model.node.DatatypeLiteral;
import org.ontoware.rdf2go.model.node.Node;
import org.ontoware.rdf2go.model.node.Resource;
import org.ontoware.rdf2go.model.node.URI;
import org.ontoware.rdf2go.model.node.impl.URIImpl;
import org.ontoware.rdf2go.util.RDFTool;
import org.ontoware.rdf2go.vocabulary.RDF;
import org.ontoware.rdf2go.vocabulary.XSD;
import org.openrdf.rdf2go.RepositoryModel;

import uk.ac.open.kmi.parking.ontology.Ontology;

/**
 * @author Jacek Kopecky
 * PARKS resource, see deliverables (todo copy description here)
 */
@Path("/parks")
public class CarParksResource {
    // todo unimplemented parameters follow
    @DefaultValue("360000000") @QueryParam("late6min") int late6min;
    @DefaultValue("360000000") @QueryParam("late6max") int late6max;
    @DefaultValue("360000000") @QueryParam("lone6min") int lone6min;
    @DefaultValue("360000000") @QueryParam("lone6max") int lone6max;
    @QueryParam("limit") int limit;

    /**
     * redirect to a list of car parks
     * @return a redirect
     */
    @GET
    @Produces("text/plain")
    public Response listCarParks() {
        return Response.seeOther(java.net.URI.create("http://parking.kmi.open.ac.uk/parks.uris")).type("text/plain").entity("a list of known car parks is at /parks.uris").build();
    }

    /**
     * submit new car park
     * @param triples new availability information
     * @return current aggregated availability if the report is accepted (same as GET without parameters)
     */
    @POST
    @Consumes("text/turtle")
    public Response addCarPark(InputStream triples) {
        if (!triples.markSupported()) {
            triples = new BufferedInputStream(triples);
        }
        boolean logOK = UpdateRequestLogger.log("CarParksResource.addCarPark", triples);
        if (!logOK) {
            return Response.status(413).
                        entity("submitted data too large (or some logging problem)").
                        type("text/plain").
                        build();
        }

        Model input = RDF2Go.getModelFactory().createModel();
        input.open();
        try {
            input.readFrom(triples, Syntax.Turtle);
        } catch (Exception e) {
            input.close();
            return Response.status(400).
                        entity("cannot parse input data: " + e.getMessage() + "\n").
                        type("text/plain").
                        build();
        }

        if (input.size() > Config.MAXIMUM_SUBMITTED_CARPARK_TRIPLES) {
            input.close();
            return Response.status(413).
                        entity("submitted too many triples").
                        type("text/plain").
                        build();
        }

        // check that there is one parking
        Resource parking = RDFUtil.checkBinstTriples(input, Config.LGO_Parking);
        if (parking == null) {
            input.close();
            return Response.status(403).
                        entity("input data must contain an instance of " + Config.LGO_Parking + "\n").
                        type("text/plain").
                        build();
        }

        // todo authentication, authorization?
        // authorization like this: recognized good submitters (incl. a built-in one) directly submit, others into moderation queue - a different triple store or just a different context?
        // or maybe all into moderation queue, and automatically approve changes by good submitters
        // the unconfirmed context could be included through a type param, and through an app preference
        boolean unverified = true;
        URI targetContext = Config.PARKING_MODERATION_CONTEXT;
        // if authorized, change the above to Config.PARKING_CONTEXT; and the boolean to false

        // check that there are no PARKING_NS statements
        for (Statement stat : input) {
            if (stat.getPredicate().toString().toUpperCase().startsWith(Ontology.PARKING_NS_UPPERCASE)) {
                return Response.status(403).
                        entity("input data must not contain statements in the ParkJam namespace, such as " + stat).
                        type("text/plain").
                        build();
            }
        }

        // check that there are no orphans
        Model inputOrphans = RDFUtil.getOrphans(input, parking, false, Config.ignoredNamespaces);
        inputOrphans.open();
        if (!inputOrphans.isEmpty()) {
            String orphans = inputOrphans.serialize(Syntax.Turtle);
            input.close();
            inputOrphans.close();
            return Response.status(403).
                        entity("input data contains statements not attached to the submitted instance of type " + Config.LGO_Parking + ": \n" + orphans + "\n").
                        type("text/plain").
                        build();
        }
        inputOrphans.close();

        // check that the URI (if any) isn't in our namespace
        if (!(parking instanceof BlankNode) && parking.asURI().toString().startsWith(Ontology.PARKING_DATA_NS)) {
            return Response.status(403).
                        entity("submitting new car park in our namespace " + Ontology.PARKING_DATA_NS + " not allowed - if the car park is already known to us, use PATCH there to update its information\n").
                        type("text/plain").
                        build();
        }

        System.err.println("a new car park submitted: " + parking);

        // check that the car park has latitude and longitude
        @SuppressWarnings("unused")
        double lat, lon;
        lat = RDFUtil.extractDouble(input, parking, Config.GEOPOS_lat, "parking latitude");
        lon = RDFUtil.extractDouble(input, parking, Config.GEOPOS_long, "parking longitude");

        // todo check that there's no other car park near the same latitude and longitude

        // check that the submitted node isn't already known in the repository (it may be known in the moderation context, but we don't care about that here)
        if (parking instanceof URI) {
            RepositoryModel repoModel = Config.openRepositoryModel(Config.PARKING_CONTEXT);
            Iterator<Statement> origs = repoModel.findStatements(null, Config.PROV_hadOriginalSource, parking);
            if (origs.hasNext()) {
                input.close();
                Statement stat = origs.next();
                if (origs.hasNext()) {
                    System.err.println("inconsistency: multiple nodes prov:hadOriginalSource " + parking);
                }

                Resource known = stat.getSubject();
                Config.closeRepositoryModel(repoModel);
                if (known instanceof URI) {
                    return Response.status(409)
                                .entity("the submitted parking instance is already submitted and known as " + known + "\n").type("text/plain")
                                .header("Location", known.toString())
                                .build();
                } else {
                    System.err.println("inconsistency: parking bnode in the triple store (refers as original source to " + parking + ")");
                    return Response.status(500)
                                .entity("inconsistency detected: parking bnode in the triple store (refers as original source to " + parking + ")\n").type("text/plain")
                                .build();
                }
            }
            Config.closeRepositoryModel(repoModel);
        }

        // create the new identifiers for the submitted parking
        String newInstID = RDFUtil.newID();
        URI newInstURI = new URIImpl(Ontology.PARKING_DATA_NS + newInstID);

        // replace in input statements the old node with the new ID
        Iterator<Statement> it = input.findStatements(parking, null, (Node)null);
        while (it.hasNext()) {
            Statement stat = it.next();
            input.removeStatement(stat);
            input.addStatement(newInstURI, stat.getPredicate(), stat.getObject());
        }
        if (!(parking instanceof BlankNode)) {
            input.addStatement(newInstURI, Config.PROV_hadOriginalSource, parking);
        }

        DatatypeLiteral submissionTimestamp = input.createDatatypeLiteral(RDFTool.dateTime2String(new Date()), XSD._dateTime);

        // todo when authenticated, put in provenance
        input.addStatement(newInstURI, Config.PARKING_submittedBy, Config.PARKING_anonymousSubmitter);
        input.addStatement(newInstURI, Config.PARKING_submissionTimestamp, submissionTimestamp);
        if (unverified) {
            input.addStatement(newInstURI, RDF.type, Config.PARKING_UnverifiedInstance);
        }

        // input validated, has no orphans, has the new ID, can be added to repository
        RepositoryModel parkingsModel = Config.openRepositoryModel(targetContext);
        parkingsModel.addModel(input);
        Config.closeRepositoryModel(parkingsModel);
        input.close();
        input = null;

        // add binary availability as true - in the availability context
        // todo maybe allow submitted car parks with availability? then put that in the availability context and in the response, instead of "true"
        RepositoryModel availModel = Config.openRepositoryModel(Config.AVAILABILITY_CONTEXT);
        DatatypeLiteral availabilityValue = availModel.createDatatypeLiteral("true", XSD._boolean);
        availModel.addStatement(newInstURI, Config.PARKING_binaryAvailability, availabilityValue); // copied below
        availModel.addStatement(newInstURI, Config.PARKING_binaryAvailabilityTimestamp, submissionTimestamp);
        availModel.addStatement(Config.OWLIM_flush, Config.OWLIM_flush, Config.OWLIM_flush);
        Config.closeRepositoryModel(availModel);
        availModel = null;

        // create output, esp. the reifications from above so the response immediately contains the links
        Model retval = RDF2Go.getModelFactory().createModel();
        retval.open();
        retval.addStatement(newInstURI, RDF.type, Config.LGO_Parking);
        retval.addStatement(newInstURI, Config.PARKING_binaryAvailability, availabilityValue);
        retval.addStatement(newInstURI, Config.PARKING_binaryAvailabilityTimestamp, submissionTimestamp);
        if (!(parking instanceof BlankNode)) {
            retval.addStatement(newInstURI, Config.PROV_hadOriginalSource, parking);
        }
        String response = retval.serialize(Syntax.Turtle);
        retval.close();

        GeoIndexingThread.geoindexSometime();

        return Response.created(java.net.URI.create(newInstURI.toString())).entity(response).type("text/turtle").build();
    }
}

