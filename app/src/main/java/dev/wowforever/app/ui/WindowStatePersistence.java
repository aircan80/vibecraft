package dev.wowforever.app.ui;

import dev.wowforever.app.ui.widgets.MasterList;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Saves the window's bounds and maximised state to {@link UiState}: {@value #SAVE_DELAY_MILLIS} ms after
 * it last moved or resized, and at once when it closes. A maximised window keeps its earlier normal bounds; a
 * minimised one saves nothing; in compact mode the bounds go to the compact bounds, and the normal window's
 * maximised state is left as it was.
 */
final class WindowStatePersistence {
  static final int SAVE_DELAY_MILLIS = 500;

  /** What is saved of a window: its bounds and {@link Frame#getExtendedState()}. */
  record Snapshot(Rectangle bounds, int extendedState) {}

  private final UiState state;
  private final Supplier<Snapshot> window;
  private final MasterList.Delay delay;

  WindowStatePersistence(UiState state, Supplier<Snapshot> window, MasterList.Delay delay) {
    this.state = Objects.requireNonNull(state, "state");
    this.window = Objects.requireNonNull(window, "window");
    this.delay = Objects.requireNonNull(delay, "delay");
  }

  /**
   * Places {@code frame} as saved (bounds on a current screen, else centred on the default one, or for the compact
   * window at its top right; maximised unless compact; on top) and saves its state from now on; returns the saver.
   */
  static WindowStatePersistence install(Frame frame, UiState state) {
    List<Rectangle> screens = screens();
    frame.setBounds(LayoutSwitch.boundsFor(state.compactMode(), state, screens, screens.get(0)));
    if (!state.compactMode() && state.maximised()) {
      frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
    }
    frame.setAlwaysOnTop(state.alwaysOnTop());
    WindowStatePersistence saver = new WindowStatePersistence(state, () -> snapshot(frame),
        MasterList.timerDelay(SAVE_DELAY_MILLIS));
    frame.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentMoved(ComponentEvent e) {
        saver.changed();
      }

      @Override
      public void componentResized(ComponentEvent e) {
        saver.changed();
      }
    });
    frame.addWindowStateListener(e -> saver.changed());
    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        saver.saveNow();
      }
    });
    return saver;
  }

  /** The window moved or resized: saves once it has rested for {@value #SAVE_DELAY_MILLIS} ms. */
  void changed() {
    delay.cancel();
    delay.schedule(this::saveNow);
  }

  /** Saves the window's state now, dropping a pending save. */
  void saveNow() {
    delay.cancel();
    Snapshot s = window.get();
    if ((s.extendedState() & Frame.ICONIFIED) != 0) {
      return;
    }
    boolean maximised = (s.extendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
    if (state.compactMode()) {
      if (!maximised) {
        state.setCompactBounds(s.bounds());
      }
    } else {
      state.setMaximised(maximised);
      if (!maximised) {
        state.setBounds(s.bounds());
      }
    }
  }

  private static Snapshot snapshot(Frame frame) {
    return new Snapshot(frame.getBounds(), frame.getExtendedState());
  }

  /** The bounds of every screen, the default screen first. */
  static List<Rectangle> screens() {
    GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice main = env.getDefaultScreenDevice();
    List<Rectangle> screens = new ArrayList<>();
    screens.add(main.getDefaultConfiguration().getBounds());
    for (GraphicsDevice d : env.getScreenDevices()) {
      if (d != main) {
        screens.add(d.getDefaultConfiguration().getBounds());
      }
    }
    return screens;
  }
}
