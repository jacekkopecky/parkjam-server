package uk.ac.open.kmi.parking.server;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.ontoware.rdf2go.RDF2Go;
import org.ontoware.rdf2go.model.Model;
import org.ontoware.rdf2go.model.Syntax;
import org.ontoware.rdf2go.model.node.Resource;
import org.ontoware.rdf2go.vocabulary.XSD;
import org.openrdf.query.GraphQueryResult;
import org.openrdf.query.QueryLanguage;
import org.openrdf.rdf2go.RepositoryModel;
import org.openrdf.rio.turtle.TurtleWriter;

import uk.ac.open.kmi.parking.ontology.Ontology;
import uk.ac.open.kmi.parking.server.Config.MyRepositoryModel;

/**
 * @author Jacek Kopecky
 * PAVAIL resource, see deliverables (todo copy description here)
 */
@Path("/parks/{id}/avail")
public class CarParkAvailabilityResource {
    @PathParam("id") String id;

    @QueryParam("trusted") List<String> trustedUserDataSources; // todo unimplemented
    @QueryParam("type") List<String> requestedTypes; // todo unimplemented

    /**
     * @return availability info about the car park
     */
    @GET
    @Produces("text/turtle")
    public Response query() {
        // test at http://localhost:8080/ParkMe-server/parks/1/avail or for now  http://localhost:8080/ParkMe-server/parks/way29869553/avail
        try {
            final MyRepositoryModel repomodel = Config.openRepositoryModel(null);

            String uri = Ontology.PARKING_DATA_NS + this.id;

            final StringBuilder sb = new StringBuilder();
            sb.append("construct { <");
            sb.append(uri);
            sb.append("> a <" + Ontology.LGO_Parking + ">; ?unv <" + Ontology.PARKING_UnverifiedInstance + "> ; <" + Ontology.PARKING_binaryAvailability + "> ?avail; <" + Ontology.PARKING_binaryAvailabilityTimestamp + "> ?availtime. }\n" +
                    "where {\n" +
                    "  <");
            sb.append(uri);
            sb.append("> a <" + Ontology.LGO_Parking + "> . \n optional { <");
            sb.append(uri);
            sb.append("> ?unv <" + Ontology.PARKING_UnverifiedInstance + "> . }\n optional { <");
            sb.append(uri);
            sb.append("> <" + Ontology.PARKING_binaryAvailability + "> ?avail. }\n optional { <");
            sb.append(uri);
            sb.append("> <" + Ontology.PARKING_binaryAvailabilityTimestamp + "> ?availtime. }}\n");
//            System.err.println(sb);
            final long time1 = System.currentTimeMillis();

            final GraphQueryResult graphQueryResult = repomodel.getConnection().prepareGraphQuery(
                    QueryLanguage.SPARQL, sb.toString()).evaluate();
            if (!graphQueryResult.hasNext()) {
                final long time2 = System.currentTimeMillis();
                System.err.println("PAVAIL query " + this.id + " took " + (time2-time1) + "ms and was not found.");
                Config.closeRepositoryModel(repomodel);
                return Response.status(404).type(MediaType.TEXT_PLAIN_TYPE).entity("Resource not known, perhaps a typo or an old link?").build();
            }

            StreamingOutput output = new StreamingOutput() {
                @Override
                public void write(OutputStream out) throws IOException {
                    try {
                        TurtleWriter writer = new TurtleWriter(out);
                        writer.startRDF();

                        writer.handleNamespace("p", Ontology.PARKING_NS);
                        writer.handleNamespace("o", Ontology.LGO_NS);
                        writer.handleNamespace("d", XSD.XSD_NS);

                        int i=0;
                        while (graphQueryResult.hasNext()) {
                            i++;
                            writer.handleStatement(graphQueryResult.next());
                        }

                        writer.endRDF();
                        out.close();

                        Config.closeRepositoryModel(repomodel);
                        final long time2 = System.currentTimeMillis();
                        System.err.println("PAVAIL query " + CarParkAvailabilityResource.this.id + " took " + (time2-time1) + "ms and returned " + i + " triples.");
                    } catch (Exception e) {
                        e.printStackTrace();
                        Config.closeRepositoryModel(repomodel);
                        throw new IOException(e);
                    }
                }
            };

            return Response.ok(output).build();
            // todo set TTL appropriately?
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().type(MediaType.TEXT_PLAIN_TYPE).entity("Server error: " + e).build();
        }
    }

    /**
     * submit availability information about this car park
     * @param triples new availability information
     * @return current aggregated availability if the report is accepted (same as GET without parameters)
     */
    @POST
    @Consumes("text/turtle")
    public Response addAvailabilityRecord(InputStream triples) {
        if (!triples.markSupported()) {
            triples = new BufferedInputStream(triples);
        }
        boolean logOK = UpdateRequestLogger.log("CarParkAvailabilityResource.addAvailabilityRecord", triples);
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
            return Response.status(400).
                        entity("cannot parse input data: " + e.getMessage()).
                        type("text/plain").
                        build();
        }

        // check that there is one availability record,
        Resource node = RDFUtil.checkBinstTriples(input, Config.PARKING_AvailabilityObservation);
        if (node == null) {
            return Response.status(403).
                        entity("input data must contain an instance of " + Config.PARKING_AvailabilityObservation).
                        type("text/plain").
                        build();
        }

        // todo authentication, authorization, submission to UDS

        // check that there are no orphans
        Model inputOrphans = RDFUtil.getOrphans(input, node, false, Config.ignoredNamespaces);
        inputOrphans.open();
        if (!inputOrphans.isEmpty()) {
            String orphans = inputOrphans.serialize(Syntax.Turtle);
            input.close();
            inputOrphans.close();
            return Response.status(403).
                        entity("input data contains statements not attached to the submitted instance of type " + Config.PARKING_AvailabilityObservation + ": \n" + orphans).
                        type("text/plain").
                        build();
        }
        inputOrphans.close();

        AvailabilityObservation observation = AvailabilityObservation.parse(input, node);
        input.close();
        if (observation == null) {
            return Response.status(403).
                        entity("submitted availability observation cannot be parsed: " + (AvailabilityObservation.errorMessage == null ? "(unknown reason)" : AvailabilityObservation.errorMessage)).
                        type("text/plain").
                        build();
        }

        // check that it pertains to this car park
        String uri = Ontology.PARKING_DATA_NS + this.id;
        if (!observation.parkingURI.toString().equals(uri)) {
            return Response.status(403).
                        entity("submission on " + uri + " pertains to a different car park " + observation.parkingURI).
                        type("text/plain").
                        build();
        }


        // now we have the data, put it in the database
        // todo this must be done better - need to aggregate with previous, do trust evaluation etc.
        RepositoryModel repomodel = Config.openRepositoryModel(Config.AVAILABILITY_CONTEXT);
        repomodel.removeStatements(observation.parkingURI, Config.PARKING_binaryAvailability, null);
        repomodel.removeStatements(observation.parkingURI, Config.PARKING_binaryAvailabilityTimestamp, null);
        repomodel.addStatement(observation.parkingURI, Config.PARKING_binaryAvailability, repomodel.createDatatypeLiteral(Boolean.toString(observation.binaryAvailability), XSD._boolean));
        repomodel.addStatement(observation.parkingURI, Config.PARKING_binaryAvailabilityTimestamp, repomodel.createDatatypeLiteral(observation.date, XSD._dateTime));
        repomodel.addStatement(Config.OWLIM_flush, Config.OWLIM_flush, Config.OWLIM_flush);
        Config.closeRepositoryModel(repomodel);

        return query();
    }
}
