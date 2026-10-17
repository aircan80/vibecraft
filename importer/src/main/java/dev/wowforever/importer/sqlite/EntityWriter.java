package dev.wowforever.importer.sqlite;

import dev.wowforever.importer.clientdata.ClientItem;
import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.lua.LuaValue;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Writes the four Questie entity tables and the relation and spawn tables derived from their fields. */
final class EntityWriter {
  private final Connection c;
  private final LuaColumns columns;
  private final ImportReport report;
  /** {@code <kind>.<field>} pairs already counted as {@code missingField.<kind>.<field>}. */
  private final Set<String> missingFields = new HashSet<>();

  EntityWriter(Connection c, LuaColumns columns, ImportReport report) {
    this.c = c;
    this.columns = columns;
    this.report = report;
  }

  /**
   * Items merged with their client data (see {@link ClientItemWriter}; an empty {@code client} map writes
   * Questie's values only), and their object/container/vendor/quest relations; {@code npcDrops} go to
   * {@code drops}.
   */
  void items(EntityTable t, NpcDrops drops, Map<Long, ClientItem> client) throws SQLException {
    try (Batch items = new Batch(c, ClientItemWriter.ITEM_INSERT);
        ClientItemWriter merge = new ClientItemWriter(c, client, report);
        Batch objectDrops = new Batch(c, "INSERT INTO item_object_drop(item_id, object_id) VALUES (?, ?)");
        Batch containers = new Batch(c, "INSERT INTO item_container_drop(item_id, container_item_id) VALUES (?, ?)");
        Batch vendors = new Batch(c, "INSERT INTO item_vendor(item_id, npc_id) VALUES (?, ?)");
        Batch rewards = new Batch(c, "INSERT INTO quest_reward_item(quest_id, item_id) VALUES (?, ?)");
        Batch related = new Batch(c, "INSERT INTO item_related_quest(item_id, quest_id) VALUES (?, ?)")) {
      for (Map.Entry<Long, LuaValue[]> e : t.rows().entrySet()) {
        RawEntity r = row(t, e.getValue());
        long id = e.getKey();
        Object[] row = merge.row(id, r.string("name"), r.scalar("itemLevel"), r.scalar("requiredLevel"),
            r.scalar("class"), r.scalar("subClass"), r.scalar("flags"), r.scalar("foodType"), r.scalar("ammoType"),
            r.scalar("startQuest"), r.scalar("teachesSpell"));
        if (row[1] == null) {
          report.count("nameless.item", 1);
        }
        items.add(row);
        for (long npc : r.ids("npcDrops")) {
          drops.add(id, npc, NpcDrops.QUESTIE);
        }
        for (long object : r.ids("objectDrops")) {
          objectDrops.add(id, object);
        }
        for (long container : r.ids("itemDrops")) {
          containers.add(id, container);
        }
        for (long npc : r.ids("vendors")) {
          vendors.add(id, npc);
        }
        for (long quest : r.ids("questRewards")) {
          rewards.add(quest, id);
        }
        for (long quest : r.ids("relatedQuests")) {
          related.add(id, quest);
        }
      }
    }
  }

  /** NPCs and their spawns; adds each spawn row's zone to {@code spawnZones} (zone id to row count). */
  void npcs(EntityTable t, Map<Long, Long> spawnZones) throws SQLException {
    try (Batch npcs = new Batch(c, "INSERT INTO npc(id, name, sub_name, min_level, max_level, rank, zone_id,"
            + " faction_id, friendly_to, npc_flags) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        Batch spawns = new Batch(c, "INSERT INTO npc_spawn(npc_id, zone_id, x, y) VALUES (?, ?, ?, ?)")) {
      for (Map.Entry<Long, LuaValue[]> e : t.rows().entrySet()) {
        RawEntity r = row(t, e.getValue());
        npcs.add(e.getKey(), r.name(), r.string("subName"), r.scalar("minLevel"), r.scalar("maxLevel"),
            r.scalar("rank"), r.zone(), r.scalar("factionID"), r.string("friendlyToFaction"), r.scalar("npcFlags"));
        r.spawns(e.getKey(), spawns, spawnZones);
      }
    }
  }

  /** Game objects and their spawns; see {@link #npcs}. */
  void objects(EntityTable t, Map<Long, Long> spawnZones) throws SQLException {
    try (Batch objects = new Batch(c, "INSERT INTO game_object(id, name, zone_id, faction_id) VALUES (?, ?, ?, ?)");
        Batch spawns = new Batch(c, "INSERT INTO object_spawn(object_id, zone_id, x, y) VALUES (?, ?, ?, ?)")) {
      for (Map.Entry<Long, LuaValue[]> e : t.rows().entrySet()) {
        RawEntity r = row(t, e.getValue());
        objects.add(e.getKey(), r.name(), r.zone(), r.scalar("factionID"));
        r.spawns(e.getKey(), spawns, spawnZones);
      }
    }
  }

  /** Quests, their objective text and their start/end givers. */
  void quests(EntityTable t) throws SQLException {
    try (Batch quests = new Batch(c, "INSERT INTO quest(id, name, required_level, quest_level, zone_or_sort,"
            + " required_races, required_classes, objectives_text, quest_flags, special_flags)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        Batch givers = new Batch(c, "INSERT INTO quest_giver(quest_id, kind, entity_id, role) VALUES (?, ?, ?, ?)")) {
      for (Map.Entry<Long, LuaValue[]> e : t.rows().entrySet()) {
        RawEntity r = row(t, e.getValue());
        long id = e.getKey();
        quests.add(id, r.name(), r.scalar("requiredLevel"), r.scalar("questLevel"), r.scalar("zoneOrSort"),
            r.scalar("requiredRaces"), r.scalar("requiredClasses"), r.lines("objectivesText"),
            r.scalar("questFlags"), r.scalar("specialFlags"));
        r.givers(id, "startedBy", "start", givers);
        r.givers(id, "finishedBy", "end", givers);
      }
    }
  }

  /** A {@link RawEntity} over one row of {@code t}, sharing this writer's missing-field bookkeeping. */
  private RawEntity row(EntityTable t, LuaValue[] values) {
    return new RawEntity(t, values, columns, report, missingFields);
  }

  /** {@code item}, {@code npc}, {@code object} or {@code quest}: the counter and search-index kind. */
  static String label(EntityKind kind) {
    return kind.name().toLowerCase(Locale.ROOT);
  }
}
