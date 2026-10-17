package dev.wowforever.importer.clientdata;

import java.util.List;
import java.util.Map;

/**
 * The client dungeons the writer adds after the AtlasLoot instances ({@link ClientInstanceLoader}), and the zone of
 * each by map id ({@link ClientInstanceLoader#zoneIds}); a map without an entry has no zone.
 */
public record ClientInstances(List<ClientInstance> instances, Map<Long, Long> zoneIds) {
  public static final ClientInstances EMPTY = new ClientInstances(List.of(), Map.of());

  public ClientInstances {
    instances = instances == null ? List.of() : List.copyOf(instances);
    zoneIds = zoneIds == null ? Map.of() : Map.copyOf(zoneIds);
  }
}
