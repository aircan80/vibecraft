package dev.wowforever.app.ui;

import dev.wowforever.app.model.SourceFilter;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.widgets.HistoryButtons;
import dev.wowforever.app.ui.widgets.MasterList;
import dev.wowforever.app.ui.widgets.Rail;
import dev.wowforever.app.ui.widgets.RailTarget;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * The main window: back/forward and search on top; the icon rail, the master list and the detail in the middle;
 * status at the bottom. Created before the database is open; {@link #attach} wires in the controller. Never
 * created in tests (they use {@link #buildContent}, {@link CompactShell} and the panels directly). The keys are
 * those of {@link KeyMap}; the window's place, size and settings come from and go back to {@link UiState}.
 *
 * <p>F10 swaps the content for the {@link CompactShell} and back. Both layouts share the one search bar, page
 * holder and status line (moved into the layout shown) and the one controller, which shows the current page again
 * in the new layout; each layout has its own saved bounds.
 */
public final class MainFrame extends JFrame implements MainView {
  public static final String APP_NAME = "WoW Forever Item & Drop Browser";
  /** The content's action that hides or shows the master list (Ctrl+B through {@link KeyMap}). */
  static final String TOGGLE_LIST = "toggleList";
  /** The status message of Ctrl+D on a page that cannot be pinned. */
  static final String CANNOT_PIN = "Only item, NPC, object, quest, zone and instance pages can be pinned";
  private static final String SHOWN_DIVIDER_SIZE = "wowforever.shownDividerSize";

  private final JButton back = HistoryButtons.back();
  private final JButton forward = HistoryButtons.forward();
  private final SearchBar searchBar;
  private final Rail rail = new Rail();
  private final MasterList list = new MasterList();
  private final StatusBar status = new StatusBar();
  private final JPanel detailHolder = new JPanel(new BorderLayout());
  /** Where {@link #detailHolder} sits in the full layout. */
  private final JPanel detailSlot = new JPanel(new BorderLayout());
  private final UiState uiState = UiState.load();
  /** Created before any size is applied, so it scales from the look and feel's own font. */
  private final TextSize textSize = new TextSize(uiState);
  private final JToolBar toolbar;
  private final JPanel content;
  private final CompactShell compactShell = new CompactShell(this::back, this::forward, this::toggleAlwaysOnTop);
  private final JMenuBar menuBar;
  private final WindowStatePersistence persistence;
  private final LayoutSwitch layout;
  private AppController controller;

  public MainFrame() {
    super(APP_NAME);
    searchBar = new SearchBar(new SearchBar.Opener() {
      @Override
      public void accept(ViewTarget t) {
        if (controller != null) {
          controller.open(t);
        }
      }

      @Override
      public void openHit(ViewTarget.Entity hit, ViewTarget.Search search) {
        if (controller != null) {
          controller.openHit(hit, search);
        }
      }
    }, uiState);
    rail.onSelect(t -> {
      if (controller != null) {
        controller.open(AppController.target(t));
      }
    });
    searchBar.setEnabled(false);
    back.addActionListener(e -> back());
    forward.addActionListener(e -> forward());
    setHistoryState(false, false);
    detailHolder.add(new JLabel("Loading…", SwingConstants.CENTER), BorderLayout.CENTER);

    toolbar = toolbar(back, forward, searchBar);
    detailSlot.add(detailHolder, BorderLayout.CENTER);
    content = buildContent(toolbar, rail, list, detailSlot, status, uiState);
    menuBar = menuBar();
    compactShell.setMenu(compactMenu());
    KeyMap.install(getRootPane(), handlers());
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    persistence = WindowStatePersistence.install(this, uiState);
    compactShell.setOnTop(isAlwaysOnTop());
    layout = new LayoutSwitch(new LayoutSwitch.Parts(toolbar, content, detailSlot, list, compactShell, searchBar,
        detailHolder, status), uiState, layoutWindow(), this::rerender);
    layout.start();
    textSize.applySaved();
  }

  /** The top bar: {@code back}, {@code forward}, then {@code search}. */
  static JToolBar toolbar(JButton back, JButton forward, SearchBar search) {
    JToolBar toolbar = new JToolBar();
    toolbar.setFloatable(false);
    toolbar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 8));
    toolbar.add(back);
    toolbar.add(forward);
    toolbar.add(search);
    return toolbar;
  }

  /**
   * The window content: {@code toolbar} on top; {@code rail} at the left of a {@code list | detail} split whose
   * divider starts at, and is saved to, {@code state}; {@code status} below. The content's {@link #TOGGLE_LIST}
   * action hides or shows the list, which starts as {@code state} last saw it.
   */
  public static JPanel buildContent(JComponent toolbar, JComponent rail, JComponent list, JComponent detail,
      JComponent status, UiState state) {
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, list, detail);
    split.setContinuousLayout(true);
    split.setResizeWeight(0);
    split.setDividerLocation(state.dividerLocation());
    split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
      if (list.isVisible()) {
        state.setDividerLocation(split.getDividerLocation());
      }
    });
    JPanel middle = new JPanel(new BorderLayout());
    middle.add(rail, BorderLayout.WEST);
    middle.add(split, BorderLayout.CENTER);
    JPanel content = new JPanel(new BorderLayout());
    content.add(toolbar, BorderLayout.NORTH);
    content.add(middle, BorderLayout.CENTER);
    content.add(status, BorderLayout.SOUTH);
    content.getActionMap().put(TOGGLE_LIST, new AbstractAction("Show list") {
      @Override
      public void actionPerformed(ActionEvent e) {
        toggleList(split, list, state);
        state.setListVisible(list.isVisible());
      }
    });
    if (!state.listVisible()) {
      toggleList(split, list, state);
    }
    return content;
  }

  /** Hides the list (the detail takes the whole split), or shows it again at the saved width. */
  private static void toggleList(JSplitPane split, JComponent list, UiState state) {
    if (list.isVisible()) {
      split.putClientProperty(SHOWN_DIVIDER_SIZE, split.getDividerSize());
      list.setVisible(false);
      split.setDividerSize(0);
    } else {
      list.setVisible(true);
      if (split.getClientProperty(SHOWN_DIVIDER_SIZE) instanceof Integer size) {
        split.setDividerSize(size);
      }
      split.setDividerLocation(state.dividerLocation());
    }
    split.revalidate();
    split.repaint();
  }

  /**
   * Pins {@code current} (Ctrl+D) when it is an entity page, or unpins it when it is pinned; returns the status
   * message: {@code "Pinned"}, {@code "Unpinned"} or {@link #CANNOT_PIN}.
   */
  static String togglePin(UiState state, ViewTarget current) {
    Optional<String> key = current instanceof ViewTarget.Entity e ? Labels.idText(e.ref()) : Optional.empty();
    if (key.isEmpty()) {
      return CANNOT_PIN;
    }
    return state.togglePinned(key.get()) ? "Pinned" : "Unpinned";
  }

  /** Shows the current page again (a layout switch, a new drop source filter); nothing before the controller. */
  private void rerender() {
    if (controller != null) {
      controller.rerender();
    }
  }

  @Override
  public SourceFilter sourceFilter() {
    return uiState.sourceFilter();
  }

  /** Wires in the controller once the database is open, and enables searching and type-ahead. */
  public void attach(AppController controller) {
    this.controller = controller;
    MouseNavigation.install(controller, content);
    searchBar.attach(SearchBar.Source.of(controller.repos().search()), controller.worker());
    searchBar.setEnabled(true);
  }

  /** The About text: the bundled NOTICE.md, then the database meta values. */
  public static String aboutText(Map<String, String> meta) {
    StringBuilder text = new StringBuilder(notice().strip()).append("\n\n# Database\n\n");
    meta.forEach((k, v) -> text.append(k).append(": ").append(v).append('\n'));
    return text.toString();
  }

  private static String notice() {
    try (InputStream in = MainFrame.class.getResourceAsStream("/NOTICE.md")) {
      if (in == null) {
        return "NOTICE.md is missing from the application.";
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Map<String, Runnable> handlers() {
    Action toggleList = content.getActionMap().get(TOGGLE_LIST);
    return Map.ofEntries(
        Map.entry(KeyMap.FOCUS_SEARCH, searchBar::focus),
        Map.entry(KeyMap.ESCAPE, this::escape),
        Map.entry(KeyMap.BACK, this::back),
        Map.entry(KeyMap.FORWARD, this::forward),
        Map.entry(KeyMap.PIN, () -> status.setMessage(togglePin(uiState, controller != null ? controller.current()
            : null))),
        Map.entry(KeyMap.TOGGLE_LIST, () -> {
          if (!layout.isCompact()) { // the compact layout has no list
            toggleList.actionPerformed(new ActionEvent(content, ActionEvent.ACTION_PERFORMED, KeyMap.TOGGLE_LIST));
          }
        }),
        Map.entry(KeyMap.COMPACT_MODE, this::toggleCompactMode),
        Map.entry(KeyMap.ALWAYS_ON_TOP, this::toggleAlwaysOnTop),
        Map.entry(KeyMap.TEXT_BIGGER, () -> showTextSize(textSize.bigger())),
        Map.entry(KeyMap.TEXT_SMALLER, () -> showTextSize(textSize.smaller())),
        Map.entry(KeyMap.TEXT_RESET, () -> showTextSize(textSize.reset())));
  }

  /**
   * Esc: the search box closes its dropdown or clears its text; failing both, the list takes the focus (in the
   * compact layout, the page's first table or list).
   */
  private void escape() {
    if (!searchBar.escape()) {
      Component target = layout.escapeTarget();
      if (target != null) {
        target.requestFocusInWindow();
      }
    }
  }

  /** F10: the other layout ({@link LayoutSwitch#toggle}). */
  private void toggleCompactMode() {
    layout.toggle();
    status.setMessage(layout.isCompact() ? "Compact window: F10 for the full one" : "Full window");
  }

  /**
   * The frame as {@link LayoutSwitch} drives it. A layout shown is brought up to date with the theme and text
   * size, which may have changed while it was off screen.
   */
  private LayoutSwitch.Window layoutWindow() {
    return new LayoutSwitch.Window() {
      @Override
      public void saveBounds() {
        persistence.saveNow();
      }

      @Override
      public void clearMaximised() {
        setExtendedState(getExtendedState() & ~Frame.MAXIMIZED_BOTH);
      }

      @Override
      public void maximise() {
        setExtendedState(getExtendedState() | Frame.MAXIMIZED_BOTH);
      }

      @Override
      public void show(JPanel pane, boolean withMenuBar) {
        setJMenuBar(withMenuBar ? menuBar : null);
        setContentPane(pane);
        SwingUtilities.updateComponentTreeUI(getRootPane());
        getRootPane().revalidate();
        repaint();
      }

      @Override
      public void place(Rectangle bounds) {
        setBounds(bounds);
      }

      @Override
      public List<Rectangle> screens() {
        return WindowStatePersistence.screens();
      }

      @Override
      public Rectangle currentScreen() {
        return getGraphicsConfiguration().getBounds();
      }
    };
  }

  /** Ctrl+Shift+T or the compact pin button: keeps the window above other windows, or stops; remembered. */
  private void toggleAlwaysOnTop() {
    setAlwaysOnTop(!isAlwaysOnTop());
    uiState.setAlwaysOnTop(isAlwaysOnTop());
    compactShell.setOnTop(isAlwaysOnTop());
    status.setMessage(isAlwaysOnTop() ? "Always on top" : "No longer on top");
  }

  private void showTextSize(int pct) {
    status.setMessage("Text size " + pct + " %");
  }

  /** The Dark theme item, which reads the saved theme whenever its menu opens (either layout may switch it). */
  private JCheckBoxMenuItem darkThemeItem() {
    JCheckBoxMenuItem dark = new JCheckBoxMenuItem("Dark theme", ThemeManager.isDark());
    dark.addActionListener(e -> ThemeManager.toggle(this));
    return dark;
  }

  /** The compact layout's context menu: what its missing menu bar would offer, and the way back. */
  private JPopupMenu compactMenu() {
    JMenuItem full = new JMenuItem("Full window (F10)"); // no accelerator: F10 is the window's own key
    full.addActionListener(e -> toggleCompactMode());
    JCheckBoxMenuItem dark = darkThemeItem();
    JMenuItem shortcuts = new JMenuItem("Keyboard shortcuts");
    shortcuts.addActionListener(e -> showShortcuts());
    JMenuItem about = new JMenuItem("About");
    about.addActionListener(e -> showAbout());
    JPopupMenu menu = new JPopupMenu();
    menu.add(full);
    menu.add(dark);
    menu.add(WindowMenus.sourceFilterMenu(uiState, this::rerender));
    menu.addSeparator();
    menu.add(shortcuts);
    menu.add(about);
    menu.addPopupMenuListener(new PopupMenuListener() {
      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        dark.setSelected(ThemeManager.isDark());
      }

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}

      @Override
      public void popupMenuCanceled(PopupMenuEvent e) {}
    });
    return menu;
  }

  private JMenuBar menuBar() {
    JCheckBoxMenuItem dark = darkThemeItem();
    Action toggleList = content.getActionMap().get(TOGGLE_LIST);
    JCheckBoxMenuItem showList = new JCheckBoxMenuItem(toggleList);
    showList.setSelected(list.isVisible());
    showList.setAccelerator(KeyStroke.getKeyStroke("ctrl B"));
    list.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        showList.setSelected(true);
      }

      @Override
      public void componentHidden(ComponentEvent e) {
        showList.setSelected(false);
      }
    });
    JMenu view = new JMenu("View");
    view.add(showList);
    view.add(dark);
    view.add(WindowMenus.sourceFilterMenu(uiState, this::rerender));
    view.addMenuListener(new MenuListener() {
      @Override
      public void menuSelected(MenuEvent e) {
        dark.setSelected(ThemeManager.isDark());
      }

      @Override
      public void menuDeselected(MenuEvent e) {}

      @Override
      public void menuCanceled(MenuEvent e) {}
    });
    JMenuItem shortcuts = new JMenuItem("Keyboard shortcuts");
    shortcuts.addActionListener(e -> showShortcuts());
    JMenuItem about = new JMenuItem("About");
    about.addActionListener(e -> showAbout());
    JMenu help = new JMenu("Help");
    help.add(shortcuts);
    help.add(about);
    JMenuBar bar = new JMenuBar();
    bar.add(view);
    bar.add(help);
    // F10 is compact mode (KeyMap), not the look and feel's "focus the menu bar"; Alt still reaches the menus
    bar.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("F10"), "none");
    return bar;
  }

  private void showShortcuts() {
    JTable table = KeyMap.helpTable();
    JScrollPane scroll = new JScrollPane(table);
    scroll.setPreferredSize(new Dimension(580, table.getRowHeight() * (table.getRowCount() + 2)));
    JOptionPane.showMessageDialog(this, scroll, "Keyboard shortcuts", JOptionPane.PLAIN_MESSAGE);
  }

  private void showAbout() {
    JTextArea text = new JTextArea(aboutText(controller != null ? controller.meta() : Map.of()), 18, 72);
    text.setEditable(false);
    text.setLineWrap(true);
    text.setWrapStyleWord(true);
    text.setCaretPosition(0);
    JOptionPane.showMessageDialog(this, new JScrollPane(text), "About " + APP_NAME, JOptionPane.INFORMATION_MESSAGE);
  }

  private void back() {
    if (controller != null) {
      controller.back();
    }
  }

  private void forward() {
    if (controller != null) {
      controller.forward();
    }
  }

  public SearchBar searchBar() {
    return searchBar;
  }

  @Override
  public MasterList masterList() {
    return list;
  }

  @Override
  public void setDetail(JComponent c) {
    detailHolder.removeAll();
    detailHolder.add(c, BorderLayout.CENTER);
    detailHolder.revalidate();
    detailHolder.repaint();
  }

  @Override
  public void setHistoryState(boolean canBack, boolean canForward) {
    back.setEnabled(canBack);
    forward.setEnabled(canForward);
    compactShell.setHistoryState(canBack, canForward);
  }

  @Override
  public StatusBar status() {
    return status;
  }

  @Override
  public void setTitleSuffix(String s) {
    setTitle(s == null || s.isEmpty() ? APP_NAME : APP_NAME + " — " + s);
  }

  @Override
  public void selectRail(RailTarget target) {
    rail.select(target);
  }

  @Override
  public boolean showsMasterList() {
    return !layout.isCompact();
  }
}
