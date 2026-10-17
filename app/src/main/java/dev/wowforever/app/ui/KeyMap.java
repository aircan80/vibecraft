package dev.wowforever.app.ui;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.table.AbstractTableModel;
import javax.swing.text.JTextComponent;

/**
 * The keyboard map (UI review P2.6) as one table: it installs the window's key bindings on the root pane and fills
 * the Help › Keyboard shortcuts table, so the help never drifts from the keys. Keys bound where they act install
 * nothing here: the master list's Up/Down/Enter and a page's Ctrl+1…6 are {@link #DOCUMENTED_ONLY} rows, and mouse
 * buttons 4/5 ({@link MouseNavigation}) have no stroke in the history row. The row copy keys go on each link table
 * ({@link #installRowCopy}), since a focused table's own Ctrl+C would otherwise take them first.
 */
public final class KeyMap {
  /** The action of a help row whose keys are bound where they act, not on the root pane. */
  public static final String DOCUMENTED_ONLY = "documented-only";
  /** The action of the row copy keys, bound on each link table. */
  public static final String ROW_COPY = "rowCopy";

  /** Root pane handler names: what {@link #install} needs a {@link Runnable} for. */
  public static final String FOCUS_SEARCH = "focusSearch";
  public static final String ESCAPE = "escape";
  public static final String BACK = "back";
  public static final String FORWARD = "forward";
  public static final String PIN = "pin";
  public static final String TOGGLE_LIST = "toggleList";
  public static final String COMPACT_MODE = "compactMode";
  public static final String ALWAYS_ON_TOP = "alwaysOnTop";
  public static final String TEXT_BIGGER = "textBigger";
  public static final String TEXT_SMALLER = "textSmaller";
  public static final String TEXT_RESET = "textReset";
  /** Link table handler names, bound by {@link #installRowCopy}. */
  public static final String COPY_NAME = "copyName";
  public static final String COPY_ID = "copyId";
  /** The root action of {@code /}: {@link #FOCUS_SEARCH}, only while no text field has the focus. */
  static final String SLASH_SEARCH = "focusSearchOutsideText";

  /** One help row: the keys as shown, the action group they belong to, and what they do. */
  public record Binding(String keys, String action, String description) {
    public Binding {
      Objects.requireNonNull(keys, "keys");
      Objects.requireNonNull(action, "action");
      Objects.requireNonNull(description, "description");
    }
  }

  /** One key of a binding: a {@link KeyStroke#getKeyStroke(String)} string and the handler it runs. */
  public record Stroke(String key, String handler) {}

  /** The keyboard map, in the order the help shows it. */
  public static final List<Binding> BINDINGS = List.of(
      new Binding("Ctrl+F, Ctrl+L, / outside text fields", FOCUS_SEARCH, "Focus search"),
      new Binding("Esc", ESCAPE, "Close dropdown, then clear search, then focus list"),
      new Binding("Alt+Left/Right, mouse 4/5", "history", "Back / forward"),
      new Binding("Up/Down, Enter", DOCUMENTED_ONLY, "Preview, open"),
      new Binding("Ctrl+1…6", DOCUMENTED_ONLY, "Jump to source section"),
      new Binding("Ctrl+C / Ctrl+Shift+C on a row", ROW_COPY, "Copy name / copy item:ID or /way"),
      new Binding("Ctrl+D", PIN, "Pin page"),
      new Binding("Ctrl+B", TOGGLE_LIST, "Toggle results list"),
      new Binding("F10, Ctrl+Shift+T", "window", "Compact mode, always on top"),
      new Binding("Ctrl+= / Ctrl+- / Ctrl+0", "textSize", "Text size"));

  /** The keys of each action group; mouse buttons 4/5 are left to {@link MouseNavigation}. */
  private static final Map<String, List<Stroke>> STROKES = Map.of(
      FOCUS_SEARCH, List.of(new Stroke("ctrl F", FOCUS_SEARCH), new Stroke("ctrl L", FOCUS_SEARCH),
          new Stroke("typed /", SLASH_SEARCH)),
      ESCAPE, List.of(new Stroke("ESCAPE", ESCAPE)),
      "history", List.of(new Stroke("alt LEFT", BACK), new Stroke("alt RIGHT", FORWARD)),
      ROW_COPY, List.of(new Stroke("ctrl C", COPY_NAME), new Stroke("shift ctrl C", COPY_ID)),
      PIN, List.of(new Stroke("ctrl D", PIN)),
      TOGGLE_LIST, List.of(new Stroke("ctrl B", TOGGLE_LIST)),
      "window", List.of(new Stroke("F10", COMPACT_MODE), new Stroke("shift ctrl T", ALWAYS_ON_TOP)),
      "textSize", List.of(new Stroke("ctrl EQUALS", TEXT_BIGGER), new Stroke("shift ctrl EQUALS", TEXT_BIGGER),
          new Stroke("ctrl PLUS", TEXT_BIGGER), new Stroke("ctrl ADD", TEXT_BIGGER),
          new Stroke("ctrl MINUS", TEXT_SMALLER), new Stroke("ctrl SUBTRACT", TEXT_SMALLER),
          new Stroke("ctrl 0", TEXT_RESET), new Stroke("ctrl NUMPAD0", TEXT_RESET)));

  /** Every handler {@link #install} needs. */
  public static final Set<String> ROOT_HANDLERS = rootHandlers();

  private KeyMap() {}

  /** The keys {@code b} binds; empty for a {@link #DOCUMENTED_ONLY} row. */
  public static List<Stroke> strokes(Binding b) {
    return STROKES.getOrDefault(b.action(), List.of());
  }

  /** Whether {@link #install} binds the keys of {@code b} on the root pane. */
  public static boolean installsOnRoot(Binding b) {
    return !b.action().equals(DOCUMENTED_ONLY) && !b.action().equals(ROW_COPY);
  }

  /**
   * Binds every root key on {@code root} (in the focused window) to its handler in {@code handlers}, which must
   * have one for each of {@link #ROOT_HANDLERS}. {@code /} focuses search only while the focus owner is not a text
   * component, so a slash typed into a field stays a slash; the focus owner comes from the keyboard focus manager.
   */
  public static void install(JComponent root, Map<String, Runnable> handlers) {
    install(root, handlers, () -> KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
  }

  /** {@link #install(JComponent, Map)} with the focus owner from {@code focusOwner} (a test seam). */
  static void install(JComponent root, Map<String, Runnable> handlers, Supplier<Component> focusOwner) {
    List<String> missing = ROOT_HANDLERS.stream().filter(h -> !handlers.containsKey(h)).toList();
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException("No handler for " + missing);
    }
    for (Binding b : BINDINGS) {
      if (!installsOnRoot(b)) {
        continue;
      }
      for (Stroke s : strokes(b)) {
        if (s.handler().equals(SLASH_SEARCH)) {
          bind(root, JComponent.WHEN_IN_FOCUSED_WINDOW, s, handlers.get(FOCUS_SEARCH),
              () -> !(focusOwner.get() instanceof JTextComponent));
        } else {
          bind(root, JComponent.WHEN_IN_FOCUSED_WINDOW, s, handlers.get(s.handler()), () -> true);
        }
      }
    }
  }

  /**
   * Binds the row copy keys on {@code table} while it has the focus: Ctrl+C runs {@code copyName} and Ctrl+Shift+C
   * {@code copyId}. They act only while a row is selected; otherwise the keys go on as if unbound.
   */
  public static void installRowCopy(JTable table, Runnable copyName, Runnable copyId) {
    List<Stroke> strokes = STROKES.get(ROW_COPY);
    BooleanSupplier selected = () -> table.getSelectedRow() >= 0;
    bind(table, JComponent.WHEN_FOCUSED, strokes.get(0), copyName, selected);
    bind(table, JComponent.WHEN_FOCUSED, strokes.get(1), copyId, selected);
  }

  /** The Help › Keyboard shortcuts table: one read-only row per binding, its keys and what they do. */
  public static JTable helpTable() {
    JTable table = new JTable(new AbstractTableModel() {
      @Override
      public int getRowCount() {
        return BINDINGS.size();
      }

      @Override
      public int getColumnCount() {
        return 2;
      }

      @Override
      public String getColumnName(int column) {
        return column == 0 ? "Keys" : "Action";
      }

      @Override
      public Object getValueAt(int row, int column) {
        Binding b = BINDINGS.get(row);
        return column == 0 ? b.keys() : b.description();
      }
    });
    table.getColumnModel().getColumn(0).setPreferredWidth(240);
    table.getColumnModel().getColumn(1).setPreferredWidth(320);
    return table;
  }

  private static void bind(JComponent c, int condition, Stroke s, Runnable handler, BooleanSupplier when) {
    KeyStroke ks = KeyStroke.getKeyStroke(s.key());
    if (ks == null) {
      throw new IllegalStateException("Bad key " + s.key());
    }
    c.getInputMap(condition).put(ks, s.handler());
    c.getActionMap().put(s.handler(), new AbstractAction(s.handler()) {
      @Override
      public boolean isEnabled() {
        return when.getAsBoolean();
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        handler.run();
      }
    });
  }

  private static Set<String> rootHandlers() {
    Set<String> names = new LinkedHashSet<>();
    for (Binding b : BINDINGS) {
      if (installsOnRoot(b)) {
        strokes(b).forEach(s -> names.add(s.handler().equals(SLASH_SEARCH) ? FOCUS_SEARCH : s.handler()));
      }
    }
    return Set.copyOf(names);
  }
}
