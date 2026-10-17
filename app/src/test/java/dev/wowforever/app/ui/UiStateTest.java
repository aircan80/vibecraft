package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.EntityKind;
import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import javax.swing.SortOrder;
import org.junit.jupiter.api.Test;

class UiStateTest {
  /** A throw-away node, so the test never touches the user's real settings. */
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
    assertThat(state.screenDeviceId()).isNull();
    assertThat(state.compactBounds()).isNull();
    assertThat(state.compactMode()).isFalse();
    assertThat(state.alwaysOnTop()).isFalse();
    assertThat(state.textSizePct()).isEqualTo(100);
    assertThat(state.columnWidths("item-drops")).isEmpty();
    assertThat(state.sort("item-drops")).isEmpty();
    assertThat(state.lastSearchKind()).isEmpty();
    assertThat(state.pinned()).isEmpty();
    assertThat(state.listVisible()).isTrue();
  }

  @Test
  void everyFieldRoundTrips() {
    UiState state = new UiState(node);
    state.setBounds(new Rectangle(10, 20, 1100, 700));
    state.setMaximised(true);
    state.setScreenDeviceId("\\Display2");
    state.setDividerLocation(377);
    state.setCompactBounds(new Rectangle(1900, 40, 420, 720));
    state.setCompactMode(true);
    state.setAlwaysOnTop(true);
    state.setTextSizePct(130);
    state.setColumnWidths("item-drops", Map.of("Source", 240, "Rate", 80));
    state.setSort("item-drops", new UiState.Sort("Rate", SortOrder.DESCENDING));
    state.setLastSearchKind(Optional.of(EntityKind.NPC));
    state.setPinned(List.of("item:19019", "npc:448"));
    state.addRecentSearch("hogger");
    state.setListVisible(false);

    UiState again = new UiState(node);
    assertThat(again.bounds()).isEqualTo(new Rectangle(10, 20, 1100, 700));
    assertThat(again.maximised()).isTrue();
    assertThat(again.screenDeviceId()).isEqualTo("\\Display2");
    assertThat(again.dividerLocation()).isEqualTo(377);
    assertThat(again.compactBounds()).isEqualTo(new Rectangle(1900, 40, 420, 720));
    assertThat(again.compactMode()).isTrue();
    assertThat(again.alwaysOnTop()).isTrue();
    assertThat(again.textSizePct()).isEqualTo(130);
    assertThat(again.columnWidths("item-drops")).isEqualTo(Map.of("Source", 240, "Rate", 80));
    assertThat(again.columnWidths("npc-loot")).isEmpty();
    assertThat(again.sort("item-drops")).contains(new UiState.Sort("Rate", SortOrder.DESCENDING));
    assertThat(again.sort("npc-loot")).isEmpty();
    assertThat(again.lastSearchKind()).contains(EntityKind.NPC);
    assertThat(again.pinned()).containsExactly("item:19019", "npc:448");
    assertThat(again.recentSearches()).containsExactly("hogger");
    assertThat(again.listVisible()).isFalse();
  }

  @Test
  void clearingOptionalFieldsRemovesThem() {
    UiState state = new UiState(node);
    state.setBounds(new Rectangle(1, 2, 300, 400));
    state.setScreenDeviceId("screen");
    state.setSort("t", new UiState.Sort("Name", SortOrder.ASCENDING));
    state.setLastSearchKind(Optional.of(EntityKind.ITEM));
    state.setBounds(null);
    state.setScreenDeviceId(null);
    state.setSort("t", null);
    state.setLastSearchKind(Optional.empty());
    assertThat(state.bounds()).isNull();
    assertThat(state.screenDeviceId()).isNull();
    assertThat(state.sort("t")).isEmpty();
    assertThat(state.lastSearchKind()).isEmpty();
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
    node.put(UiState.LAST_SEARCH_KIND, "DRAGON");
    node.put(UiState.SORT_PREFIX + "t", "Rate\tSIDEWAYS");
    UiState state = new UiState(node);
    assertThat(state.bounds()).isNull();
    assertThat(state.lastSearchKind()).isEmpty();
    assertThat(state.sort("t")).isEmpty();
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
  void aWindowWhoseTitleBarPartlyShowsIsKept() {
    UiState state = new UiState(node);
    List<Rectangle> screens = List.of(new Rectangle(0, 0, 1920, 1080));
    state.setBounds(new Rectangle(1800, 1050, 1000, 700)); // 120 × 30 px of the top strip on screen
    assertThat(state.windowBounds(screens)).isEqualTo(new Rectangle(1800, 1050, 1000, 700));
    state.setBounds(new Rectangle(1800, 1085, 1000, 700)); // the top strip starts below the screen
    assertThat(state.windowBounds(screens)).isEqualTo(new Rectangle(320, 140, 1280, 800));
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
