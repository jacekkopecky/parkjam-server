package uk.ac.open.kmi.parking.admin;

import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
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
import org.ontoware.rdf2go.util.RDFTool;
import org.ontoware.rdf2go.vocabulary.RDF;
import org.ontoware.rdf2go.vocabulary.XSD;
import org.openrdf.rdf2go.RepositoryModel;

import uk.ac.open.kmi.parking.server.Config;

/**
 * resource that handles internal submissions of availability data imported from elsewhere
 * currently (dec 2012), only car-counting and carpark-closed data accepted here
 * @author Jacek Kopecky
 */
@Path("/import-avail/")
public class AvailabilityImportResource {
    private static String lastImportStatus = "no last import";

    /**
     * returns the status of the last import
     * @return last string
     */
    @GET
    @Produces("text/plain")
    public String getLastImportStatus() {
        return lastImportStatus;
    }

    /**
     * import availability information about car parks
     * @param triples new availability information
     * @return description of changes done
     */
    @POST
    @Consumes("text/turtle")
    public Response importAvailabilityRecords(InputStream triples) {

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

        StringBuilder retval = new StringBuilder("import at " + new Date() + ": \n");
        int retvalStatus = 200;

        RepositoryModel availmodel = Config.openRepositoryModel(Config.AVAILABILITY_CONTEXT);
        RepositoryModel staticmodel = Config.openRepositoryModel(Config.PARKING_CONTEXT);

        final DatatypeLiteral falseLiteral = availmodel.createDatatypeLiteral("false", XSD._boolean);
        final DatatypeLiteral trueLiteral = availmodel.createDatatypeLiteral("true", XSD._boolean);
        final DatatypeLiteral nowTimestamp = input.createDatatypeLiteral(RDFTool.dateTime2String(new Date()), XSD._dateTime);

        int count = 1;

        // go through the submitted records
        Iterator<Statement> observations = input.findStatements(null, RDF.type, Config.PARKING_AvailabilityObservation);
        while (observations.hasNext()) {
            Statement obstat = observations.next();
            Resource observation = obstat.getSubject();

            retval.append("handing observation " + count++ + "\n");

            // build the list of car parks to which this observation pertains; either directly the feature of interest, or car parks that match the feature of interest taken as a template
            List<URI> carparks = new ArrayList<URI>();
            Iterator<Statement> targets = input.findStatements(observation, Config.SSN_featureOfInterest, null);
            while (targets.hasNext()) {
//              -     check that its featureOfInterest has max one triple about it
//              -       if it's one triple, then the subject should be a bnode; find all car parks ?cp that fit the triple
//              -       if it's no triples, then the subject should be a URI, that's our car park ?cp
                Node target = targets.next().getObject();
                if (target instanceof URI) {
                    carparks.add((URI)target);
                } else if (target instanceof BlankNode) {
                    Iterator<Statement> descriptions = input.findStatements((BlankNode)target, null, null);
                    if (!descriptions.hasNext()) {
                        retval.append("feature of interest blank node without properties ignored\n");
                        continue;
                    }

                    Statement description = descriptions.next();
                    if (descriptions.hasNext()) {
                        retval.append("feature of interest blank node with description triple " + description + " has more description triples, whole node ignored\n");
                        continue;
                    }

                    retval.append("using feature of interest description " + description.getPredicate() + " " + description.getObject() + "\n");
                    Iterator<Statement> carparksIterator = staticmodel.findStatements(null, description.getPredicate(), description.getObject());
                    if (!carparksIterator.hasNext()) {
                        retval.append("no car park matches description " + description + "\n");
                    }
                    while (carparksIterator.hasNext()) {
                        Resource carpark = carparksIterator.next().getSubject();
                        if (carpark instanceof URI) {
                            carparks.add((URI)carpark);
                        } else {
                            retval.append("blank node will be ignored that was found matching the description " + description + "\n");
                        }
                    }
                } else {
                    retval.append("feature of interest node " + target + " neither URI nor BlankNode, ignored\n");
                    continue;
                }
            }

            if (carparks.isEmpty()) {
                retval.append("no car parks found that match the feature of interest, ignoring whole observation\n");
                continue;
            }

            retval.append("observation affects " + carparks.size() + " car parks:\n");
//            for (URI cp : carparks) {
//                retval.append("  " + cp + "\n");
//            }

            // todo ignoring sampling time from observation
//          -     extract sampling time or use server time (probably the latter) but maybe compare them to see sampling time not too long ago

            // find the actual observed value
//          -     get ?observation S:observationResult [] S:hasValue ?val
            Iterator<Statement> it = input.findStatements(observation, Config.SSN_observationResult, null);
            if (!it.hasNext()) {
                retval.append("observation without observationResult, ignored\n");
                continue;
            }
            Node result = it.next().getObject();
            if (it.hasNext()) {
                retval.append("observation ignored because it has multiple observationResults\n");
                continue;
            }

            if (!(result instanceof Resource)) {
                retval.append("observationResult not a resource (is " + result + "), observation ignored\n");
                continue;
            }

            it = input.findStatements((Resource)result, Config.SSN_hasValue, null);
            if (!it.hasNext()) {
                retval.append("observation result without hasValue, ignored\n");
                continue;
            }
            Node value = it.next().getObject();
            if (it.hasNext()) {
                retval.append("observation result ignored because it has multiple hasValue\n");
                continue;
            }

            if (!(value instanceof Resource)) {
                retval.append("value of observationResult not a resource (is " + value + "), observation ignored\n");
                continue;
            }

            // go through all the triples on ?value, find single carCountingAvailability, isClosed, report unknowns and ignore them
            DatatypeLiteral carCountingAvailability = null;
            DatatypeLiteral isClosed = null;

            it = input.findStatements((Resource)value, null, null);
            while (it.hasNext()) {
                Statement valstat = it.next();
                URI valpred = valstat.getPredicate();
                if (valpred.equals(Config.PARKING_closed)) {
                    Node val = valstat.getObject();
                    if (!(val instanceof DatatypeLiteral)) {
                        retval.append("value of " + Config.PARKING_closed + " not a datatype literal\n");
                        continue;
                    }
                    if (isClosed != null) {
                        retval.append("multiple values for " + Config.PARKING_closed + " - all but one ignored\n");
                        continue;
                    }
                    isClosed = (DatatypeLiteral)val;
                    if (!XSD._boolean.equals(isClosed.getDatatype())) {
                        isClosed = null;
                        retval.append("non-boolean value for " + Config.PARKING_closed + " ignored\n");
                    }
                } else if (valpred.equals(Config.PARKING_carCountingAvailability)) {
                    Node val = valstat.getObject();
                    if (!(val instanceof DatatypeLiteral)) {
                        retval.append("value of " + Config.PARKING_carCountingAvailability + " not a datatype literal\n");
                        continue;
                    }
                    if (carCountingAvailability != null) {
                        retval.append("multiple values for " + Config.PARKING_carCountingAvailability + " - all but one ignored\n");
                        continue;
                    }
                    carCountingAvailability = (DatatypeLiteral)val;
                    if (!XSD._boolean.equals(carCountingAvailability.getDatatype())) {
                        carCountingAvailability = null;
                        retval.append("non-boolean value for " + Config.PARKING_carCountingAvailability + " ignored\n");
                    }
                } else {
                    retval.append("unknown predicate about observation value ignored: " + valpred + "\n");
                    continue;
                }
            }

            Boolean effectOnBinaryAvailability = null;
            if (carCountingAvailability != null) {
//    -       if carCountingAvailability present then only if false, write as timestamped hasCarCountingAvailability on all ?cp, because while full applies to all car parks in the area, available may apply only to some
                String boolstr = carCountingAvailability.getValue();
                if ("false".equalsIgnoreCase(boolstr) || "0".equals(boolstr)) {
                    effectOnBinaryAvailability = false;
                    for (URI cp : carparks) {
                        // todo write timestamped carCountingAvailability on all ?cp

//                        retval.append("todo add timestamped carCountingAvailability on " + cp + "\n");
                    }
                } else if  ("true".equalsIgnoreCase(boolstr) || "1".equals(boolstr)) {
                    effectOnBinaryAvailability = true;
                } else {
                    retval.append("error: car counting availability unknown boolean value " + boolstr + "\n");
                }

            }

            if (isClosed != null) {
//    -       if isClosed present then if true, write as timestamped closed, otherwise remove the old one
                String boolstr = isClosed.getValue();
                if ("true".equalsIgnoreCase(boolstr) || "1".equals(boolstr)) {
                    effectOnBinaryAvailability = false;
                    for (URI cp : carparks) {
                        // todo write timestamped closed on all ?cp
//                        retval.append("todo add timestamped closed on " + cp + "\n");
                    }
                } else {
                    for (URI cp : carparks) {
                        // todo remove closed on all ?cp
//                        retval.append("todo remove closed on " + cp + "\n");
                    }
                }

            }

            if (effectOnBinaryAvailability != null) {
//    -       if last binary availability older than some time (1h?), and if cCA == false or isC == true, add new binaryAvailability false

                DatatypeLiteral availabilityLiteral = effectOnBinaryAvailability ? trueLiteral : falseLiteral;

                for (URI cp : carparks) {
                    // retrieve binary availability timestamp
                    Iterator<Statement> timestamps = availmodel.findStatements(cp, Config.PARKING_binaryAvailabilityTimestamp, null);
                    boolean doUpdate = true;
                    if (timestamps.hasNext()) {
                        Node ts = timestamps.next().getObject();
                        // if there are multiple binary availability timestamps, consider that an invalid situation and force an update
                        if (!timestamps.hasNext()) {
                            if (ts instanceof DatatypeLiteral) {
                                try {
                                    Date tsdate = RDFTool.string2DateTime(ts.asDatatypeLiteral().getValue());
                                    Calendar tscal = new GregorianCalendar();
                                    tscal.setTime(tsdate);
                                    Calendar nowcal = new GregorianCalendar();
                                    nowcal.add(Calendar.HOUR_OF_DAY, -1);
                                    if (tscal.after(nowcal)) {
                                        doUpdate = false;
                                        retval.append("carpark " + cp + " has recent availability, not changing\n");
                                    }
                                } catch (ParseException e) {
                                    // faulty date, update it
                                    retval.append("timestamp parsing error on " + cp + " with timestamp " + ts.asLiteral().getValue() + " - updating\n");
                                }
                            }
                        }
                    }
                    if (doUpdate) {
                        // remove old binaryAvailability, binaryAvailabilityTimestamp
                        availmodel.removeStatements(cp, Config.PARKING_binaryAvailability, null);
                        availmodel.removeStatements(cp, Config.PARKING_binaryAvailabilityTimestamp, null);
                        // put in new binaryAvailability, binaryAvailabilityTimestamp
                        availmodel.addStatement(cp, Config.PARKING_binaryAvailability, availabilityLiteral);
                        availmodel.addStatement(cp, Config.PARKING_binaryAvailabilityTimestamp, nowTimestamp);
                        retval.append("updated old binary availability on " + cp + " to " + effectOnBinaryAvailability + "\n");
                    }
                }
            }
        }
        availmodel.addStatement(Config.OWLIM_flush, Config.OWLIM_flush, Config.OWLIM_flush);
        input.close();
        Config.closeRepositoryModel(availmodel);
        Config.closeRepositoryModel(staticmodel);

        retval.append("import done at " + new Date() + "\n");
        String retvalString = retval.toString();
        lastImportStatus = retvalString;
        return Response.status(retvalStatus).entity(retvalString).type("text/plain").build();
    }
}
