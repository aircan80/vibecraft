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
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
import javax.imageio.ImageIO;
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
    Matcher m = Pattern.compile("(?m)^clientdata\\.coverage\\.pct = (\\d+)\\s*$").matcher(committedReport());
    assertThat(m.find()).as("clientdata.coverage.pct in import-report.txt").isTrue();
    assertThat(Integer.parseInt(m.group(1))).isGreaterThanOrEqualTo(85);
  }

  @Test
  void committedImportReportListsTheUnverifiedCountersAndTheConflicts() throws Exception {
    String report = committedReport();
    for (String kind : List.of("armor", "block", "damage", "stats")) {
      assertThat(report).as("clientdata.unverified.%s in import-report.txt", kind)
          .containsPattern("(?m)^clientdata\\.unverified\\." + kind + " = \\d+\\s*$");
    }
    assertThat(report).as("the Conflicts section").containsPattern("(?m)^== Conflicts ==\\s*$");
    assertThat(report).as("Era effects whose spell Forever lacks")
        .containsPattern("(?m)^clientdata\\.era\\.effectUnresolved = \\d+\\s*$");
  }

  private static String committedReport() throws Exception {
    try (InputStream in = ShippedDataTest.class.getResourceAsStream("/data/import-report.txt")) {
      assertThat(in).as("/data/import-report.txt").isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
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
  void iconOnlyItemsCarryTheirEmbeddedIcon() {
    // Illari's Key has only an Item row: no card, but its own icon
    assertThat(scalar("select has_client_data from item where id = 212347")).isZero();
    assertThat(scalar("select icon_id from item where id = 212347")).isEqualTo(134236L);
    assertThat(scalar("select count(*) from item i where i.has_client_data = 0 and i.icon_id is not null"
        + " and not exists (select 1 from icon c where c.id = i.icon_id)")).isZero();
  }

  @Test
  void eraSourcedItemsShowTheirEraEffects() {
    // Onslaught Girdle and Netherwind Crown: ItemSparse and effects from Classic Era, the text from Forever
    assertThat(column("select text from item_tooltip_line where item_id = 19137 and kind = 'equip' order by ordinal"))
        .containsExactly("Equip: Improves your chance to get a critical strike with melee attacks by 1.0%.",
            "Equip: Improves your chance to hit by 1.0%.");
    assertThat(column("select text from item_tooltip_line where item_id = 16914 and kind = 'equip'")).hasSize(2);
    String eraEpics = "from item i where i.client_source = 'era' and i.quality >= 4 and i.class_id in (2, 4)";
    long epics = scalar("select count(*) " + eraEpics);
    long withEffects = scalar("select count(*) " + eraEpics + " and exists (select 1 from item_tooltip_line l"
        + " where l.item_id = i.id and l.kind in ('use', 'equip', 'proc'))");
    assertThat(withEffects * 100).as("%d of %d Era epic weapons and armour have effect lines", withEffects, epics)
        .isGreaterThanOrEqualTo(85 * epics);
  }

  @Test
  void schoolSpellDamageIsNamed() {
    assertThat(scalar("select count(*) from item_tooltip_line where text like '%Stat #8_' and text not like '%#83'"))
        .as("stat ids 84 to 89").isZero();
    assertThat(column("select text from item_tooltip_line where item_id = 6365 and kind = 'equip'"))
        .as("Strong Fishing Pole").containsExactly("Equip: Increased Fishing +5.");
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

  // --- quest data (phase 3A, schema v3) ---------------------------------------------------------------------------

  @Test
  void schemaVersionIsThree() {
    assertThat(db.meta().get("schema_version")).isEqualTo("3");
  }

  @Test
  void questTableMinimumCounts() {
    assertThat(count("quest_objective")).isGreaterThan(5_000);
    assertThat(count("quest_objective_point")).isGreaterThan(100);
    assertThat(count("quest_source_item")).isGreaterThan(1_000);
    assertThat(count("quest_relation")).isGreaterThan(7_000);
    assertThat(column("select distinct kind from quest_objective"))
        .containsExactlyInAnyOrder("creature", "object", "item", "reputation", "spell", "explore");
    assertThat(column("select distinct kind from quest_source_item")).containsExactlyInAnyOrder("provided", "required");
    assertThat(column("select distinct kind from quest_relation")).containsExactlyInAnyOrder("pre_single", "pre_group",
        "next", "child", "parent", "exclusive", "group", "breadcrumb_for", "breadcrumb");
  }

  @Test
  void questObjectivesNameKnownEntities() {
    assertThat(scalar("select count(*) from quest_objective o where o.kind = 'item'"
        + " and not exists (select 1 from item i where i.id = o.entity_id)")).isZero();
    assertThat(scalar("select count(*) from quest_objective o where o.kind = 'creature'"
        + " and not exists (select 1 from npc n where n.id = o.entity_id)")).isZero();
    assertThat(scalar("select count(*) from quest_objective o where o.kind = 'object'"
        + " and not exists (select 1 from game_object g where g.id = o.entity_id)")).isZero();
    // every point belongs to an explore objective
    assertThat(scalar("select count(*) from quest_objective_point p where not exists (select 1 from quest_objective o"
        + " where o.quest_id = p.quest_id and o.ordinal = p.ordinal and o.kind = 'explore')")).isZero();
  }

  @Test
  void wantedHogger() {
    // Questie records no creature objective for Hogger: the one objective is the Huge Gnoll Claw
    assertThat(rows("select ordinal, kind, entity_id from quest_objective where quest_id = 176"))
        .singleElement().satisfies(r -> {
          assertThat(((Number) r[0]).intValue()).isZero();
          assertThat(r[1]).isEqualTo("item");
          assertThat(((Number) r[2]).longValue()).isEqualTo(1931L);
        });
    assertThat(column("select name from item where id = 1931")).containsExactly("Huge Gnoll Claw");
    assertThat(column("select kind || ' ' || entity_id || ' ' || role from quest_giver where quest_id = 176"))
        .containsExactlyInAnyOrder("object 68 start", "object 156561 start", "npc 240 end");
    assertThat(column("select name from game_object where id in (68, 156561)")).containsOnly("Wanted Poster");
    assertThat(column("select name from npc where id = 240")).containsExactly("Marshal Dughan");
  }

  @Test
  void riseThunderfuryNeedsTheVesselChain() {
    // 7787 Rise, Thunderfury! <- 7786 Thunderaan the Windseeker <- 7785 Examine the Vessel
    assertThat(longs("select related_id from quest_relation where quest_id = 7787 and kind = 'pre_single'"))
        .containsExactly(7786L);
    assertThat(longs("select related_id from quest_relation where quest_id = 7786 and kind = 'pre_single'"))
        .containsExactly(7785L);
    assertThat(prerequisiteClosure(7787)).containsExactlyInAnyOrder(7786L, 7785L);
  }

  @Test
  void drakefireAmuletChain() {
    // 6502 Drakefire Amulet: one prerequisite (6501), whose pre chain walks back 15 quests to 4182 Dragonkin Menace
    assertThat(column("select related_id || ' ' || kind from quest_relation where quest_id = 6502"))
        .containsExactly("6501 pre_single");
    List<Long> chain = prerequisiteClosure(6502);
    assertThat(chain).hasSize(15).contains(6501L, 6403L, 4322L, 4182L);
    String members = chain.stream().map(String::valueOf).reduce("6502", (a, b) -> a + ", " + b);
    assertThat(scalar("select count(*) from quest_relation where quest_id in (" + members + ")"))
        .isGreaterThanOrEqualTo(10);
    // the relation graph does not join it to the Horde chain's 6602 Blood of the Black Dragon Champion; the two
    // share their one objective, the item Blood of the Black Dragon Champion (16663)
    assertThat(column("select kind || ' ' || entity_id from quest_objective where quest_id in (6502, 6602)"))
        .containsExactly("item 16663", "item 16663");
  }

  @Test
  void negativePreQuestGroupIdsAreStoredAbsolute() {
    // 4964's corrected preQuestGroup is {4976, -4962}
    assertThat(longs("select related_id from quest_relation where quest_id = 4964 and kind = 'pre_group'"))
        .containsExactlyInAnyOrder(4976L, 4962L);
    assertThat(scalar("select count(*) from quest_relation where related_id <= 0")).isZero();
  }

  @Test
  void repeatableFollowsTheSpecialFlag() {
    assertThat(scalar("select count(*) from quest where repeatable <> (coalesce(special_flags, 0) & 1)")).isZero();
    assertThat(scalar("select count(*) from quest where repeatable = 1")).isGreaterThan(500);
    assertThat(scalar("select repeatable from quest where id = 16")).as("Give Gerard a Drink").isEqualTo(1);
    assertThat(scalar("select repeatable from quest where id = 176")).as("Wanted: Hogger").isZero();
  }

  @Test
  void questSortLabels() {
    assertThat(scalar("select count(*) from quest where sort_label is not null")).isGreaterThan(1_000);
    assertThat(scalar("select count(*) from quest where sort_label is not null and zone_or_sort >= 0")).isZero();
    assertThat(column("select distinct sort_label from quest"))
        .contains("Warlock", "Camping", "Darkmoon Faire", "Lunar Festival", "Blacksmithing", "Legendary");
  }

  @Test
  void committedImportReportShowsTheQuestCounters() throws Exception {
    String report = committedReport();
    // 4604's preQuestSingle 2955 is not a quest of the database; the row is kept
    assertThat(reportCounter(report, "quest.relation.missing")).isEqualTo(1);
    assertThat(scalar("select count(*) from quest_relation r where not exists (select 1 from quest q"
        + " where q.id = r.related_id)")).isEqualTo(1);
    assertThat(column("select quest_id || ' ' || related_id || ' ' || kind from quest_relation r"
        + " where not exists (select 1 from quest q where q.id = r.related_id)")).containsExactly("4604 2955 pre_single");
    long kinds = 0;
    for (Object kind : column("select distinct kind from quest_objective")) {
      long n = reportCounter(report, "quest.objective.kinds." + kind);
      assertThat(n).as("quest.objective.kinds.%s", kind)
          .isEqualTo(scalar("select count(*) from quest_objective where kind = '" + kind + "'"));
      kinds += n;
    }
    assertThat(kinds).isEqualTo(count("quest_objective"));
    assertThat(reportCounter(report, "quest.objective.unknownKind")).isZero();
    assertThat(reportCounter(report, "quest.sourceItems")).isEqualTo(count("quest_source_item"));
    assertThat(reportCounter(report, "clientdata.uiAssets")).isEqualTo(6);
  }

  @Test
  void railIconsAreEmbedded() throws Exception {
    List<String> names = new ArrayList<>();
    for (String icon : List.of("home", "items", "npcs", "quests", "zones", "instances")) {
      names.add("rail." + icon);
      names.add("rail." + icon + "@24");
    }
    assertThat(column("select name from ui_asset")).containsExactlyInAnyOrderElementsOf(names);
    for (String name : names) {
      byte[] png;
      synchronized (db) {
        try (var ps = db.connection().prepareStatement("select png from ui_asset where name = ?")) {
          ps.setString(1, name);
          try (ResultSet rs = ps.executeQuery()) {
            rs.next();
            png = rs.getBytes(1);
          }
        }
      }
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
      assertThat(image).as(name).isNotNull();
      if (name.endsWith("@24")) {
        assertThat(image.getWidth() + "x" + image.getHeight()).as(name).isEqualTo("24x24");
      }
    }
  }

  /** The quests reachable from {@code quest} over pre_single and pre_group rows, the quest itself excluded. */
  private static List<Long> prerequisiteClosure(long quest) {
    return longs("with recursive c(id) as (select " + quest + " union select r.related_id from quest_relation r"
        + " join c on r.quest_id = c.id where r.kind in ('pre_single', 'pre_group')) select id from c where id <> "
        + quest);
  }

  private static List<Long> longs(String sql) {
    return column(sql).stream().map(o -> ((Number) o).longValue()).toList();
  }

  private static long reportCounter(String report, String key) {
    Matcher m = Pattern.compile("(?m)^" + Pattern.quote(key) + " = (\\d+)\\s*$").matcher(report);
    assertThat(m.find()).as("%s in import-report.txt", key).isTrue();
    return Long.parseLong(m.group(1));
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
