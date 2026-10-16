package dev.wowforever.app.ui.widgets;

import java.awt.event.ActionEvent;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/** Presses keys on a component headlessly, through its focused key bindings as Swing would. */
public final class Keys {
  private Keys() {}

  /**
   * Runs the action {@code key} (a {@link KeyStroke#getKeyStroke(String)} string) is bound to while {@code c} has
   * the focus, if that action is enabled; returns whether it ran, i.e. whether Swing would consume the key.
   */
  public static boolean press(JComponent c, String key) {
    Object name = c.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key));
    Action action = name == null ? null : c.getActionMap().get(name);
    if (action == null || !action.isEnabled()) {
      return false;
    }
    action.actionPerformed(new ActionEvent(c, ActionEvent.ACTION_PERFORMED, key));
    return true;
  }
}
