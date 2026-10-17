package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.ui.theme.Theme;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/** Read-only explanatory copy which wraps within narrow panes and follows the current theme. */
public final class WrappingText extends JTextArea {
  public WrappingText(String text, String accessibleName) {
    super(text, 0, 30);
    setEditable(false);
    setFocusable(false);
    setOpaque(false);
    setLineWrap(true);
    setWrapStyleWord(true);
    setFont(UIManager.getFont("Label.font"));
    getAccessibleContext().setAccessibleName(accessibleName);
  }

  @Override
  public Color getForeground() { return Theme.current().textMuted(); }

  /** Measure at the current container width instead of a stale one-pixel text view. */
  @Override
  public Dimension getPreferredSize() {
    Dimension natural = super.getPreferredSize();
    int width = getWidth();
    if (getParent() != null && getParent().getWidth() > 0) {
      Insets parentInsets = getParent().getInsets();
      width = getParent().getWidth() - parentInsets.left - parentInsets.right;
    }
    if (width <= 0) width = natural.width;
    Insets in = getInsets();
    var view = getUI().getRootView(this);
    view.setSize(Math.max(1, width - in.left - in.right), Integer.MAX_VALUE);
    int height = (int) Math.ceil(view.getPreferredSpan(javax.swing.text.View.Y_AXIS)) + in.top + in.bottom;
    return new Dimension(natural.width, height);
  }

  @Override
  public Dimension getMinimumSize() {
    return new Dimension(0, getPreferredSize().height);
  }

  @Override
  public Dimension getMaximumSize() {
    return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
  }
}
