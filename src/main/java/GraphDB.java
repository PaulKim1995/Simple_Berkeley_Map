import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.*;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Wraps the parsing functionality of the MapDBHandler as an example.
 * You may choose to add to the functionality of this class if you wish.
 * @author Alan Yao
 */
public class GraphDB {

    private LinkedList<Edge>[] adjLists;
    private HashMap<Point, Integer> vertexAddress;
    private Trie autocompleteTrie;
    private HashMap<String, LinkedList<Map<String, Object>>> search;
    private DictionaryBasedTrie trieAgain;

    //we may need to override Point::equals and hashCode
    //startVertex will be defined by each new created iterator!



    /**
     * Example constructor shows how to create and start an XML parser.
     * @param dbPath Path to the XML file to be parsed.
     */
    public GraphDB(String dbPath) {
        this.adjLists = new LinkedList[30000];
        for (int i = 0; i < adjLists.length; i++) {
            adjLists[i] = new LinkedList<Edge>();
        }
        this.autocompleteTrie = new Trie();
        this.search = new HashMap<>();
        this.vertexAddress = new HashMap<>();
        this.trieAgain = new DictionaryBasedTrie();

        try {
            File inputFile = new File(dbPath);
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            MapDBHandler maphandler = new MapDBHandler(this);
            saxParser.parse(inputFile, maphandler);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
        clean();

    }

    public LinkedList<Edge>[] getAdjLists() {
        return adjLists;
    }

    public HashMap<Point, Integer> getVertexAddress() {
        return vertexAddress;
    }

    public HashMap<String, LinkedList<Map<String, Object>>> getSearch() {
        return search;
    }

    public DictionaryBasedTrie getTrieAgain() {
        return trieAgain;
    }

    /**
     * Helper to process strings into their "cleaned" form, ignoring punctuation and capitalization.
     * @param s Input string.
     * @return Cleaned string.
     */
    static String cleanString(String s) {
        return s.replaceAll("[^a-zA-Z ]", "").toLowerCase();
    }

    /**
     *  Remove nodes with no connections from the graph.
     *  While this does not guarantee that any two nodes in the remaining graph are connected,
     *  we can reasonably assume this since typically roads are connected.
     */
    private void clean() {
    }

    public void addEdge(Point from, Point to, double distance) {
        adjLists[vertexAddress.get(from)].add(new Edge(from, to, distance));
        adjLists[vertexAddress.get(to)].add(new Edge(to, from, distance));
    }

    public boolean isAdjacent(Point from, Point to) {
        //precondition: point from is connected (has at least one adjacent node)
        int index = 0;
        while (index < adjLists[vertexAddress.get(from)].size()) {
            if (adjLists[vertexAddress.get(from)].get(index).to().equals(to)) return true;
        }
        return false;
    }

    public List neighbors(Point vertex) {
        List<Point> vertices = new ArrayList<>();
        int index = 0;
        while (index < this.adjLists[this.vertexAddress.get(vertex)].size()) {
            vertices.add(this.adjLists[this.vertexAddress.get(vertex)].get(index).to());
            index++;
        }
        return vertices;
    }

    public int inDegree(Point vertex) {
        return adjLists[vertexAddress.get(vertex)].size();
    }




}
