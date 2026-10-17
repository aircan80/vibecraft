package dev.wowforever.app.model;

import java.util.List;
import java.util.Optional;

/** One immutable, loaded hierarchy; queries and image decoding happen off the UI thread. */
public record WorldMapCatalog(List<WorldMapRegion> regions, String sourceBuild) {
  public WorldMapCatalog { regions = List.copyOf(regions); sourceBuild = sourceBuild == null ? "" : sourceBuild; }
  public WorldMapCatalog(List<WorldMapRegion> regions) { this(regions, ""); }
  public List<WorldMapRegion> children(long parentId) {
    return regions.stream().filter(r -> r.parentUiMapId() == parentId).toList();
  }
  public Optional<WorldMapRegion> map(long id) {
    return regions.stream().filter(r -> r.uiMapId() == id).findFirst();
  }
}
