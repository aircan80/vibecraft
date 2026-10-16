package dev.wowforever.app.ui;

import javax.swing.JComponent;

/** A headless {@link MainView}: real panels, no frame. */
final class StubView implements MainView {
  private final ResultsPanel results = new ResultsPanel(t -> {});
  private final StatusBar status = new StatusBar();
  JComponent detail;
  boolean canBack;
  boolean canForward;
  String titleSuffix;

  @Override
  public ResultsPanel results() {
    return results;
  }

  @Override
  public void setDetail(JComponent c) {
    detail = c;
  }

  @Override
  public void setHistoryState(boolean canBack, boolean canForward) {
    this.canBack = canBack;
    this.canForward = canForward;
  }

  @Override
  public StatusBar status() {
    return status;
  }

  @Override
  public void setTitleSuffix(String s) {
    titleSuffix = s;
  }
}
