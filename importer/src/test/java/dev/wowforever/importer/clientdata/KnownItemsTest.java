package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.report.ImportReport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The known-items gate (spec 4.4, ruling 10): the computed values of 28 items against the secondary oracle, Wowhead's
 * Forever tooltips ({@code fixtures/clientdata/known-items.csv}, produced as {@code KnownItemsOracle.md} describes).
 *
 * <p>Each row is compared only in its {@code compare_kinds}; this task's kinds are {@code damage} (min, max, school),
 * {@code speed} (delay), {@code dps} (to the precision the tooltip shows), {@code armor} (the base value; an empty
 * cell expects none), {@code stat} and {@code resist}. Stats are compared as values per id over the ids Wowhead shows
 * as plain {@code +N} lines ({@link #TOOLTIP_STATS}); ratings and spell power, which it rewrites as "Equip:" text,
 * are outside the gate. Every row runs against the fixture, and again against the real client data when it is cached
 * on this machine.
 */
class KnownItemsTest {
  static final Set<Integer> TOOLTIP_STATS = Set.of(0, 1, 3, 4, 5, 6, 7, 39, 48, 50, 51, 52, 53, 54, 55, 56);
  static final Set<String> KINDS = Set.of("damage", "speed", "dps", "armor", "stat", "resist");

  record Row(long id, String name, Set<String> kinds, String stats, String damage, String armor) {}

  @Test
  void theFixtureCoversEveryCategoryTheSpecAsksFor() {
    List<Row> rows = rows();
    assertThat(rows).hasSizeGreaterThanOrEqualTo(25);
    Map<Long, ClientItem> items = ItemSparseLoader.load(ClientItems.fixture(),
        rows.stream().map(Row::id).collect(Collectors.toSet()), new ImportReport());
    assertThat(items.keySet()).as("every known item is in the fixture")
        .containsAll(rows.stream().map(Row::id).toList());
    assertThat(items.values()).allMatch(i -> ClientItem.FOREVER.equals(i.clientSource()));
    Set<Integer> qualities = items.values().stream().map(ClientItem::quality).collect(Collectors.toSet());
    assertThat(qualities).containsAll(List.of(0, 1, 2, 3, 4, 5));
    Set<Integer> slots = items.values().stream().map(ClientItem::inventoryType).collect(Collectors.toSet());
    assertThat(slots).as("head, neck, wrist, chest, legs, feet, hands, finger, trinket, back, holdable, one-hand, "
        + "two-hand, main hand, off hand, shield, bow, thrown, ranged right, ammo")
        .containsAll(List.of(1, 2, 9, 5, 7, 8, 10, 11, 12, 16, 23, 13, 17, 21, 22, 14, 15, 25, 26, 24));
    for (Row r : rows) {
      assertThat(KINDS).as("row %d kinds", r.id()).containsAll(r.kinds());
    }
  }

  @Test
  void fixtureValuesMatchTheOracle() {
    assertThat(mismatches(ClientItems.fixture())).isEmpty();
  }

  @Test
  void realClientDataMatchesTheOracle() {
    Optional<Path> dir = RealClientData.dir();
    assumeTrue(dir.isPresent(), "no cached client data on this machine");
    assertThat(mismatches(new ClientDataSource(dir.get()))).isEmpty();
  }

  static List<String> mismatches(ClientDataSource src) {
    List<Row> rows = rows();
    Map<Long, ClientItem> items = ItemSparseLoader.load(src, rows.stream().map(Row::id).collect(Collectors.toSet()),
        new ImportReport());
    ScalingTables tables = ScalingTables.load(src);
    List<String> out = new ArrayList<>();
    for (Row r : rows) {
      ClientItem item = items.get(r.id());
      if (item == null) {
        out.add(r.id() + " " + r.name() + ": not in the client data");
        continue;
      }
      ImportReport report = new ImportReport();
      List<ItemStat> stats = ItemStatsCalculator.stats(item, tables, report);
      Optional<ItemDamage> damage = ItemDamageCalculator.damage(item, tables, report);
      Optional<ItemArmor> armor = ItemArmorCalculator.armor(item, tables, report);
      String p = r.id() + " " + r.name() + ": ";

      Map<Integer, Integer> expected = parseStats(r.stats());
      Map<Integer, Integer> actual = new TreeMap<>();
      stats.stream().filter(s -> TOOLTIP_STATS.contains(s.statId())).forEach(s -> actual.put(s.statId(), s.value()));
      for (String kind : List.of("stat", "resist")) {
        if (r.kinds().contains(kind)) {
          Map<Integer, Integer> e = only(expected, kind);
          Map<Integer, Integer> a = only(actual, kind);
          if (!e.equals(a)) {
            out.add(p + kind + " expected " + e + " got " + a);
          }
        }
      }
      if (r.kinds().contains("armor")) {
        String e = r.armor().isEmpty() ? "none" : r.armor().split("/")[0];
        String a = armor.map(x -> String.valueOf(x.armor())).orElse("none");
        if (!e.equals(a)) {
          out.add(p + "armor expected " + e + " got " + a);
        }
      }
      if (r.kinds().contains("damage") || r.kinds().contains("speed") || r.kinds().contains("dps")) {
        if (damage.isEmpty()) {
          out.add(p + "no damage computed");
          continue;
        }
        ItemDamage d = damage.get();
        String[] parts = r.damage().split("/"); // min-max/delayMs/dps/school
        String[] range = parts[0].split("-");
        if (r.kinds().contains("damage")) {
          String e = range[0] + "-" + range[1] + " school " + parts[3];
          String a = d.min() + "-" + d.max() + " school " + d.school();
          if (!e.equals(a)) {
            out.add(p + "damage expected " + e + " got " + a);
          }
        }
        if (r.kinds().contains("speed") && Integer.parseInt(parts[1]) != d.delayMs()) {
          out.add(p + "speed expected " + parts[1] + " got " + d.delayMs());
        }
        if (r.kinds().contains("dps")) {
          int decimals = parts[2].contains(".") ? parts[2].length() - parts[2].indexOf('.') - 1 : 0;
          double tolerance = 0.5 * Math.pow(10, -decimals) + 1e-9;
          if (Math.abs(Double.parseDouble(parts[2]) - d.dps()) > tolerance) {
            out.add(p + "dps expected " + parts[2] + " got " + d.dps());
          }
        }
      }
    }
    return out;
  }

  static List<Row> rows() {
    CsvTable t = CsvTable.read(Fixtures.clientDataRoot().resolve("known-items.csv"));
    List<Row> out = new ArrayList<>();
    for (CsvTable.Row r : t.rows()) {
      Set<String> kinds = new LinkedHashSet<>();
      for (String k : r.get("compare_kinds").split(",")) {
        if (!k.isBlank()) {
          kinds.add(k.strip());
        }
      }
      out.add(new Row(r.getLong("item_id").orElseThrow(), r.get("name"), kinds, r.get("stats"), r.get("damage"),
          r.get("armor")));
    }
    return out;
  }

  private static Map<Integer, Integer> parseStats(String cell) {
    Map<Integer, Integer> out = new TreeMap<>();
    for (String pair : cell.split(";")) {
      if (!pair.isBlank()) {
        String[] kv = pair.split(":");
        out.merge(Integer.parseInt(kv[0].strip()), Integer.parseInt(kv[1].strip()), Integer::sum);
      }
    }
    return out;
  }

  private static Map<Integer, Integer> only(Map<Integer, Integer> stats, String kind) {
    Map<Integer, Integer> out = new TreeMap<>();
    stats.forEach((id, v) -> {
      if (StatNames.kind(id).equals(kind)) {
        out.put(id, v);
      }
    });
    return out;
  }
}
