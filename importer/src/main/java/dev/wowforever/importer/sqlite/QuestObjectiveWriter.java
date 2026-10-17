package dev.wowforever.importer.sqlite;

import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.lua.LuaNil;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValue;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A quest's objectives ({@code objectives}, {@code triggerEnd}, {@code extraObjectives}) to {@code quest_objective}
 * and {@code quest_objective_point}, and its {@code sourceItemId} / {@code requiredSourceItems} to
 * {@code quest_source_item}.
 *
 * <p>Ordinals run over the objectives in the order creature, object, item, reputation, killCredit, spell, explore
 * (the triggerEnd first, then each extra objective). A killCredit group is one objective: one {@code creature} row
 * per member at the group's ordinal, all with {@code credit_group} = that ordinal, the base creature first and alone
 * carrying the group's text. Reputation rows hold the faction id and the value as text. A spell objective's item is
 * not a source item and is dropped. Questie records no counts, so none is written.
 *
 * <p>Counters: {@code quest.objective.kinds.<kind>} (rows), {@code quest.objective.unknownKind} (an objectives key
 * outside 1..6), {@code quest.sourceItems} (rows); malformed values count {@code badValue.quest.<field>} through
 * {@link LuaColumns}.
 */
final class QuestObjectiveWriter implements AutoCloseable {
  private static final String KIND = "quest";
  private static final String OBJECTIVES = "objectives";
  private static final String TRIGGER_END = "triggerEnd";
  private static final String EXTRA = "extraObjectives";
  /** {@code objectives} sub-tables 1..3: lists of {@code {id, text, icon}}. */
  private static final String[] ENTITY_KINDS = {"creature", "object", "item"};
  private static final int REPUTATION = 4;
  private static final int KILL_CREDIT = 5;
  private static final int SPELL = 6;

  private final LuaColumns columns;
  private final ImportReport report;
  private final Batch objectives;
  private final Batch points;
  private final Batch sourceItems;

  QuestObjectiveWriter(Connection c, LuaColumns columns, ImportReport report) throws SQLException {
    this.columns = columns;
    this.report = report;
    this.objectives = new Batch(c, "INSERT INTO quest_objective(quest_id, ordinal, kind, entity_id, text,"
        + " credit_group) VALUES (?, ?, ?, ?, ?, ?)");
    this.points = new Batch(c, "INSERT INTO quest_objective_point(quest_id, ordinal, zone_id, x, y) VALUES (?, ?, ?, ?, ?)");
    this.sourceItems = new Batch(c, "INSERT INTO quest_source_item(quest_id, item_id, kind) VALUES (?, ?, ?)");
    // always shown, so a clean run reads 0 rather than nothing
    report.count("quest.objective.unknownKind", 0);
    report.count("quest.sourceItems", 0);
  }

  void write(long questId, RawEntity r) throws SQLException {
    new Quest(questId).write(r);
    sources(questId, r);
  }

  private void sources(long questId, RawEntity r) throws SQLException {
    Object provided = r.scalar("sourceItemId");
    if (provided instanceof Long id) {
      if (id != 0) {
        source(questId, id, "provided");
      }
    } else if (provided != null) {
      columns.bad(KIND, "sourceItemId");
    }
    for (long id : r.ids("requiredSourceItems")) {
      source(questId, id, "required");
    }
  }

  private void source(long questId, long itemId, String kind) throws SQLException {
    sourceItems.add(questId, itemId, kind);
    report.count("quest.sourceItems", 1);
  }

  /** One quest's objective rows; holds the running ordinal. */
  private final class Quest {
    private final long id;
    private int ordinal;

    Quest(long id) {
      this.id = id;
    }

    void write(RawEntity r) throws SQLException {
      LuaTable o = r.nestedTable(OBJECTIVES).orElse(null);
      if (o != null) {
        for (Map.Entry<LuaValue, LuaValue> e : o.entries()) {
          if (!(e.getKey() instanceof LuaNumber n && n.isInteger() && n.asLong() >= 1 && n.asLong() <= SPELL)) {
            report.count("quest.objective.unknownKind", 1);
          }
        }
        for (int k = 0; k < ENTITY_KINDS.length; k++) {
          for (LuaValue entry : list(o.get(k + 1))) {
            entity(ENTITY_KINDS[k], entry);
          }
        }
        reputation(o.get(REPUTATION));
        for (LuaValue group : list(o.get(KILL_CREDIT))) {
          killCredit(group);
        }
        for (LuaValue entry : list(o.get(SPELL))) {
          entity("spell", entry);
        }
      }
      Optional<LuaTable> trigger = r.nestedTable(TRIGGER_END);
      if (trigger.isPresent()) {
        explore(TRIGGER_END, text(TRIGGER_END, trigger.get().get(1)), trigger.get().get(2));
      }
      Optional<LuaTable> extras = r.nestedTable(EXTRA);
      for (LuaValue extra : extras.map(t -> list(EXTRA, t)).orElse(List.of())) {
        if (extra instanceof LuaTable e) {
          explore(EXTRA, text(EXTRA, e.get(3)), e.get(1));
        } else {
          columns.bad(KIND, EXTRA);
        }
      }
    }

    /** {@code {id, text, ...}}: creature, object, item and spell objectives. */
    private void entity(String kind, LuaValue entry) throws SQLException {
      if (!(entry instanceof LuaTable t && id(t.get(1)) instanceof Long entity)) {
        columns.bad(KIND, OBJECTIVES);
        return;
      }
      row(kind, entity, text(OBJECTIVES, t.get(2)), null);
      ordinal++;
    }

    /** {@code {faction, value}}. */
    private void reputation(LuaValue v) throws SQLException {
      if (v instanceof LuaNil) {
        return;
      }
      if (!(v instanceof LuaTable t && id(t.get(1)) instanceof Long faction && t.get(2) instanceof LuaNumber value)) {
        columns.bad(KIND, OBJECTIVES);
        return;
      }
      row("reputation", faction, value.isInteger() ? Long.toString(value.asLong()) : Double.toString(value.value()),
          null);
      ordinal++;
    }

    /** {@code {{creature ids}, baseCreatureId, baseCreatureText, icon}}. */
    private void killCredit(LuaValue group) throws SQLException {
      if (!(group instanceof LuaTable g)) {
        columns.bad(KIND, OBJECTIVES);
        return;
      }
      Set<Long> members = new LinkedHashSet<>();
      Long base = id(g.get(2));
      if (base != null) {
        members.add(base);
      } else if (!(g.get(2) instanceof LuaNil)) {
        columns.bad(KIND, OBJECTIVES);
      }
      members.addAll(columns.idList(KIND, OBJECTIVES, g.get(1)));
      if (members.isEmpty()) {
        columns.bad(KIND, OBJECTIVES);
        return;
      }
      String text = text(OBJECTIVES, g.get(3));
      for (long creature : members) {
        row("creature", creature, text, (long) ordinal);
        text = null;
      }
      ordinal++;
    }

    /**
     * One explore row with the points of a {@code {[zoneId] = {{x, y}, ...}}} map (nil: text only); one with
     * neither text nor points is a bad value and writes nothing.
     */
    private void explore(String field, String text, LuaValue zones) throws SQLException {
      if (text == null && zones instanceof LuaNil) {
        columns.bad(KIND, field);
        return;
      }
      row("explore", null, text, null);
      if (zones instanceof LuaTable byZone) {
        for (Map.Entry<LuaValue, LuaValue> zone : byZone.entries()) {
          if (!(id(zone.getKey()) instanceof Long zoneId && zone.getValue() instanceof LuaTable coords)) {
            columns.bad(KIND, field);
            continue;
          }
          for (LuaValue coord : list(field, coords)) {
            if (coord instanceof LuaTable xy && xy.get(1) instanceof LuaNumber x && xy.get(2) instanceof LuaNumber y) {
              points.add(id, ordinal, zoneId, x.value(), y.value());
            } else {
              columns.bad(KIND, field);
            }
          }
        }
      } else if (!(zones instanceof LuaNil)) {
        columns.bad(KIND, field);
      }
      ordinal++;
    }

    private void row(String kind, Long entity, String text, Long creditGroup) throws SQLException {
      objectives.add(id, ordinal, kind, entity, text, creditGroup);
      report.count("quest.objective.kinds." + kind, 1);
    }

    /** The non-nil array values of an objectives sub-table; a non-table is a bad value. */
    private List<LuaValue> list(LuaValue v) {
      return v instanceof LuaNil ? List.of() : list(OBJECTIVES, v);
    }

    private List<LuaValue> list(String field, LuaValue v) {
      if (!(v instanceof LuaTable t)) {
        columns.bad(KIND, field);
        return List.of();
      }
      return t.array().stream().filter(e -> !(e instanceof LuaNil)).toList();
    }

    /** A string, or null for nil and (counted) for any other value. */
    private String text(String field, LuaValue v) {
      if (v instanceof LuaString s) {
        return s.value();
      }
      if (!(v instanceof LuaNil)) {
        columns.bad(KIND, field);
      }
      return null;
    }
  }

  /** An integer id, or null. */
  private static Long id(LuaValue v) {
    return v instanceof LuaNumber n && n.isInteger() ? n.asLong() : null;
  }

  @Override
  public void close() throws SQLException {
    try (objectives; points; sourceItems) {
      // closes (and flushes) all three
    }
  }
}
