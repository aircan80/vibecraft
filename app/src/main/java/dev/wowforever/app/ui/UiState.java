package dev.wowforever.app.ui;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.ui.widgets.MasterList;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Window layout and recent searches remembered between runs, in the user's preferences (the node
 * {@link ThemeManager} uses). For now the list | detail divider and the last searches.
 */
public final class UiState {
  static final String DIVIDER = "layout.dividerLocation";
  static final String RECENT = "search.recent";
  /** How many recent searches are kept. */
  public static final int RECENT_SEARCHES = 10;
  /** Longest recent search kept, in characters, so that the saved list stays far below a preference's limit. */
  static final int RECENT_MAX_LENGTH = 200;
  private static final String RECENT_SEPARATOR = "\n";

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

  /** The last {@value #RECENT_SEARCHES} searches, most recent first. */
  public List<String> recentSearches() {
    String saved = prefs.get(RECENT, "");
    return saved.isEmpty() ? List.of() : List.of(saved.split(RECENT_SEPARATOR));
  }

  /**
   * Puts {@code text} (stripped, line breaks as spaces, cut to {@value #RECENT_MAX_LENGTH} characters) first in the
   * recent searches, dropping an earlier search that differs only in case and the oldest beyond
   * {@value #RECENT_SEARCHES}; blank or null text is ignored.
   */
  public void addRecentSearch(String text) {
    if (text == null) {
      return;
    }
    String s = text.replaceAll("[\r\n]+", " ").strip();
    if (s.isEmpty()) {
      return;
    }
    if (s.length() > RECENT_MAX_LENGTH) {
      s = s.substring(0, RECENT_MAX_LENGTH).strip();
    }
    String key = s.toLowerCase(Locale.ROOT);
    List<String> recent = new ArrayList<>();
    recent.add(s);
    for (String old : recentSearches()) {
      if (recent.size() < RECENT_SEARCHES && !old.toLowerCase(Locale.ROOT).equals(key)) {
        recent.add(old);
      }
    }
    prefs.put(RECENT, String.join(RECENT_SEPARATOR, recent));
  }
}
