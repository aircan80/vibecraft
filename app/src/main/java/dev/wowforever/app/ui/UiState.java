package dev.wowforever.app.ui;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.ui.widgets.MasterList;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.prefs.Preferences;
import javax.swing.SortOrder;

/**
 * Window layout, view settings, pinned pages and recent searches remembered between runs, all in one preferences
 * node (the one {@link ThemeManager} uses): the window bounds, maximised state and screen; the list | detail
 * divider and whether the list shows; the compact window's bounds and mode; always on top; the text size; column
 * widths and sort per table kind; the last search kind; pinned pages; and the last searches. A value that cannot be
 * read (say, edited by hand) reads as its default.
 */
public final class UiState {
  static final String BOUNDS = "window.bounds";
  static final String MAXIMISED = "window.maximised";
  static final String SCREEN = "window.screen";
  static final String ALWAYS_ON_TOP = "window.alwaysOnTop";
  static final String DIVIDER = "layout.dividerLocation";
  static final String LIST_VISIBLE = "layout.listVisible";
  static final String COMPACT_BOUNDS = "compact.bounds";
  static final String COMPACT_MODE = "compact.mode";
  static final String TEXT_SIZE = "view.textSizePct";
  static final String WIDTHS_PREFIX = "table.widths.";
  static final String SORT_PREFIX = "table.sort.";
  static final String LAST_SEARCH_KIND = "search.lastKind";
  static final String PINNED = "pinned";
  static final String RECENT = "search.recent";
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
  /** Longest table kind: a preference key is at most 80 characters. */
  static final int TABLE_KIND_MAX_LENGTH = 40;
  private static final String LINE = "\n";
  private static final String FIELD = "\t";

  /** How a table is sorted: by the column titled {@code column}, in {@code order}. */
  public record Sort(String column, SortOrder order) {
    public Sort {
      Objects.requireNonNull(column, "column");
      Objects.requireNonNull(order, "order");
    }
  }

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
   * {@link #bounds()} when their top {@value #TITLE_STRIP} px (the title bar) overlap a screen, so the window can
   * still be dragged; else a {@value #DEFAULT_WIDTH}×{@value #DEFAULT_HEIGHT} window (shrunk to fit) centred on
   * the default screen.
   */
  public Rectangle windowBounds(List<Rectangle> screens) {
    if (screens.isEmpty()) {
      throw new IllegalArgumentException("No screens");
    }
    Rectangle saved = bounds();
    if (reachable(saved, screens)) {
      return saved;
    }
    Rectangle screen = screens.get(0);
    int w = Math.min(DEFAULT_WIDTH, screen.width);
    int h = Math.min(DEFAULT_HEIGHT, screen.height);
    return new Rectangle(screen.x + (screen.width - w) / 2, screen.y + (screen.height - h) / 2, w, h);
  }

  /**
   * Where to put the compact window on {@code screens}: the saved {@link #compactBounds()} when their title bar is
   * on a screen (as for {@link #windowBounds}); else a {@value #COMPACT_WIDTH}×{@value #COMPACT_HEIGHT}
   * logical-pixel window (shrunk to fit) at the top right of {@code current}, the screen the window is on.
   */
  public Rectangle compactWindowBounds(List<Rectangle> screens, Rectangle current) {
    Rectangle saved = compactBounds();
    if (reachable(saved, screens)) {
      return saved;
    }
    int w = Math.min(UIScale.scale(COMPACT_WIDTH), current.width);
    int h = Math.min(UIScale.scale(COMPACT_HEIGHT), current.height);
    return new Rectangle(current.x + current.width - w, current.y, w, h);
  }

  /** Whether {@code saved} is a window whose top {@value #TITLE_STRIP} px overlap one of {@code screens}. */
  private static boolean reachable(Rectangle saved, List<Rectangle> screens) {
    if (saved == null || saved.width <= 0 || saved.height <= 0) {
      return false;
    }
    Rectangle titleBar = new Rectangle(saved.x, saved.y, saved.width, Math.min(TITLE_STRIP, saved.height));
    return screens.stream().anyMatch(titleBar::intersects);
  }

  /** Whether the normal (not compact) window was maximised. */
  public boolean maximised() {
    return prefs.getBoolean(MAXIMISED, false);
  }

  public void setMaximised(boolean maximised) {
    prefs.putBoolean(MAXIMISED, maximised);
  }

  /** The id of the screen the window was on ({@link java.awt.GraphicsDevice#getIDstring()}); null when unknown. */
  public String screenDeviceId() {
    return prefs.get(SCREEN, null);
  }

  /** Saves the window's screen id; null removes it. */
  public void setScreenDeviceId(String id) {
    putOrRemove(SCREEN, id);
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

  /** The saved column widths of tables of {@code tableKind}, by column title; empty when none are saved. */
  public Map<String, Integer> columnWidths(String tableKind) {
    Map<String, Integer> widths = new LinkedHashMap<>();
    String saved = prefs.get(WIDTHS_PREFIX + checkKind(tableKind), "");
    for (String line : saved.split(LINE)) {
      int tab = line.lastIndexOf(FIELD);
      if (tab > 0) {
        try {
          widths.put(line.substring(0, tab), Integer.parseInt(line.substring(tab + 1)));
        } catch (NumberFormatException e) {
          // a garbled entry reads as no saved width
        }
      }
    }
    return Map.copyOf(widths);
  }

  /** Saves the column widths of tables of {@code tableKind} by column title; an empty map removes them. */
  public void setColumnWidths(String tableKind, Map<String, Integer> widths) {
    List<String> lines = new ArrayList<>();
    widths.forEach((title, width) -> lines.add(oneLine(title) + FIELD + width));
    putOrRemove(WIDTHS_PREFIX + checkKind(tableKind), lines.isEmpty() ? null : String.join(LINE, lines));
  }

  /** How tables of {@code tableKind} were last sorted; empty when unsorted. */
  public Optional<Sort> sort(String tableKind) {
    String saved = prefs.get(SORT_PREFIX + checkKind(tableKind), null);
    int tab = saved == null ? -1 : saved.lastIndexOf(FIELD);
    if (tab <= 0) {
      return Optional.empty();
    }
    try {
      return Optional.of(new Sort(saved.substring(0, tab), SortOrder.valueOf(saved.substring(tab + 1))));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** Saves how tables of {@code tableKind} are sorted; null removes it. */
  public void setSort(String tableKind, Sort sort) {
    putOrRemove(SORT_PREFIX + checkKind(tableKind),
        sort == null ? null : oneLine(sort.column()) + FIELD + sort.order().name());
  }

  /** The kind chip of the last search; empty for All. */
  public Optional<EntityKind> lastSearchKind() {
    String saved = prefs.get(LAST_SEARCH_KIND, null);
    try {
      return saved == null ? Optional.empty() : Optional.of(EntityKind.valueOf(saved));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** Saves the kind chip of the last search; empty (All) removes it. */
  public void setLastSearchKind(Optional<EntityKind> kind) {
    putOrRemove(LAST_SEARCH_KIND, kind.map(Enum::name).orElse(null));
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

  private static String checkKind(String tableKind) {
    if (tableKind == null || tableKind.isBlank() || tableKind.length() > TABLE_KIND_MAX_LENGTH) {
      throw new IllegalArgumentException("Bad table kind: " + tableKind);
    }
    return tableKind;
  }
}
