package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.util.UIScale;
import java.util.stream.IntStream;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
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
}
