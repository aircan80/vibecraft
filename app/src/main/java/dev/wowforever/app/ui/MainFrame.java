package dev.wowforever.app.ui;

import dev.wowforever.app.model.SourceFilter;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.widgets.HistoryButtons;
import dev.wowforever.app.ui.widgets.MasterList;
import dev.wowforever.app.ui.widgets.Rail;
import dev.wowforever.app.ui.widgets.RailIcons;
import dev.wowforever.app.ui.widgets.RailTarget;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
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
import java.util.function.Consumer;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private static final Logger LOG = LoggerFactory.getLogger(MainFrame.class);
  /** The content's action that hides or shows the master list (Ctrl+B through {@link KeyMap}). */
  static final String TOGGLE_LIST = "toggleList";
  private static final String SHOWN_DIVIDER_SIZE = "wowforever.shownDividerSize";
  /** The content's client property holding its {@link ListVisibility}. */
  private static final String LIST_VISIBILITY = "wowforever.listVisibility";

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

      @Override
      public void openHit(ViewTarget.Entity hit, ViewTarget.Search search, String title) {
        if (controller != null) {
          controller.openHit(hit, search, title);
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
    wireStatusToggles();
  }

  /**
   * The status line's toggles: Slate is selected while the Slate theme is on and switches to Parchment and back,
   * as the View menu's theme items do; Compact is selected in the compact layout and switches layouts as F10 does.
   */
  private void wireStatusToggles() {
    status.themeToggle().setModel(new WindowMenus.LiveToggle(ThemeManager::isDark));
    status.themeToggle().addActionListener(e -> ThemeManager.toggle(this));
    status.layoutToggle().setModel(new WindowMenus.LiveToggle(layout::isCompact));
    status.layoutToggle().addActionListener(e -> toggleCompactMode());
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
   * divider starts at, and is saved to, {@code state}; {@code status} below. When the list shows is up to the
   * content's {@link ListVisibility} ({@link #listVisibility}): the content's {@link #TOGGLE_LIST} action (Ctrl+B)
   * goes through it, and the list starts shown when {@code state} saved it pinned.
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
    ListVisibility visibility = new ListVisibility(state.listVisible());
    Runnable apply = () -> showList(split, list, state, visibility.visible());
    content.putClientProperty(LIST_VISIBILITY, new ListControl(visibility, apply));
    content.getActionMap().put(TOGGLE_LIST, new AbstractAction("Show list") {
      @Override
      public void actionPerformed(ActionEvent e) {
        visibility.toggle();
        apply.run();
        state.setListVisible(visibility.pinned());
      }
    });
    apply.run();
    return content;
  }

  /** The list's visibility in a content, and what shows or hides the list as it says. */
  private record ListControl(ListVisibility visibility, Runnable apply) {}

  /** The list's visibility in {@code content}, made by {@link #buildContent}. */
  static ListVisibility listVisibility(JPanel content) {
    return ((ListControl) content.getClientProperty(LIST_VISIBILITY)).visibility();
  }

  /**
   * Updates the list of {@code content} with {@code change} to its {@link ListVisibility}, then shows or hides it as
   * that says.
   */
  static void updateList(JPanel content, Consumer<ListVisibility> change) {
    ListControl control = (ListControl) content.getClientProperty(LIST_VISIBILITY);
    change.accept(control.visibility());
    control.apply().run();
  }

  /**
   * Hides the list (the detail takes the whole split), or shows it again at the saved width; nothing when it is
   * already so.
   */
  private static void showList(JSplitPane split, JComponent list, UiState state, boolean show) {
    if (list.isVisible() == show) {
      return;
    }
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

  /** Shows the current page again (a layout switch); nothing before the controller. */
  private void rerender() {
    if (controller != null) {
      controller.rerender();
    }
  }

  @Override
  public SourceFilter sourceFilter() {
    return uiState.sourceFilter();
  }

  @Override
  public void setSourceFilter(SourceFilter filter) {
    uiState.setSourceFilter(filter);
  }

  /**
   * A result opened: the list collapses unless pinned or the window is wide ({@link ListVisibility}). When the list
   * had the focus (Enter on a row), the focus moves to the page, so it does not fall wherever AWT puts it.
   */
  @Override
  public void onResultOpened() {
    if (!layout.isCompact()) { // the compact layout has no list
      Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      updateList(content, v -> v.resultOpened(getWidth()));
      Component target = focusAfterCollapse(owner, list, detailHolder);
      if (target != null) {
        target.requestFocusInWindow();
      }
    }
  }

  /**
   * Where the focus goes once the list may have collapsed: when {@code owner} (the focus owner before) is inside
   * {@code list} and the list is now hidden, the page in {@code holder} ({@link #focusTargetIn}), or the holder while
   * it holds none; otherwise null, the focus staying where it is.
   */
  static Component focusAfterCollapse(Component owner, JComponent list, JPanel holder) {
    if (list.isVisible() || !focusStaysInPage(owner, list)) {
      return null;
    }
    return holder.getComponentCount() > 0 && holder.getComponent(0) instanceof JComponent page
        ? focusTargetIn(page) : holder;
  }

  /**
   * Shows the list of {@code content} again when an opened result collapsed it, without pinning it (as a search's
   * results do, {@link ListVisibility#resultsShown}): Esc's last step focuses the list, which must be on screen.
   */
  static void revealList(JPanel content) {
    if (!listVisibility(content).visible()) {
      updateList(content, ListVisibility::resultsShown);
    }
  }

  /** A search filled the list: it shows. */
  @Override
  public void onResultsShown() {
    if (!layout.isCompact()) {
      updateList(content, ListVisibility::resultsShown);
    }
  }

  @Override
  public BooleanSetting routes() {
    return uiState.routesSetting();
  }

  @Override
  public BooleanSetting questPins() {
    return uiState.questPinsSetting();
  }

  /**
   * Wires in the controller once the database is open, and enables searching and type-ahead. The rail's client art
   * loads once, on the controller's worker; on the EDT it then becomes the {@linkplain RailIcons#shared shared} icons
   * and replaces the rail's SVG glyphs (which stay if it fails).
   */
  public void attach(AppController controller) {
    this.controller = controller;
    MouseNavigation.install(controller, getRootPane());
    searchBar.attach(SearchBar.Source.of(controller.repos().search()), controller.worker());
    searchBar.setEnabled(true);
    Async.run(controller.worker(), () -> RailIcons.load(controller.repos().assets()::uiIcon), icons -> {
      RailIcons.install(icons);
      rail.setIcons(icons);
    }, e -> LOG.warn("Loading the rail icons failed; the rail keeps its own glyphs", e));
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
   * Esc: the search box closes its dropdown or clears its text; failing both, the list takes the focus, shown again
   * if an opened result collapsed it (in the compact layout, the page's first table or list).
   */
  private void escape() {
    JComponent page = detailHolder.getComponentCount() > 0 && detailHolder.getComponent(0) instanceof JComponent c
        ? c : null;
    if (!escapeContent(searchBar, page)) {
      if (!layout.isCompact()) {
        revealList(content); // a list collapsed by an opened result comes back for Esc, unpinned
      }
      Component target = layout.escapeTarget();
      if (target != null) {
        target.requestFocusInWindow();
      }
    }
  }

  /** Headless seam for the real dropdown → page filter → search Escape order. */
  static boolean escapeContent(SearchBar search, JComponent page) {
    if (search.dismissDropdown()) return true;
    if (page instanceof dev.wowforever.app.ui.pages.PageEscape local && local.escape()) return true;
    return search.escape();
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

  /** What the menu items both layouts share do ({@link WindowMenus}). */
  private WindowMenus.Actions menuActions() {
    return new WindowMenus.Actions(ThemeManager::isDark, () -> ThemeManager.toggle(this), this::showShortcuts,
        this::showAbout);
  }

  /** The compact layout's context menu: what its missing menu bar would offer, and the way back. */
  private JPopupMenu compactMenu() {
    JMenuItem full = new JMenuItem("Full window (F10)"); // no accelerator: F10 is the window's own key
    full.addActionListener(e -> toggleCompactMode());
    JPopupMenu menu = new JPopupMenu();
    menu.add(full);
    WindowMenus.addViewItems(menu, menuActions());
    WindowMenus.addHelpItems(menu, menuActions());
    return menu;
  }

  private JMenuBar menuBar() {
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
    WindowMenus.addViewItems(view, menuActions());
    JMenu help = new JMenu("Help");
    WindowMenus.addHelpItems(help, menuActions());
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
    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    boolean keepFocusInPage = focusStaysInPage(owner, detailHolder);
    detailHolder.removeAll();
    detailHolder.add(c, BorderLayout.CENTER);
    detailHolder.revalidate();
    detailHolder.repaint();
    if (keepFocusInPage) {
      // Removing the focused page still hands the focus on by traversal (to the search box, as it happens); focus
      // requests are queued, and this last one wins. SearchBar ignores that transient traversal gain, so the recent
      // searches never pop open.
      focusTargetIn(c).requestFocusInWindow();
    }
  }

  /** Whether the keyboard focus, held by {@code owner}, sits inside {@code holder} (a page about to be replaced). */
  static boolean focusStaysInPage(Component owner, Container holder) {
    return owner != null && SwingUtilities.isDescendingFrom(owner, holder);
  }

  /** Where the focus goes on a freshly shown page: its first table or list, else the page itself. */
  static Component focusTargetIn(JComponent page) {
    Component browsable = LayoutSwitch.firstBrowsable(page);
    return browsable != null ? browsable : page;
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
