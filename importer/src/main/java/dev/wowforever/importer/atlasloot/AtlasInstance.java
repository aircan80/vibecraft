package dev.wowforever.importer.atlasloot;

import java.util.List;

/** A dungeon or raid from AtlasLoot with its bosses. */
public record AtlasInstance(
    String key,
    String name,
    Long mapId,
    Long instanceId,
    String contentType,
    Integer levelMin,
    Integer levelMax,
    List<AtlasBoss> bosses) {}
