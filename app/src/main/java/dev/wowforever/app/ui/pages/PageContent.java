package dev.wowforever.app.ui.pages;

import dev.wowforever.app.ui.theme.Theme;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/**
 * The body of a page that scrolls as a whole: its children top to bottom at their preferred heights
 * ({@link TopStack}), as wide as the viewport (tables fit the width instead of scrolling sideways), at least as tall
 * as it, children aligned left. A page shorter than the viewport leaves the space below its last child empty
 * instead of stretching a child into it.
 */
final class PageContent extends JPanel implements Scrollable {
  private static final int UNIT = 16;

  PageContent() {
    setLayout(new TopStack());
  }

  /** A label in the muted text colour, looked up per paint so theme changes apply. */
  static JLabel mutedLabel(String text) {
    JLabel label = new JLabel(text) {
      @Override
      public Color getForeground() {
        return Theme.current().textMuted();
      }
    };
    label.setAlignmentX(LEFT_ALIGNMENT);
    return label;
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
    return UNIT;
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
    return orientation == SwingConstants.VERTICAL ? Math.max(UNIT, visible.height - UNIT) : visible.width;
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return getParent() != null && getParent().getHeight() > getPreferredSize().height;
  }

  /**
   * A vertical stack anchored at the top: each visible child at its preferred height, as wide as the container
   * allows within its minimum and maximum widths, placed by its X alignment. Unlike a {@code BoxLayout}, extra
   * height is never handed to the children (a panel's unbounded maximum height would take it all and push the rest
   * of the page to the bottom); it stays below the last one.
   */
  static final class TopStack implements LayoutManager {
    @Override
    public void addLayoutComponent(String name, Component comp) {}

    @Override
    public void removeLayoutComponent(Component comp) {}

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      return size(parent, false);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      return size(parent, true);
    }

    private static Dimension size(Container parent, boolean minimum) {
      int width = 0;
      int height = 0;
      for (Component c : parent.getComponents()) {
        if (c.isVisible()) {
          width = Math.max(width, (minimum ? c.getMinimumSize() : c.getPreferredSize()).width);
          height += c.getPreferredSize().height;
        }
      }
      Insets in = parent.getInsets();
      return new Dimension(width + in.left + in.right, height + in.top + in.bottom);
    }

    @Override
    public void layoutContainer(Container parent) {
      Insets in = parent.getInsets();
      int available = Math.max(0, parent.getWidth() - in.left - in.right);
      int y = in.top;
      for (Component c : parent.getComponents()) {
        if (!c.isVisible()) {
          continue;
        }
        int width = Math.max(c.getMinimumSize().width, Math.min(c.getMaximumSize().width, available));
        int x = in.left + Math.max(0, Math.round((available - width) * c.getAlignmentX()));
        int height = c.getPreferredSize().height;
        c.setBounds(x, y, width, height);
        y += height;
      }
    }
  }
}
