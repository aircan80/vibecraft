package dev.wowforever.importer.atlasloot;

import java.util.List;

/** A boss (or extra loot list such as trash) of an instance, in AtlasLoot source order. */
public record AtlasBoss(
    String name,
    Long npcId,
    Long objectId,
    Integer level,
    String specialType,
    boolean extraList,
    int sortOrder,
    List<AtlasLoot> loot) {}
