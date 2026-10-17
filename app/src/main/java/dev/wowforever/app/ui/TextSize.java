package dev.wowforever.app.ui;

import com.formdev.flatlaf.FlatLaf;
import java.awt.Font;
import java.util.Objects;
import javax.swing.UIManager;

/**
 * The text size, {@value UiState#TEXT_MIN}–{@value UiState#TEXT_MAX} % of the look and feel's font in steps of
 * {@value UiState#TEXT_STEP} (Ctrl+= larger, Ctrl+- smaller, Ctrl+0 back to 100 %), saved in {@link UiState}. The
 * size goes in as FlatLaf's {@value #DEFAULT_FONT}, scaled from the base font taken once when this is created,
 * and every window is updated.
 */
public final class TextSize {
  /** The FlatLaf key every component font derives from. */
  static final String DEFAULT_FONT = "defaultFont";

  private final UiState state;
  private final Font base;

  /** Sizes relative to the look and feel's current {@value #DEFAULT_FONT}; create it before any size is applied. */
  public TextSize(UiState state) {
    this(state, UIManager.getFont(DEFAULT_FONT));
  }

  TextSize(UiState state, Font base) {
    this.state = Objects.requireNonNull(state, "state");
    this.base = Objects.requireNonNull(base, "base font");
  }

  /** Applies the saved size; nothing changes at the default size. */
  public void applySaved() {
    int pct = state.textSizePct();
    if (pct != UiState.TEXT_DEFAULT) {
      install(pct);
    }
  }

  /** One step larger, up to the largest; returns the new size. */
  public int bigger() {
    return set(state.textSizePct() + UiState.TEXT_STEP);
  }

  /** One step smaller, down to the smallest; returns the new size. */
  public int smaller() {
    return set(state.textSizePct() - UiState.TEXT_STEP);
  }

  /** Back to 100 %; returns it. */
  public int reset() {
    return set(UiState.TEXT_DEFAULT);
  }

  private int set(int pct) {
    state.setTextSizePct(pct);
    int saved = state.textSizePct();
    install(saved);
    return saved;
  }

  private void install(int pct) {
    UIManager.put(DEFAULT_FONT, base.deriveFont(base.getSize2D() * pct / 100f));
    FlatLaf.updateUI();
  }
}
