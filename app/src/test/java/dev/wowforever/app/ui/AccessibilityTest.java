package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.detail.DetailPanels;
import dev.wowforever.app.ui.pages.ListOptions;
import dev.wowforever.app.ui.pages.ListPage;
import dev.wowforever.app.ui.pages.ListRows;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.theme.Tokens;
import dev.wowforever.app.ui.theme.WowForeverDarkLaf;
import dev.wowforever.app.ui.widgets.FakeDropdownHost;
import dev.wowforever.app.ui.widgets.HistoryButtons;
import dev.wowforever.app.ui.widgets.ManualDelay;
import dev.wowforever.app.ui.widgets.MapView;
import dev.wowforever.app.ui.widgets.MasterList;
import dev.wowforever.app.ui.widgets.Rail;
import dev.wowforever.app.ui.widgets.SearchDropdown;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.accessibility.AccessibleContext;
import javax.swing.AbstractButton;
import javax.swing.CellRendererPane;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.UIResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Every button that shows no text in the window content has an accessible name, so a screen reader can say what
 * it does. Built under the app's own look and feel, whose search box adds an icon-only clear button. Every page
 * (one of each kind from the fixture, and every list page with its filter bar) names its buttons, combos, spinners,
 * tables, lists and maps too.
 */
@ExtendWith(FixtureDb.class)
class AccessibilityTest {
  private final Preferences node = UiStateTest.testNode();
  private final UiState state = new UiState(node);

  private LookAndFeel savedLaf;
  private Theme savedTokens;

  /** The dark theme, always, without touching the saved theme preference (which ThemeManager.apply writes). */
  @BeforeEach
  void installDarkTheme() {
    savedLaf = UIManager.getLookAndFeel();
    savedTokens = Theme.current();
    WowForeverDarkLaf.setup();
    Theme.setCurrent(Tokens.DARK);
  }

  @AfterEach
  void restore() throws BackingStoreException, UnsupportedLookAndFeelException {
    if (savedLaf != null) {
      UIManager.setLookAndFeel(savedLaf);
    }
    Theme.setCurrent(savedTokens);
    node.removeNode();
  }

  private JPanel content() {
    SearchBar search = new SearchBar(t -> {}, state, new ManualDelay(), new SearchDropdown(new FakeDropdownHost()));
    search.textField().setText("hogger"); // the clear button shows only over text
    return MainFrame.buildContent(MainFrame.toolbar(HistoryButtons.back(), HistoryButtons.forward(), search),
        new Rail(),
        new MasterList(new ManualDelay()), new JPanel(), new StatusBar(), state);
  }

  /** The buttons under {@code c} that show no text, skipping the look and feel's own (scroll bar arrows). */
  private static List<AbstractButton> iconOnlyButtons(Component c) {
    List<AbstractButton> found = new ArrayList<>();
    if (c instanceof AbstractButton b && !(b instanceof UIResource) && (b.getText() == null || b.getText().isBlank())) {
      found.add(b);
    }
    if (c instanceof Container parent) {
      for (Component child : parent.getComponents()) {
        found.addAll(iconOnlyButtons(child));
      }
    }
    return found;
  }

  private static String describe(AbstractButton b) {
    return b.getClass().getName() + " in " + b.getParent().getClass().getName()
        + (b.getName() != null ? " named " + b.getName() : "");
  }

  @Test
  void everyIconOnlyButtonHasAnAccessibleName() {
    List<AbstractButton> buttons = iconOnlyButtons(content());
    // six rail buttons, back and forward, and the search box's clear button
    assertThat(buttons).hasSizeGreaterThanOrEqualTo(9);
    for (AbstractButton b : buttons) {
      assertThat(b.getAccessibleContext().getAccessibleName()).as(describe(b)).isNotBlank();
    }
  }

  /** The components under {@code c} of {@code type}. */
  private static <T> List<T> all(Component c, Class<T> type) {
    List<T> found = new ArrayList<>();
    if (type.isInstance(c)) {
      found.add(type.cast(c));
    }
    if (c instanceof Container parent) {
      for (Component child : parent.getComponents()) {
        found.addAll(all(child, type));
      }
    }
    return found;
  }

  /** One page of each kind, built from the fixture, and every list page (with its filter bar). */
  private static List<JComponent> pages() {
    Repositories repos = FixtureDb.repositories();
    List<JComponent> pages = new ArrayList<>();
    for (EntityRef ref : List.of(new EntityRef(EntityKind.ITEM, 17204), new EntityRef(EntityKind.ITEM, 19682),
        new EntityRef(EntityKind.ITEM, 14149), new EntityRef(EntityKind.NPC, 639),
        new EntityRef(EntityKind.OBJECT, 386759), new EntityRef(EntityKind.QUEST, 2),
        new EntityRef(EntityKind.ZONE, 40), new EntityRef(EntityKind.INSTANCE, 1))) {
      pages.add(DetailPanels.build(ref, repos, t -> {}));
    }
    ListOptions options = ListOptions.load(repos);
    for (EntityKind kind : List.of(EntityKind.ITEM, EntityKind.NPC, EntityKind.QUEST, EntityKind.ZONE,
        EntityKind.INSTANCE)) {
      ViewTarget.List target = new ViewTarget.List(kind);
      pages.add(new ListPage(target, options, ListRows.load(target, repos), t -> {}, f -> {}, new ManualDelay()));
    }
    return pages;
  }

  private static String nameOf(Component c) {
    AccessibleContext context = c.getAccessibleContext();
    return context == null ? null : context.getAccessibleName();
  }

  @Test
  void everyPageNamesItsButtonsInputsTablesListsAndMaps() {
    List<JComponent> pages = pages();
    assertThat(pages).hasSize(13).noneMatch(JLabel.class::isInstance); // no "Not found" placeholder
    int inputs = 0;
    for (JComponent page : pages) {
      String where = page.getClass().getSimpleName();
      for (AbstractButton b : iconOnlyButtons(page)) {
        assertThat(nameOf(b)).as(describe(b) + " on " + where).isNotBlank();
      }
      List<Component> named = new ArrayList<>();
      named.addAll(all(page, JComboBox.class));
      named.addAll(all(page, JSpinner.class));
      named.addAll(all(page, JTable.class));
      named.addAll(all(page, JList.class).stream().filter(l -> !(l.getParent() instanceof CellRendererPane))
          .filter(l -> SwingUtilities.getAncestorOfClass(JComboBox.class, l) == null).toList());
      named.addAll(all(page, MapView.class));
      for (Component c : named) {
        assertThat(nameOf(c)).as(c.getClass().getSimpleName() + " on " + where).isNotBlank();
      }
      inputs += all(page, JComboBox.class).size() + all(page, JSpinner.class).size();
    }
    assertThat(inputs).as("the filter bars' combos and spinners were walked").isGreaterThanOrEqualTo(12);
    assertThat(pages.stream().mapToInt(p -> all(p, MapView.class).size()).sum()).as("a zone map was walked")
        .isPositive();
  }

  @Test
  void theFilterBarNamesItsLevelSpinnersByWhatTheyBound() {
    Repositories repos = FixtureDb.repositories();
    ViewTarget.List target = new ViewTarget.List(EntityKind.NPC);
    ListPage page = new ListPage(target, ListOptions.load(repos), ListRows.load(target, repos), t -> {}, f -> {},
        new ManualDelay());
    assertThat(nameOf(page.filterBar().minLevel())).isEqualTo("Minimum level");
    assertThat(nameOf(page.filterBar().maxLevel())).isEqualTo("Maximum level");
    assertThat(nameOf(page.filterBar().zones())).isEqualTo("Zone");
  }

  @Test
  void theSearchClearButtonIsNamed() {
    JPanel content = content();
    List<AbstractButton> clear = iconOnlyButtons(content).stream()
        .filter(b -> SwingUtilities.getAncestorOfClass(SearchBar.class, b) != null).toList();
    assertThat(clear).isNotEmpty();
    assertThat(clear).allSatisfy(b -> assertThat(b.getAccessibleContext().getAccessibleName())
        .isEqualTo(SearchBar.CLEAR_NAME));
  }

  /** The compact window: its own back, forward and pin buttons, and the search bar with its clear button. */
  private CompactShell compactShell() {
    SearchBar search = new SearchBar(t -> {}, state, new ManualDelay(), new SearchDropdown(new FakeDropdownHost()));
    search.textField().setText("hogger");
    CompactShell shell = new CompactShell(() -> {}, () -> {}, () -> {});
    shell.claim(search, new JPanel(), new StatusBar());
    return shell;
  }

  @Test
  void everyIconOnlyButtonOfTheCompactShellHasAnAccessibleName() {
    CompactShell shell = compactShell();
    List<AbstractButton> buttons = iconOnlyButtons(shell);
    // back, forward, pin, and the search box's clear button
    assertThat(buttons).hasSizeGreaterThanOrEqualTo(4).contains(shell.backButton(), shell.forwardButton(),
        shell.pinButton());
    for (AbstractButton b : buttons) {
      assertThat(b.getAccessibleContext().getAccessibleName()).as(describe(b)).isNotBlank();
    }
  }
}
