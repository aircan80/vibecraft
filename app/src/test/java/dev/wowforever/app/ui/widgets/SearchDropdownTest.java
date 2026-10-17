package dev.wowforever.app.ui.widgets;

import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.QUEST;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatLaf;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.ui.theme.QualityColors;
import dev.wowforever.app.ui.widgets.SearchDropdown.Row;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class SearchDropdownTest {

  /** Records what the dropdown asked for. */
  static final class Recorder implements SearchDropdown.Listener {
    final List<String> calls = new ArrayList<>();

    @Override
    public void open(ListEntry hit) {
      calls.add("open " + hit.title());
    }

    @Override
    public void showAll() {
      calls.add("show all");
    }

    @Override
    public void searchRecent(String text) {
      calls.add("recent " + text);
    }
  }

  private final FakeDropdownHost host = new FakeDropdownHost();
  private final Recorder listener = new Recorder();
  private final JTextField field = new JTextField();
  private final SearchDropdown dropdown = new SearchDropdown(host);

  {
    dropdown.install(field, listener);
  }

  private static ListEntry hit(EntityKind kind, long id, String name) {
    return new ListEntry(new EntityRef(kind, id), name, kind.label(), null, null);
  }

  private boolean press(String key) {
    return Keys.press(field, key);
  }

  static String describe(Row row) {
    return switch (row) {
      case Row.Heading h -> "# " + h.text();
      case Row.Hit h -> h.entry().title();
      case Row.Recent r -> "recent " + r.text();
      case Row.ShowAll s -> "show all " + s.total();
    };
  }

  private List<String> rows() {
    return dropdown.rows().stream().map(SearchDropdownTest::describe).toList();
  }

  @Test
  void topEightHitsGroupedByKindInRankOrderThenShowAll() {
    List<ListEntry> hits = List.of(hit(INSTANCE, 1, "Deadmines"), hit(ITEM, 2, "Cruel Barb"),
        hit(NPC, 3, "Edwin VanCleef"), hit(ITEM, 4, "Smelting Pants"), hit(ZONE, 5, "Westfall"),
        hit(ITEM, 6, "Blackened Defias Belt"), hit(QUEST, 7, "The Defias Brotherhood"), hit(NPC, 8, "Mr. Smite"),
        hit(ITEM, 9, "Ninth Item"), hit(ITEM, 10, "Tenth Item"));
    dropdown.showHits(hits, 22);
    assertThat(rows()).containsExactly(
        "# Dungeons & Raids", "Deadmines",
        "# Items", "Cruel Barb", "Smelting Pants", "Blackened Defias Belt",
        "# NPCs", "Edwin VanCleef", "Mr. Smite",
        "# Zones", "Westfall",
        "# Quests", "The Defias Brotherhood",
        "show all 22");
    assertThat(dropdown.isShowing()).isTrue();
    assertThat(host.showing).isTrue();
    assertThat(dropdown.selected()).isEmpty();
  }

  @Test
  void showAllNeverCountsFewerThanShown() {
    dropdown.showHits(List.of(hit(ITEM, 1, "A"), hit(ITEM, 2, "B")), 1);
    assertThat(rows()).last().isEqualTo("show all 2");
  }

  @Test
  void noHitsSaysSoWithoutRows() {
    dropdown.showHits(List.of(), 0);
    assertThat(rows()).containsExactly("# No matches");
    assertThat(press("DOWN")).isTrue();
    assertThat(dropdown.selected()).isEmpty();
    press("ENTER"); // nothing to open: the full search runs
    assertThat(listener.calls).containsExactly("show all");
  }

  @Test
  void enterOpensTheTopHit() {
    dropdown.showHits(List.of(hit(ITEM, 1, "Cruel Barb"), hit(NPC, 2, "Hogger")), 2);
    assertThat(press("ENTER")).isTrue();
    assertThat(listener.calls).containsExactly("open Cruel Barb");
    assertThat(dropdown.isShowing()).isFalse();
    assertThat(host.showing).isFalse();
  }

  @Test
  void enterWhileClosedRunsTheFullSearch() {
    assertThat(press("ENTER")).isTrue();
    assertThat(listener.calls).containsExactly("show all");
  }

  @Test
  void enterAfterTheTextChangedRunsTheFullSearchUnlessARowWasChosen() {
    dropdown.showHits(List.of(hit(ITEM, 1, "Cruel Barb"), hit(NPC, 2, "Hogger")), 2);
    dropdown.markStale();
    press("ENTER");
    assertThat(listener.calls).containsExactly("show all");

    listener.calls.clear();
    dropdown.showHits(List.of(hit(ITEM, 1, "Cruel Barb"), hit(NPC, 2, "Hogger")), 2);
    dropdown.markStale();
    press("DOWN");
    press("DOWN");
    press("ENTER");
    assertThat(listener.calls).containsExactly("open Hogger");
  }

  @Test
  void markingStaleDropsTheSelection() {
    dropdown.showHits(List.of(hit(ITEM, 1, "Cruel Barb")), 1);
    press("DOWN");
    dropdown.markStale();
    assertThat(dropdown.selected()).isEmpty();
  }

  @Test
  void shiftEnterAlwaysRunsTheFullSearch() {
    dropdown.showHits(List.of(hit(ITEM, 1, "Cruel Barb")), 1);
    press("shift ENTER");
    assertThat(listener.calls).containsExactly("show all");
    assertThat(dropdown.isShowing()).isFalse();
  }

  @Test
  void upAndDownSkipHeadingsAndStopAtTheEnds() {
    dropdown.showHits(List.of(hit(ITEM, 1, "Cruel Barb"), hit(NPC, 2, "Hogger")), 5);
    press("DOWN");
    assertThat(dropdown.selected()).map(SearchDropdownTest::describe).contains("Cruel Barb");
    press("DOWN");
    assertThat(dropdown.selected()).map(SearchDropdownTest::describe).contains("Hogger");
    press("DOWN");
    press("DOWN");
    assertThat(dropdown.selected()).map(SearchDropdownTest::describe).contains("show all 5");
    press("UP");
    press("UP");
    press("UP");
    assertThat(dropdown.selected()).isEmpty(); // above the first row is the field again
    press("DOWN");
    press("DOWN");
    press("DOWN");
    press("ENTER");
    assertThat(listener.calls).containsExactly("show all");
  }

  @Test
  void escClosesAndIsLeftAloneWhileClosed() {
    dropdown.showHits(List.of(hit(ITEM, 1, "Cruel Barb")), 1);
    assertThat(press("ESCAPE")).isTrue();
    assertThat(dropdown.isShowing()).isFalse();
    assertThat(host.showing).isFalse();
    assertThat(press("ESCAPE")).isFalse(); // passes on to the window's own Esc handling
    assertThat(press("DOWN")).isFalse();
    assertThat(press("UP")).isFalse();
    assertThat(listener.calls).isEmpty();
  }

  @Test
  void recentsUnderARecentHeading() {
    dropdown.showRecents(List.of("hogger", "deadmines"));
    assertThat(rows()).containsExactly("# Recent", "recent hogger", "recent deadmines");
    press("DOWN");
    press("DOWN");
    press("ENTER");
    assertThat(listener.calls).containsExactly("recent deadmines");
  }

  @Test
  void enterOnRecentsWithoutASelectionRunsTheFullSearch() {
    dropdown.showRecents(List.of("hogger"));
    press("ENTER");
    assertThat(listener.calls).containsExactly("show all");
  }

  @Test
  void noRecentsHidesTheDropdown() {
    dropdown.showHits(List.of(hit(ITEM, 1, "Cruel Barb")), 1);
    dropdown.showRecents(List.of());
    assertThat(dropdown.isShowing()).isFalse();
    assertThat(rows()).isEmpty();
  }

  @Test
  void manyHitsShowEightAndCountAll() {
    List<ListEntry> hits = IntStream.rangeClosed(1, 30).mapToObj(i -> hit(ITEM, i, "Item " + i)).toList();
    dropdown.showHits(hits, 30);
    assertThat(dropdown.rows()).filteredOn(r -> r instanceof Row.Hit).hasSize(SearchDropdown.MAX_HITS);
    assertThat(rows()).first().isEqualTo("# Items");
    assertThat(rows()).last().isEqualTo("show all 30");
  }

  @Test
  void hidingClearsTheSelection() {
    dropdown.showHits(List.of(hit(ITEM, 1, "Cruel Barb")), 1);
    press("DOWN");
    dropdown.hide();
    assertThat(dropdown.isShowing()).isFalse();
    assertThat(dropdown.selected()).isEmpty();
  }

  @Test
  void rendersEveryRowKind() {
    dropdown.showHits(List.of(hit(ITEM, 1, "Cruel Barb")), 3);
    var list = dropdown.jlist();
    for (int i = 0; i < list.getModel().getSize(); i++) {
      var c = list.getCellRenderer().getListCellRendererComponent(list, list.getModel().getElementAt(i), i, false,
          false);
      assertThat(c.getPreferredSize().height).isPositive();
    }
    var showAll = list.getCellRenderer().getListCellRendererComponent(list, new Row.ShowAll(1_234), 0, false, false);
    assertThat(showAll).isInstanceOfSatisfying(javax.swing.JLabel.class,
        l -> assertThat(l.getText()).isEqualTo("Show all 1,234 results"));
    var one = list.getCellRenderer().getListCellRendererComponent(list, new Row.ShowAll(1), 0, false, false);
    assertThat(one).isInstanceOfSatisfying(javax.swing.JLabel.class,
        l -> assertThat(l.getText()).isEqualTo("Show 1 result"));
  }

  @Test
  void anItemHitShowsItsIconAndQualityColour() {
    MasterListTest.withIcons(() -> {
      dropdown.showHits(List.of(new ListEntry(new EntityRef(ITEM, 17204), "Eye of Sulfuras", "Item", 5, 134124L)),
          1);
      var list = dropdown.jlist();
      int row = dropdown.rows().indexOf(dropdown.rows().stream().filter(Row.Hit.class::isInstance).findFirst()
          .orElseThrow());
      var c = list.getCellRenderer().getListCellRendererComponent(list, list.getModel().getElementAt(row), row,
          false, false);
      assertThat(c).isInstanceOfSatisfying(MasterList.EntryRenderer.class, r -> {
        assertThat(r.title().getForeground()).isEqualTo(QualityColors.text(5, FlatLaf.isLafDark()));
        assertThat(r.icon().getIcon()).isSameAs(IconCache.shared().cached(134124L, IconCache.LIST).orElseThrow());
      });
    }, 134124L);
  }

  /** The summed preferred heights of the rows shown, as the list lays them out. */
  private int rowHeights() {
    var list = dropdown.jlist();
    int sum = 0;
    for (int i = 0; i < list.getModel().getSize(); i++) {
      sum += list.getCellRenderer().getListCellRendererComponent(list, list.getModel().getElementAt(i), i, false,
          false).getPreferredSize().height;
    }
    return sum;
  }

  @Test
  void heightFitsEveryRowWithoutScrollingOrPadding() {
    List<ListEntry> hits = List.of(hit(INSTANCE, 1, "Deadmines"), hit(ITEM, 2, "Cruel Barb"),
        hit(NPC, 3, "Edwin VanCleef"), hit(ITEM, 4, "Smelting Pants"), hit(ZONE, 5, "Westfall"),
        hit(ITEM, 6, "Blackened Defias Belt"), hit(QUEST, 7, "The Defias Brotherhood"), hit(NPC, 8, "Mr. Smite"));
    dropdown.showHits(hits, 22);
    int border = 2; // a one-pixel line above and below
    assertThat(host.height).isEqualTo(rowHeights() + border);
    // eight two-line hits are taller than eight rows of the first (heading) row, the old scroll pane height
    var list = dropdown.jlist();
    int headingHeight = list.getCellRenderer().getListCellRendererComponent(list, dropdown.rows().get(0), 0, false,
        false).getPreferredSize().height;
    assertThat(host.height).isGreaterThan(8 * headingHeight + border);

    dropdown.showHits(List.of(hit(ITEM, 1, "Cruel Barb")), 1);
    assertThat(dropdown.rows()).hasSize(3);
    assertThat(host.height).isEqualTo(rowHeights() + border);
  }

  @Test
  void heightIsCapped() {
    dropdown.showRecents(IntStream.rangeClosed(1, 60).mapToObj(i -> "search " + i).toList());
    assertThat(rowHeights()).isGreaterThan(com.formdev.flatlaf.util.UIScale.scale(SearchDropdown.MAX_HEIGHT));
    assertThat(host.height).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(SearchDropdown.MAX_HEIGHT));
  }

  @Test
  void notShowingWhenTheHostCannotShowIt() {
    host.anchorShowing = false;
    dropdown.showHits(List.of(hit(ITEM, 1, "Cruel Barb")), 1);
    assertThat(dropdown.isShowing()).isFalse();
    assertThat(Keys.press(field, "ESCAPE")).isFalse();
    press("ENTER"); // nothing on screen to open: the full search runs
    assertThat(listener.calls).containsExactly("show all");
  }

  @Test
  void theDropdownStaysOnTheAnchorsScreen() {
    Rectangle screen = new Rectangle(1920, 0, 2560, 1440);
    assertThat(SearchDropdown.left(2000, 420, screen)).isEqualTo(2000);
    // the compact window at the top right: a field starting 300 px from the edge cannot fit 420 px of dropdown
    assertThat(SearchDropdown.left(1920 + 2560 - 300, 420, screen)).isEqualTo(1920 + 2560 - 420);
    // wider than the screen: it starts at the screen's left edge
    assertThat(SearchDropdown.left(1950, 3000, screen)).isEqualTo(1920);
  }
}
