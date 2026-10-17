package dev.wowforever.app.nav;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

/**
 * Browser-style back/forward history. Not thread-safe: use from the Swing event dispatch thread
 * only.
 */
public final class NavigationHistory {
  private final ArrayList<ViewTarget> entries = new ArrayList<>();
  private int cursor = -1;

  /** Visits {@code t}. Ignored if equal to the current target; otherwise clears forward history. */
  public void push(ViewTarget t) {
    Objects.requireNonNull(t, "t");
    if (t.equals(entries.isEmpty() ? null : entries.get(cursor))) {
      return;
    }
    entries.subList(cursor + 1, entries.size()).clear();
    entries.add(t);
    cursor++;
  }

  /**
   * Puts {@code t} in place of the current target without growing the history or touching what is forward of
   * it (used for list previews); on an empty history it is {@link #push}.
   */
  public void replaceCurrent(ViewTarget t) {
    Objects.requireNonNull(t, "t");
    if (entries.isEmpty()) {
      push(t);
      return;
    }
    entries.set(cursor, t);
  }

  /** Moves back and returns the new current target, or empty when already at the start. */
  public Optional<ViewTarget> back() {
    if (!canBack()) {
      return Optional.empty();
    }
    cursor--;
    return current();
  }

  /** Moves forward and returns the new current target, or empty when already at the end. */
  public Optional<ViewTarget> forward() {
    if (!canForward()) {
      return Optional.empty();
    }
    cursor++;
    return current();
  }

  public boolean canBack() {
    return cursor > 0;
  }

  public boolean canForward() {
    return cursor < entries.size() - 1;
  }

  public Optional<ViewTarget> current() {
    return cursor < 0 ? Optional.empty() : Optional.of(entries.get(cursor));
  }

  /** How many targets the history holds, back and forward of the current one included. */
  public int size() {
    return entries.size();
  }
}
