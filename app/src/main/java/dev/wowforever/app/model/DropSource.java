package dev.wowforever.app.model;

/**
 * An NPC that drops an item. {@code name} (and the level and zone columns) are null when the drop row
 * points at an NPC the database does not know.
 */
public record DropSource(
    EntityRef ref, String name, Integer levelMin, Integer levelMax, String zoneName, Long zoneId,
    Double dropPct, String pctSource, String origin) {

  public String displayName() {
    return ref.displayName(name);
  }
}
