package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TextSizeTest {
  private static final Font BASE = new Font(Font.SANS_SERIF, Font.PLAIN, 20);
  private final Preferences node = UiStateTest.testNode();
  private final UiState state = new UiState(node);
  @AfterEach
  void restore() throws BackingStoreException {
    UIManager.put(TextSize.DEFAULT_FONT, null); // drop the override: the look and feel's own font shows again
    node.removeNode();
  }

  private static float defaultFontSize() {
    return UIManager.getFont(TextSize.DEFAULT_FONT).getSize2D();
  }

  @Test
  void stepsUpAndDownInTenPercentBetweenNinetyAndOneFifty() {
    TextSize size = new TextSize(state, BASE);
    assertThat(size.bigger()).isEqualTo(110);
    assertThat(defaultFontSize()).isEqualTo(22f);
    for (int i = 0; i < 10; i++) {
      size.bigger();
    }
    assertThat(state.textSizePct()).isEqualTo(150);
    assertThat(defaultFontSize()).isEqualTo(30f);
    assertThat(size.reset()).isEqualTo(100);
    assertThat(defaultFontSize()).isEqualTo(20f);
    assertThat(size.smaller()).isEqualTo(90);
    assertThat(size.smaller()).isEqualTo(90);
    assertThat(defaultFontSize()).isEqualTo(18f);
    assertThat(new UiState(node).textSizePct()).isEqualTo(90);
  }

  @Test
  void theSavedSizeIsAppliedFromTheBaseFont() {
    state.setTextSizePct(130);
    TextSize size = new TextSize(state, BASE);
    size.applySaved();
    assertThat(defaultFontSize()).isEqualTo(26f);
    size.bigger();
    assertThat(defaultFontSize()).isEqualTo(28f); // scaled from the base, not from the scaled font
    assertThat(UIManager.getFont(TextSize.DEFAULT_FONT).getFamily()).isEqualTo(BASE.getFamily());
  }

  @Test
  void theBaseFontIsCapturedOnceAtConstruction() {
    UIManager.put(TextSize.DEFAULT_FONT, BASE);
    TextSize size = new TextSize(state);
    size.bigger();
    size.bigger();
    assertThat(defaultFontSize()).isEqualTo(24f); // 120 % of 20, not 110 % of 22
  }
}
