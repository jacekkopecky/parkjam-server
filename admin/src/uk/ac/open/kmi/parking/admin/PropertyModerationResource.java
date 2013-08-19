package uk.ac.open.kmi.parking.admin;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
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
import org.ontoware.rdf2go.vocabulary.XSD;

import uk.ac.open.kmi.parking.server.Config;
import uk.ac.open.kmi.parking.server.Config.MyRepositoryModel;

/**
 * admin interface for moderation of submitted car park properties
 * @author Jacek Kopecky
 */
@Path("/moderate-props/")
public class PropertyModerationResource {

    // todo later an atom feed of submissions?

    // todo this should highlight moderated properties for carparks that haven't been approved yet

    private static final int BAGS_PER_PAGE = 100;

    /**
     * @param startTimestamp the time stamp up to which bags should be ignored
     * @return html page for moderation
     */
    @GET
    @Produces("text/html")
    public Response listPropsForModeration(@QueryParam("time") String startTimestamp) {
        // todo multiple todos
        // later  query param: carpark - URI - id of car park for which we should list the bags, otherwise all car parks
        // later  query param: submitter - ID or "anon" - provenance of submitted bags, list only those; otherwise all
        // later  query param: orderby=submitter to order bags by submitter then by timestamp (ultimate order by timestamp)
        // later  query param: orderby=carpark to order bags by carpark (ultimate order by timestamp)
        // later     orderby can have multiple values
        // later     if carpark param present, remove carpark from orderby
        // later     if submitter param present, remove submitter from orderby
        // later  query param: index - number - how many bags should we skip
        // later  query param: count - number - how many bags should we show
        // later  query param: time - datetime - don't display bags older than this timestamp

        StringBuilder retval = new StringBuilder("<!doctype html>\n" +
                "<html><head><title>ParkJam submitted property moderation</title>\n" +
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
                "  .unconfirmed { color: red }\n" +
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
                "  .itemwise { visibility: hidden; }\n" +  // todo remove when implemented
                "</style>\n" +
                "\n" +
                "<script type=\"text/javascript\">\n" +
                "function doBag(approval, carpark, timestamp)\n" +
                "{\n" +
                "  bag = document.getElementById(timestamp);\n" +
                "  bag.className += \" done \" + approval;\n" +
                "  url = approval + \"?ts=\" + encodeURIComponent(timestamp) + \"&carpark=\" + encodeURIComponent(carpark);\n" +
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
                "function approveBag(carpark, timestamp)\n" +
                "{\n" +
                "  return doBag(\"approved\", carpark, timestamp);\n" +
                "}\n" +
                "function declineBag(carpark, timestamp)\n" +
                "{\n" +
                "  return doBag(\"declined\", carpark, timestamp);\n" +
                "}\n" +
                "\n" +
                "function doOne(approval, carpark, property, value, timestamp, id)\n" +
                "{\n" +
                "  bag = document.getElementById(timestamp);\n" +
                "  bag.className+=\" partial\";\n" +
                "  document.getElementById(id).className += \" done \" + approval;\n" +
                "  url = approval + \"-one?ts=\" + encodeURIComponent(timestamp) \n" +
                "                 + \"&carpark=\" + encodeURIComponent(carpark) \n" +
                "                 + \"&property=\" + encodeURIComponent(property) \n" +
                "                 + \"&value=\" + encodeURIComponent(value);\n" +
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
                "function approveOne(carpark, property, value, timestamp, id)\n" +
                "{\n" +
                "  return doOne(\"approved\", carpark, property, value, timestamp, id);\n" +
                "}\n" +
                "function declineOne(carpark, property, value, timestamp, id)\n" +
                "{\n" +
                "  return doOne(\"declined\", carpark, property, value, timestamp, id);\n" +
                "}\n" +
                "</script>\n" +
                "\n" +
                "</head><body><h1>ParkJam submitted property moderation</h1>\n");

        MyRepositoryModel moderationmodel = Config.openRepositoryModel(Config.PARKING_MODERATION_CONTEXT);
        MyRepositoryModel staticmodel = Config.openRepositoryModel(Config.PARKING_CONTEXT);

        // todo later if carpark param not null, write out car park (see below)
        // todo later if submitter param not null, write out submitter (see below)
        // todo later parse presentation ontology from repository so that I can access item by prop-uri

        // todo write out submitter along with the time stamp

        // todo
        // list bags of unverified properties (in general, or for the given car park and/or submitter), ordered by orderby(s) and then timestamp
        // later only the first PAGE bags
        // later depending on orderby, print out headings for carpark and/or submitter as new ones are encountered
        // for each bag of unverified properties make a div with an ID from timestamp
        // later if not present in orderby:
        // line  "car park: " [name "-"] <URI> <a href="google maps link">"lat: " lat ", lon: " lon</a>
        //       later |     submitter ID or "anonymous"
        // table line: time stamp    |   form with hidden fields: car park, timestamp (hopefully unique - if it's same as the previous, disable the bag buttons and say why), yes/no buttons
        // for each property (except provenance and time stamp) in the bag, table line:
        // <a href="uri">prop label if preferred property or <URI> if not</a>    |    "html-escaped-value"     |    datatype? (not at the moment)    |    form with hidden fields: car park, prop, escaped prop value, datatype?, timestamp, yes/no buttons
        // later link to next PAGE

        HashMap<Resource,Resource> bagToCarpark = new HashMap<Resource, Resource>();
        Set<Resource> carparks = new HashSet<Resource>();
        Set<Resource> unconfirmedCarparks = new HashSet<Resource>();

        ArrayList<Map.Entry<Resource, String>> bagList = findBags(retval, moderationmodel, bagToCarpark, carparks);

        Map<Resource,String> locationMap = findLocationsAndUnconfirmedCarparks(retval, staticmodel, moderationmodel, carparks, unconfirmedCarparks);

        int bagId = 1;

        if (startTimestamp != null) {
            retval.append("<p><a href='?'>Show all bags</a> &mdash; now only showing from timestamp " + startTimestamp + "</p>\n");
        }

        // now print out all the bags
        String lastTimestamp = null;
        for (Map.Entry<Resource, String> entry : bagList) {
            Resource bag = entry.getKey();
            String timestamp = entry.getValue();

            if (startTimestamp != null && startTimestamp.compareTo(timestamp) >= 0) {
                continue; // skipping this because it's before the start timestamp
            }

            if (timestamp.equals(lastTimestamp)) {
                retval.append("<h2>Error: timestamp conflict, if it's for the same car park then approval may not work for the bag above and below</h2>\n");
            }
            lastTimestamp = timestamp;

            Resource carparkRes = bagToCarpark.get(bag);
            String location = locationMap.get(carparkRes); // not escaped, should be harmless because it's just numbers and comes from us (location should be checked in car park submission)
            String carpark = carparkRes.toString();  // not escaped, should be harmless because the URI comes from us
            String carparkJs = StringEscapeUtils.escapeXml(StringEscapeUtils.escapeJavaScript(carpark));
            String carparkUriParam = null;
            try {
                carparkUriParam = URLEncoder.encode(carpark, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            retval.append("<div class=\"bag\" id=\"");
            retval.append(timestamp);
            retval.append("\">\n");
            retval.append("  <p>Car park: &lt;<a target=\"carpark\" href=\"view-source:");
            retval.append(carpark);
            retval.append("\">");
            retval.append(carpark);
            retval.append("</a>&gt;"); // todo should add car park name
            if (unconfirmedCarparks.contains(carparkRes)) {
                retval.append(" &nbsp;&nbsp; <span class='unconfirmed'>(unconfirmed)</span>");
            }
            if (location != null) {
                retval.append(" &nbsp;&nbsp; <a href=\"http://maps.google.com/maps?q=");
                retval.append(location);
                retval.append("\" target=\"map\">location</a>");
            }
            retval.append("</p>\n");
            retval.append("  <img class='qr' src='https://chart.googleapis.com/chart?chs=120x120&cht=qr&chl=");
            retval.append(carparkUriParam);
            retval.append("&choe=UTF-8&chld=|0' />\n");
            retval.append(
                    "  <div class='tableholder'><table>\n" +
                    "  <tr><td colspan=\"2\" class=\"ts\">");
            retval.append(timestamp);
            retval.append("</td>\n" +
                    "      <td class=\"btns\"><span class=\"approve bagwise\" onClick=\"approveBag('");
            retval.append(carparkJs);
            retval.append("','");
            retval.append(timestamp);
            retval.append("');\">&#x2714;</span>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span class=\"decline bagwise\" onClick=\"declineBag('");
            retval.append(carparkJs);
            retval.append("','");
            retval.append(timestamp);
            retval.append("');\">&#x2716;</span></td></tr>\n");

            boolean odd = true;
            int propId = 1;

            Iterator<Statement> propStats = moderationmodel.findStatements(bag, null, null);
            while (propStats.hasNext()) {
                Statement propStat = propStats.next();
                if (propStat.getPredicate().equals(Config.PARKING_submissionTimestamp) ||
                        propStat.getPredicate().equals(Config.PARKING_submittedBy)) {
                    continue;
                }
                String prop = propStat.getPredicate().toString();
                String propXml = StringEscapeUtils.escapeXml(prop);
                String propJs = StringEscapeUtils.escapeXml(StringEscapeUtils.escapeJavaScript(prop));
                String valueXml;
                String valueJs;
                Node valNode = propStat.getObject();
                if (!(valNode instanceof Literal)) {
                    valueXml = "Error: value not a literal? \"" + StringEscapeUtils.escapeXml(valNode.toString()) + "\"";
                    valueJs = "";
                } else {
                    String val = ((Literal) valNode).getValue();
                    valueXml = StringEscapeUtils.escapeXml(val);
                    valueJs = StringEscapeUtils.escapeXml(StringEscapeUtils.escapeJavaScript(val));
                }

                retval.append(
                        "  <tr id=\"id");
                retval.append(bagId);
                retval.append(".");
                retval.append(propId);
                retval.append("\" class=\"");
                retval.append((odd ? "odd" : "even"));
                retval.append("\">\n" +
                        "      <td class=\"prop\"><a href=\"");
                retval.append(propXml);
                retval.append("\">");
                retval.append(propXml);
                retval.append("</a></td>\n" +
                        "      <td>\"");
                retval.append(valueXml);
                retval.append("\"</td>\n" +
                        "      <td class=\"btns\"><a class=\"approve itemwise\" href=\"#\" onClick=\"approveOne('");
                retval.append(carparkJs);
                retval.append("','");
                retval.append(propJs);
                retval.append("','");
                retval.append(valueJs);
                retval.append("','");
                retval.append(timestamp);
                retval.append("','id");
                retval.append(bagId);
                retval.append(".");
                retval.append(propId);
                retval.append("');\">&#x2714;</a>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a class=\"decline itemwise\" href=\"#\" onClick=\"declineOne('");
                retval.append(carparkJs);
                retval.append("','");
                retval.append(propJs);
                retval.append("','");
                retval.append(valueJs);
                retval.append("','");
                retval.append(timestamp);
                retval.append("','id");
                retval.append(bagId);
                retval.append(".");
                retval.append(propId);
                retval.append("');\">&#x2716;</a></td></tr>\n");
                propId++;
                odd = !odd;
            }
            retval.append(
                    "  </table></div>\n" +
                    "</div>\n<p><a href=\"?time=");
            retval.append(timestamp);
            retval.append("\">Show only bags from here</a></p>\n");

            if (++bagId > BAGS_PER_PAGE) {
                retval.append("<h2>Only displayed the first " + BAGS_PER_PAGE + " bags.</h2>");
                break;
            }

        }

        Config.closeRepositoryModel(staticmodel);
        Config.closeRepositoryModel(moderationmodel);
        retval.append("</body></html>\n");
        CacheControl cc = new CacheControl();
        cc.setNoCache(true);
        return Response.ok(retval.toString()).cacheControl(cc).build();
    }

    private Map<Resource, String> findLocationsAndUnconfirmedCarparks(StringBuilder retval, MyRepositoryModel staticmodel, MyRepositoryModel moderationmodel, Set<Resource> carparks, Set<Resource> unconfirmedCarparks) {
        Map<Resource, String> locationMap = new HashMap<Resource, String>(carparks.size());
        for (Resource carpark : carparks) {
            String lat;
            Node latValue;
            Iterator<Statement> lats = staticmodel.findStatements(carpark, Config.GEOPOS_lat, null);
            if (!lats.hasNext()) {
                lats = moderationmodel.findStatements(carpark, Config.GEOPOS_lat, null);
                unconfirmedCarparks.add(carpark);
            }
            if (!lats.hasNext() || !((latValue = lats.next().getObject()) instanceof Literal)) {
                retval.append("<h2>Error: car park " + carpark + " without a literal latitude?</h2>");
                continue;
            }
            lat = latValue.asLiteral().getValue();
            if (lats.hasNext()) {
                retval.append("<h2>Error: car park " + carpark + " has multiple latitudes?</h2>");
                continue;
            }

            String lon;
            Node lonValue;
            Iterator<Statement> lons = staticmodel.findStatements(carpark, Config.GEOPOS_long, null);
            if (!lons.hasNext()) {
                lons = moderationmodel.findStatements(carpark, Config.GEOPOS_long, null);
                unconfirmedCarparks.add(carpark);
            }
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

    private ArrayList<Map.Entry<Resource, String>> findBags(StringBuilder retval, MyRepositoryModel moderationmodel,
            HashMap<Resource, Resource> bagToCarpark, Set<Resource> carParks) {
        // find bags
        HashMap<Resource,String> bagMap = new HashMap<Resource, String>();
        Iterator<Statement> bagStats = moderationmodel.findStatements(null, Config.PARKING_hasUnverifiedProperties, null);
        if (!bagStats.hasNext()) {
            retval.append("<h2>Nothing to moderate</h2>");
        }

        while (bagStats.hasNext()) {
            Statement bagStat = bagStats.next();
            Node bagNode = bagStat.getObject();
            if (!(bagNode instanceof Resource)) {
                retval.append("<h2>Error: bag of unverified properties not a resource?</h2><pre>");
                retval.append(bagStat);
                retval.append("</pre>");
                continue;
            }
            Resource bag = (Resource) bagNode;
            bagMap.put(bag, null);
            bagToCarpark.put(bag, bagStat.getSubject());
            carParks.add(bagStat.getSubject());
        }

        fillBagMapWithTimestampsAndRemoveBadBags(retval, moderationmodel, bagMap);

        ArrayList<Map.Entry<Resource, String>> bagList = new ArrayList<Map.Entry<Resource, String>>(bagMap.entrySet());
        Collections.sort(bagList, new Comparator<Map.Entry<Resource, String>>() {
            @Override
            public int compare(Entry<Resource, String> e, Entry<Resource, String> f) {
                int x = e.getValue().compareTo(f.getValue());
                return x != 0 ? x : e.getKey().compareTo(f.getKey());
            }
        });
        return bagList;
    }

    private void fillBagMapWithTimestampsAndRemoveBadBags(StringBuilder retval, MyRepositoryModel moderationmodel,
            HashMap<Resource, String> bagMap) {
        Set<Resource> badBags = new HashSet<Resource>();
        // getting all existing timestamps in moderation model here so that we don't query the repository separately for each one of them
        Iterator<Statement> timestampStats = moderationmodel.findStatements(null, Config.PARKING_submissionTimestamp, null);
        while (timestampStats.hasNext()) {
            Statement tsStat = timestampStats.next();
            Resource bag = tsStat.getSubject();
            if (!bagMap.containsKey(bag)) {
                // also car parks now have timestamps
                continue;
            } else if (bagMap.get(bag) != null) {
                retval.append("<h2>Error: bag of unverified properties has multiple timestamps.</h2><pre>");
                retval.append(bag);
                retval.append("</pre>");
                badBags.add(bag);
                continue;
            }

            Node tsNode = tsStat.getObject();
            if (!(tsNode instanceof Literal)) {
                retval.append("<h2>Error: timestamp not a literal?</h2><pre>");
                retval.append(tsNode);
                retval.append("</pre>");
                badBags.add(bag);
                continue;
            }
            String ts = ((Literal) tsNode).getValue();
            if (!StringEscapeUtils.escapeXml(ts).equals(ts)) {
                retval.append("<h2>Error: timestamp needing escaping?</h2><pre>");
                retval.append(ts);
                retval.append("</pre>");
                badBags.add(bag);
                continue;
            }
            bagMap.put(bag, ts);
        }

        for (Resource bag : bagMap.keySet()) {
            if (bagMap.get(bag) == null) {
                retval.append("<h2>Error: bag of unverified properties doesn't have a timestamp.</h2><pre>");
                retval.append(bag);
                retval.append("</pre>");
                badBags.add(bag);
            }
        }

        for (Resource badBag : badBags) {
            bagMap.remove(badBag);
        }
    }

    /**
     * approve a bag
     * @param ts timestamp of the bag to be approved
     * @param carpark the car park whose bag should be approved
     * @return response
     */
    @POST
    @Produces("text/plain")
    @Path("approved")
    public Response approveBag(@QueryParam("ts") String ts, @QueryParam("carpark") String carpark) {
        return handleBag(ts, carpark, true);
    }

    /**
     * decline a bag
     * @param ts timestamp of the bag to be declined
     * @param carpark the car park whose bag should be declined
     * @return response
     */
    @POST
    @Produces("text/plain")
    @Path("declined")
    public Response declineBag(@QueryParam("ts") String ts, @QueryParam("carpark") String carpark) {
        return handleBag(ts, carpark, false);
    }

    /**
     * approves or declines a bag of unverified properties
     */
    private Response handleBag(String ts, String carpark, boolean approve) {
        if (ts == null || "".equals(ts)) {
            return Response.status(400).entity("ts query parameter required with a timestamp string").build();
        }
        if (carpark == null || "".equals(carpark)) {
            return Response.status(400).entity("carpark query parameter required with a car park URI").build();
        }
        MyRepositoryModel moderationModel = Config.openRepositoryModel(Config.PARKING_MODERATION_CONTEXT);
        URI carparkUri = moderationModel.createURI(carpark);

        // find bags whose timestamp is ts
        Literal tsLiteral = moderationModel.createDatatypeLiteral(ts, XSD._dateTime);
        Iterator<Statement> bagStatsByTimestamp = moderationModel.findStatements(null, Config.PARKING_submissionTimestamp, tsLiteral);
        if (!bagStatsByTimestamp.hasNext()) {
            return Response.status(400).entity("no bag of unapproved properties dated \"" + ts + "\" found").build();
        }

        Resource theBag = null;
        Statement carparkBagStat = null;
        while (bagStatsByTimestamp.hasNext()) {
            Resource bag = bagStatsByTimestamp.next().getSubject();
            // for each check that it belongs to the carpark
            Iterator<Statement> carparkStats = moderationModel.findStatements(carparkUri, Config.PARKING_hasUnverifiedProperties, bag);
            if (carparkStats.hasNext()) {
                if (theBag == null) {
                    theBag = bag;
                    carparkBagStat = carparkStats.next();
                } else {
                    return Response.status(500).entity("multiple bags of unapproved properties for car park " + carpark + " and time stamp " + ts + " so failing").build();
                }
            }
        }
        if (theBag == null) {
            return Response.status(400).entity("no bags of unapproved properties for car park " + carpark + " and time stamp " + ts + "!").build();
        }


        List<Statement> statsForRemovalFromModerationModel = new LinkedList<Statement>();
        int handledProps = 0;

        if (approve) {
            // retrieve provenance information
            Node submitter = Config.PARKING_anonymousSubmitter;
            Iterator<Statement> provenance = moderationModel.findStatements(theBag, Config.PARKING_submittedBy, null);
            if (provenance.hasNext()) {
                submitter = provenance.next().getObject();
            }
            if (provenance.hasNext()) {
                return Response.status(500).entity("error: multiple submitters on the bag of unapproved properties on car park " + carpark + " and time stamp " + ts + "!").build();
            }

            MyRepositoryModel approvedModel = Config.openRepositoryModel(Config.PARKING_CONTEXT);

            Iterator<Statement> bagProperties = moderationModel.findStatements(theBag, null, null);
            while (bagProperties.hasNext()) {
                Statement bagPropertyStat = bagProperties.next();
                statsForRemovalFromModerationModel.add(bagPropertyStat);
                URI property = bagPropertyStat.getPredicate();
                if (Config.PARKING_submissionTimestamp.equals(property) ||
                        Config.PARKING_submittedBy.equals(property)) {
                    continue; // do not approve these properties
                }
                approveProperty(approvedModel, carparkUri, bagPropertyStat, tsLiteral, submitter); // todo provenance
                handledProps++;
    //            moderationModel.removeStatement(bagPropertyStat);
            }
            Config.closeRepositoryModel(approvedModel);
        } else {
            Iterator<Statement> bagProperties = moderationModel.findStatements(theBag, null, null);
            while (bagProperties.hasNext()) {
                statsForRemovalFromModerationModel.add(bagProperties.next());
                handledProps++;
            }
        }

        // then deleteBag(bag) delete bag ? ?. ? ? bag. from moderation queue - currently only removing the one found carpark ? bag statement, not ? ? bag
        for (Statement stat : statsForRemovalFromModerationModel) {
            moderationModel.removeStatement(stat);
        }
        moderationModel.removeStatement(carparkBagStat);

        Config.closeRepositoryModel(moderationModel);

        return Response.ok((approve ? "approved " : "declined " ) + handledProps + " properties in car park " + carpark + " bag at " + ts).build();
    }

    /**
     * approve a single property
     * @param ts timestamp of the bag that contains the property to be approved
     * @param carpark the car park whose property should be approved
     * @param property the property to be approved
     * @param value the value to be approved
     * @return response
     */
    @SuppressWarnings("unused")
    @POST
    @Produces("text/plain")
    @Path("approved-one")
    public Response approveOne(@QueryParam("ts") String ts, @QueryParam("carpark") String carpark, @QueryParam("property") String property, @QueryParam("value") String value) {
        // todo
        return Response.status(500).entity("not implemented yet").build();
//        return Response.ok("approved property " + property + " with value " + value + " of " + carpark + " at " + ts).build();
    }

    /**
     * decline a single property
     * @param ts timestamp of the bag that contains the property to be declined
     * @param carpark the car park whose property should be declined
     * @param property the property to be declined
     * @param value the value to be declined
     * @return response
     */
    @SuppressWarnings("unused")
    @POST
    @Produces("text/plain")
    @Path("declined-one")
    public Response declineOne(@QueryParam("ts") String ts, @QueryParam("carpark") String carpark, @QueryParam("property") String property, @QueryParam("value") String value) {
        // todo
        return Response.status(500).entity("not implemented yet").build();
//        return Response.ok("declined property " + property + " with value " + value + " of " + carpark + " at " + ts).build();
    }


    // creates provenance and timestamp information together with putting the property in static info
    private void approveProperty(MyRepositoryModel model, URI carparkUri, Statement bagStatement, Literal timestamp, Node submitter) {
        model.removeStatements(carparkUri,  bagStatement.getPredicate(), null); // remove old values; todo maybe also remove the reifications
        Statement stat = model.createStatement(carparkUri, bagStatement.getPredicate(), bagStatement.getObject());
        model.addStatement(stat);
        Resource reification = model.addReificationOf(stat);
        model.addStatement(carparkUri, Config.PARKING_hasStatement, reification);
        model.addStatement(reification, Config.PARKING_submissionTimestamp, timestamp);
        model.addStatement(reification, Config.PARKING_submittedBy, submitter);
        model.addStatement(reification, Config.OWLIM_flush, Config.OWLIM_flush);
    }

}
