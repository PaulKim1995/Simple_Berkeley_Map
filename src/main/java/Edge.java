/**
 * Created by Paul on 8/1/16.
 */
public class Edge {
    private Point from;
    private Point to;
    private double distance;

    public Edge(Point from, Point to, double distance) {
        this.from = from;
        this.to = to;
        this.distance = distance;
    }

    public Point to() {
        return to;
    }

    public Point from() {
        return from;
    }

    public double distance() {
        return distance;
    }


}
