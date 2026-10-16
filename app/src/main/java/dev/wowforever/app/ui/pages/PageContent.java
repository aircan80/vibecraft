package dev.wowforever.app.ui.pages;

import dev.wowforever.app.ui.theme.Theme;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/**
 * The body of a page that scrolls as a whole: a vertical box, as wide as the viewport (tables fit the width
 * instead of scrolling sideways), at least as tall as it, children aligned left.
 */
final class PageContent extends JPanel implements Scrollable {
  private static final int UNIT = 16;

  PageContent() {
    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
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
}
