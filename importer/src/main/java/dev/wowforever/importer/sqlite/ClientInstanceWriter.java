package dev.wowforever.importer.sqlite;

import dev.wowforever.importer.clientdata.ClientEncounter;
import dev.wowforever.importer.clientdata.ClientInstance;
import dev.wowforever.importer.clientdata.ClientInstances;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Writes the client dungeons ({@link dev.wowforever.importer.clientdata.ClientInstanceLoader}) after the AtlasLoot
 * instances, their ids continuing the {@code instance} and {@code boss} sequences, in map id order.
 *
 * <ul>
 *   <li>{@code instance}: {@code key} {@code "client:<map id>"}, {@code name} the Map's name, {@code map_id} and
 *       {@code client_map_id} the map id, {@code content_type} {@value #CONTENT_TYPE} (AtlasLoot's word for a dungeon),
 *       {@code level_min} and {@code tuned_min_level} the tuned minimum level, {@code level_max} null,
 *       {@code zone_id} from {@link ClientInstances#zoneIds()}, {@code source} {@value #SOURCE}, {@code bosses_state}
 *       {@value #BOSSES_STATE} (the client names the bosses, nothing more) and {@code loot_state} {@value #LOOT_STATE}
 *       (the client has no loot tables).
 *   <li>{@code boss}: one row per encounter in order, {@code sort_order} from 1, without NPC, object, level or special
 *       type, never an extra list. No {@code boss_loot}.
 * </ul>
 */
final class ClientInstanceWriter {
  static final String SOURCE = "client";
  static final String CONTENT_TYPE = "DUNGEON";
  static final String BOSSES_STATE = "client_named";
  static final String LOOT_STATE = "none";
  static final String KEY_PREFIX = "client:";
  private final Connection c;

  ClientInstanceWriter(Connection c) {
    this.c = c;
  }

  void write(ClientInstances instances) throws SQLException {
    if (instances.instances().isEmpty()) {
      return;
    }
    long instanceId = maxId("instance");
    long bossId = maxId("boss");
    try (Batch instanceRows = new Batch(c, "INSERT INTO instance(id, key, name, map_id, instance_id, content_type,"
            + " level_min, level_max, zone_id, source, client_map_id, tuned_min_level, bosses_state, loot_state)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        Batch bossRows = new Batch(c, "INSERT INTO boss(id, instance_id, name, npc_id, object_id, level, special_type,"
            + " is_extra_list, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
      for (ClientInstance inst : instances.instances()) {
        instanceId++;
        instanceRows.add(instanceId, KEY_PREFIX + inst.mapId(), inst.name(), inst.mapId(), null, CONTENT_TYPE,
            inst.tunedMinLevel(), null, instances.zoneIds().get(inst.mapId()), SOURCE, inst.mapId(),
            inst.tunedMinLevel(), BOSSES_STATE, LOOT_STATE);
        int sort = 0;
        for (ClientEncounter e : inst.encounters()) {
          bossId++;
          sort++;
          bossRows.add(bossId, instanceId, e.name(), null, null, null, null, 0, sort);
        }
      }
    }
  }

  /** The highest id of {@code table} so far (0 when empty): the AtlasLoot rows are written first. */
  private long maxId(String table) throws SQLException {
    try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT coalesce(max(id), 0) FROM " + table)) {
      rs.next();
      return rs.getLong(1);
    }
  }
}
