package dev.wowforever.app.ui;

import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.widgets.HistoryButtons;
import dev.wowforever.app.ui.widgets.Icons;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;

/**
 * The compact window's content, a single column for a second screen ({@value UiState#COMPACT_WIDTH}×{@value
 * UiState#COMPACT_HEIGHT} logical pixels by default): a slim top bar of back, forward, the search bar (with its
 * dropdown, the kind chips folded into a menu) and a pin button for always on top; then the page; then the status
 * line. No rail, no master list, no menu bar: the keyboard map still applies, and the top bar's context menu
 * ({@link #setMenu}) reaches what the menu bar would.
 *
 * <p>The search bar, the page holder and the status line are the window's own, shared with the full layout:
 * {@link #claim} moves them in. Tables on the page are compact while they are inside this shell
 * ({@link CompactColumns}), and so are a page's sections: every one but the first reads only
 * {@code "Heading (count)"} until a click on the heading opens it
 * ({@link dev.wowforever.app.ui.widgets.SourceSection}). This shell carries {@link CompactColumns#KEY}, which both
 * follow.
 */
public final class CompactShell extends JPanel {
  /** The accessible name of the pin button, which shows only an icon. */
  public static final String PIN_NAME = "Always on top";

  private final JButton back = HistoryButtons.back();
  private final JButton forward = HistoryButtons.forward();
  private final JToggleButton pin = new JToggleButton(Icons.icon("pin", 18));
  private final JPanel topBar = new JPanel(new BorderLayout(4, 0));
  private final JPanel searchSlot = new JPanel(new BorderLayout());
  private final JPanel pageSlot = new JPanel(new BorderLayout());
  private final JPanel statusSlot = new JPanel(new BorderLayout());

  /** A shell whose buttons run {@code onBack}, {@code onForward} and {@code onToggleOnTop}. */
  public CompactShell(Runnable onBack, Runnable onForward, Runnable onToggleOnTop) {
    super(new BorderLayout());
    Objects.requireNonNull(onBack, "onBack");
    Objects.requireNonNull(onForward, "onForward");
    Objects.requireNonNull(onToggleOnTop, "onToggleOnTop");
    putClientProperty(CompactColumns.KEY, Boolean.TRUE);
    back.addActionListener(e -> onBack.run());
    forward.addActionListener(e -> onForward.run());
    pin.getAccessibleContext().setAccessibleName(PIN_NAME);
    pin.setToolTipText(PIN_NAME + " (Ctrl+Shift+T)");
    pin.setFocusable(false);
    pin.addActionListener(e -> onToggleOnTop.run());
    JPanel history = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
    history.setOpaque(false);
    history.add(back);
    history.add(forward);
    for (JComponent c : new JComponent[] {history, back, forward, pin}) {
      c.setInheritsPopupMenu(true);
    }
    searchSlot.setOpaque(false);
    topBar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    topBar.add(history, BorderLayout.WEST);
    topBar.add(searchSlot, BorderLayout.CENTER);
    topBar.add(pin, BorderLayout.EAST);
    add(topBar, BorderLayout.NORTH);
    add(pageSlot, BorderLayout.CENTER);
    add(statusSlot, BorderLayout.SOUTH);
    setHistoryState(false, false);
  }

  /**
   * Moves the window's {@code search} bar (made compact), {@code page} holder and {@code status} line into this
   * shell, out of wherever they were.
   */
  public void claim(SearchBar search, JComponent page, JComponent status) {
    search.setCompact(true);
    searchSlot.add(search, BorderLayout.CENTER);
    pageSlot.add(page, BorderLayout.CENTER);
    statusSlot.add(status, BorderLayout.CENTER);
    revalidate();
    repaint();
  }

  /** Enables or disables back and forward. */
  public void setHistoryState(boolean canBack, boolean canForward) {
    back.setEnabled(canBack);
    forward.setEnabled(canForward);
  }

  /** Shows the pin pressed while the window stays on top. */
  public void setOnTop(boolean onTop) {
    pin.setSelected(onTop);
  }

  /** The top bar's context menu: a right-click on the bar or its buttons shows it. */
  public void setMenu(JPopupMenu menu) {
    topBar.setComponentPopupMenu(menu);
  }

  JPanel topBar() {
    return topBar;
  }

  JButton backButton() {
    return back;
  }

  JButton forwardButton() {
    return forward;
  }

  JToggleButton pinButton() {
    return pin;
  }
}
