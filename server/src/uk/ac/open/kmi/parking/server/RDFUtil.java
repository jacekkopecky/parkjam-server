package uk.ac.open.kmi.parking.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.ontoware.rdf2go.RDF2Go;
import org.ontoware.rdf2go.model.Model;
import org.ontoware.rdf2go.model.Statement;
import org.ontoware.rdf2go.model.node.DatatypeLiteral;
import org.ontoware.rdf2go.model.node.Node;
import org.ontoware.rdf2go.model.node.Resource;
import org.ontoware.rdf2go.model.node.URI;
import org.ontoware.rdf2go.model.node.impl.URIImpl;
import org.ontoware.rdf2go.vocabulary.RDF;
import org.ontoware.rdf2go.vocabulary.XSD;

/**
 * utility functions and constants for RDF handling in the user API.
 * Definition: orphan is an RDF statement (a triple) about a resource that isn't
 * forward-reachable through statements from a given root resource, or a statement
 * about a resource that cuts forward reachability (it is in an ignored namespace).
 * Definition: forward-reachable is a transitive property that links a node with the objects
 * and predicates of all statements where the node is the subject.
 *
 * todo there should be a list of disallowed property namespaces that would make statements become orphans
 *
 * @author Jacek Kopecky
 *
 */
public class RDFUtil {
//    /**
//     * RDF class URI for Graph (a named graph resource on which GET/POST/PUT/DELETE may be allowed)
//     * todo fix namespace in all the following URIs
//     */
//    public static final URI GRAPH_CLASS = new URIImpl("http://jacek.cz/ns/rdf#Graph");
//    /**
//     * RDF property URI for the property that a graph contains a statement or a subgraph
//     */
//    public static final URI GRAPH_CONTAINS = new URIImpl("http://jacek.cz/ns/rdf#contains");
//
    /**
     * checks that the input data contains a single node of type classOfInterest
     * @param model the input data
     * @param classOfInterest the class of interest
     * @return the single node of type classOfInterest, or null if there are none or more than 1 such nodes
     */
    public static Resource checkBinstTriples(Model model, URI classOfInterest) {
        // todo may need to do some basic inference to avoid false negatives - can we leave that to the model?
        Resource retval = null;
        Iterator<Statement> it = model.findStatements(null, RDF.type, classOfInterest);
        while (it.hasNext()) {
            Statement stat = it.next();
            if (retval != null && !retval.equals(stat.getSubject())) {
                return null;
            }
            retval = stat.getSubject();
        }
        return retval;
    }

    /**
     * checks that the input data contains a single node from which all the other statements go
     * @param model the input data
     * @return the single node, or null if there are none or more than 1 such nodes
     */
    public static Resource checkInstTriples(Model model) {
        Set<Resource> subjects = new HashSet<Resource>();
        Set<Resource> objects = new HashSet<Resource>();
        for (Statement stat : model) {
            subjects.add(stat.getSubject());
            Node object = stat.getObject();
            if (object instanceof Resource) {
                objects.add(object.asResource());
            }
        }
        subjects.removeAll(objects);
        if (subjects.size() == 1) {
            return subjects.iterator().next();
        }
        return null;
    }

    private static boolean isInIgnoredNamespace(Node node, List<String> ignoredNamespaces) {
        if (node instanceof URI) {
            String uri = ((URI)node).toString();
            for (String ns : ignoredNamespaces) {
                if (uri.startsWith(ns)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns a model that contains only statements that are forward-reachable from the resource root through the objects and predicates of statements (no orphans)
     * @param data the input model which may contain orphans; the model must be open
     * @param root the root node for identifying orphans (triples not forward-reachable from the root)
     * @param predicateReachability if true, forward-reachability follows predicates as well, if false, only statement objects
     * @param ignoredNamespaces a set of namespace strings; nodes in these namespaces are treated as leaves in the tree (any statements about them will be orphans)
     * @return a new in-memory model without orphans; the returned model will be closed
     */
    public static Model getTreeWithoutOrphans(Model data, Resource root, boolean predicateReachability, List<String> ignoredNamespaces) {
        Model orphans = getOrphans(data, root, predicateReachability, ignoredNamespaces);
        orphans.open();
        Model retval = RDF2Go.getModelFactory().createModel();
        retval.open();
        retval.addModel(data);
        retval.removeAll(orphans.iterator());
        retval.close();
        orphans.close();
        return retval;
    }

    /**
     * Returns a model that contains only statements that are forward-reachable
     * from the resource root through the statement <code>through</code> and
     * then through the objects and predicates of statements (no orphans)
     *
     * @param data
     *            the input model which may contain orphans; the model must be open
     * @param through
     *            the only statement on the root that should be followed, it
     *            also identifies the root of the orphan-free tree
     * @param predicateReachability
     *            if true, forward-reachability follows predicates as well, if
     *            false, only statement objects
     * @param ignoredNamespaces
     *            a set of namespace strings; nodes in these namespaces are
     *            treated as leaves in the tree (any statements about them will
     *            be orphans)
     * @return a new in-memory model without orphans; the returned model will be closed
     */
    public static Model getSubtreeWithoutOrphans(Model data, Statement through, boolean predicateReachability, List<String> ignoredNamespaces) {
        Model orphans = getOrphans(data, through.getSubject(), through, predicateReachability, ignoredNamespaces);
        orphans.open();
        Model retval = RDF2Go.getModelFactory().createModel();
        retval.open();
        retval.addModel(data);
        retval.removeAll(orphans.iterator());
        retval.close();
        orphans.close();
        return retval;
    }

    /**
     * Returns a model that contains only orphans (statements that are NOT forward-reachable from the resource root through the objects and predicates of statements)
     * @param data the input model which may contain orphans; the model must be open
     * @param root the root node for identifying orphans (triples not forward-reachable from the root)
     * @param predicateReachability if true, forward-reachability follows predicates as well, if false, only statement objects
     * @param ignoredNamespaces a set of namespace strings; nodes in these namespaces are treated as leaves in the tree (any statements about them will be orphans)
     * @return a new in-memory model containing only the orphans; the returned model will be closed
     */
    public static Model getOrphans(Model data, Resource root, boolean predicateReachability, List<String> ignoredNamespaces) {
        return getOrphans(data, root, null, predicateReachability, ignoredNamespaces);
    }

    /**
     * like the public getOrphans, but considers all statements on root other than <code>through</code> (if not null) to be orphans as well
     */
    private static Model getOrphans(Model data, Resource root, Statement through, boolean predicateReachability, List<String> ignoredNamespaces) {
        Model retval = RDF2Go.getModelFactory().createModel();
        retval.open();
        retval.addModel(data);
        if (through != null) {
            retval.removeStatement(through);
        }
        Set<Resource> reachable = new HashSet<Resource>();
        Set<Resource> done = new HashSet<Resource>();
        if (through == null) {
            reachable.add(root);
        } else {
            done.add(root);
            Node object = through.getObject();
            if (object instanceof Resource && !isInIgnoredNamespace(object, ignoredNamespaces)) {
                reachable.add((Resource)object);
            }
        }
        List<Statement> toRemove = new ArrayList<Statement>((int)retval.size());

        while (reachable.size() > 0) {
            toRemove.clear();
            done.addAll(reachable); // in this iteration, all the statements about the reachable nodes will be removed
            for (Statement stat : retval) {
                if (reachable.contains(stat.getSubject())) {
                    toRemove.add(stat);
                    Node object = stat.getObject();
                    if (object instanceof Resource && !isInIgnoredNamespace(object, ignoredNamespaces)) {
                        reachable.add((Resource)object);
                    }
                    if (predicateReachability && !isInIgnoredNamespace(stat.getPredicate(), ignoredNamespaces)) {
                        reachable.add(stat.getPredicate()); // if the graph contains any information about the predicates, we may not want to lose it
                    }
                }
            }
            reachable.removeAll(done); // retain only those that are new in this iteration
            retval.removeAll(toRemove.iterator());
        }
        retval.close();
        return retval;
    }

//    /**
//     * returns a new model that is a copy of the old one, with all the API reifications (graphs and statements) removed from it
//     * @param data the input model
//     * @return a closed model that has all the data from the input model except for the reifications
//     */
//    public static Model removeReifications(Model data) {
//        Model retval = RDF2Go.getModelFactory().createModel();
//        retval.open();
//        retval.addModel(data);
//
//        List<Statement> statementsToDrop = new ArrayList<Statement>((int) data.size());
//
//        for (Statement stat : data) {
//            if ((RDF.type.equals(stat.getPredicate()) && (GRAPH_CLASS.equals(stat.getObject()) || RDF.Statement.equals(stat.getObject()))) ||
//                    GRAPH_CONTAINS.equals(stat.getPredicate()) ||
//                    RDF.subject.equals(stat.getPredicate()) ||
//                    RDF.predicate.equals(stat.getPredicate()) ||
//                    RDF.object.equals(stat.getPredicate())) {
//                statementsToDrop.add(stat);
//            }
//        }
//
//        retval.removeAll(statementsToDrop.iterator());
//
//        retval.close();
//        return retval;
//    }
//
    private static final long GRANULARITY = 1000; // a new ID will be generated every this many milliseconds - if a runtime restart takes less than this, newID is not guaranteed to be unique
    private static final long START = 1337440824000l / GRANULARITY; // the current millisecond (div granularity) count since epoch when writing this code
    private static long oldID = START;
    private static long subID = 0;

    /**
     * creates a new unique string containing decimal number (maybe with a
     * period); this method expects that any runtime restart will take more than
     * a second, then uniqueness is guaranteed
     * @return a new unique string
     */
    public synchronized static String newID() {
        long time = System.currentTimeMillis() / GRANULARITY;
        StringBuilder sb = new StringBuilder(Long.toString(time - START));
        if (oldID == time) {
            sb.append('.');
            sb.append(Long.toString(++subID));
        } else {
            oldID = time;
            subID = 0;
        }
        return sb.toString();
    }

    /**
     * creates an instance URI given a class short name and the instance ID; so far it's trivial, but it could add a fragment ID
     * @param base the base URI of the application
     * @param classShortName the short name of the class
     * @param instID the instance ID
     * @return the instance URI
     * todo if instanceURI == instanceGraphURI then getTreeWithoutOrphans from instanceURI will also get all the reifications and thus also follow through all the predicates, ignoring predicateReachability (but it's true everywhere anyway now)
     */
    public static URI getInstanceURI(java.net.URI base, String classShortName, String instID) {
        return new URIImpl(base.resolve("./" + classShortName + "/" + instID).toString());
//        or to avoid conflating the user and the graph       </api/users/1#this> a :User .
//                                                </api/users/1> a graph; contains { </api/users/1#this> a :User }
// and add all the proper reifications and handle all the new orphans
//        also when instanceURI != instanceGraphURI then maybe add statement instanceGraphURI about instanceURI (where to get 'about' from?)
    }

    /**
     * creates an instance graph URI given a class short name and the instance ID
     * @param base the base URI of the application
     * @param classShortName the short name of the class
     * @param instID the instance ID
     * @return the instance graph URI
     */
    public static URI getInstanceGraphURI(java.net.URI base, String classShortName, String instID) {
        return new URIImpl(base.resolve("./" + classShortName + "/" + instID).toString());
    }

    /**
     * creates a property graph URI given a class short name, the instance ID and the property shortname
     * @param base the base URI of the application
     * @param classShortName the short name of the class
     * @param instID the instance ID
     * @param propertyShortName the short name of the property
     * @return the property graph URI
     */
    public static URI getPropertyGraphURI(java.net.URI base, String classShortName, String instID, String propertyShortName) {
        return new URIImpl(base.resolve("./" + classShortName + "/" + instID + "/" + propertyShortName).toString());
    }

    /**
     * creates a property value graph URI given a class short name, the instance ID and the property shortname
     * @param base the base URI of the application
     * @param classShortName the short name of the class
     * @param instID the instance ID
     * @param propertyShortName the short name of the property
     * @param valID the value ID
     * @return the property value graph URI
     */
    public static URI getPropertyValueGraphURI(java.net.URI base, String classShortName, String instID, String propertyShortName, String valID) {
        return new URIImpl(base.resolve("./" + classShortName + "/" + instID + "/" + propertyShortName + "/" + valID).toString());
    }

//    /**
//     * Creates reifications for standalone classes, properties of interest and their values.
//     * It uses the configuration from the Config class.
//     * @param data statements for which reifications should be created, this model must be open
//     * @param base the base URI of the application
//     * @param classShortName the short name of the standalone class
//     * @param instID the string ID of the instance
//     * @return reification and graph statements, the returned model will be closed
//     */
//    public static Model createReifications(Model data, java.net.URI base, String classShortName, String instID) {
//        URI graphURI = getInstanceGraphURI(base, classShortName, instID);
//        URI instURI = getInstanceURI(base, classShortName, instID);
//
//        Model retval = RDF2Go.getModelFactory().createModel();
//        retval.open();
//
//        List<String> props = Config.listPropertyShortnamesOfInterest(classShortName);
//        for (String prop : props) {
//            URI propGraphURI = getPropertyGraphURI(base, classShortName, instID, prop);
//            if (data.contains(propGraphURI, RDF.type, GRAPH_CLASS)) {
//                continue;
//            }
//            retval.addStatement(graphURI, GRAPH_CONTAINS, propGraphURI);
//            retval.addStatement(propGraphURI, RDF.type, GRAPH_CLASS);
//            retval.addStatement(propGraphURI, GRAPH_CONTAINS,
//                    retval.addReificationOf(retval.createStatement(instURI, Config.getPropertyURI(classShortName, prop), retval.createBlankNode())));
//        }
//
//        Set<URI> propURIs = Config.listPropertyURIsOfInterest(classShortName);
//        Iterator<Statement> stats = data.findStatements(instURI, null, null);
//        while (stats.hasNext()) {
//            Statement stat = stats.next();
//            if (isStatementIgnoredInReification(stat)) {
//                continue;
//            }
//            if (data.hasReifications(stat)) {
//                // already reified and therefore doesn't need a new reification
//                continue;
//            }
//            URI propURI = stat.getPredicate();
//            URI propValURI;
//            URI propGraphURI;
//            if (propURIs.contains(propURI)) {
//                String prop = Config.getPropertyShortname(classShortName, propURI);
//                propValURI = getPropertyValueGraphURI(base, classShortName, instID, prop, newID());
//                propGraphURI = getPropertyGraphURI(base, classShortName, instID, prop);
//            } else {
//                String shortname = Config.getWildcardPropertyShortname(classShortName);
//                if (shortname == null) {
//                    // todo this check could probably go elsewhere, plus there should also be a check for other properties that could be present in the data and ignored in reification (see isStatementIgnoredInReification)
//                    throw new WebApplicationException(
//                            Response.
//                                status(403).
//                                entity("input data about instances of class " +
//                                        Config.getStandaloneClassURI(classShortName) +
//                                        " must not contain properties other than the explicitly allowed ones in this API").
//                                type("text/plain").
//                                build());
//                }
//                propValURI = getPropertyValueGraphURI(base, classShortName, instID, shortname, newID());
//                propGraphURI = getInstanceGraphURI(base, classShortName, instID);
//            }
//            retval.addStatement(propGraphURI, GRAPH_CONTAINS, propValURI);
//            retval.addStatement(propValURI, RDF.type, GRAPH_CLASS);
//            retval.addStatement(propValURI, GRAPH_CONTAINS, retval.addReificationOf(stat));
//        }
//        retval.close();
//        return retval;
//    }
//
//    private static boolean isStatementIgnoredInReification(Statement stat) {
//        URI pred = stat.getPredicate();
//        if (GRAPH_CONTAINS.equals(pred)) {
//            return true;
//        }
//        if (RDF.type.equals(pred)) {
//            return true;
//            // todo maybe some type statements might be of interest, then pick out only some, like below
////            Node obj = stat.getObject();
////            if (GRAPH_CLASS.equals(obj)) {
////                return true;
////            }
//        }
//        return false;
//    }

    /**
     * checks whether a model contains the RDF reification vocabulary, i.e., whether it contains reifications
     * @param data an open model
     * @return true if the model contains reifications, false if not
     */
    public static boolean hasReifications(Model data) {
        // todo maybe we could put the internal reifications in our own namespace and guard against that in the input data; then normal reifications would be allowed
        if (data.contains(null, RDF.type, RDF.Statement) ||
                data.contains(null, RDF.subject, (Node)null) ||
                data.contains(null, RDF.object, (Node)null) ||
                data.contains(null, RDF.predicate, (Node)null)) {
            return true;
        }
        return false;
    }

    /**
     * parses text/uri-list into a list of URIs.
     * @param urilist a string that contains data in the text/uri-list format
     * @return a list of URIs
     */
    public static List<URI> parseURIList(String urilist) {
        List<URI> retval = new ArrayList<URI>();
        BufferedReader br = new BufferedReader(new StringReader(urilist));
        try {
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                if (!line.startsWith("#")) {
                    retval.add(new URIImpl(line.trim()));
                }
            }
        } catch (IOException e) {
            // should never happen
            throw new RuntimeException(e);
        }
        return retval;
    }

    /**
     * checks that there is a single value for the given property in the model, returns the value as double
     * @param model the model in which to check
     * @param resource the resource that should have the value
     * @param prop the property we're looking for
     * @param propname the name of the property to use in error messages
     * @return the value, if there is only one
     * @throws WebApplicationException when there's none or more-than-one value or if it can't be parsed as double
     */
    public static double extractDouble(Model model, Resource resource, URI prop, String propname) throws WebApplicationException {
        Iterator<Statement> stats = model.findStatements(resource, prop, null);
        if (stats.hasNext()) {
            Statement stat = stats.next();
            if (stats.hasNext()) {
                model.close();
                throw new WebApplicationException(
                        Response.status(403).
                            entity("input data gives more than one " + propname + "\n").
                            type("text/plain").
                            build());
            }
            Node val = stat.getObject();
            if (!(val instanceof DatatypeLiteral) || !val.asDatatypeLiteral().getDatatype().equals(XSD._double)) {
                model.close();
                throw new WebApplicationException(
                        Response.status(403).
                            entity(propname + " must be an XSD:double datatyped literal\n").
                            type("text/plain").
                            build());
            }
            try {
                return Double.parseDouble(val.asDatatypeLiteral().getValue());
            } catch (NumberFormatException e) {
                model.close();
                throw new WebApplicationException(
                        Response.status(403).
                            entity(propname + " '" + val.asDatatypeLiteral().getValue() + "' cannot be parsed as a valid double value\n").
                            type("text/plain").
                            build());
            }
        } else {
            model.close();
            throw new WebApplicationException(
                    Response.status(403).
                        entity("input data doesn't specify a " + propname + "\n").
                        type("text/plain").
                        build());
        }
    }
}
