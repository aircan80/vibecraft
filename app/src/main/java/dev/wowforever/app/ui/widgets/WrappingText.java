package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.ui.theme.Theme;
import java.awt.Color;
import java.awt.Dimension;
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

  @Override
  public Dimension getMaximumSize() {
    return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
  }
}
