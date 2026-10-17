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
  /** Known ancestors through the selected map, never inferring a missing parent. */
  public List<GeographyStep> breadcrumbs(long id) {
    var path=new java.util.ArrayList<GeographyStep>();
    var seen=new java.util.HashSet<Long>();
    long current=id;
    while(current>0 && path.size()<64) {
      if(!seen.add(current)) return List.of(new GeographyStep(map(id).map(WorldMapRegion::name).orElse("Map "+id),id,GeographyStep.Kind.MAP));
      var found=map(current);
      if(found.isEmpty())break;
      var area=found.get();
      path.add(new GeographyStep(area.name(),area.uiMapId(),GeographyStep.Kind.MAP));
      current=area.parentUiMapId();
    }
    java.util.Collections.reverse(path);
    return path.isEmpty() ? List.of(new GeographyStep("Map "+id,id,GeographyStep.Kind.MAP)) : List.copyOf(path);
  }

}
