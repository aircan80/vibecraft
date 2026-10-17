package dev.wowforever.importer.sqlite;

import dev.wowforever.importer.report.ImportReport;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A quest's links to other quests to {@code quest_relation}: {@code preQuestGroup} (pre_group),
 * {@code preQuestSingle} (pre_single), {@code nextQuestInChain} (next), {@code childQuests} (child),
 * {@code parentQuest} (parent), {@code exclusiveTo} (exclusive), {@code inGroupWith} (group),
 * {@code breadcrumbForQuestId} (breadcrumb_for) and {@code breadcrumbs} (breadcrumb).
 *
 * <p>A (related quest, kind) pair is written once per quest. A related quest absent from the quests being written is
 * still written and counted as {@code quest.relation.missing} (once per row). A scalar id of 0 is Questie's "none";
 * malformed values count {@code badValue.quest.<field>} through {@link LuaColumns}.
 *
 * <p>A negative prerequisite id is written as its absolute id. Questie's {@code QuestieDB:IsPreQuestGroupFulfilled}
 * reads a negative preQuestGroup entry as "negative = completed required, exclusive alternative not accepted": a
 * strict prerequisite on the absolute id. preQuestSingle has no negative handling in Questie; it gets the same
 * treatment defensively.
 */
final class QuestRelationWriter implements AutoCloseable {
  private static final String KIND = "quest";
  /** {@code {field, kind}} for the id-list fields. */
  private static final String[][] LISTS = {
      {"preQuestGroup", "pre_group"}, {"preQuestSingle", "pre_single"}, {"childQuests", "child"},
      {"inGroupWith", "group"}, {"exclusiveTo", "exclusive"}, {"breadcrumbs", "breadcrumb"}};
  /** {@code {field, kind}} for the single-id fields. */
  private static final String[][] SCALARS = {
      {"nextQuestInChain", "next"}, {"parentQuest", "parent"}, {"breadcrumbForQuestId", "breadcrumb_for"}};

  private final LuaColumns columns;
  private final ImportReport report;
  private final Set<Long> questIds;
  private final Batch relations;

  /** {@code questIds}: every quest being written, which decides what counts as missing. */
  QuestRelationWriter(Connection c, LuaColumns columns, ImportReport report, Set<Long> questIds) throws SQLException {
    this.columns = columns;
    this.report = report;
    this.questIds = questIds;
    this.relations = new Batch(c, "INSERT INTO quest_relation(quest_id, related_id, kind) VALUES (?, ?, ?)");
    // always shown, so a clean run reads 0 rather than nothing
    report.count("quest.relation.missing", 0);
  }

  void write(long questId, RawEntity r) throws SQLException {
    Set<String> written = new HashSet<>();
    for (String[] list : LISTS) {
      List<Long> ids = r.ids(list[0]);
      if (list[1].startsWith("pre_")) {
        ids = ids.stream().map(Math::abs).toList();
      }
      add(questId, ids, list[1], written);
    }
    for (String[] scalar : SCALARS) {
      Object v = r.scalar(scalar[0]);
      if (v instanceof Long id) {
        if (id != 0) {
          add(questId, List.of(id), scalar[1], written);
        }
      } else if (v != null) {
        columns.bad(KIND, scalar[0]);
      }
    }
  }

  private void add(long questId, List<Long> related, String kind, Set<String> written) throws SQLException {
    for (long id : related) {
      if (!written.add(kind + ":" + id)) {
        continue;
      }
      relations.add(questId, id, kind);
      if (!questIds.contains(id)) {
        report.count("quest.relation.missing", 1);
      }
    }
  }

  @Override
  public void close() throws SQLException {
    relations.close();
  }
}
