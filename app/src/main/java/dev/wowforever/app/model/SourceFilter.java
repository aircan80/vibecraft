package dev.wowforever.app.model;

/**
 * Which drop rows the item and NPC pages show, by where a row comes from ({@link DropSource#origin()},
 * {@link LootRow#origin()}: {@code questie}, {@code atlasloot} or {@code both}). Rows confirmed by in-game captures, and rows both sources know, are always shown.
 */
public enum SourceFilter {
  ALL("All sources", null),
  HIDE_ATLASLOOT_ONLY("Hide AtlasLoot-only rows", "atlasloot"),
  HIDE_QUESTIE_ONLY("Hide Questie-only rows", "questie");

  private final String label;
  private final String hiddenOrigin;

  SourceFilter(String label, String hiddenOrigin) {
    this.label = label;
    this.hiddenOrigin = hiddenOrigin;
  }

  /** The menu item's text. */
  public String label() {
    return label;
  }

  /** Whether a row of {@code origin} is shown; a row without an origin always is. */
  public boolean keeps(String origin) {
    return hiddenOrigin == null || !hiddenOrigin.equals(origin);
  }
}
