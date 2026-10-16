package dev.wowforever.importer.zones;

/** A map area. Kind is one of zone, subzone, dungeon, raid, city or continent. */
public record Zone(long id, String name, Long parentId, String kind) {}
