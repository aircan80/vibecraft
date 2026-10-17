package dev.wowforever.importer.sqlite;

import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.schema.EntitySchema;
import dev.wowforever.lua.LuaNil;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValue;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One Questie entity row, read by field name through {@link LuaColumns}. A field absent from the schema reads as
 * nil and is counted once per kind as {@code missingField.<kind>.<field>} (the {@code missingFields} set is shared
 * by every row of a write), so an upstream rename shows in the report.
 */
final class RawEntity {
  private static final String[] GIVER_KINDS = {"npc", "object", "item"};

  private final EntitySchema schema;
  private final String kind;
  private final LuaValue[] values;
  private final LuaColumns columns;
  private final ImportReport report;
  private final Set<String> missingFields;

  RawEntity(EntityTable table, LuaValue[] values, LuaColumns columns, ImportReport report, Set<String> missingFields) {
    this.schema = table.schema();
    this.kind = EntityWriter.label(table.kind());
    this.values = values;
    this.columns = columns;
    this.report = report;
    this.missingFields = missingFields;
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

  /**
   * The array values (indices 1..n, holes as {@link LuaNil#NIL}) of the table reached from {@code field} by the
   * 1-based {@code path}: {@code nested("objectives", 3)} is the item objective entries, {@code nested("startedBy",
   * 2)} the object starter ids. A scalar at the end of the path is a one-element list. Empty when the field or any
   * step is nil or the field holds an unresolved value (counted {@code unresolved.<kind>.<field>}); a step through
   * a non-table is empty and counted {@code badValue.<kind>.<field>}.
   */
  List<LuaValue> nested(String field, int... path) {
    LuaValue v = at(field, path);
    if (v instanceof LuaTable t) {
      return t.array();
    }
    return v instanceof LuaNil ? List.of() : List.of(v);
  }

  /**
   * The table reached from {@code field} by {@code path}, for hash-keyed tables such as the {@code triggerEnd}
   * zone map; empty under the rules of {@link #nested}, and when the value there is not a table (counted
   * {@code badValue}).
   */
  Optional<LuaTable> nestedTable(String field, int... path) {
    LuaValue v = at(field, path);
    if (v instanceof LuaTable t) {
      return Optional.of(t);
    }
    if (!(v instanceof LuaNil)) {
      columns.bad(kind, field);
    }
    return Optional.empty();
  }

  /** The value at {@code path} inside {@code field}, nil when it is missing or malformed. */
  private LuaValue at(String field, int... path) {
    LuaValue v = raw(field);
    if (path.length == 0 && !(v instanceof LuaTable)) {
      return columns.scalar(kind, field, v) == null ? LuaNil.NIL : v;
    }
    Optional<LuaTable> root = columns.table(kind, field, v);
    if (root.isEmpty()) {
      return LuaNil.NIL;
    }
    v = root.get();
    for (int index : path) {
      if (v instanceof LuaNil) {
        return v;
      }
      if (!(v instanceof LuaTable t)) {
        columns.bad(kind, field);
        return LuaNil.NIL;
      }
      v = t.get(index);
    }
    return v;
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
