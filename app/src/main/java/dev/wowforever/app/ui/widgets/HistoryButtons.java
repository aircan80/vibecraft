package dev.wowforever.app.ui.widgets;

import javax.swing.JButton;

/**
 * The icon-only back and forward buttons of the full window's toolbar and the compact window's top bar: each named
 * for accessibility, with a tooltip giving its keys, and never taking the keyboard focus.
 */
public final class HistoryButtons {
  private HistoryButtons() {}

  /** The back button. */
  public static JButton back() {
    return button("arrow-left", "Back", "Back (Alt+Left, mouse button 4)");
  }

  /** The forward button. */
  public static JButton forward() {
    return button("arrow-right", "Forward", "Forward (Alt+Right, mouse button 5)");
  }

  /** A button showing icon {@code icon}, named {@code name} for accessibility, with {@code tooltip}. */
  public static JButton button(String icon, String name, String tooltip) {
    JButton b = new JButton(Icons.icon(icon, 18));
    b.getAccessibleContext().setAccessibleName(name);
    b.setToolTipText(tooltip);
    b.setFocusable(false);
    return b;
  }
}
