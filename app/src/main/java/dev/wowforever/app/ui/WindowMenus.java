package dev.wowforever.app.ui;

import dev.wowforever.app.model.SourceFilter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

/**
 * Menus both window layouts offer, built by one helper so the full window's View menu and the compact window's
 * context menu stay the same.
 */
final class WindowMenus {
  /** The title of the drop source filter's submenu. */
  static final String DROP_SOURCES = "Drop sources";

  private WindowMenus() {}

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
