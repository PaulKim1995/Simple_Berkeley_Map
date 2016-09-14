import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.*;

/**
 *  Parses OSM XML files using an XML SAX parser. Used to construct the graph of roads for
 *  pathfinding, under some constraints.
 *  See OSM documentation on
 *  <a href="http://wiki.openstreetmap.org/wiki/Key:highway">the highway tag</a>,
 *  <a href="http://wiki.openstreetmap.org/wiki/Way">the way XML element</a>,
 *  <a href="http://wiki.openstreetmap.org/wiki/Node">the node XML element</a>,
 *  and the java
 *  <a href="https://docs.oracle.com/javase/tutorial/jaxp/sax/parsing.html">SAX parser tutorial</a>.
 *  @author Alan Yao
 */
public class MapDBHandler extends DefaultHandler {
    /**
     * Only allow for non-service roads; this prevents going on pedestrian streets as much as
     * possible. Note that in Berkeley, many of the campus roads are tagged as motor vehicle
     * roads, but in practice we walk all over them with such impunity that we forget cars can
     * actually drive on them.
     */
    private static final Set<String> ALLOWED_HIGHWAY_TYPES = new HashSet<>(Arrays.asList
            ("motorway", "trunk", "primary", "secondary", "tertiary", "unclassified",
                    "residential", "living_street", "motorway_link", "trunk_link", "primary_link",
                    "secondary_link", "tertiary_link"));
    private String activeState = "";
    private final GraphDB g;

    private HashMap<Long, Point> idPoint;
    private Point lastPoint;
    private int currentAddress;
    private double lat;
    private double lon;
    private long id;
    private LinkedList<Point> tempList;
    private boolean allowedHighway;

    public MapDBHandler(GraphDB g) {
        this.g = g;
        this.idPoint = new HashMap<>();
        this.lastPoint = null;
        this.currentAddress = 0;
        this.tempList = new LinkedList<>();
        this.allowedHighway = false;

    }

    /**
     * Called at the beginning of an element. Typically, you will want to handle each element in
     * here, and you may want to track the parent element.
     * @param uri The Namespace URI, or the empty string if the element has no Namespace URI or
     *            if Namespace processing is not being performed.
     * @param localName The local name (without prefix), or the empty string if Namespace
     *                  processing is not being performed.
     * @param qName The qualified name (with prefix), or the empty string if qualified names are
     *              not available. This tells us which element we're looking at.
     * @param attributes The attributes attached to the element. If there are no attributes, it
     *                   shall be an empty Attributes object.
     * @throws SAXException Any SAX exception, possibly wrapping another exception.
     * @see Attributes
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        if (qName.equals("node")) {
            activeState = "node";
            id = Long.parseLong(attributes.getValue("id"));
            lat = Double.parseDouble(attributes.getValue("lat"));
            lon = Double.parseDouble(attributes.getValue("lon"));
            Point node = new Point(lat, lon, id);
            idPoint.put(id, node);
        }
        if (qName.equals("way")) {
            activeState = "way";
            lastPoint = null;
            tempList = new LinkedList<>();
            allowedHighway = false;
        }
        if (activeState.equals("way") && qName.equals("nd")) {
            long idT = Long.parseLong(attributes.getValue("ref"));
            Point curr = idPoint.get(idT);
            tempList.add(curr);
        }
        if (activeState.equals("node") && qName.equals("tag")) {
            String key = attributes.getValue("k");
            String value = attributes.getValue("v");
            if (key.equals("name")) {
                String name = value;
                String cleanedName = GraphDB.cleanString(name);
                // g.autocompleteTrie.add(cleanedName);
                g.getTrieAgain().addWord(name);

                Map<String, Object> addMap = new HashMap<>();
                addMap.put("lat", lat);
                addMap.put("lon", lon);
                addMap.put("name", name);
                addMap.put("id", id);

                if (g.getSearch().containsKey(name)) {
                    g.getSearch().get(name).add(addMap);
                } else {
                    LinkedList<Map<String, Object>> addLink = new LinkedList<>();
                    addLink.add(addMap);
                    g.getSearch().put(cleanedName, addLink);
                }
            }
        }
        if (activeState.equals("way") && qName.equals("tag")) {
            String key = attributes.getValue("k");
            String value = attributes.getValue("v");
            if (key.equals("highway") && ALLOWED_HIGHWAY_TYPES.contains(value)) {
                allowedHighway = true;
            }
        }
    }
        //might need to do stuff regarding Tries for autocomplete here


        /* Some example code on how you might begin to parse XML files. *//*
        if (qName.equals("node")) {
            activeState = "node";
        } else if (qName.equals("way")) {
            activeState = "way";
//            System.out.println("Beginning a way...");
        } else if (activeState.equals("way") && qName.equals("tag")) {
            String k = attributes.getValue("k");
            String v = attributes.getValue("v");
//            System.out.println("Tag with k=" + k + ", v=" + v + ".");
        } else if (activeState.equals("node") && qName.equals("tag") && attributes.getValue("k")
                .equals("name")) {
            System.out.println("Node with name: " + attributes.getValue("v"));
        }*/

    /**
     * Receive notification of the end of an element. You may want to take specific terminating
     * actions here, like finalizing vertices or edges found.
     * @param uri The Namespace URI, or the empty string if the element has no Namespace URI or
     *            if Namespace processing is not being performed.
     * @param localName The local name (without prefix), or the empty string if Namespace
     *                  processing is not being performed.
     * @param qName The qualified name (with prefix), or the empty string if qualified names are
     *              not available.
     * @throws SAXException  Any SAX exception, possibly wrapping another exception.
     */
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (qName.equals("way") && allowedHighway) {
            Iterator<Point> pointIter = tempList.iterator();
            while (pointIter.hasNext()) {
                Point curr = pointIter.next();
                if (!g.getVertexAddress().containsKey(curr)) {
                    g.getVertexAddress().put(curr, currentAddress);
                    currentAddress++;
                }
                if (lastPoint == null) {
                    lastPoint = curr;
                } else {
                    g.addEdge(lastPoint, curr, Point.distance(lastPoint, curr));
                    lastPoint = curr;
                }
            }
        }
    }
}
