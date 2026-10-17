package dev.wowforever.app.ui;

import dev.wowforever.app.ui.widgets.MasterList;
import dev.wowforever.app.ui.widgets.RailTarget;
import javax.swing.JComponent;

/** What {@link AppController} drives: the main window in production, a headless stub in tests. */
public interface MainView {
  /** The master list left of the detail. */
  MasterList masterList();

  /** Replaces the right-hand detail area. */
  void setDetail(JComponent c);

  /** Enables or disables the back and forward buttons. */
  void setHistoryState(boolean canBack, boolean canForward);

  /** The status bar at the bottom. */
  StatusBar status();

  /** Shows {@code s} after the application name in the window title; empty for none. */
  void setTitleSuffix(String s);

  /** Highlights the rail section of the page on screen, without opening it; null highlights none. */
  void selectRail(RailTarget target);

  /**
   * Whether the master list is on screen. The compact window has none: there a full search's results take the
   * detail area instead.
   */
  default boolean showsMasterList() {
    return true;
  }
}
