import java.awt.geom.Point2D;

/**
 * Created by changyeonclarapark on 7/15/16.
 */
public class Point extends Point2D.Double {

    /** Check if a point p is out of bounds (outside the given
     * map/root node bounds). If it is, move the point so that
     * it is within the map.
     */
    private long getId;

    public Point(double x, double y) {
        this.x = x;
        this.y = y;

    }

    public Point(double x, double y, long id) {
        this.x = x;
        this.y = y;
        this.getId = id;
    }

    public long getId() {
        return getId;
    }

    public void moveInBounds() {
        if (getX() > MapServer.ROOT_LRLON) {
            setLocation(MapServer.ROOT_LRLON, getY());
        } else if (getX() < MapServer.ROOT_ULLON) {
            setLocation(MapServer.ROOT_ULLON, getY());
        }
        if (getY() > MapServer.ROOT_ULLAT) {
            setLocation(getX(), MapServer.ROOT_ULLAT);
        } else if (getY() < MapServer.ROOT_LRLAT) {
            setLocation(getX(), MapServer.ROOT_LRLAT);
        }
    }

    public static double distance(Point start, Point end) {
        return Math.sqrt(Math.pow(end.getX() - start.getX(), 2)
                + Math.pow(end.getY() - start.getY(), 2));
    }

    public double distanceTo(Point end) {
        return distance(this, end);
    }

    public double distanceFrom(Point start) {
        return distance(start, this);
    }


}
