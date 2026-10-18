package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.journal.JournalDetails.JournalPage;
import dev.wowforever.app.ui.journal.JournalDungeonPage;
import dev.wowforever.app.ui.journal.JournalHomePage;
import dev.wowforever.app.ui.widgets.ManualDelay;
import dev.wowforever.app.ui.widgets.MasterList;
import dev.wowforever.app.ui.widgets.RailTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Real controller, real fixture database, and real Journal panels without native windows. */
@ExtendWith(FixtureDb.class)
class AppControllerJournalTest {
  private final JournalView view = new JournalView();

  private AppController controller() {
    return new AppController(FixtureDb.repositories(), view, Runnable::run);
  }

  @Test
  void journalStartupOpensTaskHomeInsteadOfTheClassicHome() {
    AppController controller = controller();
    controller.start();
    assertThat(controller.current()).isEqualTo(new ViewTarget.Home());
    assertThat(view.detail).isInstanceOf(JournalHomePage.class);
    assertThat(view.lastTarget).isEqualTo(new ViewTarget.Home());
    assertThat(view.title).isEqualTo("Explorer Journal");
  }

  @Test
  void bothDungeonEntrypointsUseTheUnifiedJournalCatalog() {
    AppController controller = controller();
    controller.open(new ViewTarget.List(EntityKind.INSTANCE));
    assertThat(view.detail).isInstanceOf(JournalDungeonPage.class);
    controller.open(new ViewTarget.Forever("ladder"));
    assertThat(view.detail).isInstanceOf(JournalDungeonPage.class);
    assertThat(view.lastTarget).isEqualTo(new ViewTarget.Forever("ladder"));
    controller.back();
    assertThat(view.lastTarget).isEqualTo(new ViewTarget.List(EntityKind.INSTANCE));
  }

  @Test
  void backRestoresDungeonQueryFiltersAndPreviewSelection() {
    AppController controller = controller();
    controller.open(new ViewTarget.List(EntityKind.INSTANCE));
    JournalDungeonPage page = (JournalDungeonPage) view.detail;
    var expected = new JournalDungeonPage.State("Deadmines", 2, 1, 0, 1, 0, "instance:2", 0);
    page.restoreState(expected);
    controller.open(new ViewTarget.Entity(new EntityRef(EntityKind.INSTANCE, 2)));
    assertThat(view.detail).isInstanceOf(JournalDungeonPage.class);
    assertThat(guide(view.detail)).isNotNull();
    assertThat(((JournalDungeonPage)view.detail).destinationState()).isEqualTo(expected);
    controller.back();
    assertThat(view.detail).isInstanceOf(JournalDungeonPage.class).isNotSameAs(page);
    assertThat(((JournalDungeonPage) view.detail).saveState()).isEqualTo(expected);
  }

  @Test
  void backAndForwardRestoreTheSelectedDungeonSection() {
    AppController controller = controller();
    var dungeon = new ViewTarget.Entity(new EntityRef(EntityKind.INSTANCE, 2));
    controller.open(dungeon);
    JournalPage first = guide(view.detail);
    first.tabs().setSelectedIndex(3);
    assertThat(first.tabs().getTitleAt(3)).isEqualTo("Maps");
    controller.open(new ViewTarget.Home());
    controller.back();
    JournalPage restored = guide(view.detail);
    assertThat(restored).isNotSameAs(first);
    assertThat(restored.tabs().getSelectedIndex()).isEqualTo(3);
    controller.forward();
    assertThat(view.detail).isInstanceOf(JournalHomePage.class);
    controller.back();
    assertThat(guide(view.detail).tabs().getSelectedIndex()).isEqualTo(3);
  }

  @Test
  void lateHomeSnapshotCannotReplaceTheNewerDungeonPage() {
    List<Runnable> pending = new ArrayList<>();
    ManualDelay loading = new ManualDelay();
    AppController controller = new AppController(FixtureDb.repositories(), view,
        pending::add, Map.of(), loading);
    controller.open(new ViewTarget.Home());
    controller.open(new ViewTarget.List(EntityKind.INSTANCE));
    assertThat(pending).hasSize(2);
    pending.remove(1).run();
    JComponent dungeon = view.detail;
    assertThat(dungeon).isInstanceOf(JournalDungeonPage.class);
    pending.remove(0).run();
    assertThat(view.detail).isSameAs(dungeon);
    assertThat(view.lastTarget).isEqualTo(new ViewTarget.List(EntityKind.INSTANCE));
    assertThat(view.title).isEqualTo("Dungeons & raids");
  }

  @Test void aChangedCatalogTokenSkipsAnOtherwiseCurrentQueuedPage() throws Exception {
    var preferences=java.util.prefs.Preferences.userRoot().node("wowforever-catalog-controller/"+java.util.UUID.randomUUID());
    try {
      var registry=dev.wowforever.app.catalog.EditionRegistry.fromLegacyMetadata(FixtureDb.repositories().database().meta());
      var session=new dev.wowforever.app.catalog.CatalogSession(registry,preferences);
      List<Runnable> pending=new ArrayList<>();
      var controller=new AppController(FixtureDb.repositories(),view,pending::add,Map.of(),new ManualDelay());
      controller.configureCatalogSession(session);
      controller.open(new ViewTarget.Home());
      session.select("forever");
      pending.removeFirst().run();
      assertThat(view.detail).isNull();
      controller.open(new ViewTarget.List(EntityKind.INSTANCE));
      pending.removeFirst().run();
      assertThat(view.detail).isInstanceOf(JournalDungeonPage.class);
    } finally {preferences.removeNode();}
  }

  @Test void aCatalogSessionFromAnotherProjectionCannotAttach() throws Exception {
    var preferences=java.util.prefs.Preferences.userRoot().node("wowforever-catalog-controller/"+java.util.UUID.randomUUID());
    try {
      var metadata=new java.util.HashMap<>(FixtureDb.repositories().database().meta());
      metadata.put("build_id","different-projection");
      var session=new dev.wowforever.app.catalog.CatalogSession(
          dev.wowforever.app.catalog.EditionRegistry.fromLegacyMetadata(metadata),preferences);
      org.assertj.core.api.Assertions.assertThatThrownBy(()->controller().configureCatalogSession(session))
          .isInstanceOf(IllegalArgumentException.class);
    } finally {preferences.removeNode();}
  }

  private static JournalPage guide(java.awt.Component component) {
    if(component instanceof JournalPage page)return page;
    if(component instanceof java.awt.Container container)
      for(var child:container.getComponents()) {JournalPage page=guide(child);if(page!=null)return page;}
    return null;
  }

  private static final class JournalView implements MainView {
    private final MasterList list = new MasterList(new ManualDelay());
    private final StatusBar status = new StatusBar();
    private JComponent detail;
    private String title;
    private ViewTarget lastTarget;
    @Override public UiExperience uiExperience() { return UiExperience.JOURNAL; }
    @Override public MasterList masterList() { return list; }
    @Override public void setDetail(JComponent component) { detail = component; }
    @Override public void setHistoryState(boolean back, boolean forward) {}
    @Override public StatusBar status() { return status; }
    @Override public void setTitleSuffix(String suffix) { title = suffix; }
    @Override public void selectRail(RailTarget target) {}
    @Override public void viewTargetChanged(ViewTarget target) { lastTarget = target; }
  }
}
