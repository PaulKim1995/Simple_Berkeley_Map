import java.util.Map;


/**
 * Created by changyeonclarapark on 7/12/16.
 */
public class QuadTree {
    private QTreeNode root;

    public QuadTree(double ulLat, double ulLon, double lrLat, double lrLon) {
        root = new QTreeNode("root", ulLat, ulLon, lrLat, lrLon, 0);
        root.UL = new QTreeNode("1", root.ulLat,
                root.ulLon, (root.ulLat + root.lrLat) / 2, (root.ulLon + root.lrLon) / 2, 1);
        root.UR = new QTreeNode("2", root.ulLat,
                (root.ulLon + root.lrLon) / 2, (root.ulLat + root.lrLat) / 2, root.lrLon, 1);
        root.LL = new QTreeNode("3", (root.ulLat + root.lrLat) / 2,
                root.ulLon, root.lrLat, (root.lrLon + root.ulLon) / 2, 1);
        root.LR = new QTreeNode("4", (root.ulLat + root.lrLat) / 2,
                (root.ulLon + root.lrLon) / 2, root.lrLat, root.lrLon, 1);
        root.UL.fillTree();
        root.UR.fillTree();
        root.LL.fillTree();
        root.LR.fillTree();
    }

    /**
     * Takes upper left, upper right, and lower left corner of intersection window
     * and returns the required tiles in a 2D array
     * @param ul Point upper left corner
     * @param ur
     * @param ll
     * @param lr
     * @param depth
     * @return
     */

    public String[][] intersectionQuery(Point ul, Point ur, Point ll, Point lr,
                                        int depth, Map<String, Object> rasteredImageParams) {

        //depth = 0, returns root
        if (depth == 0) {
            return new String[][]{{"root"}};
        }

        //gets the tiles containing the corners
        QTreeNode ulTile = root.getTile(ul, depth);
        QTreeNode lrTile = root.getTile(lr, depth);

        rasteredImageParams.put("raster_ul_lon", ulTile.ulLon);
        rasteredImageParams.put("raster_ul_lat", ulTile.ulLat);
        rasteredImageParams.put("raster_lr_lon", lrTile.lrLon);
        rasteredImageParams.put("raster_lr_lat", lrTile.lrLat);

        //finds the right and down distances of the intersection window
        double intersectionWidth = lrTile.lrLon - ulTile.ulLon;
        double intersectionHeight = ulTile.ulLat - lrTile.lrLat;
        double tileWidth = ulTile.lrLon - ulTile.ulLon;
        double tileHeight = ulTile.ulLat - ulTile.lrLat;
        /**
        int intersectionWidth = (int)((LRTile.lrLon - ULTile.ulLon)/distPerPixel);
        int intersectionHeight = (int)((ULTile.ulLat - LRTile.lrLat)/latDistPerPixel);
        int tileWidth = (int)((ULTile.lrLon - ULTile.ulLon)/distPerPixel);
        int tileHeight = (int)((ULTile.ulLat - ULTile.lrLat)/latDistPerPixel);
         */

        /*System.out.println("intersection width " + intersectionWidth);
        System.out.println("intersection height " + intersectionHeight);
        System.out.println("tile width " + tileWidth);
        System.out.println("tile height " + tileHeight);*/
        
        //number of tiles to go to the right and down
        int tilesRight = (int) (Math.round(intersectionWidth / tileWidth));
        int tilesDown = (int) (Math.round(intersectionHeight / tileHeight));

        /*Point potentialUR = new Point(ULTile.ulLon + (tileWidth * tilesRight), ULTile.ulLat);
        Point potentialLL = new Point(ULTile.ulLon, ULTile.ulLat - (tileHeight * tilesDown));

        while (!root.getTile(potentialUR, depth).contains(UR)) {
            tilesRight++;
            potentialUR = new Point(ULTile.ulLon + (tileWidth * tilesRight), ULTile.ulLat);
        }
        while (!root.getTile(potentialLL, depth).contains(LL)) {
            tilesDown++;
            potentialLL = new Point(ULTile.ulLon, ULTile.ulLat - (tileHeight * tilesDown));
        }*/

        //System.out.println("tiles right " + tilesRight);
        //System.out.println("tiles down " + tilesDown);


        Point[][] searchArray = new Point[tilesDown][tilesRight];

        for (int i = 0; i < searchArray.length; i++) {
            for (int j = 0; j < searchArray[i].length; j++) {
                searchArray[i][j] = new Point((ulTile.ulLon + (tileWidth * j) + 0.000000001),
                        (ulTile.ulLat - (tileHeight * i) - 0.000000001));
            }
        }

        String[][] returnArray = new String[tilesDown][tilesRight];

        for (int i = 0; i < returnArray.length; i++) {
            for (int j = 0; j < returnArray[i].length; j++) {
                returnArray[i][j] = root.getTile(searchArray[i][j], depth).tileName + ".png";
            }
        }

        return returnArray;
    }

    /**
     * Created by changyeonclarapark on 7/12/16.
     */
    public class QTreeNode {
        private double ulLat;
        private double ulLon;
        private double lrLat;
        private double lrLon;

        private String tileName;
        private int depth;

        private QTreeNode UL;
        private QTreeNode UR;
        private QTreeNode LL;
        private QTreeNode LR;

        public QTreeNode(String tileName, double ulLat, double ulLon,
                         double lrLat, double lrLon, int depth) {
            this.tileName = tileName;
            this.ulLat = ulLat;
            this.ulLon = ulLon;
            this.lrLat = lrLat;
            this.lrLon = lrLon;
            this.depth = depth;
        }

        /**
        public QTreeNode getUL() {
            return this.UL;
        }

        public void setUL(QTreeNode newUL) {
            this.UL = newUL;
        }

        public QTreeNode getUR() {
            return this.UR;
        }

        public void setUR(QTreeNode newUR) {
            this.UR = newUR;
        }

        public QTreeNode getLL() {
            return this.LL;
        }

        public void setLL(QTreeNode newLL) {
            this.LL = newLL;
        }

        public QTreeNode getLR() {
            return this.LR;
        }

        public void setLR(QTreeNode newLR) {
            this.LR = newLR;
        }
         */

        public void fillTree() {
            if (this.depth >= 7) {
                return;
            } else {
                this.UL = new QTreeNode(this.tileName + "1", this.ulLat,
                        this.ulLon, (this.ulLat + this.lrLat) / 2,
                        (this.ulLon + this.lrLon) / 2, this.depth + 1);
                //System.out.println("UL: " + this.UL.tileName);
                this.UL.fillTree();
                this.UR = new QTreeNode(this.tileName + "2", this.ulLat,
                        (this.ulLon + this.lrLon) / 2, (this.ulLat + this.lrLat) / 2, this.lrLon,
                        this.depth + 1);
                //System.out.println("UR: " + this.UR.tileName);
                this.UR.fillTree();
                this.LL = new QTreeNode(this.tileName + "3", (this.ulLat + this.lrLat) / 2,
                        this.ulLon, this.lrLat, (this.lrLon + this.ulLon) / 2, this.depth + 1);
                //System.out.println("LL: " + this.LL.tileName);
                this.LL.fillTree();
                this.LR = new QTreeNode(this.tileName + "4", (this.ulLat + this.lrLat) / 2,
                        (this.ulLon + this.lrLon) / 2, this.lrLat, this.lrLon, this.depth + 1);
                //System.out.println("LR: " + this.LR.tileName);
                this.LR.fillTree();
            }
        }

        public QTreeNode getTile(Point corner, int dep) {
            QTreeNode currTile = this;
            int currDepth = dep;
            while (currDepth != 0) {
                if (currTile.UL.contains(corner)) {
                    currTile = currTile.UL;
                } else if (currTile.UR.contains(corner)) {
                    currTile = currTile.UR;
                } else if (currTile.LL.contains(corner)) {
                    currTile = currTile.LL;
                } else if (currTile.LR.contains(corner)) {
                    currTile = currTile.LR;
                }
                currDepth--;
            }
            System.out.println(currTile.tileName);
            return currTile;
        }

        public boolean contains(Point corner) {
            if (corner.getX() >= this.ulLon && corner.getX() <= this.lrLon) {
                if (corner.getY() >= this.lrLat && corner.getY() <= this.ulLat) {
                    return true;
                }
            }
            return false;
        }
    }
}


