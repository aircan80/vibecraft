package dev.wowforever.app.ui.detail;

import static dev.wowforever.app.model.EntityKind.BOSS;
import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.OBJECT;
import static dev.wowforever.app.model.EntityKind.QUEST;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.db.SpawnFixtureDb;
import dev.wowforever.app.model.SpawnPin;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.RateSource;
import dev.wowforever.app.model.SourceFilter;
import dev.wowforever.app.ui.pages.InstancePage;
import dev.wowforever.app.ui.pages.ItemPage;
import dev.wowforever.app.ui.pages.NpcPage;
import dev.wowforever.app.ui.pages.ObjectPage;
import dev.wowforever.app.ui.pages.QuestPage;
import dev.wowforever.app.ui.StatusSink;
import dev.wowforever.app.ui.pages.ZoneMapPanel;
import dev.wowforever.app.ui.pages.ZonePage;
import dev.wowforever.app.ui.widgets.MapView.Pin;
import dev.wowforever.app.db.AssetRepository;
import dev.wowforever.app.ui.widgets.IconCache;
import dev.wowforever.app.ui.widgets.Images;
import dev.wowforever.app.ui.widgets.MapCache;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class DetailPanelsTest {
  private final Repositories repos = FixtureDb.repositories();

  private JComponent render(EntityKind kind, long id) {
    Object detail = DetailPanels.load(new EntityRef(kind, id), repos).orElseThrow();
    return TestPages.render(detail, repos, t -> {});
  }

  @Test
  void rendersAPanelForEveryNavigableKind() {
    assertThat(render(ITEM, 14149)).isInstanceOf(ItemPage.class);
    assertThat(render(NPC, 639)).isInstanceOf(NpcPage.class);
    assertThat(render(OBJECT, 386759)).isInstanceOf(ObjectPage.class);
    assertThat(render(QUEST, 2)).isInstanceOf(QuestPage.class);
    assertThat(render(ZONE, 40)).isInstanceOf(ZonePage.class);
    assertThat(render(INSTANCE, 1)).isInstanceOf(InstancePage.class);
  }

  @Test
  void anInstanceLoadsTheQuestsFiledUnderItsZone() {
    var deadmines = (DetailPanels.LoadedInstance) DetailPanels.load(new EntityRef(INSTANCE, 2), repos).orElseThrow();
    assertThat(deadmines.detail().summary().zoneId()).isEqualTo(1581L);
    assertThat(deadmines.quests()).isEqualTo(repos.quests().byZone(1581));
    var noZone = (DetailPanels.LoadedInstance) DetailPanels.load(new EntityRef(INSTANCE, 1), repos).orElseThrow();
    assertThat(noZone.quests()).isEqualTo(noZone.detail().summary().zoneId() == null ? List.of()
        : repos.quests().byZone(noZone.detail().summary().zoneId()));
    assertThat(DetailPanels.name(deadmines)).isEqualTo("The Deadmines");
  }

  @Test
  void namesTheEntity() {
    assertThat(DetailPanels.name(DetailPanels.load(new EntityRef(ITEM, 14149), repos).orElseThrow()))
        .isEqualTo("Subterranean Cape");
    assertThat(DetailPanels.name(DetailPanels.load(new EntityRef(ZONE, 40), repos).orElseThrow()))
        .isEqualTo("Westfall");
  }

  @Test
  void anIconOnlyItemLoadsItsOwnIconNotTheFallback() {
    // Illari's Key has an Item row (icon 134236) but no ItemSparse row, so its card is empty
    var keyPage = (DetailPanels.LoadedItem) DetailPanels.load(new EntityRef(ITEM, 212347), repos).orElseThrow();
    assertThat(keyPage.detail().card().iconId()).isNull();
    assertThat(samePixels(keyPage.icon(), decoded(134236L))).as("Illari's Key shows icon 134236").isTrue();
    // an item without any client data still gets the question-mark fallback
    var saltsPage = (DetailPanels.LoadedItem) DetailPanels.load(new EntityRef(ITEM, 1176), repos).orElseThrow();
    assertThat(samePixels(saltsPage.icon(), decoded(0L))).as("Smelling Salts shows the fallback").isTrue();
  }

  @Test
  void aMissingIconBlobFallsBackToTheQuestionMark() {
    var page = (DetailPanels.LoadedItem) DetailPanels.load(new EntityRef(ITEM, 1307), repos).orElseThrow();
    assertThat(samePixels(page.icon(), decoded(AssetRepository.FALLBACK_ICON_ID))).isTrue();
  }

  @Test
  void anItemLoadsWithItsCardAndIcon() {
    var page = (DetailPanels.LoadedItem) DetailPanels.load(new EntityRef(ITEM, 17204), repos).orElseThrow();
    assertThat(page.icon()).as("decoded in load, off the EDT").isNotNull();
    assertThat(page.icon().getWidth()).as("scaled to the card size in load").isEqualTo(IconCache.CARD);
    assertThat(page.icon().getHeight()).isEqualTo(IconCache.CARD);
    assertThat(page.icon().getType()).isEqualTo(BufferedImage.TYPE_INT_ARGB);
    assertThat(page.detail().card().quality()).isEqualTo(5);
    assertThat(page.detail().card().lines()).hasSize(5);
    assertThat(TestPages.render(page, repos, t -> {})).isInstanceOf(ItemPage.class);
  }

  /** Runs {@code body} with an empty shared icon cache over the fixture database; returns the cache. */
  private IconCache withEmptyCache(Runnable body) {
    IconCache cache = new IconCache(repos.assets()::icon, 100, null);
    IconCache before = IconCache.install(cache);
    try {
      body.run();
    } finally {
      IconCache.install(before);
    }
    return cache;
  }

  @Test
  void anNpcPageWarmsTheIconsOfItsDropsInLoad() {
    IconCache cache = withEmptyCache(() -> DetailPanels.load(new EntityRef(NPC, 639), repos));
    assertThat(cache.cached(135325L, IconCache.ROW)).as("Cruel Barb").isPresent();
    assertThat(cache.cached(133471L, IconCache.ROW)).as("An Unsent Letter").isPresent();
    assertThat(cache.cached(AssetRepository.FALLBACK_ICON_ID, IconCache.ROW)).as("items without client data")
        .isPresent();
  }

  @Test
  void aQuestPageWarmsTheIconsOfItsItemGivers() {
    // quest 2 is started by Sharptalon's Claw, an item without client data, and rewards nothing
    IconCache cache = withEmptyCache(() -> DetailPanels.load(new EntityRef(QUEST, 2), repos));
    assertThat(cache.cached(AssetRepository.FALLBACK_ICON_ID, IconCache.ROW)).isPresent();
  }

  @Test
  void anInstancePageWarmsTheIconsOfTheBossItShowsFirst() {
    IconCache cache = withEmptyCache(() -> DetailPanels.load(new EntityRef(INSTANCE, 1), repos));
    assertThat(cache.cached(135648L, IconCache.ROW)).as("Cursed Felblade").isPresent();
    assertThat(cache.cached(133770L, IconCache.ROW)).as("Subterranean Cape").isPresent();
  }

  @Test
  void aZoneMapIsDecodedInLoad() {
    Object loaded = DetailPanels.load(new EntityRef(ZONE, 40), repos).orElseThrow();
    assertThat(loaded).isInstanceOfSatisfying(DetailPanels.LoadedZone.class, page -> {
      assertThat(page.map()).as("decoded in load, off the EDT").isNotNull();
      assertThat(page.map().getWidth()).isEqualTo(1002);
      // the fixture has no boss or rare spawning in Westfall
      assertThat(page.pins()).isEmpty();
    });
    assertThat(TestPages.render(loaded, repos, t -> {})).isInstanceOf(ZonePage.class);
  }

  @Test
  void aZoneWithoutAMapLoadsWithoutOne() {
    Object loaded = DetailPanels.load(new EntityRef(ZONE, 2437), repos).orElseThrow();
    assertThat(((DetailPanels.LoadedZone) loaded).map()).isNull();
    assertThat(((DetailPanels.LoadedZone) loaded).pins()).isEmpty();
    assertThat(TestPages.render(loaded, repos, t -> {})).isInstanceOf(ZonePage.class);
  }

  private BufferedImage decoded(long iconId) {
    return Images.decode(repos.assets().icon(iconId).orElseThrow()).orElseThrow();
  }

  private static boolean samePixels(BufferedImage a, BufferedImage b) {
    assertThat(a).isNotNull();
    return a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight()
        && Arrays.equals(a.getRGB(0, 0, a.getWidth(), a.getHeight(), null, 0, a.getWidth()),
            b.getRGB(0, 0, b.getWidth(), b.getHeight(), null, 0, b.getWidth()));
  }

  @Test
  void anItemLoadsTheRateSourcesOfItsDropsAndLootLists() {
    var letter = (DetailPanels.LoadedItem) DetailPanels.load(new EntityRef(ITEM, 2874), repos).orElseThrow();
    assertThat(letter.rateSources()).containsOnlyKeys(new RateSource.Key(639, 2874));
    assertThat(letter.rateSources().get(new RateSource.Key(639, 2874))).extracting(RateSource::source)
        .containsExactly("questie-wowhead", "questie-correction", "atlasloot", "atlasloot-override");
    // the loot list's boss is an NPC too: its rate is the same pair
    var bag = (DetailPanels.LoadedItem) DetailPanels.load(new EntityRef(ITEM, 14149), repos).orElseThrow();
    assertThat(bag.rateSources()).containsOnlyKeys(new RateSource.Key(11520, 14149));
    var page = (ItemPage) TestPages.render(letter, repos, t -> {});
    JTable drops = page.droppedBy().table();
    assertThat(tooltipAt(drops, 0, drops.getColumnModel().getColumnIndex("Rate"))).isEqualTo(
        "Questie 56.07 %  ·  Questie correction 60.00 %  ·  AtlasLoot 56.07 %  ·  AtlasLoot override 57.00 %");
  }

  @Test
  void anNpcLoadsTheRateSourcesOfTheDropsThatHaveAny() {
    var vanCleef = (DetailPanels.LoadedNpc) DetailPanels.load(new EntityRef(NPC, 639), repos).orElseThrow();
    assertThat(vanCleef.rateSources().keySet()).extracting(RateSource.Key::itemId)
        .containsExactlyInAnyOrder(2874L, 5191L, 5193L, 5202L, 10399L);
    var lucifron = (DetailPanels.LoadedNpc) DetailPanels.load(new EntityRef(NPC, 12118), repos).orElseThrow();
    assertThat(lucifron.detail().drops()).isNotEmpty();
    assertThat(lucifron.rateSources()).as("drops without rates load none").isEmpty();
  }

  @Test
  void renderAppliesTheSourceFilterToItemAndNpcPages() {
    Object vanCleef = DetailPanels.load(new EntityRef(NPC, 639), repos).orElseThrow();
    var all = (NpcPage) TestPages.render(vanCleef, repos, t -> {});
    assertThat(all.section("Drops").heading().text()).isEqualTo("Drops (5)");
    var filtered = (NpcPage) TestPages.render(vanCleef, repos, t -> {}, Runnable::run, StatusSink.NONE,
        SourceFilter.HIDE_ATLASLOOT_ONLY);
    assertThat(filtered.section("Drops").heading().text()).isEqualTo("Drops (2)");
    assertThat(filtered.section("Drops").note().getText()).isEqualTo("3 rows hidden by the source filter");
    Object atlasOnly = DetailPanels.load(new EntityRef(ITEM, 16800), repos).orElseThrow();
    var item = (ItemPage) TestPages.render(atlasOnly, repos, t -> {}, Runnable::run, StatusSink.NONE,
        SourceFilter.HIDE_ATLASLOOT_ONLY);
    // its AtlasLoot-only NPC row goes; the boss's instance loot list is never filtered and stays
    assertThat(item.section("Dropped by").heading().text()).isEqualTo("Dropped by (1)");
    assertThat(item.section("Dropped by").note().getText()).isEqualTo("1 row hidden by the source filter");
  }

  /** The tooltip a pointer resting in the middle of view cell ({@code row}, {@code col}) shows. */
  private static String tooltipAt(JTable t, int row, int col) {
    java.awt.Rectangle cell = t.getCellRect(row, col, false);
    return t.getToolTipText(new java.awt.event.MouseEvent(t, java.awt.event.MouseEvent.MOUSE_MOVED, 0L, 0,
        cell.x + cell.width / 2, cell.y + cell.height / 2, 0, false));
  }

  @Test
  void missingEntitiesAndBossesLoadEmpty() {
    assertThat(DetailPanels.load(new EntityRef(ITEM, -1), repos)).isEmpty();
    assertThat(DetailPanels.load(new EntityRef(BOSS, 1), repos)).isEmpty();
  }

  @Test
  void buildFallsBackToNotFound() {
    JComponent c = TestPages.build(new EntityRef(NPC, -7), repos, t -> {});
    assertThat(c).isInstanceOfSatisfying(JLabel.class, l -> assertThat(l.getText()).isEqualTo("Not found: NPC #-7"));
  }

  @Test
  void aZoneLoadsItsBossAndRarePinsWithItsMap() {
    Repositories spawns = SpawnFixtureDb.repositories();
    var loaded = (DetailPanels.LoadedZone) DetailPanels.load(new EntityRef(ZONE, 40), spawns).orElseThrow();
    assertThat(loaded.pins()).extracting(SpawnPin::name)
        .containsExactlyInAnyOrder("Foe Reaper 4000", "Vultros", "Test Boss");
    assertThat(TestPages.render(loaded, spawns, t -> {}))
        .isInstanceOfSatisfying(ZonePage.class, p -> assertThat(p.mapPanel().mapView().pins()).hasSize(3));
  }

  @Test
  void anNpcLoadsTheMapOfItsFirstSpawnZone() {
    Object loaded = DetailPanels.load(new EntityRef(NPC, 448), repos).orElseThrow();
    assertThat(loaded).isInstanceOfSatisfying(DetailPanels.LoadedNpc.class, page -> {
      assertThat(page.detail().summary().name()).isEqualTo("Hogger");
      assertThat(page.mapZoneId()).isEqualTo(12L);
      assertThat(page.map()).as("decoded in load, off the EDT").isNotNull();
      assertThat(page.map().getWidth()).isEqualTo(1002);
    });
    assertThat(TestPages.render(loaded, repos, t -> {}))
        .isInstanceOfSatisfying(NpcPage.class, p -> assertThat(p.spawnMap().pins()).hasSize(5));
    assertThat(DetailPanels.name(loaded)).isEqualTo("Hogger");
    // Edwin VanCleef spawns only inside his instance (no coordinates): no map
    var vanCleef = (DetailPanels.LoadedNpc) DetailPanels.load(new EntityRef(NPC, 639), repos).orElseThrow();
    assertThat(vanCleef.mapZoneId()).isNull();
    assertThat(vanCleef.map()).isNull();
    assertThat(((NpcPage) TestPages.render(vanCleef, repos, t -> {})).spawnMap()).isNull();
  }

  @Test
  void anObjectLoadsTheMapOfItsFirstSpawnZone() {
    var loaded = (DetailPanels.LoadedObject) DetailPanels.load(new EntityRef(OBJECT, 386759), repos).orElseThrow();
    assertThat(loaded.mapZoneId()).isEqualTo(12L);
    assertThat(loaded.map()).isNotNull();
    assertThat(((ObjectPage) TestPages.render(loaded, repos, t -> {})).spawnMap().pins()).hasSize(1);
    assertThat(DetailPanels.name(loaded)).isEqualTo("Library Book");
  }

  @Test
  void aMapShownAgainIsNotDecodedAgain() {
    var first = (DetailPanels.LoadedZone) DetailPanels.load(new EntityRef(ZONE, 40), repos).orElseThrow();
    assertThat(MapCache.shared().contains(1436)).isTrue();
    var again = (DetailPanels.LoadedZone) DetailPanels.load(new EntityRef(ZONE, 40), repos).orElseThrow();
    assertThat(again.map()).isSameAs(first.map());
    // Hogger and the Library Book both spawn in Elwynn Forest: one decoded map for both pages
    var hogger = (DetailPanels.LoadedNpc) DetailPanels.load(new EntityRef(NPC, 448), repos).orElseThrow();
    var book = (DetailPanels.LoadedObject) DetailPanels.load(new EntityRef(OBJECT, 386759), repos).orElseThrow();
    assertThat(book.map()).isSameAs(hogger.map());
  }

  @Test
  void renderReportsAZoneMapsLoadsToTheStatusSink() {
    List<String> messages = new ArrayList<>();
    StatusSink status = new StatusSink() {
      @Override
      public void message(String text) {
        messages.add(text);
      }

      @Override
      public void fail(Exception e) {}
    };
    Object loaded = DetailPanels.load(new EntityRef(ZONE, 12), repos).orElseThrow();
    var page = (ZonePage) TestPages.render(loaded, repos, t -> {}, Runnable::run, status);
    page.mapPanel().allNpcsToggle().doClick();
    assertThat(messages).containsExactly(ZoneMapPanel.LOADING, "5 pins on the map");
    assertThat(page.mapPanel().mapView().visiblePins()).extracting(Pin::name).containsOnly("Hogger");
  }
}
