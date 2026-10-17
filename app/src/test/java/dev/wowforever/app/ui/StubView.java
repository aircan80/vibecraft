package dev.wowforever.app.ui;

import dev.wowforever.app.ui.widgets.ManualDelay;
import dev.wowforever.app.ui.widgets.MasterList;
import dev.wowforever.app.ui.widgets.RailTarget;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * A headless {@link MainView}: real panels, no frame; list previews wait for {@code delay.fire()}. The detail goes
 * into {@link #holder}, which a test may put in a window layout of its own (the compact shell, say).
 */
final class StubView implements MainView {
  final ManualDelay delay = new ManualDelay();
  private final MasterList list = new MasterList(delay);
  private final StatusBar status = new StatusBar();
  final JPanel holder = new JPanel(new BorderLayout());
  JComponent detail;
  boolean canBack;
  boolean canForward;
  String titleSuffix;
  /** The rail section last selected; null for none. */
  RailTarget rail;
  /** What {@link #showsMasterList()} answers: false plays the compact window. */
  boolean showsMasterList = true;

  @Override
  public MasterList masterList() {
    return list;
  }

  @Override
  public void setDetail(JComponent c) {
    detail = c;
    holder.removeAll();
    holder.add(c, BorderLayout.CENTER);
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

  @Override
  public void selectRail(RailTarget target) {
    rail = target;
  }

  @Override
  public boolean showsMasterList() {
    return showsMasterList;
  }
}
