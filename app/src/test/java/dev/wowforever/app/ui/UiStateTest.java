package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.util.UIScale;
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
}
