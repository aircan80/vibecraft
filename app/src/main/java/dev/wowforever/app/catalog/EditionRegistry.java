package dev.wowforever.app.catalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Installed projections, not archived comparison snapshots, authorize navigation. */
public final class EditionRegistry {
  public record Edition(String id, String name, CatalogContext context, String status) {
    public boolean installed() { return context != null; }
    @Override public String toString() { return name + " · " + status; }
  }
  private final List<Edition> editions;
  private EditionRegistry(List<Edition> editions) { this.editions = List.copyOf(editions); }
  public List<Edition> editions() { return editions; }
  public Optional<CatalogContext> installed(String id) {
    return editions.stream().filter(e -> e.id().equals(id) && e.installed()).map(Edition::context).findFirst();
  }
  /** Existing schema is Forever-only. Fail closed on an unknown/mislabeled database. */
  public static EditionRegistry fromLegacyMetadata(Map<String, String> meta) {
    String build = meta.get("client_build"), projection = meta.get("build_id");
    boolean forever = "wow_classic_beta".equals(meta.get("client_product"))
        && build != null && build.startsWith("1.60.") && projection != null && !projection.isBlank();
    CatalogContext context = forever ? new CatalogContext("forever", projection, build,
        meta.getOrDefault("client_locale", "enUS")) : null;
    return new EditionRegistry(List.of(
        new Edition("forever", "WoW Forever", context, forever ? "Installed · " + build : "Not installed"),
        new Edition("classic-era", "WoW Classic Era", null, "Not installed"),
        new Edition("retail", "WoW Retail", null, "Not installed"),
        new Edition("anniversary-tbc", "WoW Anniversary (TBC)", null, "Not installed")));
  }
}
