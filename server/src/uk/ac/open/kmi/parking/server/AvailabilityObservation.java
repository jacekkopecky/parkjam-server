package uk.ac.open.kmi.parking.server;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;

import org.ontoware.rdf2go.model.Model;
import org.ontoware.rdf2go.model.Statement;
import org.ontoware.rdf2go.model.node.DatatypeLiteral;
import org.ontoware.rdf2go.model.node.Resource;
import org.ontoware.rdf2go.model.node.URI;
import org.ontoware.rdf2go.vocabulary.XSD;

/**
 * this class encapsulates the RDF structure of availability observation
 * @author Jacek Kopecky
 *
 */
public class AvailabilityObservation {
    /**
     * the URI of the parking to which this observation pertains
     */
    public URI parkingURI;
    /**
     * the sampling time of the observation
     */
    public String date;
    /**
     * the binary availability of the observation
     */
    public boolean binaryAvailability;
    
    /**
     * parses availability observation from RDF (starting with the given node)
     * @param model an RDF graph
     * @param node the availability observation node to start from
     * @return filled-in availability observation or null if there is a problem (in which case errorMessage may be filled in)
     */
    public static AvailabilityObservation parse(Model model, Resource node) {
        AvailabilityObservation retval = new AvailabilityObservation();
        Resource submission = null;
        try {
            // parse the properties of parking:AvailabilityObservation
            for (Iterator<Statement> it = model.findStatements(node,  null, null); it.hasNext(); ) {
                Statement s = it.next();
                // check that it pertains to one car park
                if (s.getPredicate().equals(Config.SSN_featureOfInterest)) {
                    if (retval.parkingURI != null) {
                        errorMessage = "an observation cannot relate to more than one parking (through ssn:featureOfInterest)";
                        return null;
                    }
                    retval.parkingURI = s.getObject().asURI();
                } else if (s.getPredicate().equals(Config.SSN_observationSamplingTime)) {
                    if (retval.date != null) {
                        errorMessage = "there shouldn't be multiple observation sampling times";
                        return null;
                    }
                    if (!(s.getObject() instanceof DatatypeLiteral) || !s.getObject().asDatatypeLiteral().getDatatype().equals(XSD._dateTime)) {
                        errorMessage = "observation sampling time should be of xs:dateTime data type";
                        return null;
                    }
                    retval.date = s.getObject().asDatatypeLiteral().getValue();
                    // todo if the report is from the future, treat it as though it's from now
                    // todo in general, what happens if an incomplete observation is submitted?
                } else if (s.getPredicate().equals(Config.SSN_observationResult)) {
                    if (submission != null) {
                        errorMessage = "there shouldn't be multiple observation results";
                        return null;
                    }
                    submission = s.getObject().asResource();
                } // else ignoring unknown statements
            }
            if (submission == null) {
                errorMessage = "observation without ssn:observationResult";
                return null;
            }
            
            // parse the properties of parking:AvailabilitySubmission
            Resource ssnvalue = null;
            for (Iterator<Statement> it = model.findStatements(submission,  null, null); it.hasNext(); ) {
                Statement s = it.next();
                if (s.getPredicate().equals(Config.SSN_hasValue)) {
                    if (ssnvalue != null) {
                        errorMessage = "there shouldn't be multiple ssn:hasValue on the observation result";
                        return null;
                    }
                    ssnvalue = s.getObject().asResource();
                } // else ignoring unknown statements
            }
            if (ssnvalue == null) {
                errorMessage = "observation result without ssn:hasValue";
                return null;
            }
            
            // parse the properties of parking:AvailabilityEstimate
            // check that the availability record is of a supported type
            boolean availabilityRecognized = false;
            for (Iterator<Statement> it = model.findStatements(ssnvalue,  null, null); it.hasNext(); ) {
                Statement s = it.next();
                if (s.getPredicate().equals(Config.PARKING_binaryAvailability)) {
                    if (availabilityRecognized) {
                        errorMessage = "there shouldn't be multiple kinds of availability estimate value";
                        return null;
                    }
                    if (!s.getObject().asDatatypeLiteral().getDatatype().equals(XSD._boolean)) {
                        errorMessage = "binaryAvailability should be of xs:boolean data type";
                        return null;
                    }
                    retval.binaryAvailability = Boolean.parseBoolean(s.getObject().asDatatypeLiteral().getValue());
                    availabilityRecognized = true;
                } // else ignoring unknown statements
            }
            if (!availabilityRecognized) {
                errorMessage = "no recognized availability estimate type found";
                return null;
            }
    
            return retval;
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            errorMessage = sw.toString();
            return null;
        }
    }
    
    /**
     * when parse returns null, it may fill errorMessage with a description of why
     */
    public static String errorMessage = null;
}
