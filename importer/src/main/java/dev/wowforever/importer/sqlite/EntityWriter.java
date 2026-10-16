package dev.wowforever.importer.sqlite;

import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.schema.EntitySchema;
import dev.wowforever.lua.LuaNil;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValue;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Writes the four Questie entity tables and the relation and spawn tables derived from their fields. */
final class EntityWriter {
  private static final String[] GIVER_KINDS = {"npc", "object", "item"};

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

  /** Items, their object/container/vendor/quest relations; {@code npcDrops} go to {@code drops}. */
  void items(EntityTable t, NpcDrops drops) throws SQLException {
    try (Batch items = new Batch(c, "INSERT INTO item(id, name, item_level, required_level, class_id, subclass_id,"
            + " flags, food_type, ammo_type, start_quest_id, teaches_spell_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        Batch objectDrops = new Batch(c, "INSERT INTO item_object_drop(item_id, object_id) VALUES (?, ?)");
        Batch containers = new Batch(c, "INSERT INTO item_container_drop(item_id, container_item_id) VALUES (?, ?)");
        Batch vendors = new Batch(c, "INSERT INTO item_vendor(item_id, npc_id) VALUES (?, ?)");
        Batch rewards = new Batch(c, "INSERT INTO quest_reward_item(quest_id, item_id) VALUES (?, ?)");
        Batch related = new Batch(c, "INSERT INTO item_related_quest(item_id, quest_id) VALUES (?, ?)")) {
      for (Map.Entry<Long, LuaValue[]> e : t.rows().entrySet()) {
        Row r = new Row(t, e.getValue());
        long id = e.getKey();
        items.add(id, r.name(), r.scalar("itemLevel"), r.scalar("requiredLevel"), r.scalar("class"),
            r.scalar("subClass"), r.scalar("flags"), r.scalar("foodType"), r.scalar("ammoType"),
            r.scalar("startQuest"), r.scalar("teachesSpell"));
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
        Row r = new Row(t, e.getValue());
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
        Row r = new Row(t, e.getValue());
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
        Row r = new Row(t, e.getValue());
        long id = e.getKey();
        quests.add(id, r.name(), r.scalar("requiredLevel"), r.scalar("questLevel"), r.scalar("zoneOrSort"),
            r.scalar("requiredRaces"), r.scalar("requiredClasses"), r.lines("objectivesText"),
            r.scalar("questFlags"), r.scalar("specialFlags"));
        r.givers(id, "startedBy", "start", givers);
        r.givers(id, "finishedBy", "end", givers);
      }
    }
  }

  /**
   * One entity row; reads fields by name. A field absent from the schema reads as nil and is counted
   * once per kind as {@code missingField.<kind>.<field>}, so an upstream rename shows in the report.
   */
  private final class Row {
    private final EntitySchema schema;
    private final String kind;
    private final LuaValue[] values;

    Row(EntityTable table, LuaValue[] values) {
      this.schema = table.schema();
      this.kind = label(table.kind());
      this.values = values;
    }

    LuaValue raw(String field) {
      if (schema.has(field)) {
        return values[schema.index(field) - 1];
      }
      if (missingFields.add(kind + "." + field)) {
        report.count("missingField." + kind + "." + field, 1);
      }
      return LuaNil.NIL;
    }

    Object scalar(String field) {
      return columns.scalar(kind, field, raw(field));
    }

    String string(String field) {
      return columns.string(kind, field, raw(field));
    }

    /** The name, counting {@code nameless.<kind>} when there is none. */
    String name() {
      String name = string("name");
      if (name == null) {
        report.count("nameless." + kind, 1);
      }
      return name;
    }

    /** {@code zoneID}; 0 is Questie's "no zone" and becomes NULL. */
    Object zone() {
      Object zone = scalar("zoneID");
      return zone instanceof Long l && l == 0 ? null : zone;
    }

    List<Long> ids(String field) {
      return columns.ids(kind, field, raw(field));
    }

    /** A list of strings joined with newlines; null when there are none. */
    String lines(String field) {
      List<String> lines = new ArrayList<>();
      columns.table(kind, field, raw(field)).ifPresent(t -> {
        for (LuaValue line : t.array()) {
          if (line instanceof LuaString s) {
            lines.add(s.value());
          } else if (!(line instanceof LuaNil)) {
            columns.bad(kind, field);
          }
        }
      });
      return lines.isEmpty() ? null : String.join("\n", lines);
    }

    /** {@code {npcIds?, objectIds?, itemIds?}} to quest_giver rows. */
    void givers(long questId, String field, String role, Batch givers) throws SQLException {
      LuaTable groups = columns.table(kind, field, raw(field)).orElse(null);
      if (groups == null) {
        return;
      }
      for (int g = 0; g < GIVER_KINDS.length; g++) {
        for (long entity : columns.idList(kind, field, groups.get(g + 1))) {
          givers.add(questId, GIVER_KINDS[g], entity, role);
        }
      }
    }

    /** {@code {[zoneId] = {{x, y[, phase]}, ...}}} to one row per coordinate; the phase is ignored. */
    void spawns(long id, Batch spawns, Map<Long, Long> spawnZones) throws SQLException {
      LuaTable byZone = columns.table(kind, "spawns", raw("spawns")).orElse(null);
      if (byZone == null) {
        return;
      }
      for (Map.Entry<LuaValue, LuaValue> zone : byZone.entries()) {
        if (!(zone.getKey() instanceof LuaNumber z && z.isInteger() && zone.getValue() instanceof LuaTable coords)) {
          columns.bad(kind, "spawns");
          continue;
        }
        for (LuaValue coord : coords.array()) {
          if (coord instanceof LuaTable xy && xy.get(1) instanceof LuaNumber x && xy.get(2) instanceof LuaNumber y) {
            spawns.add(id, z.asLong(), x.value(), y.value());
            spawnZones.merge(z.asLong(), 1L, Long::sum);
          } else {
            columns.bad(kind, "spawns");
          }
        }
      }
    }
  }

  /** {@code item}, {@code npc}, {@code object} or {@code quest}: the counter and search-index kind. */
  static String label(EntityKind kind) {
    return kind.name().toLowerCase(Locale.ROOT);
  }
}
