package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.ui.LinkRenderer;
import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionListener;

/**
 * One kind of source on a page that scrolls as a whole: a {@link SectionHeading} with the count
 * ({@code "Dropped by (3)"}) over an {@linkplain LinkTable#inline inline} link table, optionally a muted note under the
 * table ({@link #setNote}) and further controls under that ({@link #addFooter}). The section never stretches taller
 * than its content, so a vertical box of sections stacks them tightly.
 *
 * <p>The table shows its first {@value #ROW_LIMIT} rows and a "Show all 34" button under them ({@link #showAll});
 * selecting a row below the cut (arrowing down) shows them all too. Inside the compact window
 * ({@link CompactColumns#within}) the section is collapsible: a click on its heading (or Enter or Space while the
 * heading has the focus) shows or hides everything but the heading, and every section but the first of its container
 * starts collapsed, reading {@code "Heading (count)"}. It follows the window it is moved into, as the table does.
 */
public final class SourceSection extends JPanel {
  /** How many rows a section shows before its "Show all" button. */
  public static final int ROW_LIMIT = 8;
  /** The gap between a lead and the table under it, in pixels. */
  static final int LEAD_GAP = 6;
  /** The heading glyph of a collapsed collapsible section. */
  static final String COLLAPSED_GLYPH = "▸";
  /** The heading glyph of an expanded collapsible section. */
  static final String EXPANDED_GLYPH = "▾";

  private final String title;
  private final SectionHeading heading;
  private final LinkTable<?> table;
  private final JButton showAll;
  private final JPanel showAllRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
  private final JPanel foot = new JPanel();
  private final JLabel glyph = new JLabel(EXPANDED_GLYPH) {
    @Override
    public Color getForeground() { // looked up per paint, so theme changes apply
      Theme t = Theme.current();
      return t != null ? t.goldText() : super.getForeground();
    }
  };
  /** What each part under the heading showed before the section collapsed (a filter may have hidden the table). */
  private final Map<Component, Boolean> shownBeforeCollapse = new IdentityHashMap<>();
  private JLabel note;
  private int count;
  private boolean allShown;
  private boolean collapsible;
  private boolean collapsed;
  /** Whether the section was last put inside the compact window; null before it was put anywhere. */
  private Boolean inCompact;

  public SourceSection(String title, int count, LinkTable<?> table) {
    super(new BorderLayout());
    this.title = Objects.requireNonNull(title, "title");
    this.table = Objects.requireNonNull(table, "table");
    if (!table.isInline()) {
      throw new IllegalArgumentException("A source section needs an inline link table: " + title);
    }
    this.count = count;
    heading = new SectionHeading(title, count);
    table.table().getAccessibleContext().setAccessibleName(title); // a screen reader names the table
    setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
    setAlignmentX(LEFT_ALIGNMENT);
    showAll = linkButton(this::showAll);
    showAll.getAccessibleContext().setAccessibleDescription("Shows every row of " + title);
    showAllRow.setOpaque(false);
    showAllRow.setAlignmentX(LEFT_ALIGNMENT);
    showAllRow.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
    showAllRow.add(showAll);
    foot.setLayout(new BoxLayout(foot, BoxLayout.PAGE_AXIS));
    foot.setOpaque(false);
    foot.add(showAllRow);
    add(heading, BorderLayout.NORTH);
    add(table, BorderLayout.CENTER);
    add(foot, BorderLayout.SOUTH);
    table.model().addTableModelListener(e -> updateLimit());
    ListSelectionListener belowTheCut = e -> {
      if (!allShown && table.table().getSelectedRow() >= ROW_LIMIT) {
        showAll();
      }
    };
    table.table().getSelectionModel().addListSelectionListener(belowTheCut);
    table.table().addPropertyChangeListener("selectionModel", e -> { // DividerRows puts in its own model
      if (e.getOldValue() instanceof ListSelectionModel old) {
        old.removeListSelectionListener(belowTheCut);
      }
      if (e.getNewValue() instanceof ListSelectionModel now) {
        now.addListSelectionListener(belowTheCut);
      }
    });
    installToggle();
    addHierarchyListener(e -> {
      if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) {
        followWindow();
      }
    });
    updateLimit();
  }

  /** A borderless button in the link colour, with a hand cursor, that runs {@code action}. */
  private static JButton linkButton(Runnable action) {
    JButton b = new JButton() {
      @Override
      public Color getForeground() { // looked up per paint, so theme changes apply
        return LinkRenderer.linkColor();
      }
    };
    b.setBorderPainted(false);
    b.setContentAreaFilled(false);
    b.setFocusPainted(false);
    b.setMargin(new Insets(0, 0, 0, 0));
    b.setBorder(BorderFactory.createEmptyBorder());
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    b.addActionListener(e -> action.run());
    return b;
  }

  /** {@code "Show all 1,234"}. */
  static String showAllText(int n) {
    return String.format(Locale.ROOT, "Show all %,d", n);
  }

  /**
   * Cuts the table at {@value #ROW_LIMIT} rows, with the button under it, while it has more rows than that and the
   * user has not asked for all of them. The button counts what the heading counts (at least the rows).
   */
  private void updateLimit() {
    int rows = table.table().getRowCount();
    table.setRowLimit(allShown ? 0 : ROW_LIMIT);
    showAll.setText(showAllText(Math.max(count, rows)));
    boolean cut = !allShown && rows > ROW_LIMIT && table.isVisible();
    showAll.setVisible(cut);
    showAllRow.setVisible(cut);
    revalidate();
    repaint();
  }

  /** Shows every row and takes the "Show all" button away. */
  public void showAll() {
    allShown = true;
    updateLimit();
  }

  /** The "Show all 34" button under the cut table; hidden while every row shows. */
  public JButton showAllButton() {
    return showAll;
  }

  /** How many table rows show: at most {@value #ROW_LIMIT} until {@link #showAll}. */
  public int shownRows() {
    return table.shownRows();
  }

  /**
   * Puts the section in or out of the compact window's state as it moves: collapsible, and collapsed unless it is
   * the first section of its container; or neither. A move that stays in the same kind of window keeps what the
   * user opened or closed.
   */
  private void followWindow() {
    boolean compact = CompactColumns.within(this);
    if (inCompact != null && inCompact == compact) {
      return;
    }
    inCompact = compact;
    setCollapsible(compact);
    setCollapsed(compact && !isFirstInParent());
  }

  /** Whether no other source section comes before this one in its container. */
  private boolean isFirstInParent() {
    Container parent = getParent();
    if (parent == null) {
      return true;
    }
    for (Component c : parent.getComponents()) {
      if (c instanceof SourceSection) {
        return c == this;
      }
    }
    return true;
  }

  /** A click on the heading, or Enter or Space on it, folds a collapsible section. */
  private void installToggle() {
    MouseAdapter click = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (collapsible) {
          setCollapsed(!collapsed);
        }
      }
    };
    heading.addMouseListener(click);
    heading.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (collapsible && (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE)) {
          setCollapsed(!collapsed);
          e.consume();
        }
      }
    });
  }

  /**
   * Makes the heading a toggle that shows or hides the rest of the section, with a {@value #COLLAPSED_GLYPH} or
   * {@value #EXPANDED_GLYPH} glyph before the title; or a plain heading again, the section expanded.
   */
  public void setCollapsible(boolean collapsible) {
    this.collapsible = collapsible;
    heading.setFocusable(collapsible);
    heading.setCursor(collapsible ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null);
    heading.remove(glyph);
    if (collapsible) {
      heading.add(glyph, BorderLayout.WEST);
    } else {
      setCollapsed(false);
    }
    heading.revalidate();
    heading.repaint();
  }

  public boolean isCollapsible() {
    return collapsible;
  }

  /** Shows the heading alone (with its count), or the whole section again. */
  public void setCollapsed(boolean collapsed) {
    glyph.setText(collapsed ? COLLAPSED_GLYPH : EXPANDED_GLYPH);
    if (collapsed == this.collapsed) {
      return;
    }
    this.collapsed = collapsed;
    for (Component c : getComponents()) {
      if (c == heading) {
        continue;
      }
      if (collapsed) {
        shownBeforeCollapse.put(c, c.isVisible());
        c.setVisible(false);
      } else {
        c.setVisible(shownBeforeCollapse.getOrDefault(c, true));
      }
    }
    if (!collapsed) {
      shownBeforeCollapse.clear();
    }
    revalidate();
    repaint();
  }

  public boolean isCollapsed() {
    return collapsed;
  }

  /**
   * Puts {@code lead} (say, a map) between the heading and the table, as wide as the table, 6 px above it. The table
   * always keeps its full height; the lead takes the rest ({@link AboveTable}).
   */
  public void addAboveTable(JComponent lead) {
    JPanel body = new JPanel();
    body.setLayout(new AboveTable(lead, table, LEAD_GAP));
    body.setOpaque(false);
    remove(table);
    body.add(lead);
    body.add(table);
    body.setVisible(!collapsed);
    add(body, BorderLayout.CENTER);
  }

  /**
   * A lead over a table, {@code gap} px apart, each as wide as the container. The table gets its preferred height
   * and the lead whatever height is left. A map's preferred height follows its width, so the section may have been
   * measured while the map was another width (a page's scrollbar came or went since); then the map, not the table,
   * takes the difference until the map lays the page out again.
   */
  static final class AboveTable implements LayoutManager {
    private final Component lead;
    private final Component table;
    private final int gap;

    AboveTable(Component lead, Component table, int gap) {
      this.lead = lead;
      this.table = table;
      this.gap = gap;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {}

    @Override
    public void removeLayoutComponent(Component comp) {}

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      Dimension l = lead.getPreferredSize();
      Dimension t = table.getPreferredSize();
      return withInsets(parent, Math.max(l.width, t.width), l.height + gap + t.height);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      Dimension l = lead.getMinimumSize();
      Dimension t = table.getPreferredSize();
      return withInsets(parent, Math.max(l.width, table.getMinimumSize().width), l.height + gap + t.height);
    }

    @Override
    public void layoutContainer(Container parent) {
      Insets in = parent.getInsets();
      int width = Math.max(0, parent.getWidth() - in.left - in.right);
      int height = Math.max(0, parent.getHeight() - in.top - in.bottom);
      int tableHeight = table.getPreferredSize().height;
      int leadHeight = Math.max(0, height - gap - tableHeight);
      lead.setBounds(in.left, in.top, width, leadHeight);
      table.setBounds(in.left, in.top + Math.max(0, height - tableHeight), width, tableHeight);
    }

    private static Dimension withInsets(Container parent, int width, int height) {
      Insets in = parent.getInsets();
      return new Dimension(width + in.left + in.right, height + in.top + in.bottom);
    }
  }

  /** Puts a muted line of {@code text} under the table, replacing an earlier one; null removes it. */
  public void setNote(String text) {
    if (note != null) {
      foot.remove(note);
      note = null;
    }
    if (text != null) {
      note = new JLabel(text) {
        @Override
        public Color getForeground() { // looked up per paint, so theme changes apply
          return Theme.current().textMuted();
        }
      };
      note.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
      note.setAlignmentX(LEFT_ALIGNMENT);
      foot.add(note, 1); // under the "Show all" row, above any footer
    }
    revalidate();
  }

  /** The note under the table; null when there is none. */
  public JLabel note() {
    return note;
  }

  /** Puts {@code footer} (say, a row of buttons) at the bottom of the section, under the note. */
  public void addFooter(JComponent footer) {
    footer.setAlignmentX(LEFT_ALIGNMENT);
    foot.add(footer);
    revalidate();
  }

  /** Shows {@code count} in the heading and the "Show all" button, for a section whose table was given new rows. */
  public void setCount(int count) {
    this.count = count;
    heading.setCount(count);
    updateLimit();
  }

  /** The kind of source, without the count: {@code "Dropped by"}. */
  public String title() {
    return title;
  }

  /** The heading: title and count, an optional icon and trailing control. */
  public SectionHeading heading() {
    return heading;
  }

  public LinkTable<?> table() {
    return table;
  }

  @Override
  public Dimension getMaximumSize() {
    return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
  }
}
