package dev.wowforever.app.ui;

import dev.wowforever.app.model.SourceFilter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

/**
 * Menus both window layouts offer, built by one helper so the full window's View and Help menus and the compact
 * window's context menu stay the same: {@link #addViewItems} (Slate theme, Parchment theme, Drop sources) and
 * {@link #addHelpItems}
 * (Keyboard shortcuts, About).
 */
final class WindowMenus {
  /** The title of the drop source filter's submenu. */
  static final String DROP_SOURCES = "Drop sources";

  /**
   * What the shared items do: whether the dark theme (Slate) is on ({@code dark}, read whenever a theme item is
   * asked, so a switch made in the other layout shows), switching it, showing the current page again after a
   * source filter change, and opening the shortcut list and the About box.
   */
  record Actions(BooleanSupplier dark, Runnable toggleTheme, Runnable rerender, Runnable shortcuts,
      Runnable about) {
    Actions {
      Objects.requireNonNull(dark, "dark");
      Objects.requireNonNull(toggleTheme, "toggleTheme");
      Objects.requireNonNull(rerender, "rerender");
      Objects.requireNonNull(shortcuts, "shortcuts");
      Objects.requireNonNull(about, "about");
    }
  }

  private WindowMenus() {}

  /**
   * Adds the View items both layouts share to {@code menu} (a {@link JMenu} or a {@link JPopupMenu}): a radio item
   * per theme ("Slate theme", "Parchment theme"), the one on selected, then the drop source filter.
   */
  static void addViewItems(JComponent menu, UiState state, Actions actions) {
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
    menu.add(sourceFilterMenu(state, actions.rerender()));
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

  /**
   * The "Drop sources" submenu: a radio item per {@link SourceFilter}, the saved one selected (read again whenever
   * the submenu opens, as the other layout's menu may have changed it). Choosing another filter saves it in
   * {@code state} and runs {@code onChange}, which shows the current page again.
   */
  static JMenu sourceFilterMenu(UiState state, Runnable onChange) {
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(onChange, "onChange");
    JMenu menu = new JMenu(DROP_SOURCES);
    ButtonGroup group = new ButtonGroup();
    Map<SourceFilter, JRadioButtonMenuItem> items = new EnumMap<>(SourceFilter.class);
    for (SourceFilter filter : SourceFilter.values()) {
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(filter.label());
      item.addActionListener(e -> {
        if (state.sourceFilter() != filter) {
          state.setSourceFilter(filter);
          onChange.run();
        }
      });
      group.add(item);
      menu.add(item);
      items.put(filter, item);
    }
    Runnable showSaved = () -> items.get(state.sourceFilter()).setSelected(true);
    showSaved.run();
    menu.addMenuListener(new MenuListener() {
      @Override
      public void menuSelected(MenuEvent e) {
        showSaved.run();
      }

      @Override
      public void menuDeselected(MenuEvent e) {}

      @Override
      public void menuCanceled(MenuEvent e) {}
    });
    return menu;
  }
}
