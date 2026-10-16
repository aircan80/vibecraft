package dev.wowforever.app.ui;

import dev.wowforever.app.ui.widgets.ManualDelay;
import dev.wowforever.app.ui.widgets.MasterList;
import javax.swing.JComponent;

/** A headless {@link MainView}: real panels, no frame; list previews wait for {@code delay.fire()}. */
final class StubView implements MainView {
  final ManualDelay delay = new ManualDelay();
  private final MasterList list = new MasterList(delay);
  private final StatusBar status = new StatusBar();
  JComponent detail;
  boolean canBack;
  boolean canForward;
  String titleSuffix;

  @Override
  public MasterList masterList() {
    return list;
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
