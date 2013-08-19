package uk.ac.open.kmi.parking.admin;

import java.util.Iterator;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.ontoware.rdf2go.model.Statement;
import org.ontoware.rdf2go.model.node.Resource;
import org.ontoware.rdf2go.model.node.URI;
import org.ontoware.rdf2go.vocabulary.RDF;
import org.openrdf.rdf2go.RepositoryModel;

import uk.ac.open.kmi.parking.server.Config;

/**
 * admin interface for listing all car parks
 * @author Jacek Kopecky
 */
@Path("/parks")
public class CarparkListingResource {

    /**
     * get a list of all car parks
     * @return a list of all car parks
     */
    @GET
    @Produces("text/uri-list")
    public Response listCarParks() {
        StringBuilder response = new StringBuilder("# approved car parks\r\n");
        RepositoryModel model = Config.openRepositoryModel(Config.PARKING_CONTEXT);
        Iterator<Statement> parkings = model.findStatements(null, RDF.type, Config.LGO_Parking);
        while (parkings.hasNext()) {
            Statement stat = parkings.next();
            Resource parking = stat.getSubject();
            if (parking instanceof URI) {
                response.append(parking.toString());
                response.append("\r\n");
            }
        }
        Config.closeRepositoryModel(model);
        response.append("# car parks under moderation\r\n");
        model = Config.openRepositoryModel(Config.PARKING_MODERATION_CONTEXT);
        parkings = model.findStatements(null, RDF.type, Config.LGO_Parking);
        while (parkings.hasNext()) {
            Statement stat = parkings.next();
            Resource parking = stat.getSubject();
            if (parking instanceof URI) {
                response.append(parking.toString());
                response.append("\r\n");
            }
        }
        Config.closeRepositoryModel(model);
        return Response.ok(response.toString()).build();
    }
}
