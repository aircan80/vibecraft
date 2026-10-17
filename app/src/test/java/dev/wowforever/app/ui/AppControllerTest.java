package dev.wowforever.app.ui;

import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.OBJECT;
import static dev.wowforever.app.model.EntityKind.QUEST;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.Database;
import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ItemFilter;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.model.ListFilter;
import dev.wowforever.app.model.NpcFilter;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.pages.FilterBar;
import dev.wowforever.app.ui.pages.ItemPage;
import dev.wowforever.app.ui.pages.ListPage;
import dev.wowforever.app.ui.pages.NpcPage;
import dev.wowforever.app.ui.pages.ZonePage;
import dev.wowforever.app.ui.widgets.ListEntry;
import dev.wowforever.app.ui.widgets.MasterList;
import dev.wowforever.app.ui.widgets.RailTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FixtureDb.class)
class AppControllerTest {
  /** A search whose hits are at least three items, to arrow through. */
  private static final ViewTarget.Search ITEMS_C = new ViewTarget.Search("c", Optional.of(ITEM));

  private final Repositories repos = FixtureDb.repositories();
  private final StubView view = new StubView();

  private MasterList list() {
    return view.masterList();
  }

  private List<EntityRef> refs() {
    return list().entries().stream().map(ListEntry::ref).toList();
  }

  /** Arrows to row {@code i} and lets the preview delay elapse. */
  private void previewRow(int i) {
    list().selectIndex(i);
    view.delay.fire();
  }

  private ListPage listPage() {
    assertThat(view.detail).isInstanceOf(ListPage.class);
    return (ListPage) view.detail;
  }

  private static <V> void select(JComboBox<FilterBar.Choice<V>> combo, V value) {
    for (int i = 0; i < combo.getItemCount(); i++) {
      if (Objects.equals(combo.getItemAt(i).value(), value)) {
        combo.setSelectedIndex(i);
        return;
      }
    }
    throw new AssertionError("No choice " + value);
  }

  private static <V> V selected(JComboBox<FilterBar.Choice<V>> combo) {
    return combo.getItemAt(combo.getSelectedIndex()).value();
  }

  @Test
  void searchFillsTheListAndPushesHistory() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.Search("subterr", Optional.empty()));
    assertThat(c.current()).isEqualTo(new ViewTarget.Search("subterr", Optional.empty()));
    assertThat(list().entries()).hasSize(1);
  }

  @Test
  void backRestoresPreviousTarget() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.List(ZONE));
    c.open(new ViewTarget.List(INSTANCE));
    c.back();
    assertThat(c.current()).isEqualTo(new ViewTarget.List(ZONE));
    assertThat(listPage().target()).isEqualTo(new ViewTarget.List(ZONE));
  }

  @Test
  void backAndForwardReShowWithoutPushingAndUpdateButtons() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.List(ZONE));
    c.open(new ViewTarget.List(INSTANCE));
    assertThat(view.canBack).isTrue();
    assertThat(view.canForward).isFalse();
    c.back();
    assertThat(listPage().header().title()).isEqualTo("Zones");
    assertThat(view.canBack).isFalse();
    assertThat(view.canForward).isTrue();
    c.forward();
    assertThat(c.current()).isEqualTo(new ViewTarget.List(INSTANCE));
    assertThat(listPage().header().title()).isEqualTo("Dungeons & Raids");
    assertThat(view.titleSuffix).isEqualTo("Dungeons & Raids");
    assertThat(c.history().canForward()).isFalse();
  }

  @Test
  void startOpensHome() {
    AppController c = new AppController(repos, view, Runnable::run, Map.of("build_id", "b-42"));
    c.start();
    assertThat(c.current()).isEqualTo(new ViewTarget.Home());
    assertThat(view.detail).isNotNull();
    assertThat(list().entries()).isEmpty();
    assertThat(view.status().info()).contains("b-42");
    assertThat(view.titleSuffix).isEmpty();
  }

  @Test
  void searchShowsHitsAsTwoLineEntries() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.Search("hogger", Optional.of(NPC)));
    assertThat(list().heading()).isEqualTo("Search: hogger (1)");
    ListEntry hit = list().entries().get(0);
    assertThat(hit.ref()).isEqualTo(new EntityRef(NPC, 448));
    assertThat(hit.title()).isEqualTo("Hogger");
    assertThat(hit.subtitle()).startsWith("NPC");
    assertThat(view.status().message()).isEqualTo("1 result");
    assertThat(view.titleSuffix).isEqualTo("Search: hogger");
  }

  @Test
  void collapsedSearchHitsShowTheirCount(@TempDir Path tmp) throws Exception {
    Path copy = tmp.resolve("db.sqlite");
    Files.copy(FixtureDb.path(), copy);
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement s = conn.createStatement()) {
      for (int id = 94001; id <= 94004; id++) {
        s.executeUpdate("insert into search_index(kind, id, name) values ('object', " + id + ", 'Westfall')");
        s.executeUpdate("insert into game_object(id, name, zone_id) values (" + id + ", 'Westfall', 40)");
      }
    }
    try (Database db = Database.open(copy)) {
      AppController c = new AppController(Repositories.of(db), view, Runnable::run);
      c.open(new ViewTarget.Search("westfall", Optional.empty()));
      assertThat(list().entries().get(0).ref()).isEqualTo(new EntityRef(ZONE, 40));
      assertThat(list().entries()).filteredOn(e -> e.ref().kind() == OBJECT).singleElement()
          .satisfies(e -> assertThat(e.subtitle()).isEqualTo("Object · Westfall · ×4"));
      assertThat(list().entries().get(0).subtitle()).doesNotContain("×");
    }
  }

  @Test
  void instanceListNamesTheWingsOfInstances(@TempDir Path tmp) throws Exception {
    Path copy = tmp.resolve("db.sqlite");
    Files.copy(FixtureDb.path(), copy);
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement s = conn.createStatement()) {
      s.executeUpdate("insert into instance(id, key, name, content_type, zone_id, level_min) values "
          + "(95001, 'LowerBlackrockSpire', 'Blackrock Spire', 'DUNGEON', 46, 55), "
          + "(95002, 'UpperBlackrockSpire', 'Blackrock Spire', 'DUNGEON', 46, 55)");
    }
    try (Database db = Database.open(copy)) {
      AppController c = new AppController(Repositories.of(db), view, Runnable::run);
      c.open(new ViewTarget.List(INSTANCE));
      List<Object> names = new ArrayList<>();
      for (var section : listPage().sections()) {
        var table = section.table().table();
        for (int r = 0; r < table.getRowCount(); r++) {
          names.add(table.getValueAt(r, 0));
        }
      }
      assertThat(names).contains("Blackrock Spire › Lower", "Blackrock Spire › Upper", "Molten Core");
      assertThat(listPage().sections()).extracting(s -> s.heading().getText()).contains("Levels 41–59 (2)");
    }
  }

  @Test
  void railTargets() {
    assertThat(AppController.target(RailTarget.HOME)).isEqualTo(new ViewTarget.Home());
    assertThat(AppController.target(RailTarget.ITEMS)).isEqualTo(new ViewTarget.List(ITEM, ItemFilter.ALL));
    assertThat(AppController.target(RailTarget.NPCS)).isEqualTo(new ViewTarget.List(NPC, NpcFilter.ALL));
    assertThat(AppController.target(RailTarget.QUESTS)).isEqualTo(new ViewTarget.List(QUEST));
    assertThat(AppController.target(RailTarget.ZONES)).isEqualTo(new ViewTarget.List(ZONE));
    assertThat(AppController.target(RailTarget.INSTANCES)).isEqualTo(new ViewTarget.List(INSTANCE));
  }

  @Test
  void eachRailTargetOpensItsListPageWithTheDefaultFilterAndLeavesTheMasterList() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.Search("hogger", Optional.of(NPC)));
    for (RailTarget t : List.of(RailTarget.ITEMS, RailTarget.NPCS, RailTarget.QUESTS, RailTarget.ZONES,
        RailTarget.INSTANCES)) {
      c.open(AppController.target(t));
      ViewTarget.List target = (ViewTarget.List) AppController.target(t);
      assertThat(c.current()).isEqualTo(target);
      assertThat(listPage().target()).as(t.label()).isEqualTo(target);
      assertThat(target.filter()).isEqualTo(ListFilter.defaultFor(target.kind()));
      assertThat(listPage().header().title()).isEqualTo(t.label());
      assertThat(view.titleSuffix).isEqualTo(t.label());
      assertThat(list().heading()).as("the master list keeps the search").isEqualTo("Search: hogger (1)");
    }
  }

  @Test
  void theRailFollowsTheTargetOnScreen() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.start();
    assertThat(view.rail).isEqualTo(RailTarget.HOME);
    c.open(AppController.target(RailTarget.NPCS));
    assertThat(view.rail).isEqualTo(RailTarget.NPCS);
    select(listPage().filterBar().ranks(), 3);
    assertThat(view.rail).isEqualTo(RailTarget.NPCS);
    listPage().results().activateRow(0);
    assertThat(view.rail).as("an entity is no rail section").isNull();
    c.back();
    assertThat(view.rail).isEqualTo(RailTarget.NPCS);
    c.open(new ViewTarget.List(INSTANCE));
    assertThat(view.rail).isEqualTo(RailTarget.INSTANCES);
    c.open(new ViewTarget.Search("hogger", Optional.empty()));
    assertThat(view.rail).isNull();
    c.back();
    assertThat(view.rail).isEqualTo(RailTarget.INSTANCES);
    c.back();
    assertThat(c.current()).isEqualTo(new ViewTarget.List(NPC, new NpcFilter(null, 3, null, null)));
    assertThat(view.rail).isEqualTo(RailTarget.NPCS);
    c.back();
    assertThat(view.rail).isEqualTo(RailTarget.HOME);
  }

  @Test
  void railSectionsOfTargets() {
    assertThat(AppController.railSection(new ViewTarget.Home())).isEqualTo(RailTarget.HOME);
    assertThat(AppController.railSection(new ViewTarget.List(ITEM))).isEqualTo(RailTarget.ITEMS);
    assertThat(AppController.railSection(new ViewTarget.List(NPC))).isEqualTo(RailTarget.NPCS);
    assertThat(AppController.railSection(new ViewTarget.List(QUEST))).isEqualTo(RailTarget.QUESTS);
    assertThat(AppController.railSection(new ViewTarget.List(ZONE))).isEqualTo(RailTarget.ZONES);
    assertThat(AppController.railSection(new ViewTarget.List(INSTANCE))).isEqualTo(RailTarget.INSTANCES);
    assertThat(AppController.railSection(new ViewTarget.Entity(new EntityRef(ZONE, 12)))).isNull();
    assertThat(AppController.railSection(new ViewTarget.Search("a", Optional.empty()))).isNull();
    for (RailTarget t : RailTarget.values()) {
      assertThat(AppController.railSection(AppController.target(t))).isEqualTo(t);
    }
  }

  @Test
  void itemListShowsEveryItemInTheDetail() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(AppController.target(RailTarget.ITEMS));
    int all = repos.items().list(ItemFilter.ALL).size();
    assertThat(listPage().results().rowCount()).isEqualTo(all);
    assertThat(view.status().message()).isEqualTo(all + " results");
  }

  @Test
  void itemClassLinkOpensTheListWithTheClassPreselected() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(ViewTarget.List.itemClass(4, 1));
    assertThat(c.current()).isEqualTo(new ViewTarget.List(ITEM, new ItemFilter(4, 1, null, null, null)));
    assertThat(selected(listPage().filterBar().classes())).isEqualTo(4);
    assertThat(selected(listPage().filterBar().subclasses())).isEqualTo(1);
    var model = listPage().results().model();
    assertThat(model.getRowCount()).isPositive();
    for (int r = 0; r < model.getRowCount(); r++) {
      assertThat(((ItemSummary) model.rowAt(r)).subclassName()).isEqualTo("Cloth");
    }
  }

  @Test
  void aFilterChangeReloadsAndReplacesTheHistoryEntry() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.Home());
    c.open(new ViewTarget.List(NPC));
    ListPage page = listPage();
    assertThat(page.results().rowCount()).isEqualTo(15);
    select(page.filterBar().ranks(), 3);
    select(page.filterBar().zones(), 2717L);
    assertThat(view.detail).as("the page stays, its table reloads").isSameAs(page);
    assertThat(page.results().rowCount()).isEqualTo(1);
    assertThat(view.status().message()).isEqualTo("1 result");
    assertThat(c.current()).isEqualTo(new ViewTarget.List(NPC, new NpcFilter(2717L, 3, null, null)));
    c.back();
    assertThat(c.current()).as("filter changes are not back steps").isEqualTo(new ViewTarget.Home());
  }

  @Test
  void backAfterOpeningAnEntityFromAListRestoresTheListWithTheSameFilter() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(AppController.target(RailTarget.NPCS));
    select(listPage().filterBar().ranks(), 3);
    listPage().results().activateRow(0); // a click on the first name
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(new EntityRef(NPC, 12118)));
    assertThat(view.detail).isInstanceOf(NpcPage.class);
    c.back();
    ViewTarget.List filtered = new ViewTarget.List(NPC, new NpcFilter(null, 3, null, null));
    assertThat(c.current()).isEqualTo(filtered);
    assertThat(listPage().target()).isEqualTo(filtered);
    assertThat(selected(listPage().filterBar().ranks())).isEqualTo(3);
    assertThat(listPage().results().rowCount()).isEqualTo(2);
    c.forward();
    assertThat(view.detail).isInstanceOf(NpcPage.class);
  }

  @Test
  void aFilterChangeAfterASearchPushesTheFilteredListAfterIt() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.List(NPC));
    ListPage page = listPage();
    c.open(new ViewTarget.Search("hogger", Optional.of(NPC))); // the list page stays in the detail
    assertThat(view.detail).isSameAs(page);
    select(page.filterBar().ranks(), 3);
    ViewTarget.List bosses = new ViewTarget.List(NPC, new NpcFilter(null, 3, null, null));
    assertThat(c.current()).isEqualTo(bosses);
    assertThat(page.results().rowCount()).isEqualTo(2);
    select(page.filterBar().ranks(), 1);
    assertThat(c.current()).isEqualTo(new ViewTarget.List(NPC, new NpcFilter(null, 1, null, null)));
    c.back();
    assertThat(c.current()).isEqualTo(new ViewTarget.Search("hogger", Optional.of(NPC)));
    c.back();
    assertThat(c.current()).isEqualTo(new ViewTarget.List(NPC));
  }

  @Test
  void aStaleFilterReloadIsDropped() {
    List<Runnable> queued = new ArrayList<>();
    AppController c = new AppController(repos, view, queued::add);
    c.open(new ViewTarget.List(NPC));
    queued.remove(0).run();
    ListPage page = listPage();
    select(page.filterBar().ranks(), 1);
    select(page.filterBar().ranks(), 3);
    queued.get(1).run();
    queued.get(0).run(); // the elite reload finishes last and must not replace the bosses
    assertThat(page.results().rowCount()).isEqualTo(2);
    assertThat(page.target().filter()).isEqualTo(new NpcFilter(null, 3, null, null));
  }

  @Test
  void aFilterChangeOnAPageNoLongerShownIsIgnored() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.List(NPC));
    ListPage old = listPage();
    c.open(new ViewTarget.Entity(new EntityRef(NPC, 448)));
    select(old.filterBar().ranks(), 3);
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(new EntityRef(NPC, 448)));
    assertThat(view.detail).isInstanceOf(NpcPage.class);
    c.back();
    assertThat(c.current()).isEqualTo(new ViewTarget.List(NPC));
  }

  @Test
  void fullSearchPageIsMarkedFirstN() {
    assertThat(AppController.cappedTitle("Search: a", 200, AppController.SEARCH_LIMIT))
        .isEqualTo("Search: a (first 200)");
    assertThat(AppController.cappedTitle("Search: a", 199, AppController.SEARCH_LIMIT)).isEqualTo("Search: a");
    assertThat(AppController.cappedTitle("Armor", 9_575, 0)).isEqualTo("Armor");
  }

  @Test
  void previewReplacesCurrentWithoutPushing() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.Home());
    c.open(ITEMS_C);
    List<EntityRef> rows = refs();
    assertThat(rows).hasSizeGreaterThanOrEqualTo(3);
    previewRow(0);
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(rows.get(0)));
    previewRow(1);
    previewRow(2);
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(rows.get(2)));
    assertThat(view.detail).isInstanceOf(ItemPage.class);
    assertThat(list().heading()).startsWith("Search: c (");
    // three previews took one history slot, right after the list
    c.back();
    assertThat(c.current()).isEqualTo(ITEMS_C);
    c.back();
    assertThat(c.current()).isEqualTo(new ViewTarget.Home());
    assertThat(c.history().canBack()).isFalse();
  }

  @Test
  void commitPushesAndKeepsTheCommittedPage() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(ITEMS_C);
    List<EntityRef> rows = refs();
    previewRow(0);
    list().commitSelected(); // Enter on the previewed row pins it
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(rows.get(0)));
    previewRow(1);
    previewRow(2);
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(rows.get(2)));
    c.back();
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(rows.get(0)));
    c.back();
    assertThat(c.current()).isEqualTo(ITEMS_C);
  }

  @Test
  void committingThePreviewedRowDoesNotLoadItAgain() {
    AtomicInteger loads = new AtomicInteger();
    AppController c = new AppController(repos, view, r -> {
      loads.incrementAndGet();
      r.run();
    });
    c.open(ITEMS_C);
    List<EntityRef> rows = refs();
    previewRow(0);
    int afterPreview = loads.get();
    var page = view.detail;
    list().commitSelected();
    assertThat(loads.get()).isEqualTo(afterPreview);
    assertThat(view.detail).isSameAs(page);
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(rows.get(0)));
    previewRow(1); // the commit pinned row 0: this preview takes a new slot
    c.back();
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(rows.get(0)));
  }

  @Test
  void clickingTheSameRowTwiceKeepsOneHistoryEntryAndOneLoad() {
    AtomicInteger loads = new AtomicInteger();
    AppController c = new AppController(repos, view, r -> {
      loads.incrementAndGet();
      r.run();
    });
    c.open(ITEMS_C);
    List<EntityRef> rows = refs();
    list().selectIndex(0);
    list().commitSelected();
    int afterFirst = loads.get();
    list().commitSelected(); // a second click on the committed row
    assertThat(loads.get()).isEqualTo(afterFirst);
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(rows.get(0)));
    c.back();
    assertThat(c.current()).isEqualTo(ITEMS_C);
  }

  @Test
  void previewingThePageOpenedByALinkKeepsItsSlot() {
    AtomicInteger loads = new AtomicInteger();
    AppController c = new AppController(repos, view, r -> {
      loads.incrementAndGet();
      r.run();
    });
    c.open(ITEMS_C);
    List<EntityRef> rows = refs();
    c.open(new ViewTarget.Entity(rows.get(0))); // opened on purpose, e.g. through a link
    int afterOpen = loads.get();
    previewRow(0); // the same entity: no new slot, no reload
    assertThat(loads.get()).isEqualTo(afterOpen);
    previewRow(1); // must push, not overwrite the page opened on purpose
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(rows.get(1)));
    c.back();
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(rows.get(0)));
    c.back();
    assertThat(c.current()).isEqualTo(ITEMS_C);
  }

  @Test
  void commitBeforeThePreviewFiresOpensAtOnce() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(ITEMS_C);
    List<EntityRef> rows = refs();
    list().selectIndex(1);
    list().commitSelected();
    assertThat(view.delay.pending()).isZero();
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(rows.get(1)));
    assertThat(view.detail).isInstanceOf(ItemPage.class);
    c.back();
    assertThat(c.current()).isEqualTo(ITEMS_C);
  }

  @Test
  void previewAfterBackDoesNotOverwriteTheEntryGoneBackTo() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(ITEMS_C);
    List<EntityRef> rows = refs();
    previewRow(0);
    c.open(new ViewTarget.Entity(new EntityRef(NPC, 448))); // a link inside the page
    c.back();
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(rows.get(0)));
    previewRow(1);
    c.back();
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(rows.get(0)));
  }

  @Test
  void committingAZoneRowOpensTheZone() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.Search("elwynn", Optional.of(ZONE)));
    list().selectIndex(refs().indexOf(new EntityRef(ZONE, 12)));
    list().commitSelected();
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(new EntityRef(ZONE, 12)));
    assertThat(view.detail).isInstanceOf(ZonePage.class);
    assertThat(list().heading()).startsWith("Search: elwynn (");
  }

  @Test
  void entityTargetRendersDetailPanel() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.Entity(new EntityRef(ITEM, 14149)));
    assertThat(view.detail).isInstanceOf(ItemPage.class);
    assertThat(view.titleSuffix).isEqualTo("Subterranean Cape");
  }

  @Test
  void clickThroughFromItemToNpcAndBack() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.Entity(new EntityRef(ITEM, 14149)));
    // the first row is the highest known rate; the fixture item has one source, Taragaman
    ((ItemPage) view.detail).droppedBy().activateRow(0);
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(new EntityRef(NPC, 11520)));
    assertThat(view.detail).isInstanceOf(NpcPage.class);
    assertThat(view.titleSuffix).isEqualTo("Taragaman the Hungerer");
    c.back();
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(new EntityRef(ITEM, 14149)));
    assertThat(view.detail).isInstanceOf(ItemPage.class);
  }

  @Test
  void missingEntityShowsNotFound() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.Entity(new EntityRef(QUEST, -5)));
    assertThat(view.detail).isInstanceOfSatisfying(JLabel.class,
        l -> assertThat(l.getText()).isEqualTo("Not found: Quest #-5"));
  }

  @Test
  void linkedZoneShowsDetailAndKeepsTheList() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.Search("hogger", Optional.of(NPC)));
    previewRow(0);
    c.open(new ViewTarget.Entity(new EntityRef(ZONE, 40)));
    assertThat(view.detail).isInstanceOf(ZonePage.class);
    assertThat(list().heading()).isEqualTo("Search: hogger (1)");
    assertThat(list().entries()).hasSize(1);
    assertThat(view.titleSuffix).isEqualTo("Westfall");
  }

  @Test
  void staleDetailIsDropped() {
    List<Runnable> queued = new ArrayList<>();
    AppController c = new AppController(repos, view, queued::add);
    c.open(new ViewTarget.Entity(new EntityRef(ITEM, 14149)));
    c.open(new ViewTarget.Entity(new EntityRef(NPC, 639)));
    queued.get(1).run();
    queued.get(0).run(); // the item finishes last and must not replace the NPC
    assertThat(view.detail).isInstanceOf(NpcPage.class);
  }

  @Test
  void aStaleListPageIsDropped() {
    List<Runnable> queued = new ArrayList<>();
    AppController c = new AppController(repos, view, queued::add);
    c.open(new ViewTarget.List(ZONE));
    c.open(new ViewTarget.Entity(new EntityRef(NPC, 639)));
    queued.get(1).run();
    queued.get(0).run(); // the zone list finishes last and must not replace the NPC
    assertThat(view.detail).isInstanceOf(NpcPage.class);
  }

  @Test
  void stalePreviewIsDropped() {
    List<Runnable> queued = new ArrayList<>();
    AppController c = new AppController(repos, view, queued::add);
    c.open(new ViewTarget.Entity(new EntityRef(NPC, 639)));
    queued.remove(0).run();
    c.preview(new EntityRef(ITEM, 14149));
    c.preview(new EntityRef(NPC, 448));
    queued.get(1).run();
    queued.get(0).run(); // the first preview finishes last and must not replace the second
    assertThat(view.detail).isInstanceOf(NpcPage.class);
    assertThat(view.titleSuffix).isEqualTo("Hogger");
  }

  @Test
  void staleResultsAreDropped() {
    List<Runnable> queued = new ArrayList<>();
    AppController c = new AppController(repos, view, queued::add);
    c.open(new ViewTarget.Search("subterr", Optional.empty()));
    c.open(new ViewTarget.Search("hogger", Optional.empty()));
    queued.get(1).run();
    queued.get(0).run(); // the older search finishes last and must not replace the newer one
    assertThat(list().heading()).startsWith("Search: hogger (");
  }

  @Test
  void failuresGoToTheStatusBar() throws Exception {
    Database closed = Database.open(FixtureDb.path());
    closed.close();
    AppController c = new AppController(Repositories.of(closed), view, Runnable::run);
    c.open(new ViewTarget.List(ZONE));
    assertThat(view.status().message()).startsWith("Error: ");
  }
}
