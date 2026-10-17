package dev.wowforever.app.ui;

import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.SearchHit;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.widgets.FakeDropdownHost;
import dev.wowforever.app.ui.widgets.HistoryButtons;
import dev.wowforever.app.ui.widgets.IconCache;
import dev.wowforever.app.ui.widgets.Keys;
import dev.wowforever.app.ui.widgets.KindChips;
import dev.wowforever.app.ui.widgets.ManualDelay;
import dev.wowforever.app.ui.widgets.SearchDropdown;
import dev.wowforever.app.ui.widgets.SearchDropdown.Row;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.AbstractButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
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

  /** Every counts-only lookup asked for, by text. */
  private final List<String> countLookups = new ArrayList<>();

  /** {@link #COUNTS} for "hog"; for other text, as many items as it has characters, so stale counts show. */
  private static Map<EntityKind, Integer> countsOf(String text) {
    return text.equals("hog") ? COUNTS : Map.of(ITEM, text.length());
  }

  private final SearchBar.Source source = new SearchBar.Source() {
    @Override
    public SearchBar.Lookup lookup(String text, Optional<EntityKind> kind) {
      lookups.add(text + " " + kind.map(Enum::name).orElse("ALL"));
      List<SearchHit> hits = HITS.stream().filter(h -> kind.isEmpty() || h.ref().kind() == kind.get()).toList();
      return new SearchBar.Lookup(hits, countsOf(text));
    }

    @Override
    public Map<EntityKind, Integer> counts(String text) {
      countLookups.add(text);
      return countsOf(text);
    }
  };

  private void attachInline() {
    bar.attach(source, Runnable::run);
  }

  private void attachQueued() {
    Executor later = queued::add;
    bar.attach(source, later);
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
  void searchBoxFollowsThePageWithoutSearchDropdownOrRecents() {
    attachQueued();
    typeAndWait("hog");
    assertThat(queued).hasSize(1);
    long edited = bar.editRevision();
    bar.followPageName("Edwin VanCleef");
    assertThat(bar.editRevision()).isEqualTo(edited);
    assertThat(bar.textField().getText()).isEqualTo("Edwin VanCleef");
    assertThat(delay.pending()).isZero();
    assertThat(host.showing).isFalse();
    runQueued(); // the older query must not reopen its popup or restore its counts
    assertThat(host.showing).isFalse();
    assertThat(state.recentSearches()).isEmpty();
    assertThat(countLookups).isEmpty();
    assertThat(lookups).containsExactly("hog ALL");
    assertThat(opened).isEmpty();
    delay.fire();
    assertThat(queued).isEmpty();
  }

  @Test
  void theDatabaseLookupWarmsTheIconsOfItsItemHits() {
    IconCache cache = new IconCache(FixtureDb.repositories().assets()::icon, 100, null);
    IconCache before = IconCache.install(cache);
    try {
      var found = SearchBar.Source.of(FixtureDb.repositories().search()).lookup("eye of sulfuras", Optional.empty());
      assertThat(found.hits()).anySatisfy(h -> assertThat(h.iconId()).isEqualTo(134124L));
    } finally {
      IconCache.install(before);
    }
    assertThat(cache.cached(134124L, IconCache.LIST)).as("warmed on the worker").isPresent();
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
    assertThat(state.recentSearches()).as("the hit opened, not the half-typed text").containsExactly("Hogger");
  }

  @Test
  void typeAheadAloneRemembersNothing() {
    attachInline();
    typeAndWait("westfa");
    typeAndWait("west");
    bar.focusChanged(false);
    assertThat(lookups).isNotEmpty();
    assertThat(state.recentSearches()).isEmpty();
  }

  @Test
  void anOpenedHitCarriesTheSearchForItsTextAndKind() {
    List<String> hits = new ArrayList<>();
    SearchBar withHits = new SearchBar(new SearchBar.Opener() {
      @Override
      public void accept(ViewTarget t) {
        hits.add("open " + t);
      }

      @Override
      public void openHit(ViewTarget.Entity hit, ViewTarget.Search search) {
        hits.add("hit " + hit.ref() + " for " + search);
      }
    }, state, delay, dropdown);
    withHits.attach(source, Runnable::run);
    withHits.focusChanged(true);
    withHits.textField().setText(" hog ");
    delay.fire();
    withHits.kindChips().chips().get(2).doClick(); // NPCs: the open dropdown re-filters
    Keys.press(withHits.textField(), "ENTER");
    assertThat(hits).containsExactly("hit " + new EntityRef(NPC, 448) + " for "
        + new ViewTarget.Search("hog", Optional.of(NPC)));
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
  void focusFallingIntoTheBoxAfterAPageSwapNeverOpensRecents() {
    attachInline();
    state.addRecentSearch("hogger");
    // Removing a focused page hands the focus on by traversal; activating the window restores it. Neither is a
    // deliberate entry into the search box.
    bar.focusChanged(true, FocusEvent.Cause.TRAVERSAL_FORWARD);
    assertThat(dropdown.isShowing()).isFalse();
    bar.focusChanged(false, FocusEvent.Cause.UNKNOWN);
    bar.focusChanged(true, FocusEvent.Cause.ACTIVATION);
    assertThat(dropdown.isShowing()).isFalse();
  }

  @Test
  void clickingIntoTheEmptyBoxShowsRecents() {
    attachInline();
    state.addRecentSearch("hogger");
    bar.focusChanged(true, FocusEvent.Cause.MOUSE_EVENT);
    assertThat(dropdown.isShowing()).isTrue();
    assertThat(dropdown.rows()).contains(new Row.Recent("hogger"));
  }

  @Test
  void clickingTheAlreadyFocusedEmptyBoxShowsRecents() {
    attachInline();
    state.addRecentSearch("hogger");
    // Focus came back through window activation: quiet. The click that follows is the deliberate entry.
    bar.focusChanged(true, FocusEvent.Cause.ACTIVATION);
    assertThat(dropdown.isShowing()).isFalse();
    bar.clicked();
    assertThat(dropdown.isShowing()).isTrue();
    bar.clicked();
    assertThat(dropdown.rows()).containsOnlyOnce(new Row.Recent("hogger"));
  }

  @Test
  void focusShortcutCountsAsADeliberateEntry() {
    attachInline();
    state.addRecentSearch("hogger");
    bar.focus();
    // Headless: requestFocusInWindow cannot succeed, so the flag is dropped and a stray focus event stays quiet.
    bar.focusChanged(true, FocusEvent.Cause.UNKNOWN);
    assertThat(dropdown.isShowing()).isFalse();
    bar.markFocusDeliberate();
    bar.focusChanged(true, FocusEvent.Cause.UNKNOWN);
    assertThat(dropdown.isShowing()).isTrue();
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
  void windowEscapeClosesDropdownThenPageFilterThenSearch() {
    class FilteredPage extends javax.swing.JPanel implements dev.wowforever.app.ui.pages.PageEscape {
      boolean focused = true;
      public boolean escape() {
        if (!focused) return false;
        focused = false;
        return true;
      }
    }
    FilteredPage page = new FilteredPage();
    attachInline();
    typeAndWait("hog");
    assertThat(MainFrame.escapeContent(bar, page)).isTrue();
    assertThat(dropdown.isShowing()).isFalse();
    assertThat(page.focused).isTrue();
    assertThat(bar.textField().getText()).isEqualTo("hog");
    assertThat(MainFrame.escapeContent(bar, page)).isTrue();
    assertThat(page.focused).isFalse();
    assertThat(bar.textField().getText()).isEqualTo("hog");
    assertThat(MainFrame.escapeContent(bar, page)).isTrue();
    assertThat(bar.textField().getText()).isEmpty();
    assertThat(MainFrame.escapeContent(bar, page)).isFalse();
  }

  @Test
  void escapeChainClosesTheDropdownThenClearsTheTextThenPassesOn() {
    state.addRecentSearch("deadmines");
    attachInline();
    typeAndWait("hog");
    assertThat(bar.escape()).isTrue();
    assertThat(dropdown.isShowing()).isFalse();
    assertThat(bar.textField().getText()).isEqualTo("hog");
    assertThat(bar.escape()).isTrue();
    assertThat(bar.textField().getText()).isEmpty();
    assertThat(dropdown.isShowing()).isFalse(); // an Esc clear does not reopen the recent searches
    assertThat(chipTexts()).first().isEqualTo("All");
    assertThat(bar.escape()).isFalse(); // the third Esc: nothing left, the window moves the focus to the list
    assertThat(opened).isEmpty();
  }

  @Test
  void escapeWithRecentsDropsThePendingLookupAndNeverReopensTheList() {
    state.addRecentSearch("deadmines");
    attachInline();
    bar.focusChanged(true);
    assertThat(dropdown.isShowing()).isTrue(); // the focused empty box shows the recents
    bar.textField().setText("hogg"); // typed before the debounce: the recents still show
    assertThat(delay.pending()).isEqualTo(1);
    assertThat(bar.escape()).isTrue();
    assertThat(dropdown.isShowing()).isFalse();
    assertThat(delay.pending()).isZero(); // the lookup cannot reopen the list a moment later
    delay.fire();
    assertThat(lookups).isEmpty();
    assertThat(bar.escape()).isTrue();
    assertThat(bar.textField().getText()).isEmpty();
    assertThat(dropdown.isShowing()).isFalse();
    assertThat(bar.escape()).isFalse();
    bar.textField().setText("d"); // typing after an Esc clear works as before
    bar.textField().setText("");
    assertThat(dropdown.isShowing()).isTrue();
  }

  @Test
  void escKeyInTheBoxDropsThePendingLookupSoTheListStaysClosed() {
    attachInline();
    typeAndWait("hog");
    bar.textField().setCaretPosition(3);
    bar.textField().replaceSelection("g"); // typed: the old hits still show while the debounce runs
    assertThat(bar.textField().getText()).isEqualTo("hogg");
    assertThat(dropdown.isShowing()).isTrue();
    assertThat(delay.pending()).isEqualTo(1);
    assertThat(press("ESCAPE")).isTrue();
    assertThat(dropdown.isShowing()).isFalse();
    assertThat(delay.pending()).isZero();
    delay.fire();
    assertThat(dropdown.isShowing()).isFalse();
    assertThat(lookups).containsExactly("hog ALL");
  }

  @Test
  void escapeClearsTextEvenWithoutTheFocus() {
    attachInline();
    bar.textField().setText("hogger");
    assertThat(bar.escape()).isTrue();
    assertThat(bar.textField().getText()).isEmpty();
    assertThat(dropdown.isShowing()).isFalse();
    assertThat(bar.escape()).isFalse();
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
    assertThat(state.recentSearches()).as("no Enter, Show all or hit: not a search to remember").isEmpty();
  }

  /** Every component under {@code c}, {@code c} included. */
  private static List<Component> tree(Component c) {
    List<Component> all = new ArrayList<>(List.of(c));
    if (c instanceof Container k) {
      for (Component child : k.getComponents()) {
        all.addAll(tree(child));
      }
    }
    return all;
  }

  @Test
  void noKindChipsInToolbar() {
    JToolBar toolbar = MainFrame.toolbar(HistoryButtons.back(), HistoryButtons.forward(), bar);
    assertThat(tree(toolbar)).noneMatch(KindChips.class::isInstance);
    assertThat(SwingUtilities.isDescendingFrom(bar.kindChips(), bar)).isFalse();
    // they are the dropdown's top row, shown with its hits
    attachInline();
    typeAndWait("hog");
    assertThat(bar.kindChips().isVisible()).isTrue();
    assertThat(bar.kindChips()).isSameAs(dropdown.kindChips());
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
    assertThat(state.recentSearches()).isEmpty();
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

  @Test
  void enterBeforeTheDebounceShowsTheCountsOfTheSearchedText() {
    attachInline();
    typeAndWait("hog");
    assertThat(chipTexts()).startsWith("All 6", "Items 4");
    bar.textField().setText("hogger");
    press("ENTER");
    assertThat(opened).containsExactly(new ViewTarget.Search("hogger", Optional.empty()));
    assertThat(countLookups).containsExactly("hogger");
    assertThat(chipTexts()).startsWith("All 6", "Items 6", "NPCs 0");
  }

  @Test
  void pastedNameShowsItsCounts() {
    attachInline();
    typeAndWait("hog");
    bar.textField().selectAll();
    assertThat(bar.pasteIntercepted("[Captain Sanders' Hidden Treasure]")).isTrue();
    assertThat(chipTexts()).startsWith("All 32", "Items 32");
  }

  @Test
  void chosenRecentSearchShowsItsCounts() {
    attachInline();
    state.addRecentSearch("deadmines");
    typeAndWait("hog");
    bar.textField().setText("");
    press("DOWN");
    press("ENTER");
    assertThat(chipTexts()).startsWith("All 9", "Items 9");
  }

  @Test
  void countsArriveUnderTheGenerationGuard() {
    attachQueued();
    bar.focusChanged(true);
    bar.textField().setText("hogger");
    press("ENTER"); // counts for "hogger" queued on the worker
    bar.textField().setText("rag"); // typing on overtakes them
    runQueued();
    assertThat(countLookups).containsExactly("hogger");
    assertThat(chipTexts()).startsWith("All", "Items");
  }

  @Test
  void enterRightAfterAKindChangeRunsTheFullSearchInsteadOfTheOldTopHit() {
    attachQueued();
    bar.focusChanged(true);
    bar.textField().setText("hog");
    delay.fire();
    runQueued();
    assertThat(dropdown.isShowing()).isTrue();
    bar.kindChips().chips().get(1).doClick(); // Items: the new rows are still on the worker
    press("ENTER");
    assertThat(opened).containsExactly(new ViewTarget.Search("hog", Optional.of(ITEM)));
  }
}
