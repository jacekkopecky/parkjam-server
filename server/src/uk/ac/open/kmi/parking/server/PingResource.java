package uk.ac.open.kmi.parking.server;

import java.util.Date;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.openrdf.query.QueryLanguage;

import uk.ac.open.kmi.parking.ontology.Ontology;
import uk.ac.open.kmi.parking.server.Config.MyRepositoryModel;

/**
 * @author Jacek Kopecky
 * Ping class, should return an uncachable positive text response when the server is alive.
 */
@Path("/ping")
public class PingResource {

    @QueryParam("extra") List<String> extras; // handled values: repostat, geoindex, unverifieds, flush

    /**
     * @return a positive text response when the server is alive
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response ping() {
        String retval = "";
        if (this.extras.contains("repostat")) {
            MyRepositoryModel repomodel = Config.openRepositoryModel(null);
            String query = "ASK { ?s a ?o. }";
            try {
                retval = retval + "repository reachable: " + repomodel.getConnection().prepareBooleanQuery(QueryLanguage.SPARQL, query).evaluate() + "\n";
            } catch (Exception e) {
                e.printStackTrace();
                retval = retval + "reaching repository failed: " + e.getMessage() + "\n";
            } finally {
                Config.closeRepositoryModel(repomodel);
            }
        }
        if (this.extras.contains("flush")) {
            retval = retval + flushRepository();
        }
        if (this.extras.contains("unverifieds")) {
            MyRepositoryModel repomodel = Config.openRepositoryModel(Config.PARKING_MODERATION_CONTEXT);
            String queryParks = "SELECT (COUNT(*) as ?count) where {?x a <" + Ontology.PARKING_UnverifiedInstance + "> .}";
            String queryProps = "SELECT (COUNT(*) as ?count) where {?x <" + Ontology.PARKING_hasUnverifiedProperties + "> ?y.}";
            try {
                retval = retval + "number of unverified car parks, unverified bags of properties: " +
                        repomodel.getConnection().prepareTupleQuery(QueryLanguage.SPARQL, queryParks).evaluate().next().getValue("count").stringValue() + ", " +
                        repomodel.getConnection().prepareTupleQuery(QueryLanguage.SPARQL, queryProps).evaluate().next().getValue("count").stringValue() +
                        "\nModeration console at http://localhost:9090/admin/moderate-parks/ and http://localhost:9090/admin/moderate-props/\n";
            } catch (Exception e) {
                e.printStackTrace();
                retval = retval + "unverifieds query failed: " + e.getMessage() + "\n";
            } finally {
                Config.closeRepositoryModel(repomodel);
            }
        }
        if (this.extras.contains("geoindex")) {
            retval = retval + geoindexRepository();
        }
        CacheControl cc = new CacheControl();
        cc.setNoCache(true);
        return Response.ok(retval + "server running " + new Date() + "\n").cacheControl(cc).build();
    }

    static String geoindexRepository() {
        return GeoIndexingThread.geoindexNow();
    }

    static String flushRepository() {
        String retval = "";
        MyRepositoryModel repomodel = Config.openRepositoryModel(null);
        try {
            repomodel.addStatement(Config.OWLIM_flush, Config.OWLIM_flush, Config.OWLIM_flush);
            Config.closeRepositoryModel(repomodel);
            retval = "flush statement added, let's hope it works\n";
        } catch (Exception e) {
            Config.closeRepositoryModel(repomodel);
            e.printStackTrace();
            retval = "reaching repository failed: " + e.getMessage() + "\n";
        }
        return retval;
    }
}
