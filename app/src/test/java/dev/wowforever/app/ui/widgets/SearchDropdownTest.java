package dev.wowforever.app.ui.widgets;

import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.QUEST;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.ui.widgets.SearchDropdown.Row;
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
}
