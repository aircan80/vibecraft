package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.Locale;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * One kind of source on a page that scrolls as a whole: a gold heading with the count ({@code "Dropped by (3)"})
 * over an {@linkplain LinkTable#inline inline} link table, as tall as all its rows, and optionally a muted note under
 * the table ({@link #setNote}). The section never stretches taller than that, so a vertical box of sections stacks
 * them tightly.
 */
public final class SourceSection extends JPanel {
  /** The gap between a lead and the table under it, in pixels. */
  static final int LEAD_GAP = 6;
  private final String title;
  private final JLabel heading;
  private final LinkTable<?> table;
  private JLabel note;

  public SourceSection(String title, int count, LinkTable<?> table) {
    super(new BorderLayout());
    this.title = Objects.requireNonNull(title, "title");
    this.table = Objects.requireNonNull(table, "table");
    if (!table.isInline()) {
      throw new IllegalArgumentException("A source section needs an inline link table: " + title);
    }
    heading = new JLabel(headingText(title, count)) {
      @Override
      public Color getForeground() { // looked up per paint, so theme changes apply
        return Theme.current().goldText();
      }
    };
    heading.setFont(heading.getFont().deriveFont(Font.BOLD));
    heading.setBorder(BorderFactory.createEmptyBorder(14, 0, 6, 0));
    table.table().getAccessibleContext().setAccessibleName(title); // a screen reader names the table
    setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
    setAlignmentX(LEFT_ALIGNMENT);
    add(heading, BorderLayout.NORTH);
    add(table, BorderLayout.CENTER);
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
      remove(note);
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
      add(note, BorderLayout.SOUTH);
    }
    revalidate();
  }

  /** The note under the table; null when there is none. */
  public JLabel note() {
    return note;
  }

  /** Shows {@code count} in the heading, for a section whose table was given new rows. */
  public void setCount(int count) {
    heading.setText(headingText(title, count));
  }

  private static String headingText(String title, int count) {
    return String.format(Locale.ROOT, "%s (%,d)", title, count);
  }

  /** The kind of source, without the count: {@code "Dropped by"}. */
  public String title() {
    return title;
  }

  public JLabel heading() {
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
