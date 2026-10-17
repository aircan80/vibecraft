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
import dev.wowforever.app.ui.detail.DetailPanels;
import dev.wowforever.app.ui.widgets.MapView;
import dev.wowforever.app.ui.widgets.MapView.Pin;
import dev.wowforever.app.ui.widgets.MapView.PinKind;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.QuestDetail;
import dev.wowforever.app.model.QuestGiver;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.LinkRenderer;
import dev.wowforever.app.ui.theme.QualityColors;
import dev.wowforever.app.ui.widgets.IconCache;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTable;
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
    assertThat(p.header().subtitle()).isEqualTo("Quest #2 · Level 30 · Requires 20 · Ashenvale");
    assertThat(p.objectives().getText()).startsWith("Bring Sharptalon's Claw to Senani Thunderheart");
    assertThat(p.objectives().isEditable()).isFalse();
    assertThat(p.objectives().getLineWrap()).isTrue();
    assertThat(p.sections()).extracting(SourceSection::title).containsExactly("Given by", "Turned in to");
    assertThat(p.section("Given by").table().model().refAt(0, 0)).contains(new EntityRef(ITEM, 16305));
    // each table holds one role: the Role column says nothing
    assertThat(columnNames(p.section("Given by").table().table())).containsExactly("Name", "Type");
    assertThat(columnNames(p.section("Turned in to").table().table())).containsExactly("Name", "Type");
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
    assertThat(wide).isLessThanOrEqualTo(900).isGreaterThan(420);
    p.setSize(420, 700);
    layOut(p);
    assertThat(p.objectives().getWidth()).as("never wider than the page").isLessThanOrEqualTo(420);
    assertThat(p.header().getWidth()).isLessThanOrEqualTo(420);
    assertThat(p.objectives().getPreferredSize().height).as("re-wrapped onto more lines").isGreaterThan(wideHeight);
  }

  private static QuestPage loaded(Repositories repos, long questId, List<ViewTarget> opened) {
    Object loaded = DetailPanels.load(new EntityRef(QUEST, questId), repos).orElseThrow();
    return (QuestPage) DetailPanels.render(loaded, repos, opened::add);
  }

  @Test
  void giverOnMap() {
    Repositories spawns = SpawnFixtureDb.repositories();
    List<ViewTarget> opened = new ArrayList<>();
    // The People's Militia: Thor in Westfall starts it, The Last Guardian elsewhere in Westfall ends it
    QuestPage militia = loaded(spawns, 900101, opened);
    MapView given = militia.givenByMap();
    assertThat(given).isNotNull();
    assertThat(given.image().getWidth()).as("Westfall's map").isEqualTo(1002);
    assertThat(given.pins()).extracting(Pin::name, Pin::kind, Pin::emphasised, Pin::x)
        .containsExactly(tuple("Thor", PinKind.QUEST_START, true, 56.6));
    assertThat(SwingUtilities.isDescendingFrom(given, militia.section("Given by"))).isTrue();
    MapView turnedIn = militia.turnedInMap();
    assertThat(turnedIn).isNotNull().isNotSameAs(given);
    // the object's two points; its second point at 52.4, 53.1 is the same pin
    assertThat(turnedIn.pins()).extracting(Pin::name, Pin::kind, Pin::x).containsExactly(
        tuple("The Last Guardian", PinKind.QUEST_END, 52.4), tuple("The Last Guardian", PinKind.QUEST_END, 52.5));
    assertThat(SwingUtilities.isDescendingFrom(turnedIn, militia.section("Turned in to"))).isTrue();
    // a giver's pin opens the giver
    given.setSize(1002, 668);
    given.dispatchEvent(new MouseEvent(given, MouseEvent.MOUSE_CLICKED, 0L, 0, 567, 351, 1, false,
        MouseEvent.BUTTON1));
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(NPC, 900004)));

    // Patrolling Westfall starts and ends with Thor, at the same spot: one map with his "!"
    QuestPage patrol = loaded(spawns, 900102, opened);
    assertThat(patrol.givenByMap().pins()).extracting(Pin::name, Pin::kind)
        .containsExactly(tuple("Thor", PinKind.QUEST_START));
    assertThat(patrol.turnedInMap()).isNull();

    // givers without a spawn point on a zone map: the tables alone
    QuestPage claw = loaded(repos, 2, opened);
    assertThat(claw.givenByMap()).isNull();
    assertThat(claw.turnedInMap()).isNull();
    assertThat(claw.sections()).extracting(SourceSection::title).containsExactly("Given by", "Turned in to");
    assertThat(loaded(repos, 490, opened).givenByMap()).as("Teldrassil has no map").isNull();
  }

  @Test
  void rewardsAndRelatedItemsAndNoObjectives() {
    EntityRef claw = new EntityRef(ITEM, 16305);
    var d = new QuestDetail(new QuestSummary(9, "Q", null, null, null), null, List.of(),
        List.of(new LootRow(new EntityRef(ITEM, 1), "Sword", 20, null, null, null, 2, "Weapon", 7)),
        List.of(new ItemSource(ItemSource.RELATED, claw, "Sharptalon's Claw")));
    var p = new QuestPage(d, t -> {});
    assertThat(p.header().subtitle()).isEqualTo("Quest #9");
    assertThat(p.objectives().isVisible()).isFalse();
    assertThat(p.sections()).extracting(SourceSection::title).containsExactly("Rewards", "Related items");
    assertThat(columnNames(p.section("Rewards").table().table())).containsExactly("Item", "Item level");
    assertThat(column(p.section("Related items").table().table(), 0)).containsExactly("Sharptalon's Claw");
  }
}
