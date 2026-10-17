package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.SourceFilter;
import java.awt.Rectangle;
import java.util.List;
import java.util.stream.IntStream;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UiStateTest {
  /** A throw-away node of its own, so tests never share settings (all preferences are in memory in tests). */
  static Preferences testNode() {
    return Preferences.userRoot().node("dev/wowforever/test/ui-state-" + UUID.randomUUID());
  }

  private final Preferences node = testNode();

  @AfterEach
  void removeNode() throws BackingStoreException {
    node.removeNode();
  }

  @Test
  void dividerDefaultsTo300LogicalPixels() {
    assertThat(new UiState(node).dividerLocation()).isEqualTo(UIScale.scale(300));
  }

  @Test
  void dividerRoundTrips() {
    new UiState(node).setDividerLocation(412);
    assertThat(new UiState(node).dividerLocation()).isEqualTo(412);
  }

  @Test
  void nonPositiveDividerIsIgnored() {
    UiState state = new UiState(node);
    state.setDividerLocation(350);
    state.setDividerLocation(0);
    state.setDividerLocation(-1);
    assertThat(state.dividerLocation()).isEqualTo(350);
  }

  @Test
  void sourceFilterDefaultsToAllAndRoundTrips() {
    assertThat(new UiState(node).sourceFilter()).isEqualTo(SourceFilter.ALL);
    new UiState(node).setSourceFilter(SourceFilter.HIDE_ATLASLOOT_ONLY);
    assertThat(new UiState(node).sourceFilter()).isEqualTo(SourceFilter.HIDE_ATLASLOOT_ONLY);
    new UiState(node).setSourceFilter(SourceFilter.HIDE_QUESTIE_ONLY);
    assertThat(new UiState(node).sourceFilter()).isEqualTo(SourceFilter.HIDE_QUESTIE_ONLY);
    new UiState(node).setSourceFilter(SourceFilter.ALL);
    assertThat(new UiState(node).sourceFilter()).isEqualTo(SourceFilter.ALL);
  }

  @Test
  void routesShownDefaultsToOffAndRoundTrips() {
    UiState state = new UiState(node);
    assertThat(state.routesShown()).isFalse();
    state.setRoutesShown(true);
    assertThat(new UiState(node).routesShown()).isTrue();
    new UiState(node).routesSetting().set(false);
    assertThat(state.routesShown()).isFalse();
  }

  @Test
  void questPinsShownDefaultsToOffAndRoundTrips() {
    assertThat(new UiState(node).questPinsShown()).isFalse();
    new UiState(node).setQuestPinsShown(true);
    assertThat(new UiState(node).questPinsShown()).isTrue();
    BooleanSetting setting = new UiState(node).questPinsSetting();
    assertThat(setting.isOn()).isTrue();
    setting.set(false);
    assertThat(new UiState(node).questPinsShown()).isFalse();
    new UiState(node).questPinsSetting().set(true);
    assertThat(setting.isOn()).as("reads the saved value").isTrue();
  }

  @Test
  void aGarbledSourceFilterReadsAsAll() {
    node.put(UiState.SOURCE_FILTER, "HIDE_EVERYTHING");
    assertThat(new UiState(node).sourceFilter()).isEqualTo(SourceFilter.ALL);
  }

  @Test
  void recentSearchesStartEmpty() {
    assertThat(new UiState(node).recentSearches()).isEmpty();
  }

  @Test
  void recentSearchesAreMostRecentFirstTrimmedAndDeduplicated() {
    UiState state = new UiState(node);
    state.addRecentSearch(" hogger ");
    state.addRecentSearch("deadmines");
    state.addRecentSearch("  ");
    state.addRecentSearch(null);
    state.addRecentSearch("Hogger");
    assertThat(new UiState(node).recentSearches()).containsExactly("Hogger", "deadmines");
  }

  @Test
  void recentSearchesKeepTheLastTen() {
    UiState state = new UiState(node);
    IntStream.rangeClosed(1, 12).forEach(i -> state.addRecentSearch("search " + i));
    assertThat(state.recentSearches()).hasSize(UiState.RECENT_SEARCHES).first().isEqualTo("search 12");
    assertThat(state.recentSearches()).last().isEqualTo("search 3");
  }

  @Test
  void recentSearchKeepsApostrophesAndFoldsLineBreaks() {
    UiState state = new UiState(node);
    state.addRecentSearch("Captain Sanders' Hidden\nTreasure");
    assertThat(state.recentSearches()).containsExactly("Captain Sanders' Hidden Treasure");
  }

  @Test
  void freshStateHasTheDefaults() {
    UiState state = new UiState(node);
    assertThat(state.bounds()).isNull();
    assertThat(state.maximised()).isFalse();
    assertThat(state.compactBounds()).isNull();
    assertThat(state.compactMode()).isFalse();
    assertThat(state.alwaysOnTop()).isFalse();
    assertThat(state.textSizePct()).isEqualTo(100);
    assertThat(state.pinned()).isEmpty();
    assertThat(state.listVisible()).as("the list is not pinned open: it collapses when a result opens").isFalse();
  }

  @Test
  void everyFieldRoundTrips() {
    UiState state = new UiState(node);
    state.setBounds(new Rectangle(10, 20, 1100, 700));
    state.setMaximised(true);
    state.setDividerLocation(377);
    state.setCompactBounds(new Rectangle(1900, 40, 420, 720));
    state.setCompactMode(true);
    state.setAlwaysOnTop(true);
    state.setTextSizePct(130);
    state.setPinned(List.of("item:19019", "npc:448"));
    state.addRecentSearch("hogger");
    state.setListVisible(false);

    UiState again = new UiState(node);
    assertThat(again.bounds()).isEqualTo(new Rectangle(10, 20, 1100, 700));
    assertThat(again.maximised()).isTrue();
    assertThat(again.dividerLocation()).isEqualTo(377);
    assertThat(again.compactBounds()).isEqualTo(new Rectangle(1900, 40, 420, 720));
    assertThat(again.compactMode()).isTrue();
    assertThat(again.alwaysOnTop()).isTrue();
    assertThat(again.textSizePct()).isEqualTo(130);
    assertThat(again.pinned()).containsExactly("item:19019", "npc:448");
    assertThat(again.recentSearches()).containsExactly("hogger");
    assertThat(again.listVisible()).isFalse();
  }

  @Test
  void clearingTheBoundsRemovesThem() {
    UiState state = new UiState(node);
    state.setBounds(new Rectangle(1, 2, 300, 400));
    state.setCompactBounds(new Rectangle(5, 6, 300, 400));
    state.setBounds(null);
    state.setCompactBounds(null);
    assertThat(state.bounds()).isNull();
    assertThat(state.compactBounds()).isNull();
  }

  @Test
  void textSizeIsClampedAndSnappedToTenPercentSteps() {
    UiState state = new UiState(node);
    state.setTextSizePct(200);
    assertThat(state.textSizePct()).isEqualTo(150);
    state.setTextSizePct(40);
    assertThat(state.textSizePct()).isEqualTo(90);
    state.setTextSizePct(114);
    assertThat(state.textSizePct()).isEqualTo(110);
    node.putInt(UiState.TEXT_SIZE, 999); // a hand-edited preference
    assertThat(state.textSizePct()).isEqualTo(150);
  }

  @Test
  void garbledSavedValuesReadAsDefaults() {
    node.put(UiState.BOUNDS, "not,a,rectangle");
    node.put(UiState.COMPACT_BOUNDS, "1,2,3");
    UiState state = new UiState(node);
    assertThat(state.bounds()).isNull();
    assertThat(state.compactBounds()).isNull();
  }

  @Test
  void pinTogglesAKeyInAndOut() {
    UiState state = new UiState(node);
    assertThat(state.togglePinned("item:19019")).isTrue();
    assertThat(state.togglePinned("npc:448")).isTrue();
    assertThat(state.pinned()).containsExactly("item:19019", "npc:448");
    assertThat(state.togglePinned("item:19019")).isFalse();
    assertThat(state.pinned()).containsExactly("npc:448");
  }

  @Test
  void pinnedKeepsTheNewestFifty() {
    UiState state = new UiState(node);
    IntStream.rangeClosed(1, 60).forEach(i -> state.togglePinned("item:" + i));
    assertThat(state.pinned()).hasSize(UiState.MAX_PINNED).first().isEqualTo("item:11");
    assertThat(state.pinned()).last().isEqualTo("item:60");
  }

  @Test
  void pinnedThatWouldOverflowAPreferenceDropsTheOldest() {
    UiState state = new UiState(node);
    List<String> long300 = IntStream.rangeClosed(1, 50).mapToObj(i -> i + ":" + "x".repeat(300)).toList();
    state.setPinned(long300); // 50 × 300 characters is far past a preference's 8,192
    List<String> kept = new UiState(node).pinned();
    assertThat(kept).isNotEmpty().last().isEqualTo(long300.get(49));
    assertThat(String.join("\n", kept).length()).isLessThanOrEqualTo(Preferences.MAX_VALUE_LENGTH);
  }

  @Test
  void savedBoundsOnAScreenAreKept() {
    UiState state = new UiState(node);
    state.setBounds(new Rectangle(2000, 100, 1000, 700));
    List<Rectangle> screens = List.of(new Rectangle(0, 0, 1920, 1080), new Rectangle(1920, 0, 2560, 1440));
    assertThat(state.windowBounds(screens)).isEqualTo(new Rectangle(2000, 100, 1000, 700));
  }

  @Test
  void offscreenBoundsAreReset() {
    UiState state = new UiState(node);
    state.setBounds(new Rectangle(5000, 3000, 1000, 700)); // a screen that is no longer attached
    List<Rectangle> screens = List.of(new Rectangle(0, 0, 1920, 1080), new Rectangle(-1280, 0, 1280, 1024));
    assertThat(state.windowBounds(screens)).isEqualTo(new Rectangle(320, 140, 1280, 800));
    // only the bottom of the window is on a screen: its title bar is out of reach, so it is reset too
    state.setBounds(new Rectangle(100, -650, 1000, 700));
    assertThat(state.windowBounds(screens)).isEqualTo(new Rectangle(320, 140, 1280, 800));
  }

  @Test
  void aWindowWhoseTitleBarPartlyShowsIsMovedWhollyOnScreen() {
    UiState state = new UiState(node);
    List<Rectangle> screens = List.of(new Rectangle(0, 0, 1920, 1080));
    state.setBounds(new Rectangle(1800, 1050, 1000, 700)); // 120 × 30 px of the top strip on screen
    assertThat(state.windowBounds(screens)).isEqualTo(new Rectangle(920, 380, 1000, 700));
    state.setBounds(new Rectangle(1800, 1085, 1000, 700)); // the top strip starts below the screen
    assertThat(state.windowBounds(screens)).isEqualTo(new Rectangle(320, 140, 1280, 800));
  }

  @Test
  void aWindowSavedOnABiggerScreenIsShrunkToTheScreenItReopensOn() {
    UiState state = new UiState(node);
    List<Rectangle> laptop = List.of(new Rectangle(0, 0, 1366, 768));
    state.setBounds(new Rectangle(0, 0, 2560, 1400));
    assertThat(state.windowBounds(laptop)).isEqualTo(new Rectangle(0, 0, 1366, 768));
    state.setBounds(new Rectangle(300, 200, 2560, 1400)); // shrunk, then moved back to the screen's corner
    assertThat(state.windowBounds(laptop)).isEqualTo(new Rectangle(0, 0, 1366, 768));
    state.setBounds(new Rectangle(500, 100, 1000, 700)); // fits: moved up and left just enough to show it whole
    assertThat(state.windowBounds(laptop)).isEqualTo(new Rectangle(366, 68, 1000, 700));
  }

  @Test
  void aWindowAcrossTwoScreensGoesToTheOneItsTitleBarIsMostOn() {
    UiState state = new UiState(node);
    List<Rectangle> screens = List.of(new Rectangle(0, 0, 1920, 1080), new Rectangle(1920, 0, 1366, 768));
    // 1,000 px of the title bar on the second (smaller) screen, 400 px on the first
    state.setBounds(new Rectangle(1520, 100, 1400, 900));
    assertThat(state.windowBounds(screens)).isEqualTo(new Rectangle(1920, 0, 1366, 768));
    // mostly on the first screen: it fits there, moved left just enough to leave the second
    state.setBounds(new Rectangle(200, 100, 1800, 900));
    assertThat(state.windowBounds(screens)).isEqualTo(new Rectangle(120, 100, 1800, 900));
  }

  @Test
  void aWindowOnAScreenLeftOfOrAboveTheMainOneIsKeptInside() {
    UiState state = new UiState(node);
    List<Rectangle> screens = List.of(new Rectangle(0, 0, 1920, 1080), new Rectangle(-1280, -1024, 1280, 1024));
    state.setBounds(new Rectangle(-1300, -1000, 1400, 600));
    assertThat(state.windowBounds(screens)).isEqualTo(new Rectangle(-1280, -1000, 1280, 600));
  }

  @Test
  void aCompactWindowSavedOnABiggerScreenIsShrunkAndMovedInside() {
    UiState state = new UiState(node);
    List<Rectangle> laptop = List.of(new Rectangle(0, 0, 1366, 768));
    state.setCompactBounds(new Rectangle(1300, 10, 460, 900)); // its title bar's left 66 px are on the screen
    assertThat(state.compactWindowBounds(laptop, laptop.get(0))).isEqualTo(new Rectangle(906, 0, 460, 768));
  }

  @Test
  void missingBoundsAreCentredOnTheDefaultScreen() {
    List<Rectangle> screens = List.of(new Rectangle(1920, 0, 2560, 1440), new Rectangle(0, 0, 1920, 1080));
    assertThat(new UiState(node).windowBounds(screens)).isEqualTo(new Rectangle(1920 + 640, 320, 1280, 800));
  }

  @Test
  void theDefaultWindowShrinksToASmallScreen() {
    List<Rectangle> screens = List.of(new Rectangle(0, 0, 1024, 768));
    assertThat(new UiState(node).windowBounds(screens)).isEqualTo(new Rectangle(0, 0, 1024, 768));
  }

  @Test
  void compactBoundsSeparate() {
    UiState state = new UiState(node);
    state.setBounds(new Rectangle(10, 20, 1000, 700));
    state.setCompactBounds(new Rectangle(1500, 0, 420, 720));
    assertThat(state.bounds()).isEqualTo(new Rectangle(10, 20, 1000, 700));
    state.setCompactBounds(new Rectangle(1400, 30, 440, 760));
    assertThat(new UiState(node).bounds()).isEqualTo(new Rectangle(10, 20, 1000, 700));
    state.setBounds(new Rectangle(50, 60, 1100, 800));
    assertThat(new UiState(node).compactBounds()).isEqualTo(new Rectangle(1400, 30, 440, 760));
    state.setBounds(null);
    assertThat(new UiState(node).compactBounds()).isEqualTo(new Rectangle(1400, 30, 440, 760));
    List<Rectangle> screens = List.of(new Rectangle(0, 0, 1920, 1080));
    assertThat(state.compactWindowBounds(screens, screens.get(0))).isEqualTo(new Rectangle(1400, 30, 440, 760));
    assertThat(state.windowBounds(screens)).isEqualTo(new Rectangle(320, 140, 1280, 800));
  }

  @Test
  void missingCompactBoundsGoToTheTopRightOfTheCurrentScreen() {
    List<Rectangle> screens = List.of(new Rectangle(0, 0, 1920, 1080), new Rectangle(1920, 0, 2560, 1440));
    int w = UIScale.scale(UiState.COMPACT_WIDTH);
    int h = UIScale.scale(UiState.COMPACT_HEIGHT);
    assertThat(new UiState(node).compactWindowBounds(screens, screens.get(1)))
        .isEqualTo(new Rectangle(1920 + 2560 - w, 0, w, h));
  }

  @Test
  void offscreenCompactBoundsAreReset() {
    UiState state = new UiState(node);
    state.setCompactBounds(new Rectangle(5000, 3000, 420, 720));
    List<Rectangle> screens = List.of(new Rectangle(0, 0, 1920, 1080));
    int w = UIScale.scale(UiState.COMPACT_WIDTH);
    int h = UIScale.scale(UiState.COMPACT_HEIGHT);
    assertThat(state.compactWindowBounds(screens, screens.get(0))).isEqualTo(new Rectangle(1920 - w, 0, w, h));
  }

  @Test
  void theDefaultCompactWindowShrinksToASmallScreen() {
    List<Rectangle> screens = List.of(new Rectangle(0, 0, 400, 600));
    assertThat(new UiState(node).compactWindowBounds(screens, screens.get(0)))
        .isEqualTo(new Rectangle(0, 0, 400, 600));
  }
}
