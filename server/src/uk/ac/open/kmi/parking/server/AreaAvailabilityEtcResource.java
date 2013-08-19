package uk.ac.open.kmi.parking.server;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import org.ontoware.rdf2go.vocabulary.RDFS;
import org.ontoware.rdf2go.vocabulary.XSD;
import org.openrdf.query.GraphQueryResult;
import org.openrdf.query.QueryLanguage;
import org.openrdf.rio.turtle.TurtleWriter;

import uk.ac.open.kmi.parking.ontology.Ontology;
import uk.ac.open.kmi.parking.server.Config.MyRepositoryModel;

/**
 * @author Jacek Kopecky
 * PNEAR resource, see deliverables (todo copy description here)
 */
@SuppressWarnings("unqualified-field-access")
@Path("/availetc")
public class AreaAvailabilityEtcResource {

    @DefaultValue("360000000") @QueryParam("late6min") int late6min;
    @DefaultValue("360000000") @QueryParam("late6max") int late6max;
    @DefaultValue("360000000") @QueryParam("lone6min") int lone6min;
    @DefaultValue("360000000") @QueryParam("lone6max") int lone6max;

    // todo unimplemented parameters follow
    @QueryParam("limit") int limit;
    @QueryParam("trusted") List<String> trustedUserDataSources;
    @QueryParam("type") List<String> requestedTypes;

    /**
     * @return a positive text response when the server is alive
     */
    @GET
    @Produces("text/turtle")
    public Response query() {
        if (late6max < -90000000 || late6max > 90000000 ||
                late6min < -90000000 || late6min > 90000000 ||
                lone6max < -180000000 || lone6max > 180000000 ||
                lone6min < -180000000 || lone6min > 180000000 ||
                late6max <= late6min || lone6max <= lone6min ||
                (late6max-late6min)>1000000 || (lone6max-lone6min)>1000000) {
            return Response.status(Status.FORBIDDEN).type(MediaType.TEXT_PLAIN_TYPE).entity("All coordinate parameters are required; latitudes must be within -90e6 and 90e6, and longitudes within -180e6 and 180e6, min must be smaller than max, and maximum difference between min and max must be 1000000.").build();
        }

        final boolean availabilityOnly = this.requestedTypes.contains("availonly");

        // todo include rdf:type parking:UnverifiedInstance in the returned data? (not necessary if only details view shows this information)
        // test at http://localhost:8080/ParkMe-server/availetc?late6min=52000000&lone6min=0&late6max=52100000&lone6max=100000
        try {
            final MyRepositoryModel repomodel = Config.openRepositoryModel(null);
            final StringBuilder sb = new StringBuilder("PREFIX omgeo: <" + Ontology.OMGEO_NS + ">\n");
            if (availabilityOnly) {
                sb.append("construct { ?s <" + Ontology.PARKING_binaryAvailability + "> ?avail; <" + Ontology.PARKING_binaryAvailabilityTimestamp + "> ?availtime. }\n" +
                      "where {\n" +
                      "  ?s a <" + Ontology.LGO_Parking + ">; \n" +
                      "     omgeo:within(");
            } else {
                sb.append("construct { ?s a <" + Ontology.LGO_Parking + ">; ?unv <" + Ontology.PARKING_UnverifiedInstance + "> ; <" + Ontology.GEOPOS_lat + "> ?lat; <" + Ontology.GEOPOS_long + "> ?lon ; <" + RDFS.label + "> ?label; <" + Ontology.PARKING_binaryAvailability + "> ?avail; <" + Ontology.PARKING_binaryAvailabilityTimestamp + "> ?availtime. }\n" +
                      "where {\n" +
                      "  ?s a <" + Ontology.LGO_Parking + ">; \n" +
                      "     omgeo:within(");
            }
            sb.append(late6min/1e6);
            sb.append(' ');
            sb.append(lone6min/1e6);
            sb.append(' ');
            sb.append(late6max/1e6);
            sb.append(' ');
            sb.append(lone6max/1e6);
            sb.append(") . \n");
            if (!availabilityOnly) {
                sb.append(
                        "  optional { \n" +
                        "     ?s <" + Ontology.GEOPOS_lat + "> ?lat ; \n" +
                        "          <" + Ontology.GEOPOS_long + "> ?lon .  }\n" +
                        "  optional { ?s ?unv <" + Ontology.PARKING_UnverifiedInstance + "> . }\n" +
                        "  optional { ?s <" + RDFS.label + "> ?label .} \n"); // todo if there's no rdfs:label but there is another label-like name property, it will not be returned here - can use presentation ontology in the query
            }
            sb.append(
                    "  optional { ?s <" + Ontology.PARKING_binaryAvailability + "> ?avail .}\n" +
                    "  optional { ?s <" + Ontology.PARKING_binaryAvailabilityTimestamp + "> ?availtime .}}\n");

            final long time1 = System.currentTimeMillis();

            final GraphQueryResult graphQueryResult = repomodel.getConnection().prepareGraphQuery(
                    QueryLanguage.SPARQL, sb.toString()).evaluate();
            if (!graphQueryResult.hasNext()) {
                final long time2 = System.currentTimeMillis();
                System.err.println("PNEAR query " + late6min + " " + lone6min + " " + late6max + " " + lone6max + " took " + (time2-time1) + "ms and returned no results.");
                Config.closeRepositoryModel(repomodel);
                return Response.noContent().build();
            }

            StreamingOutput output = new StreamingOutput() {

                @Override
                public void write(OutputStream out) throws IOException {
                    try {
                        TurtleWriter writer = new TurtleWriter(out);
                        writer.startRDF();

                        writer.handleNamespace("d", XSD.XSD_NS);
                        writer.handleNamespace("p", Ontology.PARKING_NS);
                        if (!availabilityOnly) {
                            writer.handleNamespace("s", RDFS.RDFS_NS);
                            writer.handleNamespace("g", Ontology.GEOPOS_NS);
                            writer.handleNamespace("o", Ontology.LGO_NS);
                        }

                        // todo add Ontology.PARKING_availabilityResource hyperlink statements to each parking

                        int i=0;
                        while (graphQueryResult.hasNext()) {
                            i++;
                            writer.handleStatement(graphQueryResult.next());
                        }

                        writer.endRDF();
                        out.close();

                        Config.closeRepositoryModel(repomodel);
                        final long time2 = System.currentTimeMillis();
                        System.err.println("PNEAR query " + late6min + " " + lone6min + " " + late6max + " " + lone6max + " took " + (time2-time1) + "ms and returned " + i + " triples.");
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
}
