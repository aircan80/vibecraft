package dev.wowforever.app.ui;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.SourceFilter;
import dev.wowforever.app.ui.widgets.MasterList;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Window layout, view settings, pinned pages and recent searches remembered between runs, all in one preferences
 * node (the one {@link ThemeManager} uses): the window bounds and maximised state; the list | detail divider and
 * whether the list shows; the compact window's bounds and mode; always on top; the text size; pinned pages; the
 * last searches; the drop source filter; and whether zone maps pin quest givers. A value that cannot be read (say,
 * edited by hand) reads as its default.
 */
public final class UiState {
  static final String BOUNDS = "window.bounds";
  static final String MAXIMISED = "window.maximised";
  static final String ALWAYS_ON_TOP = "window.alwaysOnTop";
  static final String DIVIDER = "layout.dividerLocation";
  static final String LIST_VISIBLE = "layout.listVisible";
  static final String COMPACT_BOUNDS = "compact.bounds";
  static final String COMPACT_MODE = "compact.mode";
  static final String TEXT_SIZE = "view.textSizePct";
  static final String PINNED = "pinned";
  static final String RECENT = "search.recent";
  static final String SOURCE_FILTER = "view.sourceFilter";
  static final String QUEST_PINS = "map.questPins";
  /** The smallest text size, in percent of the theme's font. */
  public static final int TEXT_MIN = 90;
  /** The text size when none is saved. */
  public static final int TEXT_DEFAULT = 100;
  /** The largest text size. */
  public static final int TEXT_MAX = 150;
  /** The step between text sizes. */
  public static final int TEXT_STEP = 10;
  /** The width of a window that has no usable saved bounds. */
  public static final int DEFAULT_WIDTH = 1280;
  /** The height of a window that has no usable saved bounds. */
  public static final int DEFAULT_HEIGHT = 800;
  /** The width of a compact window that has no usable saved bounds, in logical pixels. */
  public static final int COMPACT_WIDTH = 420;
  /** The height of a compact window that has no usable saved bounds, in logical pixels. */
  public static final int COMPACT_HEIGHT = 720;
  /** The top strip of a saved window, in pixels, that must be on a screen for its bounds to be kept. */
  static final int TITLE_STRIP = 40;
  /** How many pinned pages are kept; pinning more drops the oldest. */
  public static final int MAX_PINNED = 50;
  /** How many recent searches are kept. */
  public static final int RECENT_SEARCHES = 10;
  /** Longest recent search kept, in characters, so that the saved list stays far below a preference's limit. */
  static final int RECENT_MAX_LENGTH = 200;
  private static final String LINE = "\n";

  private final Preferences prefs;

  /** State kept in {@code prefs}; tests pass a throw-away node. */
  public UiState(Preferences prefs) {
    this.prefs = Objects.requireNonNull(prefs, "prefs");
  }

  /** The user's saved state. */
  public static UiState load() {
    return new UiState(Preferences.userNodeForPackage(UiState.class));
  }

  /** The saved bounds of the normal (not compact, not maximised) window; null when none are saved. */
  public Rectangle bounds() {
    return rectangle(BOUNDS);
  }

  /** Saves the normal window's bounds; null removes them. */
  public void setBounds(Rectangle bounds) {
    putRectangle(BOUNDS, bounds);
  }

  /**
   * Where to put the window on {@code screens} (each screen's bounds, the default screen first): the saved
   * {@link #bounds()} when their top {@value #TITLE_STRIP} px (the title bar) overlap a screen, made to fit the
   * screen the title bar overlaps most and moved inside it ({@link #onScreen}); else a
   * {@value #DEFAULT_WIDTH}×{@value #DEFAULT_HEIGHT} window (shrunk to fit) centred on the default screen.
   */
  public Rectangle windowBounds(List<Rectangle> screens) {
    if (screens.isEmpty()) {
      throw new IllegalArgumentException("No screens");
    }
    Rectangle saved = onScreen(bounds(), screens);
    if (saved != null) {
      return saved;
    }
    Rectangle screen = screens.get(0);
    int w = Math.min(DEFAULT_WIDTH, screen.width);
    int h = Math.min(DEFAULT_HEIGHT, screen.height);
    return new Rectangle(screen.x + (screen.width - w) / 2, screen.y + (screen.height - h) / 2, w, h);
  }

  /**
   * Where to put the compact window on {@code screens}: the saved {@link #compactBounds()} when their title bar is
   * on a screen, fitted and moved inside it (as for {@link #windowBounds}); else a
   * {@value #COMPACT_WIDTH}×{@value #COMPACT_HEIGHT} logical-pixel window (shrunk to fit) at the top right of
   * {@code current}, the screen the window is on.
   */
  public Rectangle compactWindowBounds(List<Rectangle> screens, Rectangle current) {
    Rectangle saved = onScreen(compactBounds(), screens);
    if (saved != null) {
      return saved;
    }
    int w = Math.min(UIScale.scale(COMPACT_WIDTH), current.width);
    int h = Math.min(UIScale.scale(COMPACT_HEIGHT), current.height);
    return new Rectangle(current.x + current.width - w, current.y, w, h);
  }

  /**
   * {@code saved} on the one of {@code screens} its top {@value #TITLE_STRIP} px (the title bar) overlap most, the
   * first such screen on a tie: no wider or taller than that screen, and moved the least that puts it wholly inside,
   * so a window saved on a bigger or since rearranged screen comes back whole. Null when {@code saved} is null or
   * empty or its title bar is on no screen (it could not be dragged back).
   */
  static Rectangle onScreen(Rectangle saved, List<Rectangle> screens) {
    if (saved == null || saved.width <= 0 || saved.height <= 0) {
      return null;
    }
    Rectangle titleBar = new Rectangle(saved.x, saved.y, saved.width, Math.min(TITLE_STRIP, saved.height));
    Rectangle best = null;
    long bestArea = 0;
    for (Rectangle screen : screens) {
      Rectangle overlap = titleBar.intersection(screen);
      long area = overlap.isEmpty() ? 0 : (long) overlap.width * overlap.height;
      if (area > bestArea) {
        best = screen;
        bestArea = area;
      }
    }
    if (best == null) {
      return null;
    }
    int w = Math.min(saved.width, best.width);
    int h = Math.min(saved.height, best.height);
    int x = Math.max(best.x, Math.min(saved.x, best.x + best.width - w));
    int y = Math.max(best.y, Math.min(saved.y, best.y + best.height - h));
    return new Rectangle(x, y, w, h);
  }

  /** Whether the normal (not compact) window was maximised. */
  public boolean maximised() {
    return prefs.getBoolean(MAXIMISED, false);
  }

  public void setMaximised(boolean maximised) {
    prefs.putBoolean(MAXIMISED, maximised);
  }

  /** The saved bounds of the compact window; null when none are saved. */
  public Rectangle compactBounds() {
    return rectangle(COMPACT_BOUNDS);
  }

  /** Saves the compact window's bounds; null removes them. */
  public void setCompactBounds(Rectangle bounds) {
    putRectangle(COMPACT_BOUNDS, bounds);
  }

  /** Whether the window was in compact mode. */
  public boolean compactMode() {
    return prefs.getBoolean(COMPACT_MODE, false);
  }

  public void setCompactMode(boolean compact) {
    prefs.putBoolean(COMPACT_MODE, compact);
  }

  /** Whether the window stays on top of other windows. */
  public boolean alwaysOnTop() {
    return prefs.getBoolean(ALWAYS_ON_TOP, false);
  }

  public void setAlwaysOnTop(boolean onTop) {
    prefs.putBoolean(ALWAYS_ON_TOP, onTop);
  }

  /** The text size in percent of the theme's font: {@value #TEXT_MIN} to {@value #TEXT_MAX} in steps of ten. */
  public int textSizePct() {
    return textSize(prefs.getInt(TEXT_SIZE, TEXT_DEFAULT));
  }

  /** Saves the text size as {@link #textSize} makes it. */
  public void setTextSizePct(int pct) {
    prefs.putInt(TEXT_SIZE, textSize(pct));
  }

  /** {@code pct} clamped to {@value #TEXT_MIN}–{@value #TEXT_MAX} and rounded down to a {@value #TEXT_STEP} step. */
  public static int textSize(int pct) {
    int clamped = Math.max(TEXT_MIN, Math.min(TEXT_MAX, pct));
    return clamped - Math.floorMod(clamped - TEXT_MIN, TEXT_STEP);
  }

  /** The pinned pages' keys (such as {@code "item:19019"}), oldest first. */
  public List<String> pinned() {
    String saved = prefs.get(PINNED, "");
    return saved.isEmpty() ? List.of() : List.of(saved.split(LINE));
  }

  /**
   * Saves the pinned pages' keys in order; blank and repeated keys are dropped, and so are the oldest beyond
   * {@value #MAX_PINNED} or beyond what one preference can hold.
   */
  public void setPinned(List<String> keys) {
    List<String> kept = new ArrayList<>(
        keys.stream().map(UiState::oneLine).filter(k -> !k.isEmpty()).distinct().toList());
    while (kept.size() > MAX_PINNED || String.join(LINE, kept).length() > Preferences.MAX_VALUE_LENGTH) {
      kept.remove(0);
    }
    putOrRemove(PINNED, kept.isEmpty() ? null : String.join(LINE, kept));
  }

  /** Pins {@code key} (last) when it is not pinned, else unpins it; returns whether it is pinned now. */
  public boolean togglePinned(String key) {
    List<String> pins = new ArrayList<>(pinned());
    boolean pin = !pins.remove(key);
    if (pin) {
      pins.add(key);
    }
    setPinned(pins);
    return pin;
  }

  /** Whether the master list shows (Ctrl+B hides it). */
  public boolean listVisible() {
    return prefs.getBoolean(LIST_VISIBLE, true);
  }

  public void setListVisible(boolean visible) {
    prefs.putBoolean(LIST_VISIBLE, visible);
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

  /** Which drop rows the item and NPC pages show; {@link SourceFilter#ALL} unless set. */
  public SourceFilter sourceFilter() {
    String saved = prefs.get(SOURCE_FILTER, null);
    try {
      return saved == null ? SourceFilter.ALL : SourceFilter.valueOf(saved);
    } catch (IllegalArgumentException e) {
      return SourceFilter.ALL;
    }
  }

  /** Saves the drop source filter; {@link SourceFilter#ALL} removes it. */
  public void setSourceFilter(SourceFilter filter) {
    putOrRemove(SOURCE_FILTER, filter == SourceFilter.ALL ? null : filter.name());
  }

  /** Whether zone maps pin quest givers (their "Quests" toggle); off unless set. */
  public boolean questPinsShown() {
    return prefs.getBoolean(QUEST_PINS, false);
  }

  public void setQuestPinsShown(boolean shown) {
    prefs.putBoolean(QUEST_PINS, shown);
  }

  /** {@link #questPinsShown()} as a setting a zone map reads and writes. */
  public BooleanSetting questPinsSetting() {
    return BooleanSetting.of(this::questPinsShown, this::setQuestPinsShown);
  }

  /** The last {@value #RECENT_SEARCHES} searches, most recent first. */
  public List<String> recentSearches() {
    String saved = prefs.get(RECENT, "");
    return saved.isEmpty() ? List.of() : List.of(saved.split(LINE));
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
    prefs.put(RECENT, String.join(LINE, recent));
  }

  private Rectangle rectangle(String key) {
    String saved = prefs.get(key, null);
    if (saved == null) {
      return null;
    }
    String[] p = saved.split(",");
    try {
      return p.length == 4 ? new Rectangle(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
          Integer.parseInt(p[3])) : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private void putRectangle(String key, Rectangle r) {
    putOrRemove(key, r == null ? null : r.x + "," + r.y + "," + r.width + "," + r.height);
  }

  private void putOrRemove(String key, String value) {
    if (value == null) {
      prefs.remove(key);
    } else {
      prefs.put(key, value);
    }
  }

  private static String oneLine(String s) {
    return s.replaceAll("[\r\n\t]+", " ").strip();
  }
}
