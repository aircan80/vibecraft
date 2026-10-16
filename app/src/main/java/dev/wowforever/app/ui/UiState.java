package dev.wowforever.app.ui;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.ui.widgets.MasterList;
import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Window layout remembered between runs, in the user's preferences (the node {@link ThemeManager} uses). For now
 * only the list | detail divider.
 */
public final class UiState {
  static final String DIVIDER = "layout.dividerLocation";

  private final Preferences prefs;

  /** State kept in {@code prefs}; tests pass a throw-away node. */
  public UiState(Preferences prefs) {
    this.prefs = Objects.requireNonNull(prefs, "prefs");
  }

  /** The user's saved state. */
  public static UiState load() {
    return new UiState(Preferences.userNodeForPackage(UiState.class));
  }

  /** The list | detail divider location in pixels; {@link MasterList#WIDTH} logical pixels when none is saved. */
  public int dividerLocation() {
    return prefs.getInt(DIVIDER, UIScale.scale(MasterList.WIDTH));
  }

  /** Saves the divider location; ignored unless positive (a collapsed or not yet laid out split). */
  public void setDividerLocation(int px) {
    if (px > 0) {
      prefs.putInt(DIVIDER, px);
    }
  }
}
