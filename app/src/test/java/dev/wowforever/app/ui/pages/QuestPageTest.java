package dev.wowforever.app.ui.pages;

import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.QUEST;
import static dev.wowforever.app.ui.pages.Tables.column;
import static dev.wowforever.app.ui.pages.Tables.columnNames;
import static dev.wowforever.app.ui.pages.Tables.layOut;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.formdev.flatlaf.FlatLaf;
import dev.wowforever.app.db.AssetRepository;
import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.db.SpawnFixtureDb;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.QuestDetail;
import dev.wowforever.app.model.QuestGiver;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.LinkRenderer;
import dev.wowforever.app.ui.detail.DetailPanels;
import dev.wowforever.app.ui.detail.TestPages;
import dev.wowforever.app.ui.theme.QualityColors;
import dev.wowforever.app.ui.widgets.IconCache;
import dev.wowforever.app.ui.widgets.MapView;
import dev.wowforever.app.ui.widgets.MapView.Pin;
import dev.wowforever.app.ui.widgets.MapView.PinKind;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class QuestPageTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void headerObjectivesAndGiversSplitByRole() {
    List<ViewTarget> opened = new ArrayList<>();
    var p = new QuestPage(repos.quests().detail(2).get(), opened::add);
    assertThat(p.header().title()).isEqualTo("Sharptalon's Claw");
    assertThat(p.header().facts()).isEqualTo("Lv 30 (req. 20)");
    assertThat(p.objectives().getText()).startsWith("Bring Sharptalon's Claw to Senani Thunderheart");
    assertThat(p.objectives().isEditable()).isFalse();
    assertThat(p.objectives().getLineWrap()).isTrue();
    assertThat(p.sections()).extracting(SourceSection::title).containsExactly("Objectives", "Given by", "Turned in to", "Quest chain");
    assertThat(p.section("Given by").table().model().refAt(0, 0)).contains(new EntityRef(ITEM, 16305));
    // each table holds one role: the Role column says nothing
    assertThat(columnNames(p.section("Given by").table().table())).containsExactly("Name", "Zone", "Coordinates");
    assertThat(columnNames(p.section("Turned in to").table().table())).containsExactly("Name", "Zone", "Coordinates");
    p.section("Turned in to").table().activateRow(0);
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(NPC, 12696)));
  }

  @Test
  void itemGiversShowTheirIconAndQualityColourOtherGiversDoNot() {
    IconCache cache = new IconCache(repos.assets()::icon, 100, null);
    cache.warm(Arrays.asList(132541L, null), IconCache.ROW);
    IconCache before = IconCache.install(cache);
    try {
      var d = new QuestDetail(new QuestSummary(9, "Q", null, null, null), null, List.of(
          new QuestGiver(new EntityRef(ITEM, 16800), "Arcanist Boots", "start", 4, 132541L),
          new QuestGiver(new EntityRef(ITEM, 16305), "Sharptalon's Claw", "start"),
          new QuestGiver(new EntityRef(NPC, 12696), "Senani Thunderheart", "start")), List.of(), List.of());
      JTable givers = new QuestPage(d, t -> {}).section("Given by").table().table();
      JLabel boots = rendered(givers, 0);
      assertThat(boots.getText()).isEqualTo("Arcanist Boots");
      assertThat(boots.getForeground()).isEqualTo(QualityColors.text(4, FlatLaf.isLafDark()));
      assertThat(boots.getIcon()).isSameAs(cache.cached(132541L, IconCache.ROW).orElseThrow());
      assertThat(rendered(givers, 1).getIcon()).as("no client data: the question mark")
          .isSameAs(cache.cached(AssetRepository.FALLBACK_ICON_ID, IconCache.ROW).orElseThrow());
      JLabel npc = rendered(givers, 2);
      assertThat(npc.getIcon()).isNull();
      assertThat(npc.getForeground()).isEqualTo(LinkRenderer.linkColor());
    } finally {
      IconCache.install(before);
    }
  }

  @Test
  void relatedItemsShowTheirIconAndQualityColour() {
    IconCache cache = new IconCache(repos.assets()::icon, 100, null);
    cache.warm(Arrays.asList(132541L, null), IconCache.ROW);
    IconCache before = IconCache.install(cache);
    try {
      var d = new QuestDetail(new QuestSummary(9, "Q", null, null, null), null, List.of(), List.of(), List.of(
          new ItemSource(ItemSource.RELATED, new EntityRef(ITEM, 16800), "Arcanist Boots", 4, 132541L),
          new ItemSource(ItemSource.RELATED, new EntityRef(ITEM, 16305), "Sharptalon's Claw")));
      JTable related = new QuestPage(d, t -> {}).section("Related items").table().table();
      JLabel boots = rendered(related, 0);
      assertThat(boots.getForeground()).isEqualTo(QualityColors.text(4, FlatLaf.isLafDark()));
      assertThat(boots.getIcon()).isSameAs(cache.cached(132541L, IconCache.ROW).orElseThrow());
      assertThat(rendered(related, 1).getIcon()).as("no client data: the question mark")
          .isSameAs(cache.cached(AssetRepository.FALLBACK_ICON_ID, IconCache.ROW).orElseThrow());
    } finally {
      IconCache.install(before);
    }
  }

  private static JLabel rendered(JTable table, int row) {
    return (JLabel) table.prepareRenderer(table.getCellRenderer(row, 0), row, 0);
  }

  @Test
  void theWrappedObjectivesNarrowWithThePageAfterBeingWide() {
    String longText = "Bring Sharptalon's Claw to Senani Thunderheart at Splintertree Post in Ashenvale. ".repeat(6);
    var d = new QuestDetail(new QuestSummary(9, "Q", 30, 20, null), longText, List.of(), List.of(), List.of());
    var p = new QuestPage(d, t -> {});
    p.setSize(900, 700);
    layOut(p);
    int wide = p.objectives().getWidth();
    int wideHeight = p.objectives().getHeight();
    assertThat(wide).isLessThanOrEqualTo(900).isGreaterThan(300);
    p.setSize(420, 700);
    layOut(p);
    assertThat(p.objectives().getWidth()).as("never wider than the page").isLessThanOrEqualTo(420);
    assertThat(p.header().getWidth()).isLessThanOrEqualTo(420);
    assertThat(p.objectives().getPreferredSize().height).as("re-wrapped onto more lines").isGreaterThan(wideHeight);
  }

  private static QuestPage loaded(Repositories repos, long questId, List<ViewTarget> opened) {
    Object loaded = DetailPanels.load(new EntityRef(QUEST, questId), repos).orElseThrow();
    return (QuestPage) TestPages.render(loaded, repos, opened::add);
  }

  @Test
  void giverOnMap() {
    Repositories spawns = SpawnFixtureDb.repositories();
    List<ViewTarget> opened = new ArrayList<>();
    // The People's Militia: Thor in Westfall starts it, The Last Guardian elsewhere in Westfall ends it
    QuestPage militia = loaded(spawns, 900101, opened);
    MapView map = militia.mapPanel().mapView();
    assertThat(map).isNotNull();
    assertThat(map.image().getWidth()).as("Westfall's map").isEqualTo(1002);
    assertThat(map.pins()).extracting(Pin::kind, Pin::emphasised, Pin::x).containsExactly(
        tuple(PinKind.QUEST_START, true, 56.6), tuple(PinKind.QUEST_END, true, 52.4),
        tuple(PinKind.QUEST_END, true, 52.5));
    assertThat(SwingUtilities.isDescendingFrom(map, militia.body())).isTrue();
    map.setSize(1002, 668);
    map.dispatchEvent(new MouseEvent(map, MouseEvent.MOUSE_CLICKED, 0L, 0, 567, 351, 1, false,
        MouseEvent.BUTTON1));
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(NPC, 900004)));

    QuestPage patrol = loaded(spawns, 900102, opened);
    MapView shared = patrol.mapPanel().mapView();
    shared.setSize(1002, 668);
    assertThat(shared.pins()).extracting(Pin::kind).containsExactly(PinKind.QUEST_START, PinKind.QUEST_END);
    assertThat(shared.clusters()).hasSize(1);
    var menu = shared.menuFor(shared.clusters().getFirst());
    assertThat(menu.getComponentCount()).isEqualTo(2);
    assertThat(((javax.swing.JMenuItem) menu.getComponent(0)).getText()).contains("Given by");
    assertThat(((javax.swing.JMenuItem) menu.getComponent(1)).getText()).contains("Turned in to");
    assertThat(loaded(repos, 490, opened).mapPanel().mapView()).as("Teldrassil has no map").isNull();
  }

  @Test
  void rewardsAndRelatedItemsAndNoObjectives() {
    EntityRef claw = new EntityRef(ITEM, 16305);
    var d = new QuestDetail(new QuestSummary(9, "Q", null, null, null), null, List.of(),
        List.of(new LootRow(new EntityRef(ITEM, 1), "Sword", 20, null, null, null, 2, "Weapon", 7)),
        List.of(new ItemSource(ItemSource.RELATED, claw, "Sharptalon's Claw")));
    var p = new QuestPage(d, t -> {});
    assertThat(p.header().facts()).isEmpty();
    assertThat(p.objectives().isVisible()).isFalse();
    assertThat(p.sections()).extracting(SourceSection::title).containsExactly("Objectives", "Quest chain", "Rewards", "Related items");
    assertThat(columnNames(p.section("Rewards").table().table())).containsExactly("Item", "Item level");
    assertThat(column(p.section("Related items").table().table(), 0)).containsExactly("Sharptalon's Claw");
  }
  private QuestPage shipped(long id) {
    var loaded = dev.wowforever.app.ui.pages.quest.QuestTestData.load(id);
    return new QuestPage(loaded.data(), loaded.maps(), target -> {}, text -> {});
  }

  @Test
  void hoggerHeader() {
    var p = shipped(176);
    assertThat(p.header().title()).isEqualTo("Wanted:  \"Hogger\"");
    assertThat(p.header().facts()).isEqualTo("Lv 11 (req. 5)");
    assertThat(p.stepper().isVisible()).isFalse();
    assertThat(p.chainPanel()).isNotNull();
    assertThat(p.header().chips()).anySatisfy(c -> {
      assertThat(c).isInstanceOf(javax.swing.JButton.class);
      assertThat(((javax.swing.JButton)c).getText()).isEqualTo("Elwynn Forest");
    });
    assertThat(p.header().chips()).noneMatch(c -> c instanceof dev.wowforever.app.ui.widgets.Chip chip
        && chip.style() == dev.wowforever.app.ui.widgets.Chip.Style.REPEATABLE);
  }

  @Test
  void repeatableChip() {
    assertThat(shipped(7796).header().chips()).anySatisfy(c -> {
      assertThat(c).isInstanceOf(dev.wowforever.app.ui.widgets.Chip.class);
      var chip = (dev.wowforever.app.ui.widgets.Chip)c;
      assertThat(chip.style()).isEqualTo(dev.wowforever.app.ui.widgets.Chip.Style.REPEATABLE);
      assertThat(chip.getText()).isEqualTo("Repeatable");
    });
  }

  @Test
  void sortLabelChipAndItemStartLine() {
    var page = shipped(7787);
    assertThat(page.header().chips()).anySatisfy(c -> {
      assertThat(c).isInstanceOf(javax.swing.JLabel.class);
      assertThat(((javax.swing.JLabel)c).getText()).isEqualTo("Legendary");
    });
    assertThat(texts(page.section("Given by"))).contains(
        "Starts from item: Dormant Wind Kissed Blade — drops from ›");
  }

  private static List<String> texts(java.awt.Container root) {
    List<String> texts = new ArrayList<>();
    for (var component : root.getComponents()) {
      if (component instanceof javax.swing.AbstractButton button) texts.add(button.getText());
      if (component instanceof java.awt.Container container) texts.addAll(texts(container));
    }
    return texts;
  }

  @Test
  void giverTablesHaveZoneAndCoordinates() {
    var page = shipped(176);
    var given = page.section("Given by").table().table();
    assertThat(column(given, 0)).contains("Wanted Poster");
    assertThat(column(given, 1)).contains("Elwynn Forest");
    assertThat(column(given, 2)).contains("24.6, 74.7");
    var turned = page.section("Turned in to").table().table();
    assertThat(column(turned, 0)).containsExactly("Marshal Dughan");
    assertThat(column(turned, 1)).containsExactly("Elwynn Forest");
    assertThat(column(turned, 2)).containsExactly("42.1, 65.9");
  }

  @Test
  void compactLayout() {
    var page = shipped(176);
    var host = new javax.swing.JPanel(new java.awt.BorderLayout());
    host.putClientProperty(dev.wowforever.app.ui.detail.CompactColumns.KEY, true);
    host.add(page);
    host.setSize(420, 700);
    for (int i=0;i<4;i++) layOut(host);
    assertThat(page.isSideBySide()).isFalse();
    assertThat(page.mapPanel().zoneCombo().isVisible()).isTrue();
    assertThat(page.mapPanel().mapView().getWidth()).isLessThanOrEqualTo(400);
    assertThat(page.mapPanel().mapView().getHeight()).isEqualTo(
        page.mapPanel().mapView().heightFor(page.mapPanel().mapView().getWidth()));
    assertThat(Tables.boundsIn(page.mapPanel(), page).y).isLessThan(Tables.boundsIn(page.objectivesPanel(), page).y);
    var table = page.section("Turned in to").table().table();
    assertThat(column(table, 0)).containsExactly("Marshal Dughan · Elwynn Forest · 42.1, 65.9");
    assertThat(page.section("Turned in to").isCollapsed()).isFalse();
  }
}
