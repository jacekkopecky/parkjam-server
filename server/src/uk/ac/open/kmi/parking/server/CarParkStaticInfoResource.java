package uk.ac.open.kmi.parking.server;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Iterator;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.ontoware.rdf2go.RDF2Go;
import org.ontoware.rdf2go.model.Model;
import org.ontoware.rdf2go.model.Statement;
import org.ontoware.rdf2go.model.Syntax;
import org.ontoware.rdf2go.model.node.BlankNode;
import org.ontoware.rdf2go.model.node.Literal;
import org.ontoware.rdf2go.model.node.URI;
import org.ontoware.rdf2go.model.node.impl.URIImpl;
import org.ontoware.rdf2go.util.RDFTool;
import org.ontoware.rdf2go.vocabulary.RDF;
import org.ontoware.rdf2go.vocabulary.RDFS;
import org.ontoware.rdf2go.vocabulary.XSD;
import org.openrdf.query.GraphQueryResult;
import org.openrdf.query.QueryLanguage;
import org.openrdf.rdf2go.RepositoryModel;
import org.openrdf.rio.turtle.TurtleWriter;

import uk.ac.open.kmi.parking.ontology.Ontology;
import uk.ac.open.kmi.parking.server.Config.MyRepositoryModel;

/**
 * @author Jacek Kopecky
 * todo somehow rate limit update requests? add a mechanism somewhere for a quick switch to read-only mode?
 * PARK resource, see deliverables (todo copy description here)
 */
@Path("/parks/{id}")
public class CarParkStaticInfoResource {
    @PathParam("id") String id;

    /**
     * @return static info about the car park
     */
    @GET
    @Produces("text/turtle")
    public Response query() {
        try {
            final MyRepositoryModel repomodel = Config.openRepositoryModel(null);

            String uri = Ontology.PARKING_DATA_NS + this.id;

            final StringBuilder sb = new StringBuilder("construct { <");
            sb.append(uri);
            sb.append("> ?p ?o . ?ss ?pp ?oo . }\n" +
                    "where {{\n" +
                    "  <");
            sb.append(uri);
            sb.append("> ?p ?o .} \n UNION { \n <");
            sb.append(uri);
            sb.append("> <" + Ontology.PARKING_hasUnverifiedProperties + "> ?ss . ?ss ?pp ?oo .} \n UNION { \n <");
            sb.append(uri);
            sb.append("> <" + Ontology.PARKING_hasStatement + "> ?ss . ?ss ?pp ?oo .}}");

            // todo add parameter extras, if not present link to it, if present return the hasStatement() things (line above)

//            System.err.println(sb);
            final long time1 = System.currentTimeMillis();

            final GraphQueryResult graphQueryResult = repomodel.getConnection().prepareGraphQuery(
                    QueryLanguage.SPARQL, sb.toString()).evaluate();
            if (!graphQueryResult.hasNext()) {
                final long time2 = System.currentTimeMillis();
                System.err.println("PARK query " + this.id + " took " + (time2-time1) + "ms and was not found.");
                Config.closeRepositoryModel(repomodel);
                return Response.status(404).type(MediaType.TEXT_PLAIN_TYPE).entity("Resource not known, perhaps a typo or an old link?").build();
            }

            StreamingOutput output = new StreamingOutput() {
                @Override
                public void write(OutputStream out) throws IOException {
                    try {
                        TurtleWriter writer = new TurtleWriter(out);
                        writer.startRDF();

                        writer.handleNamespace("D", Ontology.DC_NS);
                        writer.handleNamespace("d", XSD.XSD_NS);
                        writer.handleNamespace("g", Ontology.GEOPOS_NS);
                        writer.handleNamespace("G", Ontology.GEORSS_NS);
                        writer.handleNamespace("o", Ontology.LGO_NS);
                        writer.handleNamespace("P", Ontology.PROV_NS);
                        writer.handleNamespace("p", Ontology.PARKING_NS);
                        writer.handleNamespace("c", Ontology.CARPARK_NS);
                        writer.handleNamespace("r", RDF.RDF_NS);
                        writer.handleNamespace("s", RDFS.RDFS_NS);
                        writer.handleNamespace("V", Ontology.VIRTRDF_NS);
                        writer.handleNamespace("x", Ontology.LGP_NS);

                        int i=0;
                        while (graphQueryResult.hasNext()) {
                            i++;
                            writer.handleStatement(graphQueryResult.next());
                        }

                        // todo add link to /parks/{id}/avail&type=users

                        writer.endRDF();
                        out.close();

                        Config.closeRepositoryModel(repomodel);
                        final long time2 = System.currentTimeMillis();
                        System.err.println("PARK query " + CarParkStaticInfoResource.this.id + " took " + (time2-time1) + "ms and returned " + i + " triples.");
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
     * submit a property of an existing car park
     * @param triples about the car park
     * @return updated static info about the car park
     */
    @POST
    @Consumes("text/turtle")
    public Response addCarParkProperty(InputStream triples) {
        if (!triples.markSupported()) {
            triples = new BufferedInputStream(triples);
        }
        boolean logOK = UpdateRequestLogger.log("CarParkStaticInfoResource.addCarParkProperty", triples);
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

        URI uri = new URIImpl(Ontology.PARKING_DATA_NS + this.id);

        // todo authentication, authorization?
        // authorization like this: recognized good submitters (incl. a built-in one) directly submit, others into moderation queue - a different triple store or just a different context?
        // or maybe all into moderation queue, and automatically approve changes by good submitters
        // the unconfirmed context is automatically included in static info responses
        URI targetContext = Config.PARKING_MODERATION_CONTEXT;
        // if authorized, change the above to Config.PARKING_CONTEXT;

        // todo only supporting literal values

        // check that the input only tells us literals about the car park
        // IMPORTANT: below we are assuming that all statements are about the car park
        for (Statement stat : input) {
            if (!uri.equals(stat.getSubject()) || !(stat.getObject() instanceof Literal)) {
                return Response.status(403).
                            entity("input data should only contain statements about the car park " + uri + " and with literal objects\n").
                            type("text/plain").
                            build();
            }
            // check that none of the properties are in our internal namespaces (e.g. that it can't fake a timestamp) - todo this also applies everywhere else where we submit triples
            if (stat.getPredicate().toString().toUpperCase().startsWith(Ontology.PARKING_NS_UPPERCASE)) {
                return Response.status(403).
                        entity("input data must not contain statements in the ParkJam namespace, such as " + stat).
                        type("text/plain").
                        build();
            }
        }

        MyRepositoryModel repomodel = Config.openRepositoryModel(null);
        String query = "ask { <" + uri + "> a <" + Ontology.LGO_Parking + ">; a ?x }";
        try {
            if (!repomodel.getConnection().prepareBooleanQuery(QueryLanguage.SPARQL, query).evaluate()) {
                Config.closeRepositoryModel(repomodel);
                return Response.status(404).type("text/plain").entity("Resource \"" + uri + "\" not known, perhaps a typo or an old link?").build();
            }
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
        Config.closeRepositoryModel(repomodel);

        // input validated, can be added to repository
        // add the triples as unverified - for every triple <inst, p, o> add <inst, unv, _a> <_a, p, o> and share _a among all the additions
        RepositoryModel parkingsModel = Config.openRepositoryModel(targetContext);
        BlankNode unverifiedProps = parkingsModel.createBlankNode();

        Iterator<Statement> it = input.iterator(); // assuming here that all statements are about the car park
        boolean adding = it.hasNext();
        if (adding) {
            parkingsModel.addStatement(uri, Config.PARKING_hasUnverifiedProperties, unverifiedProps);
            parkingsModel.addStatement(unverifiedProps, Config.PARKING_submissionTimestamp, parkingsModel.createDatatypeLiteral(RDFTool.dateTime2String(new Date()), XSD._dateTime));
            parkingsModel.addStatement(unverifiedProps, Config.PARKING_submittedBy, Config.PARKING_anonymousSubmitter);

            // todo provenance - with authentication, add who it came from
        }
        while ( it.hasNext() ) {
            Statement stat = it.next();
            parkingsModel.addStatement(unverifiedProps, stat.getPredicate(), stat.getObject());
        }
        if (adding) {
            parkingsModel.addStatement(Config.OWLIM_flush, Config.OWLIM_flush, Config.OWLIM_flush);
        }
        Config.closeRepositoryModel(parkingsModel);
        input.close();

        return query();
    }
}
