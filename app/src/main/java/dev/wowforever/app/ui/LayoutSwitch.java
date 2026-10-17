package dev.wowforever.app.ui;

import dev.wowforever.app.ui.widgets.MasterList;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JToolBar;

/**
 * The F10 switch between the full layout and the {@link CompactShell}, headless: it moves the window's shared
 * search bar, page holder and status line into the layout shown and decides the window's bounds, while the frame
 * behind {@link Window} only applies what it is told. Each layout has its own saved bounds in {@link UiState}; the
 * same controller shows the current page again in the new layout ({@code rerender}).
 */
final class LayoutSwitch {
  /** What the switch asks of the frame. */
  interface Window {
    /** Saves the window's bounds now, to the slot of the layout shown ({@link WindowStatePersistence#saveNow}). */
    void saveBounds();

    /** Takes the window out of the maximised state. */
    void clearMaximised();

    /** Maximises the window. */
    void maximise();

    /** Shows {@code content} as the window's content, with the menu bar or without it. */
    void show(JPanel content, boolean menuBar);

    /** Moves and sizes the window. */
    void place(Rectangle bounds);

    /** The bounds of every screen, the default screen first. */
    List<Rectangle> screens();

    /** The bounds of the screen the window is on. */
    Rectangle currentScreen();
  }

  /**
   * The parts of the two layouts: the full layout's {@code toolbar} (search bar last), {@code content} (status line
   * at its bottom), {@code detailSlot} (holds the page holder) and {@code list}; the {@code shell}; and the shared
   * {@code search} bar, page {@code holder} and {@code status} line.
   */
  record Parts(JToolBar toolbar, JPanel content, JPanel detailSlot, MasterList list, CompactShell shell,
      SearchBar search, JComponent holder, JComponent status) {
    Parts {
      Objects.requireNonNull(toolbar, "toolbar");
      Objects.requireNonNull(content, "content");
      Objects.requireNonNull(detailSlot, "detailSlot");
      Objects.requireNonNull(list, "list");
      Objects.requireNonNull(shell, "shell");
      Objects.requireNonNull(search, "search");
      Objects.requireNonNull(holder, "holder");
      Objects.requireNonNull(status, "status");
    }
  }

  private final Parts parts;
  private final UiState state;
  private final Window window;
  private final Runnable rerender;
  private boolean compact;

  LayoutSwitch(Parts parts, UiState state, Window window, Runnable rerender) {
    this.parts = Objects.requireNonNull(parts, "parts");
    this.state = Objects.requireNonNull(state, "state");
    this.window = Objects.requireNonNull(window, "window");
    this.rerender = Objects.requireNonNull(rerender, "rerender");
  }

  /**
   * The bounds of the layout {@code compact} names: the compact window's saved bounds, else its default at the top
   * right of {@code current} ({@link UiState#compactWindowBounds}); or the full window's ({@link
   * UiState#windowBounds}).
   */
  static Rectangle boundsFor(boolean compact, UiState state, List<Rectangle> screens, Rectangle current) {
    return compact ? state.compactWindowBounds(screens, current) : state.windowBounds(screens);
  }

  /** Shows the layout saved last; the window was already placed for it ({@link WindowStatePersistence#install}). */
  void start() {
    arrange(state.compactMode());
  }

  /**
   * F10: saves the bounds of the layout shown to its own slot, takes the window out of the maximised state (a
   * maximised compact window too), records the new mode (before the window moves, so the saves its moves set off
   * go to the new slot), shows the other layout at its bounds, maximises it again when the full window was saved
   * maximised, and shows the current page again.
   */
  void toggle() {
    boolean toCompact = !compact;
    window.saveBounds();
    window.clearMaximised();
    state.setCompactMode(toCompact);
    arrange(toCompact);
    window.place(boundsFor(toCompact, state, window.screens(), window.currentScreen()));
    if (!toCompact && state.maximised()) {
      window.maximise();
    }
    rerender.run();
  }

  /** Whether the compact layout is shown. */
  boolean isCompact() {
    return compact;
  }

  /**
   * Where Esc moves the focus once the search box has nothing left to close or clear: the master list in the full
   * layout; in the compact one, which has no list, the page's first table or list (null when it has none).
   */
  Component escapeTarget() {
    return compact ? firstBrowsable(parts.holder()) : parts.list().jlist();
  }

  /** The first focusable, visible, enabled table or list under {@code c}, or null. */
  static Component firstBrowsable(Component c) {
    if ((c instanceof JTable || c instanceof JList) && c.isFocusable() && c.isVisible() && c.isEnabled()) {
      return c;
    }
    if (c instanceof Container parent) {
      for (Component child : parent.getComponents()) {
        Component found = firstBrowsable(child);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  private void arrange(boolean toCompact) {
    compact = toCompact;
    if (toCompact) {
      parts.shell().claim(parts.search(), parts.holder(), parts.status());
      window.show(parts.shell(), false);
    } else {
      parts.search().setCompact(false);
      parts.toolbar().add(parts.search());
      parts.detailSlot().add(parts.holder(), BorderLayout.CENTER);
      parts.content().add(parts.status(), BorderLayout.SOUTH);
      window.show(parts.content(), true);
    }
  }
}
