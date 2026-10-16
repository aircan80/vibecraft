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
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.pages.ItemPage;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FixtureDb.class)
class AppControllerTest {
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
  }

  @Test
  void backAndForwardReShowWithoutPushingAndUpdateButtons() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.List(ZONE));
    c.open(new ViewTarget.List(INSTANCE));
    assertThat(view.canBack).isTrue();
    assertThat(view.canForward).isFalse();
    c.back();
    assertThat(list().heading()).startsWith("Zones (");
    assertThat(view.canBack).isFalse();
    assertThat(view.canForward).isTrue();
    c.forward();
    assertThat(c.current()).isEqualTo(new ViewTarget.List(INSTANCE));
    int instances = repos.instances().all().size();
    assertThat(list().entries()).hasSize(instances);
    assertThat(list().heading()).isEqualTo("Dungeons & Raids (" + instances + ")");
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
  void instanceListNamesTheWingsOfSameNamedInstances(@TempDir Path tmp) throws Exception {
    Path copy = tmp.resolve("db.sqlite");
    Files.copy(FixtureDb.path(), copy);
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement s = conn.createStatement()) {
      s.executeUpdate("insert into instance(id, key, name, content_type, zone_id) values "
          + "(95001, 'LowerBlackrockSpire', 'Blackrock Spire', 'DUNGEON', 46), "
          + "(95002, 'UpperBlackrockSpire', 'Blackrock Spire', 'DUNGEON', 46)");
    }
    try (Database db = Database.open(copy)) {
      AppController c = new AppController(Repositories.of(db), view, Runnable::run);
      c.open(new ViewTarget.List(INSTANCE));
      assertThat(list().entries()).extracting(ListEntry::title)
          .contains("Blackrock Spire › Lower", "Blackrock Spire › Upper", "Molten Core");
    }
  }

  @Test
  void itemClassListsEveryItemOfTheClass() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.ItemClass(4, -1));
    int all = repos.items().list(4, null, null, 0).size();
    assertThat(list().entries()).hasSize(all).isNotEmpty()
        .allSatisfy(e -> {
          assertThat(e.ref().kind()).isEqualTo(ITEM);
          assertThat(e.subtitle()).isNotBlank().doesNotContain("null", "iLvl 1 ", "Req 0");
        });
    assertThat(list().heading()).isEqualTo("Armor (" + all + ")");
    assertThat(view.titleSuffix).isEqualTo("Armor");
  }

  @Test
  void subclassListFiltersAndNamesBoth() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.ItemClass(4, 1));
    assertThat(list().entries()).isNotEmpty().allSatisfy(e -> assertThat(e.subtitle()).startsWith("Cloth"));
    assertThat(list().heading()).startsWith("Armor › Cloth (");
  }

  @Test
  void allItemsWhenClassIsMinusOne() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.ItemClass(ViewTarget.ItemClass.ALL_CLASSES, -1));
    int all = repos.items().list(null, null, null, 0).size();
    assertThat(list().entries()).hasSize(all);
    assertThat(all).isGreaterThan(repos.items().list(4, null, null, 0).size());
    assertThat(list().heading()).isEqualTo("All items (" + all + ")");
  }

  @Test
  void fullSearchPageIsMarkedFirstN() {
    assertThat(AppController.cappedTitle("Search: a", 200, AppController.SEARCH_LIMIT))
        .isEqualTo("Search: a (first 200)");
    assertThat(AppController.cappedTitle("Search: a", 199, AppController.SEARCH_LIMIT)).isEqualTo("Search: a");
    assertThat(AppController.cappedTitle("Armor", 9_575, 0)).isEqualTo("Armor");
  }

  @Test
  void zoneListShowsNpcsAndPushesZoneEntity() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.openZoneList(NPC, 2437);
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(new EntityRef(ZONE, 2437)));
    String zone = repos.zones().detail(2437).get().summary().name();
    assertThat(list().heading()).startsWith("NPCs in " + zone + " (");
    assertThat(refs()).contains(new EntityRef(NPC, 11520));
    assertThat(view.detail).isInstanceOf(ZonePage.class);
  }

  @Test
  void objectZoneList() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.openZoneList(OBJECT, 12);
    assertThat(list().heading()).startsWith("Objects in Elwynn Forest (");
    assertThat(list().entries()).isNotEmpty().allSatisfy(e -> {
      assertThat(e.ref().kind()).isEqualTo(OBJECT);
      assertThat(e.subtitle()).isEqualTo("Object · Elwynn Forest");
    });
  }

  @Test
  void railTargets() {
    assertThat(AppController.target(RailTarget.HOME)).isEqualTo(new ViewTarget.Home());
    assertThat(AppController.target(RailTarget.ITEMS))
        .isEqualTo(new ViewTarget.ItemClass(ViewTarget.ItemClass.ALL_CLASSES, -1));
    assertThat(AppController.target(RailTarget.NPCS)).isEqualTo(new ViewTarget.List(NPC));
    assertThat(AppController.target(RailTarget.QUESTS)).isEqualTo(new ViewTarget.List(QUEST));
    assertThat(AppController.target(RailTarget.ZONES)).isEqualTo(new ViewTarget.List(ZONE));
    assertThat(AppController.target(RailTarget.INSTANCES)).isEqualTo(new ViewTarget.List(INSTANCE));
  }

  @Test
  void npcListIsAZonePickerWhoseCommitListsTheZonesNpcs() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(AppController.target(RailTarget.NPCS));
    assertThat(list().heading()).startsWith("NPCs: pick a zone (");
    assertThat(list().entries()).hasSize(repos.zones().all().size())
        .allSatisfy(e -> assertThat(e.ref().kind()).isEqualTo(ZONE));
    int row = refs().indexOf(new EntityRef(ZONE, 12));
    assertThat(row).isNotNegative();
    previewRow(row);
    assertThat(view.detail).isInstanceOf(ZonePage.class);
    list().commitSelected();
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(new EntityRef(ZONE, 12)));
    assertThat(list().heading()).startsWith("NPCs in Elwynn Forest (");
    assertThat(list().entries()).allSatisfy(e -> assertThat(e.ref().kind()).isEqualTo(NPC));
  }

  @Test
  void questListIsAZonePicker() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(AppController.target(RailTarget.QUESTS));
    assertThat(list().heading()).startsWith("Quests: pick a zone (");
    list().selectIndex(refs().indexOf(new EntityRef(ZONE, 12)));
    list().commitSelected();
    assertThat(list().heading()).startsWith("Quests in Elwynn Forest (");
  }

  @Test
  void previewReplacesCurrentWithoutPushing() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.Home());
    c.open(new ViewTarget.ItemClass(4, 1));
    List<EntityRef> rows = refs();
    assertThat(rows).hasSizeGreaterThanOrEqualTo(3);
    previewRow(0);
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(rows.get(0)));
    previewRow(1);
    previewRow(2);
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(rows.get(2)));
    assertThat(view.detail).isInstanceOf(ItemPage.class);
    assertThat(list().heading()).startsWith("Armor › Cloth (");
    // three previews took one history slot, right after the list
    c.back();
    assertThat(c.current()).isEqualTo(new ViewTarget.ItemClass(4, 1));
    c.back();
    assertThat(c.current()).isEqualTo(new ViewTarget.Home());
    assertThat(c.history().canBack()).isFalse();
  }

  @Test
  void commitPushesAndKeepsTheCommittedPage() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.ItemClass(4, 1));
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
    assertThat(c.current()).isEqualTo(new ViewTarget.ItemClass(4, 1));
  }

  @Test
  void committingThePreviewedRowDoesNotLoadItAgain() {
    AtomicInteger loads = new AtomicInteger();
    AppController c = new AppController(repos, view, r -> {
      loads.incrementAndGet();
      r.run();
    });
    c.open(new ViewTarget.ItemClass(4, 1));
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
    c.open(new ViewTarget.ItemClass(4, 1));
    List<EntityRef> rows = refs();
    list().selectIndex(0);
    list().commitSelected();
    int afterFirst = loads.get();
    list().commitSelected(); // a second click on the committed row
    assertThat(loads.get()).isEqualTo(afterFirst);
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(rows.get(0)));
    c.back();
    assertThat(c.current()).isEqualTo(new ViewTarget.ItemClass(4, 1));
  }

  @Test
  void previewingThePageOpenedByALinkKeepsItsSlot() {
    AtomicInteger loads = new AtomicInteger();
    AppController c = new AppController(repos, view, r -> {
      loads.incrementAndGet();
      r.run();
    });
    c.open(new ViewTarget.ItemClass(4, 1));
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
    assertThat(c.current()).isEqualTo(new ViewTarget.ItemClass(4, 1));
  }

  @Test
  void commitBeforeThePreviewFiresOpensAtOnce() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.ItemClass(4, 1));
    List<EntityRef> rows = refs();
    list().selectIndex(1);
    list().commitSelected();
    assertThat(view.delay.pending()).isZero();
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(rows.get(1)));
    assertThat(view.detail).isInstanceOf(ItemPage.class);
    c.back();
    assertThat(c.current()).isEqualTo(new ViewTarget.ItemClass(4, 1));
  }

  @Test
  void previewAfterBackDoesNotOverwriteTheEntryGoneBackTo() {
    AppController c = new AppController(repos, view, Runnable::run);
    c.open(new ViewTarget.ItemClass(4, 1));
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
    c.open(new ViewTarget.List(INSTANCE));
    queued.get(1).run();
    queued.get(0).run(); // the older search finishes last and must not replace the newer list
    assertThat(list().heading()).startsWith("Dungeons & Raids (");
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
