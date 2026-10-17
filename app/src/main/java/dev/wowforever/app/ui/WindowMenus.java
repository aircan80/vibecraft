package dev.wowforever.app.ui;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;

/**
 * Menus both window layouts offer, built by one helper so the full window's View and Help menus and the compact
 * window's context menu stay the same: {@link #addViewItems} (Slate theme, Parchment theme) and
 * {@link #addHelpItems} (Keyboard shortcuts, About). The drop source filter is no menu: it sits beside the drop
 * sections' headings ({@link dev.wowforever.app.ui.widgets.SourceFilterControl}).
 */
final class WindowMenus {
  /**
   * What the shared items do: whether the dark theme (Slate) is on ({@code dark}, read whenever a theme item is
   * asked, so a switch made in the other layout shows), switching it, and opening the shortcut list and the About
   * box.
   */
  record Actions(BooleanSupplier dark, Runnable toggleTheme, Runnable shortcuts, Runnable about, Runnable sources) {
    Actions {
      Objects.requireNonNull(dark, "dark");
      Objects.requireNonNull(toggleTheme, "toggleTheme");
      Objects.requireNonNull(shortcuts, "shortcuts");
      Objects.requireNonNull(about, "about");
      Objects.requireNonNull(sources, "sources");
    }
  }

  private WindowMenus() {}

  /**
   * Adds the View items both layouts share to {@code menu} (a {@link JMenu} or a {@link JPopupMenu}): a radio item
   * per theme ("Slate theme", "Parchment theme"), the one on selected.
   */
  static void addViewItems(JComponent menu, Actions actions) {
    JMenuItem sources = new JMenuItem("Data sources");
    sources.addActionListener(e -> actions.sources().run());
    menu.add(sources);
    for (ThemeManager.Mode mode : ThemeManager.Mode.values()) {
      boolean dark = mode == ThemeManager.Mode.DARK;
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(mode.label() + " theme");
      item.setModel(new LiveToggle(() -> actions.dark().getAsBoolean() == dark));
      item.addActionListener(e -> {
        if (actions.dark().getAsBoolean() != dark) {
          actions.toggleTheme().run();
        }
      });
      menu.add(item);
    }
  }

  /**
   * Adds the Help items both layouts share to {@code menu}; a context menu ({@link JPopupMenu}) gets a separator
   * before them.
   */
  static void addHelpItems(JComponent menu, Actions actions) {
    if (menu instanceof JPopupMenu popup) {
      popup.addSeparator();
    }
    JMenuItem shortcuts = new JMenuItem("Keyboard shortcuts");
    shortcuts.addActionListener(e -> actions.shortcuts().run());
    menu.add(shortcuts);
    JMenuItem about = new JMenuItem("About");
    about.addActionListener(e -> actions.about().run());
    menu.add(about);
  }

  /**
   * A toggle model whose selection is {@code selected}'s, read on every query: clicking still fires the item's
   * action, which changes what {@code selected} reports.
   */
  static final class LiveToggle extends JToggleButton.ToggleButtonModel {
    private final BooleanSupplier selected;

    LiveToggle(BooleanSupplier selected) {
      this.selected = selected;
    }

    @Override
    public boolean isSelected() {
      return selected.getAsBoolean();
    }
  }
}
