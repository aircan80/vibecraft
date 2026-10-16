package dev.wowforever.app.ui;

import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.SearchHit;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.widgets.FakeDropdownHost;
import dev.wowforever.app.ui.widgets.Keys;
import dev.wowforever.app.ui.widgets.ManualDelay;
import dev.wowforever.app.ui.widgets.SearchDropdown;
import dev.wowforever.app.ui.widgets.SearchDropdown.Row;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.AbstractButton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SearchBarTest {
  private final Preferences node = UiStateTest.testNode();
  private final UiState state = new UiState(node);
  private final List<ViewTarget> opened = new ArrayList<>();
  private final ManualDelay delay = new ManualDelay();
  private final FakeDropdownHost host = new FakeDropdownHost();
  private final SearchDropdown dropdown = new SearchDropdown(host);
  private final SearchBar bar = new SearchBar(opened::add, state, delay, dropdown);
  /** Every type-ahead lookup asked for, as "text kind". */
  private final List<String> lookups = new ArrayList<>();
  /** Lookups waiting for {@link #runQueued()}; used by the tests that hold the worker back. */
  private final List<Runnable> queued = new ArrayList<>();

  private static final List<SearchHit> HITS = List.of(
      new SearchHit(new EntityRef(NPC, 448), "Hogger", "NPC · Elite · Level 11"),
      new SearchHit(new EntityRef(ITEM, 1), "Hogger Tusk", "Junk"),
      new SearchHit(new EntityRef(ZONE, 40), "Hogger Hill", "Zone"));
  private static final Map<EntityKind, Integer> COUNTS = Map.of(NPC, 1, ITEM, 4, ZONE, 1);

  @AfterEach
  void removeNode() throws BackingStoreException {
    node.removeNode();
  }

  private SearchBar.Lookup lookup(String text, Optional<EntityKind> kind) {
    lookups.add(text + " " + kind.map(Enum::name).orElse("ALL"));
    List<SearchHit> hits = HITS.stream().filter(h -> kind.isEmpty() || h.ref().kind() == kind.get()).toList();
    return new SearchBar.Lookup(hits, COUNTS);
  }

  private void attachInline() {
    bar.attach(this::lookup, Runnable::run);
  }

  private void attachQueued() {
    Executor later = queued::add;
    bar.attach(this::lookup, later);
  }

  private void runQueued() {
    List<Runnable> due = List.copyOf(queued);
    queued.clear();
    due.forEach(Runnable::run);
  }

  /** Types {@code text} into the focused field and lets the debounce elapse. */
  private void typeAndWait(String text) {
    bar.focusChanged(true);
    bar.textField().setText(text);
    delay.fire();
  }

  private boolean press(String key) {
    return Keys.press(bar.textField(), key);
  }

  private List<String> chipTexts() {
    return bar.kindChips().chips().stream().map(AbstractButton::getText).toList();
  }

  @Test
  void typingWaitsForTheDebounceThenShowsHitsAndCounts() {
    attachInline();
    bar.focusChanged(true);
    bar.textField().setText("  hog ");
    assertThat(lookups).isEmpty();
    assertThat(delay.pending()).isEqualTo(1);
    delay.fire();
    assertThat(lookups).containsExactly("hog ALL");
    assertThat(dropdown.isShowing()).isTrue();
    assertThat(dropdown.rows()).filteredOn(r -> r instanceof Row.Hit).hasSize(3);
    assertThat(dropdown.rows()).last().isEqualTo(new Row.ShowAll(6));
    assertThat(chipTexts()).startsWith("All 6", "Items 4", "NPCs 1");
  }

  @Test
  void eachKeystrokeRestartsTheDebounce() {
    attachInline();
    bar.focusChanged(true);
    bar.textField().setText("h");
    bar.textField().setText("ho");
    bar.textField().setText("hog");
    assertThat(delay.pending()).isEqualTo(1);
    delay.fire();
    assertThat(lookups).containsExactly("hog ALL");
  }

  @Test
  void resultsWhileTheFieldIsNotFocusedUpdateTheChipsButOpenNothing() {
    attachInline();
    bar.textField().setText("hog");
    delay.fire();
    assertThat(dropdown.isShowing()).isFalse();
    assertThat(chipTexts()).startsWith("All 6");
  }

  @Test
  void enterOpensTheTopHitAndRemembersTheSearch() {
    attachInline();
    typeAndWait("hog");
    press("ENTER");
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(NPC, 448)));
    assertThat(dropdown.isShowing()).isFalse();
    assertThat(state.recentSearches()).containsExactly("hog");
  }

  @Test
  void enterBeforeTheHitsArriveRunsTheFullSearch() {
    attachInline();
    bar.focusChanged(true);
    bar.textField().setText(" hog ");
    press("ENTER");
    assertThat(opened).containsExactly(new ViewTarget.Search("hog", Optional.empty()));
    assertThat(delay.pending()).isZero();
    assertThat(lookups).isEmpty();
    assertThat(state.recentSearches()).containsExactly("hog");
  }

  @Test
  void enterAfterTypingPastTheShownHitsRunsTheFullSearchForTheNewText() {
    attachInline();
    typeAndWait("hog");
    bar.textField().setText("hogger");
    press("ENTER");
    assertThat(opened).containsExactly(new ViewTarget.Search("hogger", Optional.empty()));
  }

  @Test
  void showAllRowAndShiftEnterRunTheFullSearch() {
    attachInline();
    typeAndWait("hog");
    press("shift ENTER");
    typeAndWait("tusk");
    press("UP"); // nothing selected: stays in the field
    for (int i = 0; i < 10; i++) {
      press("DOWN"); // to the last row: Show all
    }
    press("ENTER");
    assertThat(opened).containsExactly(new ViewTarget.Search("hog", Optional.empty()),
        new ViewTarget.Search("tusk", Optional.empty()));
    assertThat(state.recentSearches()).containsExactly("tusk", "hog");
  }

  @Test
  void blankTextNeverSearches() {
    attachInline();
    bar.focusChanged(true);
    bar.textField().setText("   ");
    delay.fire();
    press("ENTER");
    press("shift ENTER");
    assertThat(lookups).isEmpty();
    assertThat(opened).isEmpty();
    assertThat(state.recentSearches()).isEmpty();
  }

  @Test
  void lateResultsForOlderTextAreDropped() {
    attachQueued();
    bar.focusChanged(true);
    bar.textField().setText("hog");
    delay.fire();
    bar.textField().setText("rag");
    runQueued();
    assertThat(lookups).containsExactly("hog ALL");
    assertThat(dropdown.isShowing()).isFalse();
    assertThat(chipTexts()).startsWith("All", "Items");
  }

  @Test
  void clearingTheTextShowsRecentsAndClearsTheCounts() {
    attachInline();
    state.addRecentSearch("deadmines");
    state.addRecentSearch("hogger");
    typeAndWait("hog");
    bar.textField().setText("");
    assertThat(dropdown.rows()).containsExactly(new Row.Heading("Recent"), new Row.Recent("hogger"),
        new Row.Recent("deadmines"));
    assertThat(chipTexts()).startsWith("All", "Items");
    assertThat(delay.pending()).isZero();
  }

  @Test
  void focusingTheEmptyFieldShowsRecentsAndLosingFocusHidesThem() {
    attachInline();
    state.addRecentSearch("hogger");
    bar.focusChanged(true);
    assertThat(dropdown.isShowing()).isTrue();
    assertThat(dropdown.rows()).contains(new Row.Recent("hogger"));
    bar.focusChanged(false);
    assertThat(dropdown.isShowing()).isFalse();
  }

  @Test
  void focusingWithoutRecentsShowsNothing() {
    bar.focusChanged(true);
    assertThat(dropdown.isShowing()).isFalse();
  }

  @Test
  void choosingARecentSearchRunsIt() {
    attachInline();
    state.addRecentSearch("deadmines");
    bar.focusChanged(true);
    press("DOWN");
    press("ENTER");
    assertThat(bar.textField().getText()).isEqualTo("deadmines");
    assertThat(opened).containsExactly(new ViewTarget.Search("deadmines", Optional.empty()));
    assertThat(dropdown.isShowing()).isFalse();
    assertThat(delay.pending()).isZero();
  }

  @Test
  void escClosesTheDropdown() {
    attachInline();
    typeAndWait("hog");
    assertThat(press("ESCAPE")).isTrue();
    assertThat(dropdown.isShowing()).isFalse();
    assertThat(bar.textField().getText()).isEqualTo("hog");
  }

  @Test
  void aKindChipFiltersTheOpenDropdownAndTheCommittedSearch() {
    attachInline();
    typeAndWait("hog");
    bar.kindChips().chips().get(2).doClick(); // NPCs
    assertThat(lookups).containsExactly("hog ALL", "hog NPC");
    assertThat(dropdown.isShowing()).isTrue();
    assertThat(dropdown.rows()).filteredOn(r -> r instanceof Row.Hit).hasSize(1);
    assertThat(dropdown.rows()).last().isEqualTo(new Row.ShowAll(1));
    assertThat(opened).isEmpty();
    press("shift ENTER");
    assertThat(opened).containsExactly(new ViewTarget.Search("hog", Optional.of(NPC)));
  }

  @Test
  void aKindChipWithTheDropdownClosedReRunsTheSearchAtOnce() {
    attachInline();
    bar.textField().setText("hog");
    bar.kindChips().chips().get(1).doClick(); // Items
    assertThat(opened).containsExactly(new ViewTarget.Search("hog", Optional.of(ITEM)));
    assertThat(delay.pending()).isZero();
  }

  @Test
  void aKindChipWithBlankTextDoesNothing() {
    attachInline();
    bar.kindChips().chips().get(1).doClick();
    assertThat(opened).isEmpty();
    assertThat(lookups).isEmpty();
  }

  @Test
  void beforeAttachTypingLooksNothingUp() {
    bar.focusChanged(true);
    bar.textField().setText("hog");
    delay.fire();
    assertThat(dropdown.isShowing()).isFalse();
    press("ENTER");
    assertThat(opened).containsExactly(new ViewTarget.Search("hog", Optional.empty()));
  }

  @Test
  void pastedLinkOpensTheItem() {
    attachInline();
    assertThat(bar.pasteIntercepted(
        "|cffa335ee|Hitem:19019:0:0:0:0:0:0:0:60|h[Thunderfury, Blessed Blade of the Windseeker]|h|r")).isTrue();
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(ITEM, 19019)));
    assertThat(bar.textField().getText()).isEmpty();
    assertThat(dropdown.isShowing()).isFalse();
  }

  @Test
  void pastedBracketedNameSearchesForIt() {
    attachInline();
    bar.kindChips().select(Optional.of(ITEM));
    assertThat(bar.pasteIntercepted("[Captain Sanders' Hidden Treasure]")).isTrue();
    assertThat(bar.textField().getText()).isEqualTo("Captain Sanders' Hidden Treasure");
    assertThat(opened).containsExactly(new ViewTarget.Search("Captain Sanders' Hidden Treasure", Optional.of(ITEM)));
    assertThat(delay.pending()).isZero();
  }

  @Test
  void plainTextOrAPasteIntoTypedTextIsAnOrdinaryPaste() {
    attachInline();
    assertThat(bar.pasteIntercepted("hogger")).isFalse();
    bar.textField().setText("level ");
    bar.textField().setCaretPosition(6);
    assertThat(bar.pasteIntercepted("19019")).isFalse();
    bar.textField().selectAll();
    assertThat(bar.pasteIntercepted("19019")).isTrue();
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(ITEM, 19019)));
  }

  @Test
  void disablingDisablesTheFieldAndChips() {
    bar.setEnabled(false);
    assertThat(bar.textField().isEnabled()).isFalse();
    assertThat(bar.kindChips().chips()).noneMatch(AbstractButton::isEnabled);
    bar.setEnabled(true);
    assertThat(bar.kindChips().chips()).allMatch(AbstractButton::isEnabled);
  }
}
