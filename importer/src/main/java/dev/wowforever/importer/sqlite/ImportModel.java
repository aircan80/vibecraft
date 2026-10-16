package dev.wowforever.importer.sqlite;

import dev.wowforever.importer.atlasloot.AtlasInstance;
import dev.wowforever.importer.drops.DropRates;
import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.zones.Zone;
import java.util.List;
import java.util.Map;

/**
 * Everything {@link SqliteWriter} writes: corrected entity tables (a missing kind writes no rows), drop
 * rates, zones, AtlasLoot instances in source order and the {@code meta} key/value pairs.
 */
public record ImportModel(
    Map<EntityKind, EntityTable> tables,
    DropRates dropRates,
    Map<Long, Zone> zones,
    List<AtlasInstance> instances,
    Map<String, String> meta) {}
