package dev.wowforever.app.ui;

import javax.swing.JComponent;

/** What {@link AppController} drives: the main window in production, a headless stub in tests. */
public interface MainView {
  /** The centre results table. */
  ResultsPanel results();

  /** Replaces the right-hand detail area. */
  void setDetail(JComponent c);

  /** Enables or disables the back and forward buttons. */
  void setHistoryState(boolean canBack, boolean canForward);

  /** The status bar at the bottom. */
  StatusBar status();

  /** Shows {@code s} after the application name in the window title; empty for none. */
  void setTitleSuffix(String s);
}
