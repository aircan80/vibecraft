package dev.wowforever.app.ui;

/**
 * Where a page reports work it does after it is shown (say, loading more pins): a status message, or a failure,
 * which the app shows in its status bar as {@code "Error: <message>"}.
 */
public interface StatusSink {
  /** Reports nothing (tests, pages built outside the app). */
  StatusSink NONE = new StatusSink() {
    @Override
    public void message(String text) {}

    @Override
    public void fail(Exception e) {}
  };

  /** Shows {@code text} as the status. */
  void message(String text);

  /** Reports a failure. */
  void fail(Exception e);
}
