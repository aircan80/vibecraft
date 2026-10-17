package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.LookAndFeel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;

/**
 * The type-ahead list under the search box: the kind chips on top ({@link KindChips}, which wrap to the list's width),
 * then the top {@value #MAX_HITS} hits grouped by kind (groups in the order of their best hit) and a "Show all n
 * results" row; or the recent searches under "Recent", without the chips. A chip clicked narrows the hits shown to its
 * kind at once and tells {@link #onKindSelect}'s listener, which looks the kind's own hits up. The search box keeps the
 * keyboard focus: {@link #install} binds Up/Down (move the selection, skipping headings), Enter (open the selected row,
 * else the top hit, else the full search), Shift+Enter (the full search) and Esc (close) in its input map. Up, Down and
 * Esc are left alone while the list is closed. Where the list shows is a {@link Host}: a non-focusable {@link JWindow}
 * in the app, a stand-in in tests.
 */
public final class SearchDropdown {
  /** Most hits shown. */
  public static final int MAX_HITS = 8;
  /** Tallest the list grows before it scrolls, in logical pixels. */
  static final int MAX_HEIGHT = 520;
  /** Narrowest the list is, in logical pixels, however narrow the search box. */
  static final int MIN_WIDTH = 420;

  /** One row of the list. */
  public sealed interface Row {
    /** A group heading (a kind, "Recent", or "No matches"); never selected. */
    record Heading(String text) implements Row {}

    /** A hit that opens its page. */
    record Hit(ListEntry entry) implements Row {}

    /** A recent search that runs again. */
    record Recent(String text) implements Row {}

    /** Runs the full search; {@code total} is how many results it has. */
    record ShowAll(int total) implements Row {}
  }

  /** What choosing a row asks for. */
  public interface Listener {
    /** Opens the page of {@code hit}. */
    void open(ListEntry hit);

    /** Runs the full search for the text in the box (Show all, Shift+Enter, or Enter with no hit to open). */
    void showAll();

    /** Runs the recent search {@code text}. */
    void searchRecent(String text);

    /** The user closed the list with Esc; nothing should reopen it until the text changes again. */
    default void closed() {}
  }

  /** Puts the list on screen under the search box. */
  public interface Host {
    /**
     * Shows {@code content}, {@code height} pixels tall, under {@code anchor} (again, resized to its new rows, when
     * already showing); returns whether it is on screen (not when the anchor is not).
     */
    boolean show(JComponent anchor, JComponent content, int height);

    /** Takes the list off screen. */
    void hide();
  }

  private final Host host;
  private final JList<Row> list = new JList<>();
  private final KindChips chips = new KindChips();
  private final JScrollPane scroll;
  /** What goes on screen: the chips over the scrolling list, in the list's border. */
  private final JPanel content = new JPanel(new BorderLayout());
  private List<Row> rows = List.of();
  /** The hits last given ({@link #showHits}) and their total, which a chip narrows. */
  private List<ListEntry> hits = List.of();
  private int hitsTotal;
  private Consumer<Optional<EntityKind>> onKind = k -> {};
  private JTextField field;
  private Listener listener = new Listener() {
    @Override
    public void open(ListEntry hit) {}

    @Override
    public void showAll() {}

    @Override
    public void searchRecent(String text) {}
  };
  private boolean showing;
  /** Whether the text changed since the rows were shown, so Enter must not open a hit nobody chose. */
  private boolean stale;

  /** A dropdown in a non-focusable window under the search box. */
  public SearchDropdown() {
    this(windowHost());
  }

  public SearchDropdown(Host host) {
    this.host = Objects.requireNonNull(host, "host");
    list.setModel(new Rows(rows));
    list.setFocusable(false);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new RowRenderer());
    MouseAdapter mouse = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        int row = rowAt(e.getPoint());
        if (row >= 0 && selectable(rows.get(row))) {
          list.setSelectedIndex(row);
        }
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        int row = rowAt(e.getPoint());
        if (SwingUtilities.isLeftMouseButton(e) && row >= 0 && selectable(rows.get(row))) {
          choose(rows.get(row));
        }
      }
    };
    list.addMouseListener(mouse);
    list.addMouseMotionListener(mouse);
    scroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    chips.setBorder(BorderFactory.createEmptyBorder(2, 6, 0, 6));
    chips.setOpaque(true);
    chips.setVisible(false);
    chips.onSelect(kind -> {
      narrow(kind);
      onKind.accept(kind);
    });
    content.add(chips, BorderLayout.NORTH);
    content.add(scroll, BorderLayout.CENTER);
  }

  /** The kind chips of the top row. */
  public KindChips kindChips() {
    return chips;
  }

  /**
   * Called with the kind of a chip the user clicks, after the hits shown are narrowed to it; replaces any earlier
   * listener.
   */
  public void onKindSelect(Consumer<Optional<EntityKind>> listener) {
    onKind = Objects.requireNonNull(listener, "listener");
  }

  /** Binds the dropdown keys in {@code field}'s input map; choices go to {@code listener}. */
  public void install(JTextField field, Listener listener) {
    this.field = Objects.requireNonNull(field, "field");
    this.listener = Objects.requireNonNull(listener, "listener");
    bind(field, "DOWN", "searchDropdown.down", true, () -> move(1));
    bind(field, "UP", "searchDropdown.up", true, () -> move(-1));
    bind(field, "ESCAPE", "searchDropdown.close", true, () -> {
      hide();
      this.listener.closed();
    });
    bind(field, "ENTER", "searchDropdown.enter", false, this::enter);
    bind(field, "shift ENTER", "searchDropdown.showAll", false, () -> {
      hide();
      this.listener.showAll();
    });
  }

  /**
   * Shows the first {@value #MAX_HITS} of {@code hits} (best first) grouped by kind, then "Show all" with
   * {@code total} results (at least as many as shown), or "No matches" when there are none.
   */
  public void showHits(List<ListEntry> hits, int total) {
    this.hits = List.copyOf(hits);
    this.hitsTotal = total;
    chips.setVisible(true);
    render(hits, total);
  }

  /**
   * Narrows the hits shown to {@code kind} (all of them for All), the "Show all" total being the kind's count on its
   * chip; nothing while the list shows no hits.
   */
  private void narrow(Optional<EntityKind> kind) {
    if (!showing || !chips.isVisible()) {
      return;
    }
    List<ListEntry> kept = hits.stream().filter(h -> kind.isEmpty() || h.ref().kind() == kind.get()).toList();
    render(kept, kind.isEmpty() ? hitsTotal : chips.count(kind).orElse(kept.size()));
  }

  private void render(List<ListEntry> hits, int total) {
    List<ListEntry> top = hits.subList(0, Math.min(MAX_HITS, hits.size()));
    Map<EntityKind, List<ListEntry>> groups = new LinkedHashMap<>();
    for (ListEntry hit : top) {
      groups.computeIfAbsent(hit.ref().kind(), k -> new ArrayList<>()).add(hit);
    }
    List<Row> r = new ArrayList<>();
    groups.forEach((kind, entries) -> {
      r.add(new Row.Heading(KindChips.label(kind)));
      entries.forEach(e -> r.add(new Row.Hit(e)));
    });
    if (top.isEmpty()) {
      r.add(new Row.Heading("No matches"));
    } else {
      r.add(new Row.ShowAll(Math.max(total, top.size())));
    }
    show(r);
  }

  /** Shows {@code recent} under "Recent"; with none, closes the list. */
  public void showRecents(List<String> recent) {
    if (recent.isEmpty()) {
      hide();
      setRows(List.of());
      return;
    }
    chips.setVisible(false);
    List<Row> r = new ArrayList<>();
    r.add(new Row.Heading("Recent"));
    recent.forEach(t -> r.add(new Row.Recent(t)));
    show(r);
  }

  /** Takes the list off screen, its selection dropped. */
  public void hide() {
    list.clearSelection();
    if (showing) {
      showing = false;
      host.hide();
    }
  }

  /**
   * Notes that the search text changed after these rows were shown: the selection is dropped, and Enter runs the
   * full search instead of opening the top hit until new rows arrive.
   */
  public void markStale() {
    stale = true;
    list.clearSelection();
  }

  public boolean isShowing() {
    return showing;
  }

  /** The rows, top to bottom. */
  public List<Row> rows() {
    return rows;
  }

  /** The selected row, if any. */
  public Optional<Row> selected() {
    return Optional.ofNullable(list.getSelectedValue());
  }

  public JList<Row> jlist() {
    return list;
  }

  private void show(List<Row> r) {
    setRows(r);
    stale = false;
    Color line = UIManager.getColor("Component.borderColor"); // read on each show: the theme may have changed
    content.setBorder(BorderFactory.createLineBorder(line != null ? line : Theme.current().textMuted()));
    chips.setBackground(list.getBackground());
    showing = host.show(field, content, preferredHeight());
  }

  /**
   * How tall the dropdown wants to be: every row at its own height and the border, so that a short list has no empty
   * space and a long one no scrollbar, up to {@value #MAX_HEIGHT} logical pixels, then the chips over it (wrapped to
   * the dropdown's width, {@link #chipRowHeight}).
   */
  int preferredHeight() {
    ListCellRenderer<? super Row> renderer = list.getCellRenderer();
    int height = content.getInsets().top + content.getInsets().bottom;
    for (int i = 0; i < rows.size(); i++) {
      height += renderer.getListCellRendererComponent(list, rows.get(i), i, false, false).getPreferredSize().height;
    }
    return Math.min(height, UIScale.scale(MAX_HEIGHT)) + chipRowHeight(); // the cap is the list's: the chips stay
  }

  /**
   * The height of the chips' row at the width the dropdown shows at (the search box's, at least {@value #MIN_WIDTH}
   * logical pixels, as {@link #windowHost} sizes it); 0 while the chips are hidden.
   */
  int chipRowHeight() {
    if (!chips.isVisible()) {
      return 0;
    }
    int width = Math.max(field == null ? 0 : field.getWidth(), UIScale.scale(MIN_WIDTH))
        - content.getInsets().left - content.getInsets().right;
    chips.setWrapWidth(width);
    return chips.heightAt(width);
  }

  private void setRows(List<Row> r) {
    rows = List.copyOf(r);
    list.setModel(new Rows(rows));
  }

  private void enter() {
    Optional<Row> chosen = selected();
    if (showing && chosen.isEmpty() && !stale) {
      chosen = rows.stream().filter(r -> r instanceof Row.Hit).findFirst();
    }
    if (showing && chosen.isPresent()) {
      choose(chosen.get());
    } else {
      hide();
      listener.showAll();
    }
  }

  private void choose(Row row) {
    hide();
    switch (row) {
      case Row.Hit h -> listener.open(h.entry());
      case Row.ShowAll s -> listener.showAll();
      case Row.Recent r -> listener.searchRecent(r.text());
      case Row.Heading h -> { }
    }
  }

  /** Moves the selection {@code delta} (±1) selectable rows; up from the first row returns to the search box. */
  private void move(int delta) {
    int i = list.getSelectedIndex();
    for (int j = i + delta; j >= 0 && j < rows.size(); j += delta) {
      if (selectable(rows.get(j))) {
        list.setSelectedIndex(j);
        list.ensureIndexIsVisible(j);
        return;
      }
    }
    if (delta < 0) {
      list.clearSelection();
    }
  }

  private static boolean selectable(Row row) {
    return !(row instanceof Row.Heading);
  }

  private int rowAt(Point p) {
    int row = list.locationToIndex(p);
    if (row < 0) {
      return -1;
    }
    Rectangle cell = list.getCellBounds(row, row);
    return cell != null && cell.contains(p) ? row : -1;
  }

  /** Binds {@code key} on {@code field} to {@code run}; when {@code onlyShowing}, only while the list is open. */
  private void bind(JTextField field, String key, String name, boolean onlyShowing, Runnable run) {
    field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), name);
    field.getActionMap().put(name, new AbstractAction() {
      @Override
      public boolean isEnabled() {
        return !onlyShowing || showing;
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        run.run();
      }
    });
  }

  /**
   * A {@link Host} showing the list in a non-focusable window just under the search box, as wide as the box (at
   * least {@value #MIN_WIDTH} logical pixels) and as tall as its rows (at most {@value #MAX_HEIGHT}); it follows
   * the main window when that moves or resizes.
   */
  public static Host windowHost() {
    return new Host() {
      private JWindow window;
      private JComponent anchor;
      private JComponent shown;
      private int height;
      private LookAndFeel themed;
      private final ComponentAdapter follow = new ComponentAdapter() {
        @Override
        public void componentMoved(ComponentEvent e) {
          place();
        }

        @Override
        public void componentResized(ComponentEvent e) {
          place();
        }
      };

      @Override
      public boolean show(JComponent anchor, JComponent content, int height) {
        if (anchor == null || !anchor.isShowing()) {
          hide();
          return false;
        }
        Window owner = SwingUtilities.getWindowAncestor(anchor);
        if (window == null || window.getOwner() != owner) {
          if (window != null) {
            window.getOwner().removeComponentListener(follow);
            window.dispose();
          }
          window = new JWindow(owner);
          window.setFocusableWindowState(false);
          window.setType(Window.Type.POPUP);
          owner.addComponentListener(follow);
        }
        if (shown != content) {
          window.getContentPane().removeAll();
          window.getContentPane().add(content);
          shown = content;
        }
        this.anchor = anchor;
        this.height = height;
        // a theme switch updates the main window's tree only, which this window is not part of
        if (themed != UIManager.getLookAndFeel()) {
          themed = UIManager.getLookAndFeel();
          SwingUtilities.updateComponentTreeUI(window);
        }
        place();
        window.setVisible(true);
        return true;
      }

      @Override
      public void hide() {
        if (window != null) {
          window.setVisible(false);
        }
      }

      private void place() {
        if (window == null || anchor == null || !anchor.isShowing()) {
          return;
        }
        Point p = anchor.getLocationOnScreen();
        int width = Math.max(anchor.getWidth(), UIScale.scale(MIN_WIDTH));
        int x = left(p.x, width, anchor.getGraphicsConfiguration().getBounds());
        window.setBounds(x, p.y + anchor.getHeight() + UIScale.scale(2), width, height);
        window.validate();
      }
    };
  }

  /**
   * The left edge of a dropdown {@code width} wide under a field starting at {@code anchorX}: the field's own,
   * moved left as far as needed to end on {@code screen} (the narrow compact window sits at a screen's right edge),
   * but never past the screen's left edge.
   */
  static int left(int anchorX, int width, Rectangle screen) {
    return Math.max(screen.x, Math.min(anchorX, screen.x + screen.width - width));
  }

  /** An immutable list model. */
  private static final class Rows extends AbstractListModel<Row> {
    private final List<Row> rows;

    Rows(List<Row> rows) {
      this.rows = rows;
    }

    @Override
    public int getSize() {
      return rows.size();
    }

    @Override
    public Row getElementAt(int index) {
      return rows.get(index);
    }
  }

  /** Hits as two-line entries; headings small, bold and muted; recents plain; "Show all" in the link colour. */
  private static final class RowRenderer implements ListCellRenderer<Row> {
    private final MasterList.EntryRenderer entry = new MasterList.EntryRenderer();
    private final JLabel label = new JLabel();
    private final Border headingBorder = BorderFactory.createEmptyBorder(6, 10, 2, 10);
    private final Border recentBorder = BorderFactory.createEmptyBorder(4, 10, 4, 10);
    private final Border showAllBorder = BorderFactory.createEmptyBorder(6, 10, 6, 10);

    @Override
    @SuppressWarnings("unchecked") // the entry renderer reads only the list's font and colours
    public Component getListCellRendererComponent(JList<? extends Row> list, Row row, int index,
        boolean isSelected, boolean cellHasFocus) {
      if (row instanceof Row.Hit h) {
        return entry.getListCellRendererComponent((JList<ListEntry>) (JList<?>) list, h.entry(), index, isSelected,
            cellHasFocus);
      }
      Theme theme = Theme.current();
      Font font = list.getFont();
      label.setOpaque(true);
      label.setIcon(null);
      label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
      switch (row) {
        case Row.Heading h -> {
          label.setText(h.text());
          label.setFont(font == null ? null : font.deriveFont(Font.BOLD, font.getSize2D() - 1f));
          label.setForeground(theme.textMuted());
          label.setBorder(headingBorder);
        }
        case Row.Recent r -> {
          label.setText(r.text());
          label.setFont(font);
          label.setForeground(isSelected ? list.getSelectionForeground() : theme.textPrimary());
          label.setBorder(recentBorder);
        }
        case Row.ShowAll s -> {
          label.setText(s.total() == 1 ? "Show 1 result"
              : String.format(Locale.ROOT, "Show all %,d results", s.total()));
          label.setFont(font);
          label.setForeground(isSelected ? list.getSelectionForeground() : theme.link());
          label.setBorder(showAllBorder);
        }
        case Row.Hit h -> throw new AssertionError(h);
      }
      return label;
    }
  }
}
