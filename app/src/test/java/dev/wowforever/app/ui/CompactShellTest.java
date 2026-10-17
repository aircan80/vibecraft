package dev.wowforever.app.ui;

import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.DropSource;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ItemDetail;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.pages.ItemPage;
import dev.wowforever.app.ui.widgets.FakeDropdownHost;
import dev.wowforever.app.ui.widgets.KindMenu;
import dev.wowforever.app.ui.widgets.ManualDelay;
import dev.wowforever.app.ui.widgets.MasterList;
import dev.wowforever.app.ui.widgets.SearchDropdown;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** {@link CompactShell}, built headless with a controller over a {@link StubView}; F10 is {@link LayoutSwitchTest}. */
@ExtendWith(FixtureDb.class)
class CompactShellTest {
  private static final ItemSummary LETTER = new ItemSummary(2874, "An Unsent Letter", 16, 16, "Quest", "Quest", 1);

  private final Preferences node = UiStateTest.testNode();
  private final UiState state = new UiState(node);
  private final Repositories repos = FixtureDb.repositories();
  private final StubView view = new StubView();
  private final List<String> calls = new ArrayList<>();
  private final CompactShell shell = new CompactShell(() -> calls.add("back"), () -> calls.add("forward"),
      () -> calls.add("onTop"));
  private final SearchBar search = new SearchBar(t -> {}, state, new ManualDelay(),
      new SearchDropdown(new FakeDropdownHost()));
  private final StatusBar status = new StatusBar();

  @AfterEach
  void removeNode() throws BackingStoreException {
    node.removeNode();
  }

  private static DropSource drop(long npc, String name, Double pct, String origin) {
    return new DropSource(new EntityRef(NPC, npc), name, 20, 21, "The Deadmines", 1581L, pct, "questie", origin);
  }

  private static ItemPage itemPage() {
    ItemDetail d = new ItemDetail(LETTER, 12, 0, null,
        List.of(drop(639, "Edwin VanCleef", 60.0, "both"), drop(2, "Defias Squallshaper", 1.5, "questie")),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    return new ItemPage(d, t -> {});
  }

  private static List<String> names(LinkTable<?> t) {
    List<String> names = new ArrayList<>();
    for (int c = 0; c < t.table().getColumnCount(); c++) {
      names.add(t.table().getColumnName(c));
    }
    return names;
  }

  private static Component at(JComponent parent, String constraint) {
    return ((BorderLayout) parent.getLayout()).getLayoutComponent(constraint);
  }

  @Test
  void theShellIsATopBarThenThePageThenTheStatus() {
    JPanel page = new JPanel();
    shell.claim(search, page, status);
    assertThat(SwingUtilities.isDescendingFrom(search, shell.topBar())).isTrue();
    assertThat(SwingUtilities.isDescendingFrom(shell.backButton(), shell.topBar())).isTrue();
    assertThat(SwingUtilities.isDescendingFrom(shell.forwardButton(), shell.topBar())).isTrue();
    assertThat(SwingUtilities.isDescendingFrom(shell.pinButton(), shell.topBar())).isTrue();
    assertThat(at(shell, BorderLayout.NORTH)).isSameAs(shell.topBar());
    assertThat(SwingUtilities.isDescendingFrom(page, at(shell, BorderLayout.CENTER))).isTrue();
    assertThat(SwingUtilities.isDescendingFrom(status, at(shell, BorderLayout.SOUTH))).isTrue();
  }

  @Test
  void anItemPagesDropTableShowsNameRateAndOriginWithTheRestInTheTooltip() {
    ItemPage page = itemPage();
    shell.claim(search, page, status);
    LinkTable<DropRow> drops = page.droppedBy();
    assertThat(names(drops)).containsExactly("Source", "Rate", "Origin");
    assertThat(drops.rowTooltip(0)).isEqualTo("Level 20–21 · Zone: The Deadmines");
  }

  @Test
  void aPageLeavingTheShellShowsEveryColumnAgain() {
    ItemPage page = itemPage();
    shell.claim(search, page, status);
    new JPanel(new BorderLayout()).add(page);
    assertThat(names(page.droppedBy())).containsExactly("Source", "Level", "Zone", "Rate", "Origin");
  }

  @Test
  void aFullSearchInCompactModeShowsItsResultsAsThePage() {
    AppController c = new AppController(repos, view, Runnable::run);
    view.masterListShown = () -> false;
    ViewTarget.Search items = new ViewTarget.Search("c", Optional.of(ITEM));
    c.open(items);
    assertThat(view.detail).isInstanceOf(MasterList.class);
    MasterList results = (MasterList) view.detail;
    assertThat(results.entries()).isNotEmpty().isEqualTo(view.masterList().entries());
    assertThat(results.heading()).isEqualTo(view.masterList().heading());

    results.selectIndex(0);
    results.commitSelected();
    assertThat(c.current()).isEqualTo(new ViewTarget.Entity(results.entries().get(0).ref()));
    assertThat(view.detail).isNotInstanceOf(MasterList.class);
    c.back();
    assertThat(c.current()).isEqualTo(items);
    assertThat(view.detail).isInstanceOf(MasterList.class);
  }

  @Test
  void inTheShellTheKindChipsFoldIntoAKindMenu() {
    shell.claim(search, new JPanel(), status);
    KindMenu menu = search.kindMenu();
    assertThat(SwingUtilities.isDescendingFrom(menu, search)).isTrue();
    assertThat(search.kindChips().getParent()).isNull();
    assertThat(menu.getText()).startsWith("All");

    JPopupMenu popup = menu.popup();
    assertThat(popup.getComponentCount()).isEqualTo(7);
    ((JRadioButtonMenuItem) popup.getComponent(1)).doClick();
    assertThat(search.kindChips().selected()).contains(ITEM);
    assertThat(menu.getText()).startsWith("Items");
    assertThat(menu.getAccessibleContext().getAccessibleName()).isEqualTo("Search kind: Items");

    search.setCompact(false);
    assertThat(menu.getParent()).isNull();
    assertThat(SwingUtilities.isDescendingFrom(search.kindChips(), search)).isTrue();
    assertThat(search.kindChips().chips().get(1).isSelected()).isTrue();
  }

  @Test
  void theButtonsRunTheirActionsAndFollowTheState() {
    shell.setHistoryState(true, false);
    assertThat(shell.backButton().isEnabled()).isTrue();
    assertThat(shell.forwardButton().isEnabled()).isFalse();
    shell.backButton().doClick();
    shell.setOnTop(true);
    assertThat(shell.pinButton().isSelected()).isTrue();
    shell.pinButton().doClick();
    assertThat(calls).containsExactly("back", "onTop");
    shell.setOnTop(false);
    assertThat(shell.pinButton().isSelected()).isFalse();
    assertThat(shell.pinButton().getAccessibleContext().getAccessibleName()).isEqualTo(CompactShell.PIN_NAME);
  }
}
