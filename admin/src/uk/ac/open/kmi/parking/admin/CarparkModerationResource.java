package uk.ac.open.kmi.parking.admin;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringEscapeUtils;
import org.ontoware.rdf2go.model.Statement;
import org.ontoware.rdf2go.model.node.Literal;
import org.ontoware.rdf2go.model.node.Node;
import org.ontoware.rdf2go.model.node.Resource;
import org.ontoware.rdf2go.model.node.URI;
import org.ontoware.rdf2go.vocabulary.RDF;

import uk.ac.open.kmi.parking.server.Config;
import uk.ac.open.kmi.parking.server.Config.MyRepositoryModel;

/**
 * admin interface for moderation of submitted car parks
 * @author Jacek Kopecky
 */
@Path("/moderate-parks/")
public class CarparkModerationResource {

    // todo later an atom feed of submissions?

    private static final int PARKS_PER_PAGE = 100;

    /**
     * @param startTimestamp the time stamp up to which car parks should be ignored
     * @return html page for moderation
     */
    @GET
    @Produces("text/html")
    public Response listParksForModeration(@QueryParam("time") String startTimestamp) {
        // todo multiple todos
        // later  query param: submitter - ID or "anon" - provenance of submitted carparks, list only those; otherwise all
        // later  query param: orderby=submitter to order carparks by submitter then by timestamp (ultimate order by timestamp)
        // later     if submitter param present, remove submitter from orderby
        // later  query param: index - number - how many carparks should we skip
        // later  query param: count - number - how many carparks should we show
        // later  query param: time - datetime - don't display carparks older than this timestamp

        StringBuilder retval = new StringBuilder("<!doctype html>\n" +
                "<html><head><title>ParkJam submitted carpark moderation</title>\n" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\" />\n" +
                "<style type=\"text/css\">\n" +
                "  .bag { padding: 1em;\n" +
                "         margin-bottom: 1em; }\n" +
                "  .bag > p { margin-top: 0; font-size: 120%; }\n" +
                "  .bag > img.qr { position: absolute; right: 20px; }\n" +
                "  .tableholder { margin-left: 2em; \n" +
                "          margin-right: 160px; }\n" +
                "  table { width: 100%; border: 0; border-collapse: collapse;\n" +
                "          background-color: #f5f5f5; }\n" +
                "  tr.odd { background-color: #fff; }\n" +
                "  td { padding-left: 4px; padding-right: 4px; border: 1px solid #555;}\n" +
                "  td.prop { width: 30em; }\n" +
                "  td.btns { font-size: 200%; text-align: center; width: 10em; }\n" +
                "  td.ts { font-size: 120%; font-family: monospace; padding-left: 1em; }\n" +
                "  span.approve { cursor: pointer; color: green; text-decoration: none; display: inline-block; padding: 5px; border: none; }\n" +
                "  span.decline { cursor: pointer; color: red; text-decoration: none; display: inline-block; padding: 5px; border: none; }\n" +
                "\n" +
                "  .approved { background-color: #cfc }\n" +
                "  .declined { background-color: #dfd8d8 }\n" +
                "  tr.approved { background-color: #cfc }\n" +
                "  tr.declined { background-color: #dfd8d8 }\n" +
                "  .approved tr { background-color: #cfc }\n" +
                "  .declined tr { background-color: #dfd8d8 }\n" +
                "  .done span.approve { visibility: hidden }\n" +
                "  .done span.decline { visibility: hidden }\n" +
                "  .partial .bagwise { visibility: hidden }\n" +
                "  .done .result { padding: .5em; margin-left: 2em; margin-top: .5em; border: 1px #888 dashed; }\n" +
                "  .partial .result { padding: .5em; margin-left: 2em; margin-top: .5em; border: 1px #888 dashed; }\n" +
                "  .error { color: #a00; }\n" +
                "</style>\n" +
                "\n" +
                "<script type=\"text/javascript\">\n" +
                "function doCarpark(approval, carpark, divID)\n" +
                "{\n" +
                "  bag = document.getElementById(divID);\n" +
                "  bag.className += \" done \" + approval;\n" +
                "  url = approval + \"?carpark=\" + encodeURIComponent(carpark);\n" +
                "  http=new XMLHttpRequest();\n" +
                "  http.onreadystatechange = function (aEvt) {\n" +
                "    if (http.readyState == 4) {\n" +
                "       if(http.status == 200)\n" +
                "        bag.innerHTML += \"<div class='result'>Result: \" + http.responseText + \"</div>\";\n" +
                "       else\n" +
                "        bag.innerHTML += \"<div class='result error'>error \" + http.status + \" \" + http.statusText + \" at <a href='\" + url + \"'>\" + url + \"</a></div>\";\n" +
                "    }};\n" +
                "  http.open(\"POST\",url);\n" +
                "  http.send();\n" +
                "  return false;\n" +
                "}\n" +
                "function approveCarpark(carpark, divID)\n" +
                "{\n" +
                "  return doCarpark(\"approved\", carpark, divID);\n" +
                "}\n" +
                "function declineCarpark(carpark, divID)\n" +
                "{\n" +
                "  return doCarpark(\"declined\", carpark, divID);\n" +
                "}\n" +
                "</script>\n" +
                "\n" +
                "</head><body><h1>ParkJam submitted carpark moderation</h1>\n");

        MyRepositoryModel moderationmodel = Config.openRepositoryModel(Config.PARKING_MODERATION_CONTEXT);

        // todo later if submitter param not null, write out submitter (see below)
        // todo later parse presentation ontology from repository so that I can access item by prop-uri

        // todo write out submitter along with the time stamp

        ArrayList<Map.Entry<Resource, String>> carparks = findCarparks(retval, moderationmodel);

        Map<Resource,String> locationMap = findLocations(retval, moderationmodel, carparks);

        int parksId = 1;

        if (startTimestamp != null) {
            retval.append("<p><a href='?'>Show all carparks</a> &mdash; now only showing from timestamp " + startTimestamp + "</p>\n");
        }

        // now print out all the car parks
        for (Map.Entry<Resource, String> entry : carparks) {
            Resource carparkRes = entry.getKey();
            String timestamp = entry.getValue();

            if (startTimestamp != null && startTimestamp.compareTo(timestamp) >= 0) {
                continue; // skipping this because it's before the start timestamp
            }

            String location = locationMap.get(carparkRes); // not escaped, should be harmless because it's just numbers and comes from us (location should be checked in car park submission)
            String carpark = carparkRes.toString();  // not escaped, should be harmless because the URI comes from us
            String carparkJs = StringEscapeUtils.escapeXml(StringEscapeUtils.escapeJavaScript(carpark));
            String carparkUriParam = null;
            try {
                carparkUriParam = URLEncoder.encode(carpark, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            retval.append("<div class=\"bag\" id=\"id");
            retval.append(parksId);
            retval.append("\">\n");
            retval.append("  <p>Car park: &lt;<a target=\"carpark\" href=\"view-source:");
            retval.append(carpark);
            retval.append("\">");
            retval.append(carpark);
            retval.append("</a>&gt;"); // todo should add car park name
            if (location != null) {
                retval.append(" &nbsp;&nbsp; <a href=\"http://maps.google.com/maps?q=");
                retval.append(location);
                retval.append("\" target=\"map\">location</a>");
            }
            retval.append("</p>\n");
            retval.append("  <img class='qr' src='https://chart.googleapis.com/chart?chs=120x120&cht=qr&chl=");
            retval.append(carparkUriParam);
            retval.append("&choe=UTF-8&chld=|0' />\n");
            // todo it probably shouldn't be a table per car park, it could be one big table or no tables at all
            retval.append(
                    "  <div class='tableholder'><table>\n" +
                    "  <tr><td class=\"ts\">");
            retval.append(timestamp);
            retval.append("</td>\n" +
                    "      <td class=\"btns\"><span class=\"approve bagwise\" onClick=\"approveCarpark('");
            retval.append(carparkJs);
            retval.append("','id");
            retval.append(parksId);
            retval.append("');\">&#x2714;</span>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class=\"decline bagwise\" onClick=\"declineCarpark('");
            retval.append(carparkJs);
            retval.append("','id");
            retval.append(parksId);
            retval.append("');\">&#x2716;</span></td></tr>\n");
            retval.append(
                    "  </table></div>\n" +
                    "</div>\n<p><a href=\"?time=");
            retval.append(timestamp);
            retval.append("\">Show only car parks from here</a></p>\n");

            if (++parksId > PARKS_PER_PAGE) {
                retval.append("<h2>Only displayed the first " + PARKS_PER_PAGE + " carparks.</h2>");
                break;
            }
        }

        retval.append("<p><a href='/data/ping?extra=geoindex'>Force geoindexing</p>\n");

        Config.closeRepositoryModel(moderationmodel);
        retval.append("</body></html>\n");
        CacheControl cc = new CacheControl();
        cc.setNoCache(true);
        return Response.ok(retval.toString()).cacheControl(cc).build();
    }

    private Map<Resource, String> findLocations(StringBuilder retval, MyRepositoryModel model, Collection<Map.Entry<Resource, String>> carparks) {
        Map<Resource, String> locationMap = new HashMap<Resource, String>(carparks.size());
        for (Map.Entry<Resource, String> carparkTs : carparks) {
            Resource carpark = carparkTs.getKey();
            Iterator<Statement> lats = model.findStatements(carpark, Config.GEOPOS_lat, null);
            String lat;
            Node latValue;
            if (!lats.hasNext() || !((latValue = lats.next().getObject()) instanceof Literal)) {
                retval.append("<h2>Error: car park " + carpark + " without a literal latitude?</h2>");
                continue;
            }
            lat = latValue.asLiteral().getValue();
            if (lats.hasNext()) {
                retval.append("<h2>Error: car park " + carpark + " has multiple latitudes?</h2>");
                continue;
            }

            Iterator<Statement> lons = model.findStatements(carpark, Config.GEOPOS_long, null);
            String lon;
            Node lonValue;
            if (!lons.hasNext() || !((lonValue = lons.next().getObject()) instanceof Literal)) {
                retval.append("<h2>Error: car park " + carpark + " without a literal longitude?</h2>");
                continue;
            }
            lon = lonValue.asLiteral().getValue();
            if (lons.hasNext()) {
                retval.append("<h2>Error: car park " + carpark + " has multiple longitudes?</h2>");
                continue;
            }

            if (lat.startsWith("-")) {
                lat = lat.substring(1) + "s+";
            } else {
                lat = lat + "n+";
            }
            if (lon.startsWith("-")) {
                lon = lon.substring(1) + "w";
            } else {
                lon = lon + "e";
            }

            locationMap.put(carpark, lat+lon);
        }
        return locationMap;
    }

    private ArrayList<Map.Entry<Resource, String>> findCarparks(StringBuilder retval, MyRepositoryModel moderationmodel) {
        // find car parks
        Map<Resource, String> carparksMap = new HashMap<Resource, String>();
        Iterator<Statement> carparkStats = moderationmodel.findStatements(null, RDF.type, Config.LGO_Parking);
        if (!carparkStats.hasNext()) {
            retval.append("<h2>Nothing to moderate</h2>");
        }

        while (carparkStats.hasNext()) {
            Statement carparkStat = carparkStats.next();
            Node carparkNode = carparkStat.getSubject();
            if (!(carparkNode instanceof Resource)) {
                retval.append("<h2>Error: carpark not a resource?</h2><pre>");
                retval.append(carparkStat);
                retval.append("</pre>");
                continue;
            }
            Resource carpark = (Resource) carparkNode;
            carparksMap.put(carpark, null);
        }

        fillCarparkMapWithTimestampsAndRemoveBadOnes(retval, moderationmodel, carparksMap);

        ArrayList<Map.Entry<Resource, String>> carparksList = new ArrayList<Map.Entry<Resource, String>>(carparksMap.entrySet());
        Collections.sort(carparksList, new Comparator<Map.Entry<Resource, String>>() {
            @Override
            public int compare(Entry<Resource, String> e, Entry<Resource, String> f) {
                int x = e.getValue().compareTo(f.getValue());
                return x != 0 ? x : e.getKey().compareTo(f.getKey());
            }
        });
        return carparksList;
    }

    private void fillCarparkMapWithTimestampsAndRemoveBadOnes(StringBuilder retval, MyRepositoryModel moderationmodel, Map<Resource, String> carparksMap) {
        Set<Resource> badOnes = new HashSet<Resource>();
        // getting all existing timestamps here so that we don't query the repository separately for each one of them
        Iterator<Statement> timestampStats = moderationmodel.findStatements(null, Config.PARKING_submissionTimestamp, null);
        while (timestampStats.hasNext()) {
            Statement tsStat = timestampStats.next();
            Resource carpark = tsStat.getSubject();
            if (!carparksMap.containsKey(carpark)) {
                // other things can have submission timestamps
                continue;
            } else if (carparksMap.get(carpark) != null) {
                retval.append("<h2>Error: carpark has multiple timestamps.</h2><pre>");
                retval.append(carpark);
                retval.append("</pre>");
                badOnes.add(carpark);
                continue;
            }

            Node tsNode = tsStat.getObject();
            if (!(tsNode instanceof Literal)) {
                retval.append("<h2>Error: timestamp not a literal?</h2><pre>");
                retval.append(tsNode);
                retval.append("</pre>");
                badOnes.add(carpark);
                continue;
            }
            String ts = ((Literal) tsNode).getValue();
            if (!StringEscapeUtils.escapeXml(ts).equals(ts)) {
                retval.append("<h2>Error: timestamp needing escaping?</h2><pre>");
                retval.append(ts);
                retval.append("</pre>");
                badOnes.add(carpark);
                continue;
            }
            carparksMap.put(carpark, ts);
        }

        for (Resource carpark : carparksMap.keySet()) {
            if (carparksMap.get(carpark) == null) {
                retval.append("<h2>Error: carpark doesn't have a submission timestamp.</h2><pre>");
                retval.append(carpark);
                retval.append("</pre>");
                carparksMap.put(carpark, "---");
            }
        }

        for (Resource badOne : badOnes) {
            carparksMap.remove(badOne);
        }
    }

    /**
     * approve a car park
     * @param carpark the car park that should be approved
     * @return response
     */
    @POST
    @Produces("text/plain")
    @Path("approved")
    public Response approveCarpark(@QueryParam("carpark") String carpark) {
        return handleCarpark(carpark, true, false);
    }

    /**
     * decline a car park
     * @param carpark the car park that should be declined
     * @param force whether the deletion should be executed even for carparks in the static context
     * @return response
     */
    @POST
    @Produces("text/plain")
    @Path("declined")
    public Response declineCarpark(@QueryParam("carpark") String carpark, @QueryParam("force") @DefaultValue("false") boolean force) {
        return handleCarpark(carpark, false, force);
    }

    /**
     * approves or declines a carpark
     */
    private Response handleCarpark(String carpark, boolean approve, boolean force) {
        if (carpark == null || "".equals(carpark)) {
            return Response.status(400).entity("carpark query parameter required with a car park URI").build();
        }
        MyRepositoryModel moderationModel = Config.openRepositoryModel(Config.PARKING_MODERATION_CONTEXT);
        URI carparkUri = moderationModel.createURI(carpark);

        // check car park exists in moderation queue
        if (!force && !moderationModel.contains(carparkUri, RDF.type, Config.LGO_Parking)) {
            return Response.status(400).entity("no unapproved carpark \"" + carpark + "\" found").build();
        }

        if (approve) {
            doApproveCarpark(moderationModel, carparkUri);
        } else {
            doDeclineCarpark(moderationModel, carparkUri);
        }
        Config.closeRepositoryModel(moderationModel);

        return Response.ok((approve ? "approved" : "declined" ) + " car park " + carpark).build();
    }

    private void doDeclineCarpark(MyRepositoryModel moderationModel, URI carparkUri) {
        List<Resource> extraNodesForRemoval = new LinkedList<Resource>();
        List<Statement> statsForRemoval = new LinkedList<Statement>();

        // remove carpark properties in moderation model
        Iterator<Statement> carparkProperties = moderationModel.findStatements(carparkUri, null, null);
        while (carparkProperties.hasNext()) {
            Statement carparkPropertyStat = carparkProperties.next();
            URI property = carparkPropertyStat.getPredicate();
            if (Config.PARKING_hasUnverifiedProperties.equals(property) ||
                    Config.PARKING_hasStatement.equals(property)) {
                Node node = carparkPropertyStat.getObject();
                if (node instanceof Resource) extraNodesForRemoval.add((Resource)node);
            }
            statsForRemoval.add(carparkPropertyStat);
        }

        for (Resource extraForRemoval : extraNodesForRemoval) {
            moderationModel.removeStatements(extraForRemoval, null, null);
        }
        extraNodesForRemoval.clear();
        for (Statement stat : statsForRemoval) {
            moderationModel.removeStatement(stat);
        }
        statsForRemoval.clear();
        moderationModel = null;

        // remove any availability data for this car park
        MyRepositoryModel availabilityModel = Config.openRepositoryModel(Config.AVAILABILITY_CONTEXT);
        availabilityModel.removeStatements(carparkUri, null, null);
        Config.closeRepositoryModel(availabilityModel);
        availabilityModel = null;

        // remove any properties already approved for this car park
        MyRepositoryModel approvedModel = Config.openRepositoryModel(Config.PARKING_CONTEXT);
        carparkProperties = approvedModel.findStatements(carparkUri, null, null);
        while (carparkProperties.hasNext()) {
            Statement carparkPropertyStat = carparkProperties.next();
            statsForRemoval.add(carparkPropertyStat);

            Node node = carparkPropertyStat.getObject();
            if (node instanceof Resource && Config.PARKING_hasStatement.equals(carparkPropertyStat.getPredicate())) {
                extraNodesForRemoval.add((Resource)node);
            }
        }
        for (Statement stat : statsForRemoval) {
            approvedModel.removeStatement(stat);
        }
        statsForRemoval.clear();
        for (Resource extraForRemoval : extraNodesForRemoval) {
            approvedModel.removeStatements(extraForRemoval, null, null);
        }
        extraNodesForRemoval.clear();
        Config.closeRepositoryModel(approvedModel);
        approvedModel = null;

    }

    private void doApproveCarpark(MyRepositoryModel moderationModel, URI carparkUri) {
        List<Statement> statsForRemovalFromModerationModel = new LinkedList<Statement>();

        MyRepositoryModel approvedModel = Config.openRepositoryModel(Config.PARKING_CONTEXT);

        Iterator<Statement> carparkProperties = moderationModel.findStatements(carparkUri, null, null);
        while (carparkProperties.hasNext()) {
            Statement carparkPropertyStat = carparkProperties.next();
            URI property = carparkPropertyStat.getPredicate();
            if (Config.PARKING_hasUnverifiedProperties.equals(property) ||
                    Config.PARKING_hasStatement.equals(property)) {
                // todo hasStatement should not occur, we might wanna check this
                continue; // do not approve these properties
            } else if (RDF.type.equals(property) && Config.PARKING_UnverifiedInstance.equals(carparkPropertyStat.getObject())) {
                // do not copy this statement
                statsForRemovalFromModerationModel.add(carparkPropertyStat);
                continue;
            }
            statsForRemovalFromModerationModel.add(carparkPropertyStat);
            approvedModel.addStatement(carparkPropertyStat);
        }
        Config.closeRepositoryModel(approvedModel);

        for (Statement stat : statsForRemovalFromModerationModel) {
            moderationModel.removeStatement(stat);
        }
    }
}
