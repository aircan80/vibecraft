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
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.model.ObjectSummary;
import dev.wowforever.app.model.SearchHit;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.detail.ItemDetailPanel;
import dev.wowforever.app.ui.detail.NpcDetailPanel;
import dev.wowforever.app.ui.detail.ZoneDetailPanel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class AppControllerTest {
  private final Repositories repos = FixtureDb.repositories();
  private final StubView view = new StubView();

  private StubView frameStub() {
    return view;
  }

  @Test
  void searchOpensResultsAndPushesHistory() {
    AppController c = new AppController(repos, frameStub(), Runnable::run);
    c.open(new ViewTarget.Search("subterr", Optional.empty()));
    assertThat(c.current()).isEqualTo(new ViewTarget.Search("subterr", Optional.empty()));
    assertThat(frameStub().results().model().getRowCount()).isEqualTo(1);
  }

  @Test
  void backRestoresPreviousTarget() {
    AppController c = new AppController(repos, frameStub(), Runnable::run);
    c.open(new ViewTarget.List(ZONE));
    c.open(new ViewTarget.List(INSTANCE));
    c.back();
    assertThat(c.current()).isEqualTo(new ViewTarget.List(ZONE));
  }

  @Test
  void backAndForwardReShowWithoutPushingAndUpdateButtons() {
    AppController c = new AppController(repos, frameStub(), Runnable::run);
    c.open(new ViewTarget.List(ZONE));
    c.open(new ViewTarget.List(INSTANCE));
    assertThat(view.canBack).isTrue();
    assertThat(view.canForward).isFalse();
    c.back();
    assertThat(view.results().title()).startsWith("Zones (");
    assertThat(view.canBack).isFalse();
    assertThat(view.canForward).isTrue();
    c.forward();
    assertThat(c.current()).isEqualTo(new ViewTarget.List(INSTANCE));
    assertThat(view.results().model().getRowCount()).isEqualTo(repos.instances().all().size());
    assertThat(view.results().title()).isEqualTo("Dungeons & Raids (" + repos.instances().all().size() + ")");
    assertThat(c.history().canForward()).isFalse();
  }

  @Test
  void startOpensHome() {
    AppController c = new AppController(repos, frameStub(), Runnable::run, Map.of("build_id", "b-42"));
    c.start();
    assertThat(c.current()).isEqualTo(new ViewTarget.Home());
    assertThat(view.detail).isNotNull();
    assertThat(view.results().model().getRowCount()).isZero();
    assertThat(view.status().info()).contains("b-42");
    assertThat(view.titleSuffix).isEmpty();
  }

  @Test
  void searchShowsHitsTitleAndStatus() {
    AppController c = new AppController(repos, frameStub(), Runnable::run);
    c.open(new ViewTarget.Search("hogger", Optional.of(NPC)));
    assertThat(view.results().title()).isEqualTo("Search: hogger (1)");
    assertThat(((SearchHit) view.results().model().rowAt(0)).ref()).isEqualTo(new EntityRef(NPC, 448));
    assertThat(view.status().message()).isEqualTo("1 result");
    assertThat(view.titleSuffix).isEqualTo("Search: hogger");
  }

  @Test
  void itemClassListsEveryItemOfTheClass() {
    AppController c = new AppController(repos, frameStub(), Runnable::run);
    c.open(new ViewTarget.ItemClass(4, -1));
    var model = view.results().model();
    assertThat(model.getRowCount()).isEqualTo(repos.items().list(4, null, 500, 0).size()).isPositive();
    for (int r = 0; r < model.getRowCount(); r++) {
      assertThat(((ItemSummary) model.rowAt(r)).className()).isEqualTo("Armor");
    }
    assertThat(view.results().title()).startsWith("Armor (");
  }

  @Test
  void subclassListFiltersAndNamesBoth() {
    AppController c = new AppController(repos, frameStub(), Runnable::run);
    c.open(new ViewTarget.ItemClass(4, 1));
    var model = view.results().model();
    assertThat(model.getRowCount()).isPositive();
    for (int r = 0; r < model.getRowCount(); r++) {
      assertThat(((ItemSummary) model.rowAt(r)).subclassName()).isEqualTo("Cloth");
    }
    assertThat(view.results().title()).startsWith("Armor › Cloth (");
  }

  @Test
  void fullPageIsMarkedFirst500() {
    assertThat(AppController.itemClassTitle("Weapon", 500)).isEqualTo("Weapon (first 500)");
    assertThat(AppController.itemClassTitle("Weapon", 499)).isEqualTo("Weapon");
    assertThat(AppController.ITEM_PAGE).isEqualTo(500);
  }

  @Test
  void zoneListShowsNpcsAndPushesZoneEntity() {
    AppController c = new AppController(repos, frameStub(), Runnable::run);
    c.openZoneList(NPC, 2437);
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(new EntityRef(ZONE, 2437)));
    String zone = repos.zones().detail(2437).get().summary().name();
    assertThat(view.results().title()).startsWith("NPCs in " + zone + " (");
    var model = view.results().model();
    List<Long> ids = new ArrayList<>();
    for (int r = 0; r < model.getRowCount(); r++) {
      ids.add(((NpcSummary) model.rowAt(r)).id());
    }
    assertThat(ids).contains(11520L);
    assertThat(view.detail).isInstanceOf(ZoneDetailPanel.class);
  }

  @Test
  void objectZoneList() {
    AppController c = new AppController(repos, frameStub(), Runnable::run);
    c.openZoneList(OBJECT, 12);
    assertThat(view.results().title()).startsWith("Objects in Elwynn Forest (");
    assertThat(((ObjectSummary) view.results().model().rowAt(0)).zoneId()).isEqualTo(12L);
  }

  @Test
  void entityTargetRendersDetailPanel() {
    AppController c = new AppController(repos, frameStub(), Runnable::run);
    c.open(new ViewTarget.Entity(new EntityRef(ITEM, 14149)));
    assertThat(view.detail).isInstanceOf(ItemDetailPanel.class);
    assertThat(view.titleSuffix).isEqualTo("Subterranean Cape");
  }

  @Test
  void clickThroughFromItemToNpcAndBack() {
    AppController c = new AppController(repos, frameStub(), Runnable::run);
    c.open(new ViewTarget.Entity(new EntityRef(ITEM, 14149)));
    var droppedBy = ((ItemDetailPanel) view.detail).droppedBy();
    int row = -1;
    for (int r = 0; r < droppedBy.rowCount(); r++) {
      if (droppedBy.model().rowAt(droppedBy.table().convertRowIndexToModel(r)).ref().id() == 11520) {
        row = r;
      }
    }
    assertThat(row).isNotNegative();
    droppedBy.activateRow(row);
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(new EntityRef(NPC, 11520)));
    assertThat(view.detail).isInstanceOf(NpcDetailPanel.class);
    assertThat(view.titleSuffix).isEqualTo("Taragaman the Hungerer");
    c.back();
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(new EntityRef(ITEM, 14149)));
    assertThat(view.detail).isInstanceOf(ItemDetailPanel.class);
  }

  @Test
  void missingEntityShowsNotFound() {
    AppController c = new AppController(repos, frameStub(), Runnable::run);
    c.open(new ViewTarget.Entity(new EntityRef(QUEST, -5)));
    assertThat(view.detail).isInstanceOfSatisfying(JLabel.class,
        l -> assertThat(l.getText()).isEqualTo("Not found: Quest #-5"));
  }

  @Test
  void linkedZoneShowsDetailAndLeavesResults() {
    AppController c = new AppController(repos, frameStub(), Runnable::run);
    c.open(new ViewTarget.Search("hogger", Optional.of(NPC)));
    c.open(new ViewTarget.Entity(new EntityRef(ZONE, 40)));
    assertThat(view.detail).isInstanceOf(ZoneDetailPanel.class);
    assertThat(view.results().title()).isEqualTo("Search: hogger (1)");
    assertThat(view.titleSuffix).isEqualTo("Westfall");
  }

  @Test
  void staleDetailIsDropped() {
    List<Runnable> queued = new ArrayList<>();
    AppController c = new AppController(repos, frameStub(), queued::add);
    c.open(new ViewTarget.Entity(new EntityRef(ITEM, 14149)));
    c.open(new ViewTarget.Entity(new EntityRef(NPC, 639)));
    queued.get(1).run();
    queued.get(0).run(); // the item finishes last and must not replace the NPC
    assertThat(view.detail).isInstanceOf(NpcDetailPanel.class);
  }

  @Test
  void staleResultsAreDropped() {
    List<Runnable> queued = new ArrayList<>();
    AppController c = new AppController(repos, frameStub(), queued::add);
    c.open(new ViewTarget.Search("subterr", Optional.empty()));
    c.open(new ViewTarget.List(INSTANCE));
    queued.get(1).run();
    queued.get(0).run(); // the older search finishes last and must not replace the newer list
    assertThat(view.results().title()).startsWith("Dungeons & Raids (");
  }

  @Test
  void failuresGoToTheStatusBar() throws Exception {
    Database closed = Database.open(FixtureDb.path());
    closed.close();
    AppController c = new AppController(Repositories.of(closed), frameStub(), Runnable::run);
    c.open(new ViewTarget.List(ZONE));
    assertThat(view.status().message()).startsWith("Error: ");
  }
}
