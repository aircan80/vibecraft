package dev.wowforever.app.ui;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.Predicate;
import javax.swing.JRootPane;
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

  /**
   * Starts navigating {@code controller} on buttons 4 and 5 pressed in the window of {@code root}, the frame's root
   * pane: it stays in the window whichever layout is its content pane, so the buttons work in both layouts.
   */
  public static MouseNavigation install(AppController controller, JRootPane root) {
    Objects.requireNonNull(controller, "controller");
    Objects.requireNonNull(root, "root");
    MouseNavigation nav =
        new MouseNavigation(controller::back, controller::forward, source -> sameWindow(source, root));
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

  /**
   * Whether {@code source} and {@code appRoot} share a top: the window they are in, or for components in no window
   * (as in a headless test) the outermost container of their tree.
   */
  static boolean sameWindow(Component source, Component appRoot) {
    return top(source) == top(appRoot);
  }

  /**
   * The first window from {@code c} up, else the outermost container. The walk stops at a window because an owned
   * window's parent is its owner.
   */
  private static Component top(Component c) {
    Component top = c;
    while (!(top instanceof Window) && top.getParent() != null) {
      top = top.getParent();
    }
    return top;
  }
}
