package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.ui.widgets.ManualDelay;
import java.awt.Frame;
import java.awt.Rectangle;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WindowStatePersistenceTest {
  private final Preferences node = UiStateTest.testNode();
  private final UiState state = new UiState(node);
  private final ManualDelay delay = new ManualDelay();
  private WindowStatePersistence.Snapshot window =
      new WindowStatePersistence.Snapshot(new Rectangle(10, 20, 1000, 700), Frame.NORMAL, "\\Display1");
  private final WindowStatePersistence persistence = new WindowStatePersistence(state, () -> window, delay);

  @AfterEach
  void removeNode() throws BackingStoreException {
    node.removeNode();
  }

  @Test
  void movesAndResizesAreSavedOnceTheWindowRests() {
    persistence.changed();
    window = new WindowStatePersistence.Snapshot(new Rectangle(30, 40, 1100, 720), Frame.NORMAL, "\\Display2");
    persistence.changed();
    assertThat(state.bounds()).isNull();
    assertThat(delay.pending()).isEqualTo(1); // the earlier save was dropped: one save per pause
    delay.fire();
    assertThat(state.bounds()).isEqualTo(new Rectangle(30, 40, 1100, 720));
    assertThat(state.maximised()).isFalse();
    assertThat(state.screenDeviceId()).isEqualTo("\\Display2");
  }

  @Test
  void closingSavesAtOnceAndDropsThePendingSave() {
    persistence.changed();
    persistence.saveNow();
    assertThat(state.bounds()).isEqualTo(new Rectangle(10, 20, 1000, 700));
    assertThat(delay.pending()).isZero();
  }

  @Test
  void aMaximisedWindowKeepsItsNormalBounds() {
    persistence.saveNow();
    window = new WindowStatePersistence.Snapshot(new Rectangle(0, 0, 1920, 1040), Frame.MAXIMIZED_BOTH, "\\Display1");
    persistence.saveNow();
    assertThat(state.maximised()).isTrue();
    assertThat(state.bounds()).isEqualTo(new Rectangle(10, 20, 1000, 700));
  }

  @Test
  void aMinimisedWindowSavesNothing() {
    persistence.saveNow();
    window = new WindowStatePersistence.Snapshot(new Rectangle(-32000, -32000, 160, 28), Frame.ICONIFIED, "\\Display1");
    persistence.saveNow();
    assertThat(state.bounds()).isEqualTo(new Rectangle(10, 20, 1000, 700));
    assertThat(state.maximised()).isFalse();
  }

  @Test
  void inCompactModeTheCompactBoundsAreSaved() {
    state.setCompactMode(true);
    window = new WindowStatePersistence.Snapshot(new Rectangle(1900, 40, 420, 720), Frame.NORMAL, "\\Display2");
    persistence.saveNow();
    assertThat(state.compactBounds()).isEqualTo(new Rectangle(1900, 40, 420, 720));
    assertThat(state.bounds()).isNull();
  }
}
