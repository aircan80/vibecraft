package dev.wowforever.app.model;

import java.util.List;

/**
 * One patrol route of an NPC in a zone (Questie {@code npcKeys.waypoints}): {@code route} is its 1-based index among
 * the NPC's routes in that zone and {@code points} are in walking order. {@code loop} is true when the route closes
 * on itself (see {@link #isLoop}); otherwise its last point is where the patrol ends, as Questie records no despawn
 * points.
 */
public record Route(long npcId, long zoneId, int route, List<Point> points, boolean loop) {
  /** How close (Euclidean, in map 0–100 coordinates, i.e. 2 %) the last point must be to the first for a loop. */
  public static final double LOOP_TOLERANCE = 2.0;

  public Route {
    points = List.copyOf(points);
  }

  /** A map position in 0–100 coordinates. */
  public record Point(double x, double y) {}

  /**
   * Whether {@code points} form a loop: at least three points, the last within {@link #LOOP_TOLERANCE} of the first.
   * One or two points are a line, never a loop.
   */
  public static boolean isLoop(List<Point> points) {
    if (points.size() < 3) {
      return false;
    }
    Point first = points.getFirst();
    Point last = points.getLast();
    return Math.hypot(last.x() - first.x(), last.y() - first.y()) <= LOOP_TOLERANCE;
  }
}
