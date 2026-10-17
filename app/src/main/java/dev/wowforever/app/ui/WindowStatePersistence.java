package dev.wowforever.app.ui;

import dev.wowforever.app.ui.widgets.MasterList;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
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
 * Saves the window's bounds, maximised state and screen to {@link UiState}: {@value #SAVE_DELAY_MILLIS} ms after
 * it last moved or resized, and at once when it closes. A maximised window keeps its earlier normal bounds; a
 * minimised one saves nothing; in compact mode the bounds go to the compact bounds.
 */
final class WindowStatePersistence {
  static final int SAVE_DELAY_MILLIS = 500;

  /** What is saved of a window: its bounds, {@link Frame#getExtendedState()} and screen id. */
  record Snapshot(Rectangle bounds, int extendedState, String screenDeviceId) {}

  private final UiState state;
  private final Supplier<Snapshot> window;
  private final MasterList.Delay delay;

  WindowStatePersistence(UiState state, Supplier<Snapshot> window, MasterList.Delay delay) {
    this.state = Objects.requireNonNull(state, "state");
    this.window = Objects.requireNonNull(window, "window");
    this.delay = Objects.requireNonNull(delay, "delay");
  }

  /**
   * Places {@code frame} as saved (bounds on a current screen, else centred on the default one; maximised; on top)
   * and saves its state from now on.
   */
  static void install(Frame frame, UiState state) {
    frame.setBounds(state.windowBounds(screens()));
    if (state.maximised()) {
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
    state.setMaximised(maximised);
    if (!maximised) {
      if (state.compactMode()) {
        state.setCompactBounds(s.bounds());
      } else {
        state.setBounds(s.bounds());
      }
    }
    state.setScreenDeviceId(s.screenDeviceId());
  }

  private static Snapshot snapshot(Frame frame) {
    GraphicsConfiguration gc = frame.getGraphicsConfiguration();
    return new Snapshot(frame.getBounds(), frame.getExtendedState(), gc == null ? null : gc.getDevice().getIDstring());
  }

  /** The bounds of every screen, the default screen first. */
  private static List<Rectangle> screens() {
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
