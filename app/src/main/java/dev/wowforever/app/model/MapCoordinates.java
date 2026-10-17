package dev.wowforever.app.model;

/**
 * Spawn coordinates are percentages of the zone map, {@value #MIN} to {@value #MAX} inclusive; a point outside (the
 * instance placeholder -1, -1, or a stray 100.5) is not on the map and is never pinned.
 */
public final class MapCoordinates {
  public static final double MIN = 0;
  public static final double MAX = 100;

  private MapCoordinates() {}

  /** Whether {@code x}, {@code y} lie on the map. */
  public static boolean onMap(double x, double y) {
    return x >= MIN && x <= MAX && y >= MIN && y <= MAX;
  }

  /**
   * The SQL condition (to append after a WHERE clause) keeping rows of {@code alias} whose {@code x} and {@code y}
   * lie on the map, as {@link #onMap} does; rows with a missing coordinate drop out too.
   */
  public static String sqlOnMap(String alias) {
    return " AND " + alias + ".x BETWEEN " + MIN + " AND " + MAX
        + " AND " + alias + ".y BETWEEN " + MIN + " AND " + MAX;
  }
}
