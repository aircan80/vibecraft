package dev.wowforever.app;

import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import dev.wowforever.app.db.DatabaseInstaller;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.SearchHit;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.AppController;
import dev.wowforever.app.ui.MainView;
import dev.wowforever.app.ui.StatusBar;
import dev.wowforever.app.ui.StatusSink;
import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.detail.DetailPanels;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.detail.TestPages;
import dev.wowforever.app.ui.pages.InstancePage;
import dev.wowforever.app.ui.pages.ItemPage;
import dev.wowforever.app.ui.pages.ListPage;
import dev.wowforever.app.ui.pages.NpcPage;
import dev.wowforever.app.ui.pages.ObjectPage;
import dev.wowforever.app.ui.pages.QuestPage;
import dev.wowforever.app.ui.pages.ZonePage;
import dev.wowforever.app.ui.widgets.MasterList;
import dev.wowforever.app.ui.widgets.RailIcons;
import dev.wowforever.app.ui.widgets.RailTarget;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Sanity checks against the real committed database. It is extracted from the classpath resource by the
 * production {@link DatabaseInstaller}, so these tests also cover the extraction path on the shipped file
 * ({@link ShippedDataSupport}). The quest data checks are in {@link ShippedQuestDataTest}, the crafting and drop rate
 * provenance checks in {@link ShippedProvenanceDataTest}. Skip them with {@code -DexcludedGroups=shipped-data}.
 */
@Tag("shipped-data")
class ShippedDataTest extends ShippedDataSupport {
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
    MasterList results = new MasterList();
    StatusBar status = new StatusBar();
    JComponent[] detail = new JComponent[1];
    MainView view = new MainView() {
      @Override public MasterList masterList() { return results; }
      @Override public void setDetail(JComponent c) { detail[0] = c; }
      @Override public void setHistoryState(boolean canBack, boolean canForward) { }
      @Override public StatusBar status() { return status; }
      @Override public void setTitleSuffix(String s) { }
      @Override public void selectRail(RailTarget target) { }
    };
    new AppController(repos, view, Runnable::run).open(ViewTarget.List.itemClass(4, -1));
    ListPage page = (ListPage) detail[0];
    assertThat((long) page.results().rowCount()).isEqualTo(armor);
    assertThat(page.section().heading().text()).isEqualTo(String.format(Locale.ROOT, "Results (%,d)", armor));
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

  /** Lays {@code c} and everything in it out at its size, as a shown window would. */
  private static void layOut(Container c) {
    c.doLayout();
    for (Component child : c.getComponents()) {
      if (child instanceof Container sub) {
        layOut(sub);
      }
    }
  }

  @Test
  void theCompactSpawnTableKeepsItsWholeRowUnderTheMap() {
    Object loaded = DetailPanels.load(new EntityRef(NPC, 448), repos).orElseThrow();
    JPanel compact = new JPanel(new BorderLayout());
    compact.putClientProperty(CompactColumns.KEY, Boolean.TRUE); // as the compact window's content does
    var hogger = (NpcPage) TestPages.render(loaded, repos, t -> {}, Runnable::run, StatusSink.NONE);
    compact.add(hogger);
    compact.setSize(420, 900);
    LinkTable<?> spawns = hogger.section("Spawns").table();
    JTable table = spawns.table();
    Set<Integer> mapWidths = new HashSet<>();
    for (int pass = 1; pass <= 3; pass++) { // as the map's own revalidations lay the window out again
      layOut(compact);
      mapWidths.add(hogger.spawnMap().getWidth());
      assertThat(table.getRowCount()).isEqualTo(1);
      assertThat(table.getHeight()).as("pass %d", pass).isGreaterThanOrEqualTo(table.getRowHeight());
      assertThat(spawns.getHeight()).as("pass %d", pass).isEqualTo(spawns.getPreferredSize().height);
    }
    // the page's scrollbar went between the first layouts, so the map was laid out at a width the Spawns section
    // had not been measured at: what cut the coordinates row to half its height
    assertThat(mapWidths).hasSizeGreaterThan(1);
    assertThat(table.getValueAt(0, 0)).isEqualTo("25.0, 92.9");
  }

  @Test
  void everyPageKindRendersFromTheShippedDatabase() {
    long moltenCore = repos.instances().all().stream().filter(i -> "Molten Core".equals(i.name())).findFirst()
        .orElseThrow().id();
    Map<EntityRef, Class<?>> pages = new LinkedHashMap<>();
    pages.put(new EntityRef(EntityKind.ITEM, 19019), ItemPage.class);
    pages.put(new EntityRef(NPC, 448), NpcPage.class);
    pages.put(new EntityRef(EntityKind.INSTANCE, moltenCore), InstancePage.class);
    pages.put(new EntityRef(ZONE, 40), ZonePage.class);
    pages.put(new EntityRef(EntityKind.OBJECT, 94), ObjectPage.class);
    pages.put(new EntityRef(EntityKind.QUEST, 176), QuestPage.class);
    pages.put(new EntityRef(EntityKind.QUEST, 7787), QuestPage.class); // Rise, Thunderfury!
    BufferedImage canvas = new BufferedImage(1100, 900, BufferedImage.TYPE_INT_ARGB);
    pages.forEach((ref, kind) -> {
      Object loaded = DetailPanels.load(ref, repos).orElseThrow(() -> new AssertionError("no page for " + ref));
      JComponent page = TestPages.render(loaded, repos, t -> {}, Runnable::run, StatusSink.NONE);
      assertThat(page).as(ref.toString()).isInstanceOf(kind);
      for (int width : new int[] {1100, 420}) { // the full window and the compact one
        page.setSize(width, 900);
        layOut(page);
        Graphics2D g = canvas.createGraphics();
        try {
          page.paint(g);
        } finally {
          g.dispose();
        }
      }
    });
  }

  /** The rail's client art (phase 3B Task 3): every target gets its 24 px plate image and a 16 px client icon. */
  @Test
  void loadsAllSixFromTheShippedDatabase() {
    RailIcons icons = RailIcons.load(repos.assets()::uiIcon);
    for (RailTarget t : RailTarget.values()) {
      assertThat(icons.plate(t)).as(t.assetName()).isPresent();
      assertThat(icons.plates().get(t).getWidth() + "x" + icons.plates().get(t).getHeight()).as(t.assetName())
          .isEqualTo("24x24");
      assertThat(icons.small(t, 16)).as(t.assetName()).isNotInstanceOf(FlatSVGIcon.class);
    }
  }

  @Test
  void theShippedPagesReadAsReviewed() {
    var thunderfury = (ItemPage) TestPages.build(new EntityRef(EntityKind.ITEM, 19019), repos, t -> {});
    assertThat(thunderfury.card().name().getText()).isEqualTo("Thunderfury, Blessed Blade of the Windseeker");
    assertThat(((ZonePage) TestPages.build(new EntityRef(ZONE, 12), repos, t -> {})).header().subtitle())
        .isEqualTo("Zone #12 · Level 1–10");
    assertThat(((ZonePage) TestPages.build(new EntityRef(ZONE, 40), repos, t -> {})).header().subtitle())
        .startsWith("Zone #40 · Level 10–20");
    // Hogger's drops: his claw and the quest note, then the generic pool hundreds of NPCs drop, collapsed
    var hogger = (NpcPage) TestPages.build(new EntityRef(NPC, 448), repos, t -> {});
    List<Object> rows = new ArrayList<>();
    for (int r = 0; r < hogger.drops().table().getRowCount(); r++) {
      rows.add(hogger.drops().table().getValueAt(r, 0));
    }
    assertThat(rows).last().asString().startsWith("World drops (").endsWith(")" + " ▸");
    assertThat(rows).contains("Huge Gnoll Claw").hasSizeLessThan(8);
    // every pool drop of Hogger's is below 10 % (Tough Jerky 6.09 %): Quest items (2), then World drops (23)
    assertThat(rows).hasSize(4).first().isEqualTo("Quest items (2)");
    assertThat(rows).last().isEqualTo("World drops (23) ▸");
    // a high-rate shared drop is what the NPC is farmed for: Murloc Tidehunter (NPC 127) drops Murloc Eye at
    // 50.0 % (questie-wowhead, 36 droppers), so it stays in Trade goods and not in the world drops
    var tidehunter = (NpcPage) TestPages.build(new EntityRef(NPC, 127), repos, t -> {});
    List<Object> tidehunterRows = new ArrayList<>();
    for (int r = 0; r < tidehunter.drops().table().getRowCount(); r++) {
      tidehunterRows.add(tidehunter.drops().table().getValueAt(r, 0));
    }
    assertThat(tidehunterRows).containsSequence("Trade goods (3)", "Murloc Eye");
    assertThat(tidehunterRows).last().asString().startsWith("World drops (");
    // a quest item 22 NPCs drop at an unknown rate stays in the table, not in the world drops
    var horror = (NpcPage) TestPages.build(new EntityRef(NPC, 3642), repos, t -> {});
    List<Object> horrorRows = new ArrayList<>();
    for (int r = 0; r < horror.drops().table().getRowCount(); r++) {
      horrorRows.add(horror.drops().table().getValueAt(r, 0));
    }
    assertThat(horrorRows).contains("Deviate Hide");
    // Edwin VanCleef is a boss, whatever Questie's rank
    assertThat(((NpcPage) TestPages.build(new EntityRef(NPC, 639), repos, t -> {})).rankChip().getText())
        .isEqualTo("Boss");
    // the Dire Maul floors (sub-zones named like their zone) are one zone hit
    assertThat(repos.search().search("Dire Maul", Optional.of(ZONE), 50)).extracting(SearchHit::name)
        .filteredOn("Dire Maul"::equals).hasSize(1);
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
    // VMaNGOS (3 %) outranks AtlasLoot (8.05 %, kept as a source); the two disagree
    assertThat(sulfuras.get().dropPct()).isNotNull().isBetween(2.5, 3.5);
    assertThat(sulfuras.get().pctSource()).isEqualTo("vmangos");
    assertThat(sulfuras.get().disagrees()).isTrue();
    assertThat(column("select pct from drop_rate_source where npc_id = 11502 and item_id = 17204"
        + " and source = 'atlasloot'")).singleElement().satisfies(p -> assertThat((Double) p).isBetween(7.5, 8.6));
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
}
