package dev.wowforever.importer.clientdata;

import dev.wowforever.importer.report.ImportReport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Item sets: {@code ItemSet(ID, Name_lang, RequiredSkill, RequiredSkillRank, ItemID_0..16)} with their bonuses,
 * {@code ItemSetSpell(ItemSetID, SpellID, Threshold)}, each bonus text being the spell's formatted description.
 * Only the requested sets are loaded (the pipeline asks for the sets its items name). Counts {@value #SETS}.
 */
public final class ItemSetLoader {
  public static final String SETS = "clientdata.sets";
  private static final int PIECES = 17;
  static final List<String> SET_COLUMNS = Stream.concat(
          Stream.of("ID", "Name_lang", "RequiredSkill", "RequiredSkillRank"),
          IntStream.range(0, PIECES).mapToObj(i -> "ItemID_" + i))
      .toList();
  static final List<String> SPELL_COLUMNS = List.of("ID", "ItemSetID", "SpellID", "Threshold");

  private ItemSetLoader() {}

  /** The sets among {@code setIds} that the client has, by id in id order. */
  public static Map<Long, ItemSetInfo> load(ClientDataSource src, Set<Long> setIds, SpellDescriptionFormatter fmt,
      ImportReport report) {
    Map<Long, CsvTable.Row> sets = require(src.table("ItemSet"), SET_COLUMNS).indexByLong("ID");
    Map<Long, List<CsvTable.Row>> spells = require(src.table("ItemSetSpell"), SPELL_COLUMNS).groupByLong("ItemSetID");
    Map<Long, ItemSetInfo> out = new LinkedHashMap<>();
    for (long id : new TreeSet<>(setIds)) {
      CsvTable.Row s = sets.get(id);
      if (s == null) {
        continue;
      }
      List<Long> pieces = new ArrayList<>();
      for (int i = 0; i < PIECES; i++) {
        long item = s.longOr("ItemID_" + i, 0);
        if (item > 0) {
          pieces.add(item);
        }
      }
      List<CsvTable.Row> rows = new ArrayList<>(spells.getOrDefault(id, List.of()));
      rows.sort(Comparator.comparingInt((CsvTable.Row r) -> r.intOr("Threshold", 0))
          .thenComparingLong(r -> r.longOr("ID", 0)));
      List<ItemSetInfo.Bonus> bonuses = new ArrayList<>();
      for (CsvTable.Row r : rows) {
        long spell = r.longOr("SpellID", 0);
        SpellDescriptionFormatter.Formatted f = fmt.description(spell);
        bonuses.add(new ItemSetInfo.Bonus(r.intOr("Threshold", 0), spell, f.text().isEmpty() ? null : f.text(),
            f.complete()));
      }
      int skill = s.intOr("RequiredSkill", 0);
      out.put(id, new ItemSetInfo(id, s.get("Name_lang"), pieces, skill > 0 ? skill : null,
          skill > 0 ? s.intOr("RequiredSkillRank", 0) : null, bonuses));
    }
    report.count(SETS, out.size());
    return Collections.unmodifiableMap(out);
  }

  private static CsvTable require(CsvTable t, List<String> columns) {
    for (String col : columns) {
      if (!t.hasColumn(col)) {
        throw new IllegalStateException("client table " + t.source() + " has no column " + col);
      }
    }
    return t;
  }
}
