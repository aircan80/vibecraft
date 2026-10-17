package dev.wowforever.app.ui.widgets;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link MasterList.Delay} the test fires by hand. Scheduling does not replace earlier runs on its own, so a
 * list that forgets to {@link #cancel()} before rescheduling shows up as extra previews.
 */
public final class ManualDelay implements MasterList.Delay {
  private final List<Runnable> pending = new ArrayList<>();

  @Override
  public void schedule(Runnable r) {
    pending.add(r);
  }

  @Override
  public void cancel() {
    pending.clear();
  }

  /** Runs every pending run, as if the delay had elapsed. */
  public void fire() {
    List<Runnable> due = List.copyOf(pending);
    pending.clear();
    due.forEach(Runnable::run);
  }

  /** How many runs are waiting. */
  public int pending() {
    return pending.size();
  }
}
