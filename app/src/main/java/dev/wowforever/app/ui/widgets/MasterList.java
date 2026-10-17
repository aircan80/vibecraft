package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
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
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * The narrow results list left of the detail: a heading with the row count over two-line rows (name; kind and key
 * facts). Moving the selection with the keyboard previews the row once it has rested for
 * {@value #PREVIEW_DELAY_MILLIS} ms, so holding an arrow key through thousands of rows loads one page, not
 * thousands; Enter or a click on a row commits it at once (the further clicks of a double-click are ignored).
 */
public final class MasterList extends JPanel {
  /** How long the selection must rest before the row is previewed. */
  public static final int PREVIEW_DELAY_MILLIS = 120;
  /** The default width of the list, in logical pixels. */
  public static final int WIDTH = 300;
  /** What the list shows before any search. */
  public static final String PLACEHOLDER = "Search results appear here";
  private static final String ROWS = "rows";
  private static final String EMPTY = "empty";

  /** Runs a task once after a fixed delay; a seam so tests can let the delay elapse by hand. */
  public interface Delay {
    /** Runs {@code r} once after the delay. */
    void schedule(Runnable r);

    /** Drops every run not yet started. */
    void cancel();
  }

  /** A {@link Delay} on a one-shot Swing timer: the task runs on the EDT. */
  public static Delay timerDelay(int millis) {
    return new Delay() {
      private final Timer timer = new Timer(millis, e -> runTask());
      private Runnable task;

      {
        timer.setRepeats(false);
      }

      private void runTask() {
        Runnable r = task;
        task = null;
        if (r != null) {
          r.run();
        }
      }

      @Override
      public void schedule(Runnable r) {
        task = r;
        timer.restart();
      }

      @Override
      public void cancel() {
        timer.stop();
        task = null;
      }
    };
  }

  private static final ListEntry PROTOTYPE =
      new ListEntry(new EntityRef(EntityKind.ITEM, 0), "Prototype Name", "Kind · Facts", null, null);

  private final Delay delay;
  private final JLabel heading = new JLabel(" ");
  /** Shown in place of the rows (and the heading) before the first search: no "Results (0)" on Home. */
  private final JLabel placeholder = new JLabel(PLACEHOLDER) {
    @Override
    public Color getForeground() { // looked up per paint, so theme changes apply
      return Theme.current().textMuted();
    }

    @Override
    public Color getBackground() { // the empty list's own background, not the panel's
      return list == null ? super.getBackground() : list.getBackground();
    }
  };
  private final CardLayout cards = new CardLayout();
  private final JPanel body = new JPanel(cards);
  private final EntryRenderer renderer = new EntryRenderer();
  private final JList<ListEntry> list = new JList<>() {
    @Override
    public void updateUI() {
      super.updateUI();
      fitCells(this);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true; // rows are as wide as the list; long names end in an ellipsis instead of a scrollbar
    }
  };
  private Entries model = new Entries(List.of());
  private Consumer<ListEntry> onPreview = e -> {};
  private Consumer<ListEntry> onCommit = e -> {};

  /** A list that previews {@value #PREVIEW_DELAY_MILLIS} ms after the selection rests. */
  public MasterList() {
    this(timerDelay(PREVIEW_DELAY_MILLIS));
  }

  /** A list whose previews wait for {@code delay}. */
  public MasterList(Delay delay) {
    super(new BorderLayout());
    this.delay = Objects.requireNonNull(delay, "delay");
    heading.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
    heading.setFont(heading.getFont().deriveFont(Font.BOLD));
    list.setModel(model);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(renderer);
    fitCells(list);
    list.addPropertyChangeListener("font", e -> fitCells(list));
    list.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        selectionMoved();
      }
    });
    list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "commit");
    list.getActionMap().put("commit", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        commitSelected();
      }
    });
    list.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        // one click commits; the second click of a double-click is the same row again, so it is ignored
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
          int row = rowAt(e.getPoint());
          if (row >= 0) {
            list.setSelectedIndex(row);
            commitSelected();
          }
        }
      }
    });
    JScrollPane scroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    body.add(scroll, ROWS);
    body.add(placeholder, EMPTY);
    placeholder.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    placeholder.setVerticalAlignment(SwingConstants.TOP);
    placeholder.setOpaque(true);
    add(heading, BorderLayout.NORTH);
    add(body, BorderLayout.CENTER);
    clear();
    setPreferredSize(new Dimension(UIScale.scale(WIDTH), UIScale.scale(600)));
  }

  /** Called with the selected row once the selection has rested; replaces any earlier listener. */
  public void onPreview(Consumer<ListEntry> listener) {
    onPreview = Objects.requireNonNull(listener, "listener");
  }

  /**
   * Called with the row Enter or a single click commits, the pending preview dropped; replaces any earlier
   * listener.
   */
  public void onCommit(Consumer<ListEntry> listener) {
    onCommit = Objects.requireNonNull(listener, "listener");
  }

  /**
   * Shows {@code entries} under {@code "<title> (<rows>)"}, or {@code "<title> (first <rows>)"} when
   * {@code capped} says they are only the first page of a longer list.
   */
  public void show(String title, List<ListEntry> entries, boolean capped) {
    heading.setText(String.format(Locale.ROOT, capped ? "%s (first %,d)" : "%s (%,d)", title, entries.size()));
    setEntries(entries);
  }

  /**
   * Replaces the rows, unselected and scrolled to the top; a pending preview is dropped. The rows and the heading
   * take the place of the {@link #PLACEHOLDER}.
   */
  public void setEntries(List<ListEntry> entries) {
    model = new Entries(List.copyOf(entries));
    list.setModel(model); // clears the selection, which cancels the pending preview
    delay.cancel();
    if (!entries.isEmpty()) {
      list.ensureIndexIsVisible(0);
    }
    heading.setVisible(true);
    cards.show(body, ROWS);
  }

  /**
   * Empties the list and shows the muted {@link #PLACEHOLDER} without a heading, as before any search; the next
   * {@link #show} brings the heading back.
   */
  public void clear() {
    setEntries(List.of());
    heading.setText(" ");
    heading.setVisible(false);
    cards.show(body, EMPTY);
  }

  /** Whether the list shows the {@link #PLACEHOLDER} (no search yet) rather than rows under a heading. */
  public boolean isPlaceholderShown() {
    return placeholder.isVisible();
  }

  public JLabel placeholder() {
    return placeholder;
  }

  public JLabel headingLabel() {
    return heading;
  }

  /** Selects row {@code index} and scrolls to it, as the arrow keys do; the preview follows after the delay. */
  public void selectIndex(int index) {
    list.setSelectedIndex(index);
    list.ensureIndexIsVisible(index);
  }

  /**
   * Selects the row of {@code ref} and scrolls to it without previewing it (its page is already shown); returns
   * false, the selection left alone, when no row has {@code ref}.
   */
  public boolean selectQuietly(EntityRef ref) {
    for (int i = 0; i < model.getSize(); i++) {
      if (model.getElementAt(i).ref().equals(ref)) {
        list.setSelectedIndex(i);
        list.ensureIndexIsVisible(i);
        delay.cancel();
        return true;
      }
    }
    return false;
  }

  /** Commits the selected row now, dropping its pending preview; nothing happens without a selection. */
  public void commitSelected() {
    delay.cancel();
    ListEntry e = list.getSelectedValue();
    if (e != null) {
      onCommit.accept(e);
    }
  }

  public List<ListEntry> entries() {
    return model.rows;
  }

  public Optional<ListEntry> selected() {
    return Optional.ofNullable(list.getSelectedValue());
  }

  /** The heading as shown, e.g. {@code "Armor (12)"}. */
  public String heading() {
    return heading.getText();
  }

  public JList<ListEntry> jlist() {
    return list;
  }

  private void selectionMoved() {
    delay.cancel();
    if (list.getSelectedValue() != null) {
      delay.schedule(this::previewSelected);
    }
  }

  private void previewSelected() {
    ListEntry e = list.getSelectedValue();
    if (e != null) {
      onPreview.accept(e);
    }
  }

  private int rowAt(Point p) {
    int row = list.locationToIndex(p);
    if (row < 0) {
      return -1;
    }
    Rectangle cell = list.getCellBounds(row, row);
    return cell != null && cell.contains(p) ? row : -1;
  }

  /**
   * Every row is as tall as the two-line prototype and has a fixed width, so even a list of every item lays out
   * without measuring each row (the rows still fill the viewport).
   */
  private static void fitCells(JList<ListEntry> list) {
    if (list.getCellRenderer() instanceof EntryRenderer r) {
      list.setFixedCellHeight(
          r.getListCellRendererComponent(list, PROTOTYPE, 0, false, false).getPreferredSize().height);
      list.setFixedCellWidth(UIScale.scale(WIDTH));
    }
  }

  /** An immutable list model. */
  private static final class Entries extends AbstractListModel<ListEntry> {
    private final List<ListEntry> rows;

    Entries(List<ListEntry> rows) {
      this.rows = rows;
    }

    @Override
    public int getSize() {
      return rows.size();
    }

    @Override
    public ListEntry getElementAt(int index) {
      return rows.get(index);
    }
  }

  /** Draws an entry as its title over a smaller, muted subtitle. */
  public static final class EntryRenderer extends JPanel implements ListCellRenderer<ListEntry> {
    private final JLabel title = new JLabel();
    private final JLabel subtitle = new JLabel();
    private Font base;
    private Font small;

    EntryRenderer() {
      super(new BorderLayout(0, 1));
      setBorder(BorderFactory.createEmptyBorder(4, 10, 5, 10));
      add(title, BorderLayout.NORTH);
      add(subtitle, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends ListEntry> list, ListEntry value, int index,
        boolean isSelected, boolean cellHasFocus) {
      Font font = list.getFont();
      if (font != null && !font.equals(base)) {
        base = font;
        small = font.deriveFont(font.getSize2D() - 1f);
      }
      title.setFont(base);
      subtitle.setFont(small);
      title.setText(value == null ? "" : value.title());
      subtitle.setText(value == null || value.subtitle().isEmpty() ? " " : value.subtitle());
      Theme theme = Theme.current();
      if (isSelected) {
        setBackground(list.getSelectionBackground());
        title.setForeground(list.getSelectionForeground());
        subtitle.setForeground(list.getSelectionForeground());
      } else {
        setBackground(list.getBackground());
        title.setForeground(theme.textPrimary());
        subtitle.setForeground(theme.textMuted());
      }
      setOpaque(true);
      return this;
    }

    /**
     * Lays the two labels out directly: a renderer is validated by the list's cell renderer pane before each
     * paint, and a plain container skips layout while it has no native peer (headless printing, tests).
     */
    @Override
    public void validate() {
      doLayout();
    }

    public JLabel title() {
      return title;
    }

    public JLabel subtitle() {
      return subtitle;
    }
  }
}
