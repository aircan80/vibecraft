package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Locale;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * One kind of source on a page that scrolls as a whole: a gold heading with the count ({@code "Dropped by (3)"})
 * over an {@linkplain LinkTable#inline inline} link table, as tall as all its rows. The section never stretches
 * taller than that, so a vertical box of sections stacks them tightly.
 */
public final class SourceSection extends JPanel {
  private final String title;
  private final JLabel heading;
  private final LinkTable<?> table;

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
        return Theme.current().accentGold();
      }
    };
    heading.setFont(heading.getFont().deriveFont(Font.BOLD));
    heading.setBorder(BorderFactory.createEmptyBorder(14, 0, 6, 0));
    setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
    setAlignmentX(LEFT_ALIGNMENT);
    add(heading, BorderLayout.NORTH);
    add(table, BorderLayout.CENTER);
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
