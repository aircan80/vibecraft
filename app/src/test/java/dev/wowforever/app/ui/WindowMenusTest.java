package dev.wowforever.app.ui;

import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.SourceFilter;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.pages.NpcPage;
import dev.wowforever.app.ui.pages.ZonePage;
import dev.wowforever.app.ui.widgets.SourceFilterControl;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class WindowMenusTest {
  private final Preferences node = UiStateTest.testNode();
  private final UiState state = new UiState(node);

  @AfterEach
  void removeNode() throws BackingStoreException {
    node.removeNode();
  }

  private static JMenuItem item(JMenu menu, String text) {
    for (int i = 0; i < menu.getItemCount(); i++) {
      if (menu.getItem(i).getText().equals(text)) {
        return menu.getItem(i);
      }
    }
    throw new AssertionError("No item " + text);
  }

  @Test
  void theFilterControlBesideDropsSavesItAndShowsTheCurrentPageAgainFiltered() {
    // the View menu's "Drop sources" moved onto the drop sections: the control beside the heading does its job
    StubView view = new StubView();
    view.sourceFilter = state::sourceFilter;
    view.saveSourceFilter = state::setSourceFilter;
    AppController c = new AppController(FixtureDb.repositories(), view, Runnable::run);
    c.open(new ViewTarget.Entity(new EntityRef(NPC, 639)));
    var before = (NpcPage) view.detail;
    assertThat(before.section("Drops").heading().text()).isEqualTo("Drops (5)");
    var control = (SourceFilterControl) before.section("Drops").heading().trailing();
    assertThat(control.getSelectedItem()).isEqualTo(SourceFilter.ALL);

    control.setSelectedItem(SourceFilter.HIDE_ATLASLOOT_ONLY);
    assertThat(new UiState(node).sourceFilter()).as("saved").isEqualTo(SourceFilter.HIDE_ATLASLOOT_ONLY);
    var after = (NpcPage) view.detail;
    assertThat(after).as("shown again").isNotSameAs(before);
    assertThat(after.section("Drops").heading().text()).isEqualTo("Drops (2)");
    assertThat(after.section("Drops").note().getText()).isEqualTo("3 rows hidden by the source filter");
    assertThat(c.history().canBack()).as("no history entry").isFalse();
    var again = (SourceFilterControl) after.section("Drops").heading().trailing();
    assertThat(again.getSelectedItem()).as("the new page's control shows the filter")
        .isEqualTo(SourceFilter.HIDE_ATLASLOOT_ONLY);

    c.changeSourceFilter(SourceFilter.HIDE_ATLASLOOT_ONLY); // already chosen: nothing to show again
    assertThat(view.detail).isSameAs(after);

    again.setSelectedItem(SourceFilter.ALL);
    assertThat(new UiState(node).sourceFilter()).isEqualTo(SourceFilter.ALL);
    assertThat(((NpcPage) view.detail).section("Drops").note()).isNull();
  }

  @Test
  void aZoneMapsQuestsToggleIsSavedAndTheNextZonePageStartsWithIt() {
    StubView view = new StubView();
    view.questPins = state.questPinsSetting();
    AppController c = new AppController(FixtureDb.repositories(), view, Runnable::run);
    c.open(new ViewTarget.Entity(new EntityRef(ZONE, 12)));
    var elwynn = (ZonePage) view.detail;
    assertThat(elwynn.mapPanel().questsToggle().isSelected()).isFalse();
    elwynn.mapPanel().questsToggle().doClick();
    assertThat(new UiState(node).questPinsShown()).as("saved").isTrue();
    c.open(new ViewTarget.Entity(new EntityRef(ZONE, 40)));
    assertThat(((ZonePage) view.detail).mapPanel().questsToggle().isSelected()).isTrue();
  }

  private static List<String> texts(JComponent menu) {
    List<String> out = new ArrayList<>();
    for (Component c : menu instanceof JMenu m ? m.getMenuComponents() : menu.getComponents()) {
      out.add(c instanceof JMenuItem i ? i.getText() : "---");
    }
    return out;
  }

  @Test
  void bothLayoutsGetTheSameViewAndHelpItemsFromOneBuilder() {
    List<String> calls = new ArrayList<>();
    boolean[] dark = {true};
    WindowMenus.Actions actions = new WindowMenus.Actions(() -> dark[0], () -> calls.add("theme"),
        () -> calls.add("shortcuts"), () -> calls.add("about"));
    JMenu view = new JMenu("View");
    WindowMenus.addViewItems(view, actions);
    JMenu help = new JMenu("Help");
    WindowMenus.addHelpItems(help, actions);
    JPopupMenu compact = new JPopupMenu();
    WindowMenus.addViewItems(compact, actions);
    WindowMenus.addHelpItems(compact, actions);
    // no "Drop sources" menu: the filter sits beside the drop sections' headings
    assertThat(texts(view)).containsExactly("Slate theme", "Parchment theme");
    assertThat(texts(help)).containsExactly("Keyboard shortcuts", "About");
    assertThat(texts(compact)).containsExactly("Slate theme", "Parchment theme", "---", "Keyboard shortcuts",
        "About");

    JMenuItem slate = (JMenuItem) compact.getComponent(0);
    JMenuItem parchment = (JMenuItem) compact.getComponent(1);
    assertThat(slate.isSelected()).isTrue();
    assertThat(parchment.isSelected()).isFalse();
    slate.doClick();
    assertThat(calls).as("choosing the theme already on does nothing").isEmpty();
    dark[0] = false; // the other layout switched the theme
    assertThat(slate.isSelected()).as("read live, no refresh needed").isFalse();
    assertThat(parchment.isSelected()).isTrue();
    slate.doClick();
    item(help, "Keyboard shortcuts").doClick();
    item(help, "About").doClick();
    assertThat(calls).containsExactly("theme", "shortcuts", "about");
  }
}
