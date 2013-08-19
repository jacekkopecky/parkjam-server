package uk.ac.open.kmi.parking.server;

import java.util.ArrayList;
import java.util.List;

import org.ontoware.rdf2go.exception.ModelRuntimeException;
import org.ontoware.rdf2go.model.node.URI;
import org.ontoware.rdf2go.model.node.impl.URIImpl;
import org.ontoware.rdf2go.vocabulary.OWL;
import org.ontoware.rdf2go.vocabulary.RDF;
import org.ontoware.rdf2go.vocabulary.RDFS;
import org.openrdf.rdf2go.RepositoryModel;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.http.HTTPRepository;

import uk.ac.open.kmi.parking.ontology.Ontology;

/**
 * Configuration for the server, so far hardwired.
 * @author Jacek Kopecky
 *
 */
@SuppressWarnings("javadoc")
public final class Config {
    private Config() {
        // instantiation forbidden
    }

    // manual configuration
    private static final String repositoryServerURI = "http://localhost:8080/openrdf-sesame";
    private static final String repositoryName = "parking";  // was tmp2
    private static final HTTPRepository sesameRepository = new HTTPRepository(repositoryServerURI, repositoryName);

    public static final String LOG_FILE_PATH = "logs/update_request_log";

    public static final int MAXIMUM_SUBMITTED_CARPARK_TRIPLES = 200;

//    private static final java.net.URI serverURI = java.net.URI.create("http://parking.kmi.open.ac.uk/data/");
    public static final URI AVAILABILITY_CONTEXT = new URIImpl("http://parking.kmi.open.ac.uk/internal/context/availability/");
    public static final URI PARKING_CONTEXT = new URIImpl("http://parking.kmi.open.ac.uk/internal/context/static-parking-info/");
    public static final URI PARKING_MODERATION_CONTEXT = new URIImpl("http://parking.kmi.open.ac.uk/internal/context/static-parking-info-moderation/");

    public static final URI SSN_featureOfInterest = new URIImpl(Ontology.SSN_featureOfInterest);
    public static final URI SSN_observationSamplingTime = new URIImpl(Ontology.SSN_observationSamplingTime);
    public static final URI SSN_observationResult = new URIImpl(Ontology.SSN_observationResult);
    public static final URI SSN_hasValue = new URIImpl(Ontology.SSN_hasValue);

    public static final URI PARKING_UnverifiedInstance = new URIImpl(Ontology.PARKING_UnverifiedInstance);
    public static final URI PARKING_AvailabilityObservation = new URIImpl(Ontology.PARKING_AvailabilityObservation);
    public static final URI PARKING_AvailabilitySubmission = new URIImpl(Ontology.PARKING_AvailabilitySubmission);
    public static final URI PARKING_AvailabilityEstimate = new URIImpl(Ontology.PARKING_AvailabilityEstimate);
    public static final URI PARKING_binaryAvailability = new URIImpl(Ontology.PARKING_binaryAvailability);
    public static final URI PARKING_binaryAvailabilityTimestamp = new URIImpl(Ontology.PARKING_binaryAvailabilityTimestamp);
    public static final URI PARKING_hasUnverifiedProperties = new URIImpl(Ontology.PARKING_hasUnverifiedProperties);
    public static final URI PARKING_submissionTimestamp = new URIImpl(Ontology.PARKING_submissionTimestamp);
    public static final URI PARKING_hasStatement = new URIImpl(Ontology.PARKING_hasStatement);
    public static final URI PARKING_submittedBy = new URIImpl(Ontology.PARKING_submittedBy);
    public static final URI PARKING_anonymousSubmitter = new URIImpl(Ontology.PARKING_anonymousSubmitter);
    public static final URI PARKING_carCountingAvailability = new URIImpl(Ontology.PARKING_carCountingAvailability);
    public static final URI PARKING_closed = new URIImpl(Ontology.PARKING_closed);
    public static final URI PARKING_carCountingAvailabilityTimestamp = new URIImpl(Ontology.PARKING_carCountingAvailabilityTimestamp);
    public static final URI PARKING_closedTimestamp = new URIImpl(Ontology.PARKING_closedTimestamp);

    public static final URI LGO_Parking = new URIImpl(Ontology.LGO_Parking);

    public static final URI GEOPOS_lat = new URIImpl(Ontology.GEOPOS_lat);
    public static final URI GEOPOS_long = new URIImpl(Ontology.GEOPOS_long);

    public static final URI PROV_hadOriginalSource = new URIImpl(Ontology.PROV_hadOriginalSource);

    public static final URI OWLIM_flush = new URIImpl("http://www.ontotext.com/owlim/system#flush");


    /**
     * namespaces of nodes that are not followed through when looking for orphans (i.e., statements about nodes in these namespaces are orphans)
     */
    public static final List<String> ignoredNamespaces = new ArrayList<String>(20);

    static {
        ignoredNamespaces.add(RDF.RDF_NS);
        ignoredNamespaces.add(RDFS.RDFS_NS);
        ignoredNamespaces.add(OWL.OWL_NS);
        ignoredNamespaces.add(Ontology.DC_NS);
        ignoredNamespaces.add(Ontology.GEOPOS_NS);
        ignoredNamespaces.add(Ontology.GEORSS_NS);
        ignoredNamespaces.add(Ontology.LGO_NS);
        ignoredNamespaces.add(Ontology.LGP_NS);
        ignoredNamespaces.add(Ontology.OMGEO_NS);
//        ignoredNamespaces.add(Ontology.PARKING_NS); // obsoleted below
        ignoredNamespaces.add(Ontology.SSN_NS);
        ignoredNamespaces.add(Ontology.VIRTRDF_NS);
        ignoredNamespaces.add("http://parking.kmi.open.ac.uk/");
    }

    /**
     * creates and opens a model that attaches to the user management repository
     * @param context an optional URI for the repository context (subgraph), may be null
     * @return an open repository model
     */
    public static MyRepositoryModel openRepositoryModel(URI context) {
        MyRepositoryModel result = null;
        if (context == null) {
            result = new MyRepositoryModel(sesameRepository);
        } else {
            result = new MyRepositoryModel(context, sesameRepository);
        }
        result.open();
        return result;
    }

    /**
     * an extension of RepositoryModel that gives access to the underlying sesame repository connection
     */
    public static class MyRepositoryModel extends RepositoryModel {

        private static final long serialVersionUID = 1L;

        /**
         * @param repository repo
         * @throws ModelRuntimeException when something goes wrong
         */
        public MyRepositoryModel(Repository repository) throws ModelRuntimeException {
            super(repository);
        }

        /**
         * @param context context
         * @param repository repo
         */
        public MyRepositoryModel(URI context, HTTPRepository repository) {
            super(context, repository);
        }

        /**
         * @return the underlying sesame repository connection, esp. useful for querying
         */
        public RepositoryConnection getConnection() {
            return this.connection;
        }
    }

    /**
     * close a model created by openRepositoryModel()
     * @param modelToClose the model to close
     */
    public static void closeRepositoryModel(RepositoryModel modelToClose) {
        modelToClose.close();
    }


//    /**
//     * returns the base URI for the application, so far hardwired
//     * @param ctxt uri context
//     * @return the base URI
//     */
//    public static java.net.URI getAppURI(@SuppressWarnings("unused") UriInfo ctxt) {
//        //  this should come from ctxt.getBaseURI() but that currently returns localhost:8080 which isn't right
//        return serverURI;
//    }

}
