package dev.wowforever.app.ui.widgets;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/** Geometry shared by route painting and hit testing, in component coordinates. */
public final class RouteGeometry {
  private RouteGeometry() {}

  public record Anchor(Point2D at, double angle) {}

  /** Direction arrows every ten points, following the segment into each point. */
  public static List<Anchor> arrowAnchors(List<? extends Point2D> points) {
    List<Anchor> anchors = new ArrayList<>();
    for (int i = 10; i < points.size(); i += 10) {
      Point2D from = points.get(i - 1), to = points.get(i);
      double dx = to.getX() - from.getX(), dy = to.getY() - from.getY();
      if (dx != 0 || dy != 0) {
        anchors.add(new Anchor(new Point2D.Double(to.getX(), to.getY()), Math.atan2(dy, dx)));
      }
    }
    return List.copyOf(anchors);
  }

  /** Minimum distance to a segment, including the closing segment of a loop. */
  public static double distanceToPolyline(Point2D point, List<? extends Point2D> points, boolean closed) {
    if (points.isEmpty()) {
      return Double.POSITIVE_INFINITY;
    }
    if (points.size() == 1) {
      return point.distance(points.getFirst());
    }
    double nearest = Double.POSITIVE_INFINITY;
    int segments = closed ? points.size() : points.size() - 1;
    for (int i = 0; i < segments; i++) {
      Point2D from = points.get(i), to = points.get((i + 1) % points.size());
      nearest = Math.min(nearest, Line2D.ptSegDist(from.getX(), from.getY(), to.getX(), to.getY(),
          point.getX(), point.getY()));
    }
    return nearest;
  }
}
