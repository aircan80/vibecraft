package dev.wowforever.app.model;

import java.util.Optional;

/** Client-authored child geography. Unplaced children remain available without invented coordinates. */
public record WorldMapRegion(long parentUiMapId, long uiMapId, String name,
    double x0, double y0, double x1, double y1, Long zoneId, boolean hasArt,
    boolean placed, Optional<LevelBand> levelBand, int questCount, int instanceCount) {
  public WorldMapRegion {
    java.util.Objects.requireNonNull(name);
    java.util.Objects.requireNonNull(levelBand);
    if (placed && (!Double.isFinite(x0) || !Double.isFinite(y0) || !Double.isFinite(x1)
        || !Double.isFinite(y1) || x0 >= x1 || y0 >= y1))
      throw new IllegalArgumentException("Invalid map rectangle");
  }
}
