package dev.wowforever.app;

import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.Database;
import dev.wowforever.app.db.DatabaseInstaller;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.SearchHit;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.AppController;
import dev.wowforever.app.ui.MainView;
import dev.wowforever.app.ui.ResultsPanel;
import dev.wowforever.app.ui.StatusBar;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JComponent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Sanity checks against the real committed database. It is extracted from the classpath resource by the
 * production {@link DatabaseInstaller}, so these tests also cover the extraction path on the shipped file.
 * Skip them with {@code -DexcludedGroups=shipped-data}.
 */
@Tag("shipped-data")
class ShippedDataTest {
  static Database db;
  static Repositories repos;

  @BeforeAll
  static void open(@TempDir Path dir) throws Exception {
    Path file = new DatabaseInstaller(dir).install();
    db = Database.open(file);
    repos = Repositories.of(db);
  }

  @AfterAll
  static void close() {
    if (db != null) {
      db.close();
    }
  }

  private static long scalar(String sql) {
    synchronized (db) {
      try (Statement st = db.connection().createStatement(); ResultSet rs = st.executeQuery(sql)) {
        rs.next();
        return rs.getLong(1);
      } catch (SQLException e) {
        throw new IllegalStateException(sql, e);
      }
    }
  }

  private static List<Object> column(String sql) {
    synchronized (db) {
      try (Statement st = db.connection().createStatement(); ResultSet rs = st.executeQuery(sql)) {
        List<Object> out = new ArrayList<>();
        while (rs.next()) {
          out.add(rs.getObject(1));
        }
        return out;
      } catch (SQLException e) {
        throw new IllegalStateException(sql, e);
      }
    }
  }

  private static long count(String table) {
    return scalar("select count(*) from " + table);
  }

  @Test
  void minimumCounts() {
    assertThat(count("item")).isGreaterThan(15_000);
    assertThat(count("npc")).isGreaterThan(8_000);
    assertThat(count("instance")).isEqualTo(33);
    assertThat(count("item_npc_drop")).isGreaterThan(50_000);
    assertThat(count("search_index")).isGreaterThan(30_000);
  }

  @Test
  void noOrphansInPrimaryLinks() {
    // AtlasLoot items missing from Questie: tolerate < 5 %
    assertThat(scalar("select count(*) from boss_loot bl left join item i on i.id = bl.item_id where i.id is null"))
        .isLessThan(count("boss_loot") / 20);
    assertThat(scalar("select count(*) from boss b left join instance i on i.id = b.instance_id where i.id is null"))
        .isZero();
    assertThat(scalar("select count(*) from npc_spawn s left join zone z on z.id = s.zone_id where z.id is null"))
        .isZero();
    // documented upstream gap: 325 at the pinned commits
    assertThat(scalar("select count(*) from item_npc_drop d left join npc n on n.id = d.npc_id where n.id is null"))
        .isLessThanOrEqualTo(400);
  }

  @Test
  void buildIdResourceMatchesTheShippedMeta() throws Exception {
    try (InputStream in = ShippedDataTest.class.getResourceAsStream("/data/build-id.txt")) {
      assertThat(in).isNotNull();
      assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8).trim()).isEqualTo(db.meta().get("build_id"));
    }
  }

  @Test
  void itemClassListsAreNotCapped() {
    long armor = scalar("select count(*) from item where class_id = 4");
    assertThat(armor).isGreaterThan(500);
    ResultsPanel results = new ResultsPanel(t -> {});
    StatusBar status = new StatusBar();
    MainView view = new MainView() {
      @Override public ResultsPanel results() { return results; }
      @Override public void setDetail(JComponent c) { }
      @Override public void setHistoryState(boolean canBack, boolean canForward) { }
      @Override public StatusBar status() { return status; }
      @Override public void setTitleSuffix(String s) { }
    };
    new AppController(repos, view, Runnable::run).open(new ViewTarget.ItemClass(4, -1));
    assertThat((long) results.model().getRowCount()).isEqualTo(armor);
    assertThat(results.title()).isEqualTo(String.format(Locale.ROOT, "Armor (%,d)", armor));
  }

  @Test
  void bossLootItemsShowTheirInstanceLoot() {
    // Scarlet Monastery trash loot: in boss_loot, but no item_npc_drop row (extra lists stay out of it)
    assertThat(repos.items().detail(10329).get().inInstanceLoot()).isNotEmpty();
    for (Object id : column("select distinct bl.item_id from boss_loot bl join item i on i.id = bl.item_id"
        + " order by random() limit 20")) {
      long item = ((Number) id).longValue();
      assertThat(repos.items().detail(item).get().inInstanceLoot()).as("item %d", item).isNotEmpty();
    }
  }

  @Test
  void zonePlaceholderNamesAreNotSearchable() {
    for (Object name : column("select name from search_index where name like 'Zone #%'")) {
      assertThat(repos.search().search((String) name, Optional.empty(), 200)).extracting(SearchHit::name)
          .noneMatch(n -> n.startsWith("Zone #"));
    }
    assertThat(repos.search().search("Zone", Optional.of(ZONE), 200)).extracting(SearchHit::name)
        .noneMatch(n -> n.startsWith("Zone #"));
  }

  @Test
  void knownFacts() {
    assertThat(repos.items().detail(14149).get().droppedBy()).extracting(s -> s.ref().id()).contains(11520L);
    // Eye of Sulfuras from Ragnaros (Thunderfury is crafted, not dropped)
    var sulfuras = repos.items().detail(17204).get().droppedBy().stream()
        .filter(s -> s.ref().id() == 11502L).findFirst();
    assertThat(sulfuras).isPresent();
    assertThat(sulfuras.get().dropPct()).isNotNull().isBetween(7.5, 8.6);
    assertThat(repos.npcs().detail(639).get().drops())
        .anyMatch(l -> l.item().id() == 2874 && l.dropPct() != null && l.dropPct() > 50);
    assertThat(repos.search().search("hogger", Optional.empty(), 5)).extracting(SearchHit::ref)
        .contains(new EntityRef(NPC, 448));
    assertThat(repos.zones().detail(1581).get().summary().name()).isEqualTo("The Deadmines");
    assertThat(repos.instances().all()).hasSize(33).extracting(InstanceSummary::name).contains("Molten Core");
  }

  // --- client data (phase 2A) -------------------------------------------------------------------------------------

  @Test
  void clientDataCoversTheNamedItems() {
    long named = scalar("select count(*) from item where name is not null");
    long withClient = scalar("select count(*) from item where name is not null and has_client_data = 1");
    assertThat(withClient * 100).as("%d of %d named items have client data", withClient, named)
        .isGreaterThanOrEqualTo(85 * named);
  }

  @Test
  void clientSourceIsForeverOrEraAndForeverDominates() {
    assertThat(column("select distinct client_source from item where client_source is not null"))
        .allMatch(s -> s.equals("forever") || s.equals("era"));
    assertThat(scalar("select count(*) from item where has_client_data = 0 and client_source is not null")).isZero();
    long era = scalar("select count(*) from item where client_source = 'era'");
    long forever = scalar("select count(*) from item where client_source = 'forever'");
    assertThat(era).isLessThan(forever);
  }

  @Test
  void committedImportReportShowsTheCoverage() throws Exception {
    try (InputStream in = ShippedDataTest.class.getResourceAsStream("/data/import-report.txt")) {
      assertThat(in).isNotNull();
      String report = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      Matcher m = Pattern.compile("(?m)^clientdata\\.coverage\\.pct = (\\d+)\\s*$").matcher(report);
      assertThat(m.find()).as("clientdata.coverage.pct in import-report.txt").isTrue();
      assertThat(Integer.parseInt(m.group(1))).isGreaterThanOrEqualTo(85);
    }
  }

  @Test
  void everyClientItemHasAnIcon() {
    // its own icon when converted, else the "?" fallback, id 0
    long missing = scalar("select count(*) from item i where i.has_client_data = 1"
        + " and not exists (select 1 from icon c where c.id = i.icon_id)");
    if (missing > 0) {
      assertThat(scalar("select count(*) from icon where id = 0")).as("%d items need the fallback", missing)
          .isEqualTo(1);
    }
    assertThat(count("icon")).isGreaterThan(2_000);
  }

  @Test
  void zoneMaps() {
    assertThat(count("zone_map")).isGreaterThanOrEqualTo(45);
    // Westfall
    assertThat(column("select m.width || 'x' || m.height from zone z join zone_map m on m.ui_map_id = z.ui_map_id"
        + " where z.id = 40")).containsExactly("1002x668");
  }

  @Test
  void legendaryTooltips() {
    // Eye of Sulfuras: a Use line; Thunderfury: a Chance on hit line
    assertThat(scalar("select quality from item where id = 17204")).isEqualTo(5);
    assertThat(column("select text from item_tooltip_line where item_id = 17204 and kind = 'use'"))
        .singleElement().asString().startsWith("Use: ");
    assertThat(scalar("select quality from item where id = 19019")).isEqualTo(5);
    assertThat(column("select text from item_tooltip_line where item_id = 19019 and kind = 'proc'"))
        .singleElement().asString().startsWith("Chance on hit: Blasts your enemy with lightning");
  }

  @Test
  void everyRatedDropHasItsSources() {
    assertThat(count("drop_rate_source"))
        .isGreaterThanOrEqualTo(scalar("select count(*) from item_npc_drop where drop_pct is not null"));
  }

  /**
   * The importer's known-items gate ({@code KnownItemsTest}) on the stored values: every row of
   * {@code importer/src/test/resources/fixtures/clientdata/known-items.csv} against {@code item_stat},
   * {@code item_armor}, {@code item_damage} and {@code item_tooltip_line}, in that row's {@code compare_kinds}. Rows whose
   * item is not in the shipped database are skipped; every other row must have client data.
   */
  @Test
  void knownItemsMatchTheOracle() throws Exception {
    List<KnownItemsCsv.Row> rows = KnownItemsCsv.read(KnownItemsCsv.locate());
    assertThat(rows).hasSizeGreaterThanOrEqualTo(25);
    List<String> mismatches = new ArrayList<>();
    List<Long> notShipped = new ArrayList<>();
    for (KnownItemsCsv.Row r : rows) {
      String p = r.id() + " " + r.name() + ": ";
      if (scalar("select count(*) from item where id = " + r.id()) == 0) {
        notShipped.add(r.id()); // an oracle item Questie does not list (17783 Talisman of Binding Fragment)
        continue;
      }
      if (scalar("select count(*) from item where id = " + r.id() + " and has_client_data = 1") == 0) {
        mismatches.add(p + "no client data");
        continue;
      }
      Map<Integer, Integer> actual = new TreeMap<>();
      for (Object[] s : rows("select stat_id, value from item_stat where item_id = " + r.id())) {
        int stat = ((Number) s[0]).intValue();
        if (KnownItemsCsv.TOOLTIP_STATS.contains(stat)) {
          actual.merge(stat, ((Number) s[1]).intValue(), Integer::sum);
        }
      }
      for (String kind : List.of("stat", "resist")) {
        if (r.kinds().contains(kind)) {
          Map<Integer, Integer> e = KnownItemsCsv.only(r.stats(), kind);
          Map<Integer, Integer> a = KnownItemsCsv.only(actual, kind);
          if (!e.equals(a)) {
            mismatches.add(p + kind + " expected " + e + " got " + a);
          }
        }
      }
      if (r.kinds().contains("armor")) {
        String e = r.armor().isEmpty() ? "none" : r.armor().split("/")[0];
        List<Object> armor = column("select armor from item_armor where item_id = " + r.id() + " and armor is not null");
        String a = armor.isEmpty() ? "none" : String.valueOf(((Number) armor.get(0)).longValue());
        if (!e.equals(a)) {
          mismatches.add(p + "armor expected " + e + " got " + a);
        }
      }
      if (r.kinds().contains("damage") || r.kinds().contains("speed") || r.kinds().contains("dps")) {
        List<Object[]> d = rows("select min, max, school, delay_ms, dps from item_damage where item_id = " + r.id());
        if (d.isEmpty()) {
          mismatches.add(p + "no damage stored");
        } else {
          mismatches.addAll(damageMismatches(p, r, d.get(0)));
        }
      }
      for (String kind : KnownItemsCsv.TEXT_KINDS) {
        if (r.kinds().contains(kind)) {
          List<String> e = r.lines().getOrDefault(kind, List.of()).stream().map(KnownItemsCsv::normal).toList();
          List<String> a = column("select text from item_tooltip_line where item_id = " + r.id() + " and kind = '"
              + kind + "' order by ordinal").stream().map(t -> KnownItemsCsv.normal((String) t)).toList();
          if (!e.equals(a)) {
            mismatches.add(p + kind + "\n  expected " + e + "\n  stored   " + a);
          }
        }
      }
    }
    assertThat(mismatches).isEmpty();
    assertThat(rows.size() - notShipped.size()).as("oracle rows compared (not shipped: %s)", notShipped)
        .isGreaterThanOrEqualTo(25);
  }

  /** {@code row}: min, max, school, delay_ms, dps; the oracle's damage cell is {@code min-max/delayMs/dps/school}. */
  private static List<String> damageMismatches(String p, KnownItemsCsv.Row r, Object[] row) {
    List<String> out = new ArrayList<>();
    String[] parts = r.damage().split("/");
    String[] range = parts[0].split("-");
    if (r.kinds().contains("damage")) {
      String e = range[0] + "-" + range[1] + " school " + parts[3];
      String a = row[0] + "-" + row[1] + " school " + row[2];
      if (!e.equals(a)) {
        out.add(p + "damage expected " + e + " got " + a);
      }
    }
    if (r.kinds().contains("speed") && (row[3] == null || Integer.parseInt(parts[1]) != ((Number) row[3]).intValue())) {
      out.add(p + "speed expected " + parts[1] + " got " + row[3]);
    }
    if (r.kinds().contains("dps")) {
      int decimals = parts[2].contains(".") ? parts[2].length() - parts[2].indexOf('.') - 1 : 0;
      double tolerance = 0.5 * Math.pow(10, -decimals) + 1e-9;
      if (row[4] == null || Math.abs(Double.parseDouble(parts[2]) - ((Number) row[4]).doubleValue()) > tolerance) {
        out.add(p + "dps expected " + parts[2] + " got " + row[4]);
      }
    }
    return out;
  }

  private static List<Object[]> rows(String sql) {
    synchronized (db) {
      try (Statement st = db.connection().createStatement(); ResultSet rs = st.executeQuery(sql)) {
        List<Object[]> out = new ArrayList<>();
        int n = rs.getMetaData().getColumnCount();
        while (rs.next()) {
          Object[] row = new Object[n];
          for (int i = 0; i < n; i++) {
            row[i] = rs.getObject(i + 1);
          }
          out.add(row);
        }
        return out;
      } catch (SQLException e) {
        throw new IllegalStateException(sql, e);
      }
    }
  }
}
