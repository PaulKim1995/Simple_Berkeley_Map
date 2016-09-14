import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
//import java.lang.reflect.Array;
//import java.nio.Buffer;
//import java.lang.reflect.Array;
import java.util.*;
import java.util.List;

/* Maven is used to pull in these dependencies. */
import com.google.gson.Gson;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import static spark.Spark.*;

/**
 * This MapServer class is the entry point for running the JavaSpark web server for the BearMaps
 * application project, receiving API calls, handling the API call processing, and generating
 * requested images and routes.
 * @author Alan Yao
 */
public class MapServer {
    /**
     * The root upper left/lower right longitudes and latitudes represent the bounding box of
     * the root tile, as the images in the img/ folder are scraped.
     * Longitude == x-axis; latitude == y-axis.
     */
    public static final double ROOT_ULLAT = 37.892195547244356, ROOT_ULLON = -122.2998046875,
            ROOT_LRLAT = 37.82280243352756, ROOT_LRLON = -122.2119140625;

    /** Each tile is 256x256 pixels. */
    public static final int TILE_SIZE = 256;

    /** HTTP failed response. */
    private static final int HALT_RESPONSE = 403;

    /** Route stroke information: typically roads are not more than 5px wide. */
    public static final float ROUTE_STROKE_WIDTH_PX = 5.0f;

    /** Route stroke information: Cyan with half transparency. */
    public static final Color ROUTE_STROKE_COLOR = new Color(108, 181, 230, 200);

    /** The tile images are in the IMG_ROOT folder. */
    private static final String IMG_ROOT = "img/";

    /**
     * The OSM XML file path. Downloaded from <a href="http://download.bbbike.org/osm/">here</a>
     * using custom region selection.
     **/
    private static final String OSM_DB_PATH = "berkeley.osm";

    /**
     * Each raster request to the server will have the following parameters
     * as keys in the params map accessible by,
     * i.e., params.get("ullat") inside getMapRaster(). <br>
     * ullat -> upper left corner latitude,<br> ullon -> upper left corner longitude, <br>
     * lrlat -> lower right corner latitude,<br> lrlon -> lower right corner longitude <br>
     * w -> user viewport window width in pixels,<br> h -> user viewport height in pixels.
     **/
    private static final String[] REQUIRED_RASTER_REQUEST_PARAMS =
    {"ullat", "ullon", "lrlat", "lrlon", "w", "h"};


    /**
     * Each route request to the server will have the following parameters
     * as keys in the params map.<br>
     * start_lat -> start point latitude,<br> start_lon -> start point longitude,<br>
     * end_lat -> end point latitude, <br>end_lon -> end point longitude.
     **/
    private static final String[] REQUIRED_ROUTE_REQUEST_PARAMS =
    {"start_lat", "start_lon", "end_lat", "end_lon"};

    /* Define any static variables here. Do not define any instance variables of MapServer. */
    private static GraphDB g;

    //initialize quadtree
    private static QuadTree imgTree;

    private static HashMap<String, BufferedImage> seenImages;

    private static ArrayList<Point> routeToDraw;

    /**
     * Place any initialization statements that will be run before the server main loop here.
     * Do not place it in the main function. Do not place initialization code anywhere else.
     * This is for testing purposes, and you may fail tests otherwise.
     **/
    public static void initialize() {
        g = new GraphDB(OSM_DB_PATH);
        imgTree = new QuadTree(ROOT_ULLAT, ROOT_ULLON, ROOT_LRLAT, ROOT_LRLON);
        seenImages = new HashMap<>();

    }

    public static void main(String[] args) {
        initialize();
        staticFileLocation("/page");
        /* Allow for all origin requests (since this is not an authenticated server, we do not
         * care about CSRF).  */
        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Request-Method", "*");
            response.header("Access-Control-Allow-Headers", "*");
        });

        /* Define the raster endpoint for HTTP GET requests. I use anonymous functions to define
         * the request handlers. */
        get("/raster", (req, res) -> {
            HashMap<String, Double> rasterParams =
                    getRequestParams(req, REQUIRED_RASTER_REQUEST_PARAMS);
            /* Required to have valid raster params */
            validateRequestParameters(rasterParams, REQUIRED_RASTER_REQUEST_PARAMS);
            /* The png image is written to the ByteArrayOutputStream */
            Map<String, Object> rasteredImgParams = new HashMap<>();
            /* getMapRaster() does almost all the work for this API call */
            BufferedImage im = getMapRaster(rasterParams, rasteredImgParams);
            /* Check if we have routing parameters. */
            HashMap<String, Double> routeParams =
                    getRequestParams(req, REQUIRED_ROUTE_REQUEST_PARAMS);
            /* If we do, draw the route too. */
            if (hasRequestParameters(routeParams, REQUIRED_ROUTE_REQUEST_PARAMS)) {
                findAndDrawRoute(routeParams, rasteredImgParams, im);
            }
            /* On an image query success, add the image data to the response */
            if (rasteredImgParams.containsKey("query_success")
                    && (Boolean) rasteredImgParams.get("query_success")) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                writeJpgToStream(im, os);
                String encodedImage = Base64.getEncoder().encodeToString(os.toByteArray());
                rasteredImgParams.put("b64_encoded_image_data", encodedImage);
                os.flush();
                os.close();
            }
            /* Encode response to Json */
            Gson gson = new Gson();
            return gson.toJson(rasteredImgParams);
        });

        /* Define the API endpoint for search */
        get("/search", (req, res) -> {
            Set<String> reqParams = req.queryParams();
            String term = req.queryParams("term");
            Gson gson = new Gson();
            /* Search for actual location data. */
            if (reqParams.contains("full")) {
                List<Map<String, Object>> data = getLocations(term);
                return gson.toJson(data);
            } else {
                /* Search for prefix matching strings. */
                List<String> matches = getLocationsByPrefix(term);
                return gson.toJson(matches);
            }
        });

        /* Define map application redirect */
        get("/", (request, response) -> {
            response.redirect("/map.html", 301);
            return true;
        });
    }

    /**
     * Check if the computed parameter map matches the required parameters on length.
     */
    private static boolean hasRequestParameters(
            HashMap<String, Double> params, String[] requiredParams) {
        return params.size() == requiredParams.length;
    }

    /**
     * Validate that the computed parameters matches the required parameters.
     * If the parameters do not match, halt.
     */
    private static void validateRequestParameters(
            HashMap<String, Double> params, String[] requiredParams) {
        if (params.size() != requiredParams.length) {
            halt(HALT_RESPONSE, "Request failed - parameters missing.");
        }
    }

    /**
     * Return a parameter map of the required request parameters.
     * Requires that all input parameters are doubles.
     * @param req HTTP Request
     * @param requiredParams TestParams to validate
     * @return A populated map of input parameter to it's numerical value.
     */
    private static HashMap<String, Double> getRequestParams(
            spark.Request req, String[] requiredParams) {
        Set<String> reqParams = req.queryParams();
        HashMap<String, Double> params = new HashMap<>();
        for (String param : requiredParams) {
            if (reqParams.contains(param)) {
                try {
                    params.put(param, Double.parseDouble(req.queryParams(param)));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    halt(HALT_RESPONSE, "Incorrect parameters - provide numbers.");
                }
            }
        }
        return params;
    }

    /**
     * Write a <code>BufferedImage</code> to an <code>OutputStream</code>. The image is written as
     * a lossy JPG, but with the highest quality possible.
     * @param im Image to be written.
     * @param os Stream to be written to.
     */
    static void writeJpgToStream(BufferedImage im, OutputStream os) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(1.0F); // Highest quality of jpg possible
        writer.setOutput(new MemoryCacheImageOutputStream(os));
        try {
            writer.write(im);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles raster API calls, queries for tiles and rasters the full image. <br>
     * <p>
     *     The rastered photo must have the following properties:
     *     <ul>
     *         <li>Has dimensions of at least w by h, where w and h are the user viewport width
     *         and height.</li>
     *         <li>The tiles collected must cover the most longitudinal distance per pixel
     *         possible, while still covering less than or equal to the amount of
     *         longitudinal distance per pixel in the query box for the user viewport size. </li>
     *         <li>Contains all tiles that intersect the query bounding box that fulfill the
     *         above condition.</li>
     *         <li>The tiles must be arranged in-order to reconstruct the full image.</li>
     *     </ul>
     *     Additional image about the raster is returned and is to be included in the Json response.
     * </p>
     * @param inputParams Map of the HTTP GET request's query parameters - the query bounding box
     *                    and the user viewport width and height.
     * @param rasteredImageParams A map of parameters for the Json response as specified:
     * "raster_ul_lon" -> Double, the bounding upper left longitude of the rastered image <br>
     * "raster_ul_lat" -> Double, the bounding upper left latitude of the rastered image <br>
     * "raster_lr_lon" -> Double, the bounding lower right longitude of the rastered image <br>
     * "raster_lr_lat" -> Double, the bounding lower right latitude of the rastered image <br>
     * "raster_width"  -> Integer, the width of the rastered image <br>
     * "raster_height" -> Integer, the height of the rastered image <br>
     * "depth"         -> Integer, the 1-indexed quadtree depth of the nodes of the rastered image.
     * Can also be interpreted as the length of the numbers in the image string. <br>
     * "query_success" -> Boolean, whether an image was successfully rastered. <br>
     * @return a <code>BufferedImage</code>, which is the rastered result.
     * @see #REQUIRED_RASTER_REQUEST_PARAMS
     */
    public static BufferedImage getMapRaster(Map<String, Double> inputParams,
                                             Map<String, Object> rasteredImageParams) {
        String[][] imgFiles;

        double queryULLat = inputParams.get("ullat");
        double queryULLon = inputParams.get("ullon");
        double queryLRLat = inputParams.get("lrlat");
        double queryLRLon = inputParams.get("lrlon");
        double queryW = inputParams.get("w");
        double queryH = inputParams.get("h");

        double querylonDistPerPixel = (queryLRLon - queryULLon) / queryW;
        double distPerPixel = (ROOT_LRLON - ROOT_ULLON) / TILE_SIZE;
        double latDistPerPixel = (ROOT_ULLAT - ROOT_LRLAT) / TILE_SIZE;
        int depth = 0;
        while (distPerPixel > querylonDistPerPixel) {
            depth++;
            distPerPixel /= 2;
            latDistPerPixel /= 2;
        }




        if (depth > 7) depth = 7;
        //is queryW always 256?

        Point ulPoint = new Point(queryULLon, queryULLat);
        Point urPoint = new Point(queryLRLon, queryULLat);
        Point llPoint = new Point(queryULLon, queryLRLat);
        Point lrPoint = new Point(queryLRLon, queryLRLat);

        ulPoint.moveInBounds();
        urPoint.moveInBounds();
        llPoint.moveInBounds();
        lrPoint.moveInBounds();


        rasteredImageParams.put("raster_ul_lon", ulPoint.getX());
        //System.out.println("raster ul lon: " + ULPoint.getX());
        rasteredImageParams.put("raster_ul_lat", ulPoint.getY());
        //System.out.println("raster ul lat: " + ULPoint.getY());
        rasteredImageParams.put("raster_lr_lon", lrPoint.getX());
        //System.out.println("raster lr lon: " + LRPoint.getX());

        rasteredImageParams.put("raster_lr_lat", lrPoint.getY());
        //System.out.println( LRPoint.getY());

        rasteredImageParams.put("depth", depth);
        rasteredImageParams.put("query_success", false);

        //System.out.println("depth = " + depth);

        imgFiles = imgTree.intersectionQuery(ulPoint, urPoint, llPoint, lrPoint,
                depth, rasteredImageParams);

        rasteredImageParams.put("raster_width", imgFiles[0].length * 256);
       // System.out.println("width:" + imgFiles[0].length*256);
        rasteredImageParams.put("raster_height", imgFiles.length * 256);
        //System.out.println("height:" + imgFiles.length*256);


        //System.out.println(rasteredImageParams.get("raster_width"));
        //System.out.println(rasteredImageParams.get("raster_height"));

        BufferedImage returnImage = new BufferedImage(
                imgFiles[0].length * 256, imgFiles.length * 256,
                BufferedImage.TYPE_INT_RGB);
        Graphics gr = returnImage.getGraphics();

        int x = 0;
        int y = 0;
        for (int i = 0; i < imgFiles.length; i++) {
            for (int j = 0; j < imgFiles[i].length; j++) {
                try {
                    if (seenImages.containsKey(imgFiles[i][j])) {
                        gr.drawImage(seenImages.get(imgFiles[i][j]), x, y, null);
                    } else {
                        BufferedImage bi = ImageIO.read(new File(IMG_ROOT + imgFiles[i][j]));
                        seenImages.put(imgFiles[i][j], bi);
                        gr.drawImage(bi, x, y, null);
                    }
                } catch (java.io.IOException e1) {
                    System.out.println("Exception thrown reading file" + i + j);
                }
                //System.out.println(imgFiles[i][j]);
                x += 256;
                if (x >= returnImage.getWidth()) {
                    x = 0;
                    y += 256;
                }
            }
        }
        rasteredImageParams.replace("query_success", true);
        //from http://stackoverflow.com/questions/3922276/
        // how-to-combine-multiple-pngs-into-one-big-png-file
        return returnImage;

    }

    /**
     * Searches for the shortest route satisfying the input request parameters, and returns a
     * <code>List</code> of the route's node ids. <br>
     * The route should start from the closest node to the start point and end at the closest node
     * to the endpoint. Distance is defined as the euclidean distance between two points
     * (lon1, lat1) and (lon2, lat2).
     * If <code>im</code> is not null, draw the route onto the image by drawing lines in between
     * adjacent points in the route. The lines should be drawn using ROUTE_STROKE_COLOR,
     * ROUTE_STROKE_WIDTH_PX, BasicStroke.CAP_ROUND and BasicStroke.JOIN_ROUND.
     * @param routeParams Params collected from the API call. Members are as
     *                    described in REQUIRED_ROUTE_REQUEST_PARAMS.
     * @param rasterImageParams parameters returned from the image rastering.
     * @param im The rastered map image to be drawn on.
     * @return A List of node ids from the start of the route to the end.
     */
    public static List<Long> findAndDrawRoute(Map<String, Double> routeParams,
                                              Map<String, Object> rasterImageParams,
                                              BufferedImage im) {
        routeToDraw = new ArrayList<>();
        double startLat = routeParams.get("start_lat");
        double startLon = routeParams.get("start_lon");
        double endLat = routeParams.get("end_lat");
        double endLon = routeParams.get("end_lon");
        Point start = new Point(startLat, startLon);
        Point end = new Point(endLat, endLon);
        Iterator<Point> pointIter = g.getVertexAddress().keySet().iterator();
        double startDistance = 999999; double endDistance = 999999;
        Point bestStart = null; Point bestEnd = null;
        while (pointIter.hasNext()) {
            Point curr = pointIter.next();
            if (Point.distance(start, curr) < startDistance) {
                bestStart = curr;
                startDistance = Point.distance(start, bestStart);
            }
            if (Point.distance(end, curr) < endDistance) {
                bestEnd = curr;
                endDistance = Point.distance(end, bestEnd);
            }
        }
        start = bestStart; end = bestEnd;
        AStarComparator<Double> distanceComparator = new AStarComparator<>();
        PriorityQueue<Point> fringe = new PriorityQueue<>(distanceComparator);
        HashMap<Point, Point> predecessor = new HashMap<>(); //predecessor map, k = curr, v = prev
        HashSet<Point> visited = new HashSet<>(); //visited set
        distanceComparator.putHeuristic(start, Point.distance(start, end));
        distanceComparator.putDistance(start, 0);
        fringe.add(start);
        ArrayList<Long> returnList = new ArrayList<>();
        while (!fringe.isEmpty()) {
            Point node = fringe.poll();
            if (node.equals(end)) break; //reached the end
            visited.add(node);
            for (Object neighbor : g.neighbors(node)) {
                Point w = (Point) neighbor;
                if (visited.contains(w)) continue;
                double predDist = distanceComparator.getDistance(node);
                double incrDist = 0; int index = 0;
                while (index < g.getAdjLists()[g.getVertexAddress().get(node)].size()) {
                    if (g.getAdjLists()[g.getVertexAddress().get(node)].get(index).to().equals(w)) {
                        incrDist = (Double) g.getAdjLists()[g.getVertexAddress().get(node)]
                                .get(index).distance();
                        break;
                    }
                    index++;
                }
                if (!fringe.contains(w)) {
                    predecessor.put(w, node);
                    distanceComparator.putHeuristic(w, predDist + incrDist
                            + Point.distance(w, end));
                    distanceComparator.putDistance(w, predDist + incrDist);
                    fringe.add(w);
                } else if (predDist + incrDist < distanceComparator.getDistance(w)) {
                    predecessor.put(w, node);
                    distanceComparator.putHeuristic(w, predDist + incrDist
                            + Point.distance(w, end));
                    distanceComparator.putDistance(w, predDist + incrDist);
                    fringe.add(w);
                }
            }
        }
        Point curr = predecessor.get(end);
        returnList.add(end.getId()); routeToDraw.add(end);
        while (curr != start) {
            returnList.add(curr.getId()); routeToDraw.add(curr);
            curr = predecessor.get(curr);
        }
        routeToDraw.add(curr); returnList.add(curr.getId());
        Collections.reverse(returnList); Collections.reverse(routeToDraw);

        if (im != null) {
            Stroke stroke = new BasicStroke(MapServer.ROUTE_STROKE_WIDTH_PX,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            Graphics2D gr = (Graphics2D) im.getGraphics();
            gr.setStroke(stroke);
            gr.setColor(ROUTE_STROKE_COLOR);
            double lonDistPerPix = ((Double) rasterImageParams.get("raster_lr_lon")
                    - (Double) rasterImageParams.get("raster_ul_lon"))
                    / (int) rasterImageParams.get("raster_width");
            double latDistPerPix = ((Double) rasterImageParams.get("raster_ul_lat")
                    - (Double) rasterImageParams.get("raster_lr_lat"))
                    / (int) rasterImageParams.get("raster_height");
            for (int i = 0; i + 1 < routeToDraw.size(); i++) {
                double x1 = (routeToDraw.get(i).getY() - (double)
                        rasterImageParams.get("raster_ul_lon")) / lonDistPerPix;
                double y1 = (routeToDraw.get(i).getX() - (double)
                        rasterImageParams.get("raster_lr_lat")) / latDistPerPix;
                double x2 = (routeToDraw.get(i + 1).getY() - (double)
                        rasterImageParams.get("raster_ul_lon")) / lonDistPerPix;
                double y2 = (routeToDraw.get(i + 1).getX() - (double)
                        rasterImageParams.get("raster_lr_lat")) / latDistPerPix;
                gr.drawLine((int) x1, (int) ((int) rasterImageParams.get("raster_height") - y1),
                        (int) x2, ((int) ((int) rasterImageParams.get("raster_height") - y2)));
            }
        }
        return returnList;
    }

    /** A helper comparator class
     *  Used by findAndDrawRoute's A* algorithm
     */
    public static class AStarComparator<T> implements Comparator {
        private HashMap<Point, Double> comparatorHeuristicMap = new HashMap<>();
        private HashMap<Point, Double> distanceMap = new HashMap<>();
        @Override
        public int compare(Object o1, Object o2) {
            Point p1 = (Point) o1;
            Point p2 = (Point) o2;
            if (comparatorHeuristicMap.get(p1) > comparatorHeuristicMap.get(p2)) {
                return 1;
            } else if (comparatorHeuristicMap.get(p1) < comparatorHeuristicMap.get(p2)) {
                return -1;
            }
            return 0;
        }

        public void putHeuristic(Point node, double heuristic) {
            comparatorHeuristicMap.put(node, heuristic);
        }

        public double getHeuristic(Point node) {
            return comparatorHeuristicMap.get(node);
        }

        public void putDistance(Point node, double distance) {
            distanceMap.put(node, distance);
        }

        public double getDistance(Point node) {
            return distanceMap.get(node);
        }

        public HashMap<Point, Double> getComparatorHeuristicMap() {
            return comparatorHeuristicMap;
        }

        public void setComparatorHeuristicMap(HashMap<Point, Double> comparatorHeuristicMap) {
            this.comparatorHeuristicMap = comparatorHeuristicMap;
        }

        public HashMap<Point, Double> getDistanceMap() {
            return distanceMap;
        }

        public void setDistanceMap(HashMap<Point, Double> distanceMap) {
            this.distanceMap = distanceMap;
        }
    }

    /**
     * In linear time, collect all the names of OSM locations that prefix-match the query string.
     * @param prefix Prefix string to be searched for. Could be any case, with our without
     *               punctuation.
     * @return A <code>List</code> of the full names of locations whose cleaned name matches the
     * cleaned <code>prefix</code>.
     */
    public static List<String> getLocationsByPrefix(String prefix) {
        return g.getTrieAgain().lookupWords(GraphDB.cleanString(prefix));




        //autocomplete:
        //for each character in nextChars (TrieNode class), recursively add each of its
        // next characters to a String
        //collect all the Strings in a List and return that

    }

    /**
     * Collect all locations that match a cleaned <code>locationName</code>, and return
     * information about each node that matches.
     * @param locationName A full name of a location searched for.
     * @return A list of locations whose cleaned name matches the
     * cleaned <code>locationName</code>, and each location is a map of parameters for the Json
     * response as specified: <br>
     * "lat" -> Number, The latitude of the node. <br>
     * "lon" -> Number, The longitude of the node. <br>
     * "name" -> String, The actual name of the node. <br>
     * "id" -> Number, The id of the node. <br>
     */
    public static List<Map<String, Object>> getLocations(String locationName) {
        return g.getSearch().get(locationName);

    }
}
