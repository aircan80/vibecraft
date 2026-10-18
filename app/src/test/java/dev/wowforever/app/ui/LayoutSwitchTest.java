package dev.wowforever.app.ui;

import static dev.wowforever.app.model.EntityKind.ITEM;
import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.pages.ItemPage;
import dev.wowforever.app.ui.widgets.FakeDropdownHost;
import dev.wowforever.app.ui.widgets.HistoryButtons;
import dev.wowforever.app.ui.widgets.ManualDelay;
import dev.wowforever.app.ui.widgets.MasterList;
import dev.wowforever.app.ui.widgets.Rail;
import dev.wowforever.app.ui.widgets.SearchDropdown;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * {@link LayoutSwitch}, the F10 switch, over the real window parts and controller (headless): a fake frame records
 * what it is told and saves its bounds through a real {@link WindowStatePersistence}.
 */
@ExtendWith(FixtureDb.class)
class LayoutSwitchTest {
  /** An item NPCs drop in the fixture database. */
  private static final ViewTarget ITEM_PAGE = new ViewTarget.Entity(new EntityRef(ITEM, 14149));
  private static final ViewTarget.Search ITEMS_C = new ViewTarget.Search("c", Optional.of(ITEM));
  private static final List<Rectangle> SCREENS = List.of(new Rectangle(0, 0, 1920, 1080));
  private static final Rectangle FULL = new Rectangle(100, 100, 1000, 700);

  private final Preferences node = UiStateTest.testNode();
  private final UiState state = new UiState(node);
  private final Repositories repos = FixtureDb.repositories();
  private final StubView view = new StubView();
  private final AppController controller = new AppController(repos, view, Runnable::run);
  private final SearchBar search = new SearchBar(t -> {}, state, new ManualDelay(),
      new SearchDropdown(new FakeDropdownHost()));
  private final JToolBar toolbar = MainFrame.toolbar(HistoryButtons.back(), HistoryButtons.forward(), search);
  private final JPanel detailSlot = new JPanel(new BorderLayout());
  private final JPanel content;
  private final CompactShell shell = new CompactShell(() -> {}, () -> {}, () -> {});
  private final FakeWindow window = new FakeWindow();
  private final LayoutSwitch layout;

  /** A frame that records each call, and whether the window was in compact mode when it was placed. */
  private final class FakeWindow implements LayoutSwitch.Window {
    final List<String> events = new ArrayList<>();
    Rectangle bounds = FULL;
    int extendedState = Frame.NORMAL;
    JPanel shown;
    boolean menuBar;
    /** The frame's root pane, whose content pane each layout shown becomes (as on the real frame). */
    final JRootPane root = new JRootPane();
    final WindowStatePersistence persistence = new WindowStatePersistence(state,
        () -> new WindowStatePersistence.Snapshot(bounds, extendedState), new ManualDelay());

    @Override
    public void saveBounds() {
      events.add("save");
      persistence.saveNow();
    }

    @Override
    public void clearMaximised() {
      events.add("unmaximise");
      extendedState &= ~Frame.MAXIMIZED_BOTH;
    }

    @Override
    public void maximise() {
      events.add("maximise");
      extendedState |= Frame.MAXIMIZED_BOTH;
    }

    @Override
    public void show(JPanel pane, boolean withMenuBar) {
      events.add((pane == shell ? "show compact" : pane == content ? "show full" : "show ?")
          + (withMenuBar ? " with menu bar" : ""));
      shown = pane;
      menuBar = withMenuBar;
      root.setContentPane(pane);
    }

    @Override
    public void place(Rectangle b) {
      events.add("place in " + (state.compactMode() ? "compact" : "full") + " mode");
      bounds = b;
    }

    @Override
    public List<Rectangle> screens() {
      return SCREENS;
    }

    @Override
    public Rectangle currentScreen() {
      return SCREENS.get(0);
    }
  }

  LayoutSwitchTest() {
    detailSlot.add(view.holder, BorderLayout.CENTER);
    content = MainFrame.buildContent(toolbar, new Rail(), view.masterList(), detailSlot, view.status(), state);
    layout = new LayoutSwitch(new LayoutSwitch.Parts(toolbar, content, detailSlot, view.masterList(), shell, search,
        view.holder, view.status()), state, window, () -> {
          window.events.add("rerender");
          controller.rerender();
        });
    view.masterListShown = () -> !layout.isCompact();
  }

  @AfterEach
  void removeNode() throws BackingStoreException {
    node.removeNode();
  }

  private static Rectangle defaultCompact() {
    int w = UIScale.scale(UiState.COMPACT_WIDTH);
    int h = UIScale.scale(UiState.COMPACT_HEIGHT);
    return new Rectangle(1920 - w, 0, w, h);
  }

  private static List<String> names(LinkTable<?> t) {
    List<String> names = new ArrayList<>();
    for (int c = 0; c < t.table().getColumnCount(); c++) {
      names.add(t.table().getColumnName(c));
    }
    return names;
  }

  private void assertFullLayout() {
    assertThat(layout.isCompact()).isFalse();
    assertThat(window.shown).isSameAs(content);
    assertThat(window.menuBar).isTrue();
    assertThat(search.getParent()).isSameAs(toolbar);
    assertThat(toolbar.getComponentIndex(search)).isEqualTo(toolbar.getComponentCount() - 1);
    assertThat(search.kindMenu().getParent()).isNull(); // no longer the compact kind menu
    assertThat(SwingUtilities.isDescendingFrom(search.kindChips(), search)).as("chips only in the dropdown")
        .isFalse();
    assertThat(view.holder.getParent()).isSameAs(detailSlot);
    assertThat(((BorderLayout) content.getLayout()).getLayoutComponent(BorderLayout.SOUTH)).isSameAs(view.status());
  }

  private void assertCompactLayout() {
    assertThat(layout.isCompact()).isTrue();
    assertThat(window.shown).isSameAs(shell);
    assertThat(window.menuBar).isFalse();
    assertThat(SwingUtilities.isDescendingFrom(search, shell.topBar())).isTrue();
    assertThat(search.kindMenu().getParent()).isSameAs(search);
    assertThat(SwingUtilities.isDescendingFrom(view.holder, shell)).isTrue();
    assertThat(SwingUtilities.isDescendingFrom(view.status(), shell)).isTrue();
  }

  @Test
  void startShowsTheFullLayoutByDefault() {
    layout.start();
    assertThat(window.events).containsExactly("show full with menu bar");
    assertFullLayout();
  }

  @Test
  void startHonoursASavedCompactMode() {
    state.setCompactMode(true);
    layout.start();
    assertThat(window.events).containsExactly("show compact");
    assertCompactLayout();
  }

  @Test
  void f10SavesThenSetsTheModeBeforeTheWindowMoves() {
    layout.start();
    window.events.clear();
    layout.toggle();
    // the mode is set before the window is placed, so the saves its moves set off go to the compact slot
    assertThat(window.events).containsExactly("save", "unmaximise", "show compact", "place in compact mode",
        "rerender");
    assertThat(state.compactMode()).isTrue();
    assertThat(state.bounds()).isEqualTo(FULL);
    assertThat(window.bounds).isEqualTo(defaultCompact());
    assertCompactLayout();
  }

  @Test
  void f10AgainBringsBackTheFullLayoutWithItsStatusLineAndSearchBar() {
    layout.start();
    layout.toggle();
    window.events.clear();
    layout.toggle();
    assertThat(window.events).containsExactly("save", "unmaximise", "show full with menu bar",
        "place in full mode", "rerender");
    assertThat(state.compactMode()).isFalse();
    assertFullLayout();
  }

  @Test
  void eachLayoutComesBackWhereItWasSaved() {
    layout.start();
    layout.toggle();
    Rectangle moved = new Rectangle(1300, 50, 450, 800);
    window.bounds = moved; // the user moves the compact window
    layout.toggle();
    assertThat(window.bounds).isEqualTo(FULL);
    assertThat(state.compactBounds()).isEqualTo(moved);
    layout.toggle();
    assertThat(window.bounds).isEqualTo(moved);
    assertThat(state.bounds()).isEqualTo(FULL);
  }

  @Test
  void aMaximisedFullWindowComesBackMaximised() {
    state.setBounds(FULL);
    layout.start();
    window.extendedState = Frame.MAXIMIZED_BOTH;
    layout.toggle();
    assertThat(window.extendedState).isEqualTo(Frame.NORMAL);
    assertThat(state.maximised()).isTrue();
    window.events.clear();
    layout.toggle();
    assertThat(window.events).containsSubsequence("place in full mode", "maximise");
    assertThat(window.extendedState).isEqualTo(Frame.MAXIMIZED_BOTH);
    assertThat(window.bounds).isEqualTo(FULL);
  }

  @Test
  void aMaximisedCompactWindowIsNotMaximisedInTheFullLayout() {
    layout.start();
    layout.toggle();
    window.extendedState = Frame.MAXIMIZED_BOTH; // maximised from its title bar
    window.events.clear();
    layout.toggle();
    assertThat(window.events).containsSubsequence("unmaximise", "place in full mode").doesNotContain("maximise");
    assertThat(window.extendedState).isEqualTo(Frame.NORMAL);
    assertThat(state.maximised()).isFalse();
    window.persistence.saveNow(); // the next save of the full window
    assertThat(state.maximised()).isFalse();
  }

  @Test
  void boundsForTheCompactLayout() {
    assertThat(LayoutSwitch.boundsFor(true, state, SCREENS, SCREENS.get(0))).isEqualTo(defaultCompact());
    state.setCompactBounds(new Rectangle(1400, 30, 440, 760));
    assertThat(LayoutSwitch.boundsFor(true, state, SCREENS, SCREENS.get(0)))
        .isEqualTo(new Rectangle(1400, 30, 440, 760));
    state.setCompactBounds(new Rectangle(5000, 3000, 420, 720)); // a screen no longer attached
    assertThat(LayoutSwitch.boundsFor(true, state, SCREENS, SCREENS.get(0))).isEqualTo(defaultCompact());
  }

  @Test
  void boundsForTheFullLayout() {
    state.setCompactBounds(new Rectangle(1400, 30, 440, 760));
    assertThat(LayoutSwitch.boundsFor(false, state, SCREENS, SCREENS.get(0)))
        .isEqualTo(new Rectangle(320, 140, 1280, 800));
    state.setBounds(FULL);
    assertThat(LayoutSwitch.boundsFor(false, state, SCREENS, SCREENS.get(0))).isEqualTo(FULL);
  }

  @Test
  void windowsSavedOnABigScreenReopenWholeOnASmallOne() {
    List<Rectangle> laptop = List.of(new Rectangle(0, 0, 1366, 768));
    state.setBounds(new Rectangle(0, 0, 2560, 1400));
    state.setCompactBounds(new Rectangle(1000, 0, 420, 1400));
    assertThat(LayoutSwitch.boundsFor(false, state, laptop, laptop.get(0))).isEqualTo(new Rectangle(0, 0, 1366, 768));
    assertThat(LayoutSwitch.boundsFor(true, state, laptop, laptop.get(0))).isEqualTo(new Rectangle(946, 0, 420, 768));
  }

  @Test
  void togglingKeepsTheTargetAndTheHistory() {
    layout.start();
    controller.start();
    controller.open(ITEM_PAGE);
    int entries = controller.history().size();
    JComponent fullPage = view.detail;
    assertThat(names(((ItemPage) fullPage).droppedBy())).hasSize(5);

    layout.toggle();
    assertThat(controller.current()).isEqualTo(ITEM_PAGE);
    assertThat(controller.history().size()).isEqualTo(entries);
    assertThat(view.canBack).isTrue();
    assertThat(view.detail).isInstanceOf(ItemPage.class).isNotSameAs(fullPage);
    assertThat(SwingUtilities.isDescendingFrom(view.detail, shell)).isTrue();
    assertThat(names(((ItemPage) view.detail).droppedBy())).containsExactly("Source", "Rate", "Origin");

    layout.toggle();
    assertThat(controller.current()).isEqualTo(ITEM_PAGE);
    assertThat(controller.history().size()).isEqualTo(entries);
    assertThat(SwingUtilities.isDescendingFrom(view.detail, content)).isTrue();
    assertThat(names(((ItemPage) view.detail).droppedBy())).hasSize(5);
  }

  @Test
  void aSearchShownCompactFillsTheListAndBringsBackThePreviousPageInTheFullLayout() {
    layout.start();
    controller.start();
    controller.open(ITEM_PAGE);
    layout.toggle();
    controller.open(ITEMS_C);
    assertThat(view.detail).isInstanceOf(MasterList.class); // the results are the compact page
    int entries = controller.history().size();

    layout.toggle();
    assertThat(controller.current()).isEqualTo(ITEMS_C);
    assertThat(controller.history().size()).isEqualTo(entries);
    assertThat(view.masterList().entries()).isNotEmpty();
    assertThat(view.detail).isInstanceOf(ItemPage.class);
    assertThat(SwingUtilities.isDescendingFrom(view.detail, content)).isTrue();
  }

  @Test
  void aSearchFromTheHomePageBringsBackTheHomePageInTheFullLayout() {
    state.setCompactMode(true);
    layout.start();
    controller.start();
    controller.open(ITEMS_C);
    layout.toggle();
    assertThat(view.masterList().entries()).isNotEmpty();
    assertThat(view.detail).isInstanceOf(JLabel.class); // the welcome text, not a blank page
    assertThat(((JLabel) view.detail).getText()).contains("Vibecraft");
  }

  /** Whether mouse button 4 released on {@code source} goes back, with the navigation installed on the root pane. */
  private boolean button4GoesBack(Component source) {
    List<String> calls = new ArrayList<>();
    MouseNavigation nav = MouseNavigation.create(() -> calls.add("back"), () -> calls.add("forward"),
        c -> MouseNavigation.sameWindow(c, window.root));
    MouseEvent e = new MouseEvent(source, MouseEvent.MOUSE_RELEASED, 0L, 0, 1, 1, 1, false, MouseEvent.BUTTON1) {
      @Override
      public int getButton() {
        return 4; // a headless toolkit rejects extra buttons in the constructor
      }
    };
    return nav.handle(e) && calls.equals(List.of("back"));
  }

  @Test
  void mouseButtonsWorkInBothLayoutsAndWhenTheAppStartsCompact() {
    state.setCompactMode(true);
    layout.start();
    assertThat(button4GoesBack(shell.topBar())).as("started compact").isTrue();
    assertThat(MouseNavigation.sameWindow(shell.topBar(), content)).as("the full layout is not in the window")
        .isFalse();
    layout.toggle();
    assertThat(button4GoesBack(view.status())).as("full").isTrue();
    layout.toggle();
    assertThat(button4GoesBack(view.status())).as("compact again").isTrue();
  }

  @Test
  void escapeFocusesPageContentInBothLayoutsWithoutReattachingLegacyList() {
    layout.start();
    assertThat(layout.escapeTarget()).isNull();
    assertThat(view.masterList().getParent()).isNull();
    assertThat(view.masterList().isVisible()).isFalse();
    controller.start();
    layout.toggle();
    assertThat(layout.escapeTarget()).isNull(); // the home page has nothing to browse
    controller.open(ITEM_PAGE);
    assertThat(layout.escapeTarget()).isSameAs(((ItemPage) view.detail).droppedBy().table());
    layout.toggle();
    assertThat(layout.escapeTarget()).isSameAs(((ItemPage) view.detail).droppedBy().table());
    assertThat(view.masterList().getParent()).isNull();
    assertThat(view.masterList().isVisible()).isFalse();
  }
}
