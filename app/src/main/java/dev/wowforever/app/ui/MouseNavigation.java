package dev.wowforever.app.ui;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.Predicate;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Mouse buttons 4 and 5 (the side buttons) go back and forward, as in a browser. Listens to every mouse event
 * of the application, but acts only on events whose source lies in the application window and is not a text
 * component.
 */
public final class MouseNavigation {
  private static final int BACK_BUTTON = 4;
  private static final int FORWARD_BUTTON = 5;

  private final Runnable back;
  private final Runnable forward;
  private final Predicate<Component> isAppWindow;
  private final AWTEventListener listener = this::onEvent;

  private MouseNavigation(Runnable back, Runnable forward, Predicate<Component> isAppWindow) {
    this.back = Objects.requireNonNull(back, "back");
    this.forward = Objects.requireNonNull(forward, "forward");
    this.isAppWindow = Objects.requireNonNull(isAppWindow, "isAppWindow");
  }

  /** Starts navigating {@code controller} on buttons 4 and 5 pressed in the window of {@code appRoot}. */
  public static MouseNavigation install(AppController controller, Component appRoot) {
    Objects.requireNonNull(controller, "controller");
    Objects.requireNonNull(appRoot, "appRoot");
    MouseNavigation nav =
        new MouseNavigation(controller::back, controller::forward, source -> sameWindow(source, appRoot));
    Toolkit.getDefaultToolkit().addAWTEventListener(nav.listener, AWTEvent.MOUSE_EVENT_MASK);
    return nav;
  }

  /** For tests: not registered with the toolkit; {@code isAppWindow} decides which sources count. */
  static MouseNavigation create(Runnable back, Runnable forward, Predicate<Component> isAppWindow) {
    return new MouseNavigation(back, forward, isAppWindow);
  }

  /** Stops listening. */
  public void uninstall() {
    Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
  }

  private void onEvent(AWTEvent event) {
    if (event instanceof MouseEvent e) {
      handle(e);
    }
  }

  /** Navigates and consumes {@code e} if it is a button 4 or 5 release in the app window; says whether it did. */
  boolean handle(MouseEvent e) {
    if (e.getID() != MouseEvent.MOUSE_RELEASED || e.isConsumed()) {
      return false;
    }
    int button = e.getButton();
    if (button != BACK_BUTTON && button != FORWARD_BUTTON) {
      return false;
    }
    if (!(e.getSource() instanceof Component source) || source instanceof JTextComponent
        || !isAppWindow.test(source)) {
      return false;
    }
    if (button == BACK_BUTTON) {
      back.run();
    } else {
      forward.run();
    }
    e.consume();
    return true;
  }

  static boolean sameWindow(Component source, Component appRoot) {
    Component window = SwingUtilities.getWindowAncestor(source);
    if (window == null) {
      return false;
    }
    Component appWindow = SwingUtilities.getWindowAncestor(appRoot);
    if (appWindow == null) {
      appWindow = SwingUtilities.getRoot(appRoot);
    }
    return window == appWindow;
  }
}
