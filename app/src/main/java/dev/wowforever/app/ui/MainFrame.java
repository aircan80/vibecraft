package dev.wowforever.app.ui;

import dev.wowforever.app.model.SourceFilter;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.widgets.HistoryButtons;
import dev.wowforever.app.ui.widgets.MasterList;
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
  public static final String APP_NAME = dev.wowforever.app.Branding.NAME;
  private static final Logger LOG = LoggerFactory.getLogger(MainFrame.class);
  /** The content's action that hides or shows the master list (Ctrl+B through {@link KeyMap}). */
  static final String TOGGLE_LIST = "toggleList";
  private static final String SHOWN_DIVIDER_SIZE = "wowforever.shownDividerSize";
  /** The content's client property holding its {@link ListVisibility}. */
  private static final String LIST_VISIBILITY = "wowforever.listVisibility";

  private final JButton back = HistoryButtons.back();
  private final JButton forward = HistoryButtons.forward();
  private final JButton themeButton = new JButton();
  private final SearchBar searchBar;
  private final MasterList list = new MasterList();
  private final StatusBar status = new StatusBar();
  private final JPanel detailHolder = new JPanel(new BorderLayout());
  /** Where {@link #detailHolder} sits in the full layout. */
  private final JPanel detailSlot = new JPanel(new BorderLayout());
  private final UiExperience experience;
  private final dev.wowforever.app.ui.journal.JournalSidebar journalSidebar;
  private final dev.wowforever.app.ui.journal.JournalBreadcrumbs journalBreadcrumbs;
  private final JComponent pageHolder;
  private JScrollPane journalNavigation;
  private final UiState uiState;
  /** Created before any size is applied, so it scales from the look and feel's own font. */
  private final TextSize textSize;
  private final JToolBar toolbar;
  private final JPanel content;
  private final CompactShell compactShell = new CompactShell(this::back, this::forward, this::toggleAlwaysOnTop);
  private final JMenuBar menuBar;
  private final WindowStatePersistence persistence;
  private final LayoutSwitch layout;
  private AppController controller;
  private dev.wowforever.app.ui.journal.PinnedResearch research;
  private ViewTarget researchTarget;
  private String researchLabel;
  private dev.wowforever.app.catalog.EditionRegistry editions;
  private dev.wowforever.app.catalog.CatalogSession catalogSession;
  private final JMenuItem gameCatalog = new JMenuItem("Game catalog…");
  private final JMenuItem interfaceSounds = new JMenuItem("Interface sounds…");


  /** Retained argument validation for callers migrating from the retired shell. */
  static UiExperience supportedExperience(UiExperience experience) {
    if (experience != UiExperience.JOURNAL) {
      throw new IllegalArgumentException("Original interface has been retired; use Explorer Journal");
    }
    return experience;
  }

  private static String titleForExperience(UiExperience experience) {
    supportedExperience(experience);
    return APP_NAME;
  }

  public MainFrame() { this(UiExperience.JOURNAL); }

  public MainFrame(UiExperience experience) {
    super(titleForExperience(experience));
    this.experience = supportedExperience(experience);
    dev.wowforever.app.ui.journal.JournalAppearance.apply();
    uiState = UiState.loadJournal();
    textSize = new TextSize(uiState);
    journalSidebar = new dev.wowforever.app.ui.journal.JournalSidebar(t -> { if (controller != null) controller.open(t); });
    journalBreadcrumbs = new dev.wowforever.app.ui.journal.JournalBreadcrumbs(t -> { if (controller != null) controller.open(t); });
    {
      JPanel journalPage = new JPanel(new BorderLayout());
      journalPage.add(journalBreadcrumbs, BorderLayout.NORTH);
      journalPage.add(detailHolder, BorderLayout.CENTER);
      pageHolder = journalPage;
      journalSidebar.setReady(false);
    }
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
    searchBar.setEnabled(false);
    back.addActionListener(e -> back());
    forward.addActionListener(e -> forward());
    setHistoryState(false, false);
    detailHolder.add(new JLabel("Loading…", SwingConstants.CENTER), BorderLayout.CENTER);

    toolbar = toolbar(back, forward, searchBar);
    themeButton.setName("journal-theme-toggle");
    themeButton.getAccessibleContext().setAccessibleName("Toggle light and dark theme");
    themeButton.addActionListener(event -> toggleTheme());
    JPanel themeControl = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.TRAILING, 0, 0));
    themeControl.setOpaque(false);
    themeControl.add(themeButton);
    toolbar.add(themeControl, BorderLayout.EAST);
    refreshJournalChrome();
    detailSlot.add(pageHolder, BorderLayout.CENTER);
    JComponent navigation;
    {
      JScrollPane sidebarScroll = new JScrollPane(journalSidebar,
          JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      sidebarScroll.setBorder(BorderFactory.createEmptyBorder());
      journalNavigation = sidebarScroll;
      navigationWidth(false);
      navigation = sidebarScroll;
      refreshJournalChrome();
    }
    content = buildContent(toolbar, navigation, list, detailSlot, status, uiState);
    if (journalSidebar != null) {
      content.addComponentListener(new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent e) {
          boolean collapsed = content.getWidth() < 980;
          journalSidebar.setCollapsed(collapsed);
          navigationWidth(collapsed);
        }
      });
    }
    menuBar = menuBar();
    compactShell.setMenu(compactMenu());
    KeyMap.install(getRootPane(), handlers());
    {
      getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_D, java.awt.event.InputEvent.CTRL_DOWN_MASK), "pin-research");
      getRootPane().getActionMap().put("pin-research", new AbstractAction() {
        @Override public void actionPerformed(java.awt.event.ActionEvent event) {
          if(research==null || researchTarget==null || researchLabel==null || researchLabel.isBlank()
              || dev.wowforever.app.ui.journal.PinnedResearch.key(researchTarget).isEmpty()) {
            status.setMessage("Open a loaded entity, map or catalog to pin it.");return;
          }
          status.setMessage(research.toggle(researchTarget,researchLabel)?"Saved to Pinned research":"Removed from Pinned research");
        }
      });
    }
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    persistence = WindowStatePersistence.install(this, uiState);
    compactShell.setOnTop(isAlwaysOnTop());
    layout = new LayoutSwitch(new LayoutSwitch.Parts(toolbar, content, detailSlot, list, compactShell, searchBar,
        pageHolder, status), uiState, layoutWindow(), this::rerender);
    layout.start();
    textSize.applySaved();
    wireStatusToggles();
    {
      status.addComponentListener(new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent e) { fitJournalStatus(status); }
      });
      status.addHierarchyListener(e -> SwingUtilities.invokeLater(() -> fitJournalStatus(status)));
      status.messageLabel().addPropertyChangeListener("text", e ->
          status.messageLabel().setToolTipText(status.message()));
      fitJournalStatus(status);
    }
    SwingUtilities.updateComponentTreeUI(this);
  }

  /**
   * The status line's toggles: Slate is selected while the Slate theme is on and switches to Parchment and back,
   * as the View menu's theme items do; Compact is selected in the compact layout and switches layouts as F10 does.
   */
  private void wireStatusToggles() {
    status.themeToggle().setModel(new WindowMenus.LiveToggle(ThemeManager::isDark));
    status.themeToggle().addActionListener(e -> toggleTheme());
    status.layoutToggle().setModel(new WindowMenus.LiveToggle(layout::isCompact));
    status.layoutToggle().addActionListener(e -> toggleCompactMode());
  }

  /** Journal's narrow layout prioritizes the current message and controls; full metadata stays in tooltips. */
  static void fitJournalStatus(StatusBar status) {
    boolean compact = dev.wowforever.app.ui.detail.CompactColumns.within(status);
    boolean wide = status.getWidth() >= 960 && !compact;
    status.provenanceLabel().setVisible(wide);
    status.infoLabel().setVisible(wide);
    status.messageLabel().setHorizontalAlignment(wide ? SwingConstants.CENTER : SwingConstants.LEFT);
    status.messageLabel().setToolTipText(status.message());
    status.setToolTipText(status.provenance() + (status.info().isEmpty() ? "" : " · " + status.info()));
    status.revalidate();
  }

  private void navigationWidth(boolean collapsed) {
    if (journalNavigation == null) return;
    journalNavigation.setPreferredSize(new Dimension(collapsed ? 64 : 224, 0));
    journalNavigation.setMinimumSize(new Dimension(collapsed ? 64 : 224, 0));
    journalNavigation.revalidate();
    if (journalNavigation.getParent() != null) journalNavigation.getParent().revalidate();
  }

  private void toggleTheme() {
    ThemeManager.toggle(this);
    if (journalSidebar != null) {
      dev.wowforever.app.ui.journal.JournalAppearance.apply();
      SwingUtilities.updateComponentTreeUI(this);
      journalSidebar.refreshTheme();
      refreshJournalChrome();
      rerender();
    }
  }

  /** Main-panel header: bordered history controls, flexible search, then theme control. */
  static JToolBar toolbar(JButton back, JButton forward, SearchBar search) {
    JToolBar toolbar = new JToolBar();
    toolbar.setLayout(new BorderLayout(12, 0));
    toolbar.setFloatable(false);
    JPanel history = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING, 5, 0));
    history.setOpaque(false);
    history.add(back); history.add(forward);
    toolbar.add(history, BorderLayout.WEST);
    toolbar.add(search, BorderLayout.CENTER);
    return toolbar;
  }

  private void refreshJournalChrome() {
    var theme=dev.wowforever.app.ui.theme.Theme.current();
    toolbar.setBackground(theme.bgSurface());
    toolbar.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 1, 0, theme.hairline()),
        BorderFactory.createEmptyBorder(18, 24, 18, 24)));
    for(JButton button:new JButton[]{back,forward,themeButton}) {
      button.setPreferredSize(new Dimension(34, 36));
      button.setBackground(theme.bgSurface());
      button.setForeground(theme.textPrimary());
      button.setBorder(javax.swing.UIManager.getBorder("Button.border"));
      button.putClientProperty("JButton.buttonType", null);
      button.putClientProperty("FlatLaf.style", java.util.Map.of(
          "arc", 14, "borderWidth", 1f, "focusWidth", 1,
          "borderColor", theme.hairline(), "focusedBorderColor", theme.link(),
          "hoverBorderColor", theme.link(), "margin", new java.awt.Insets(5, 5, 5, 5),
          "disabledBackground", theme.bgSurface()));
      button.setBorderPainted(true);
    }
    themeButton.setIcon(dev.wowforever.app.ui.widgets.Icons.icon(theme.dark()?"sun":"moon",18));
    themeButton.setToolTipText(theme.dark()?"Switch to light mode":"Switch to dark mode");
    searchBar.styleForJournal();
  }

  /**
   * The window content: full-height {@code rail} at the left; {@code toolbar} above the main
   * {@code list | detail} split whose
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
    JPanel workspace = new JPanel(new BorderLayout());
    workspace.add(toolbar, BorderLayout.NORTH);
    workspace.add(split, BorderLayout.CENTER);
    JPanel content = new JPanel(new BorderLayout());
    content.add(rail, BorderLayout.WEST);
    content.add(workspace, BorderLayout.CENTER);
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
   * Wires in the controller once the database is open, and enables searching and type-ahead. Journal client art
   * loads once, on the controller's worker; on the EDT it then becomes the {@linkplain RailIcons#shared shared} icons
   * and refreshes the journal navigation.
   */
  public void attach(AppController controller) {
    this.controller = controller;
    MouseNavigation.install(controller, getRootPane());
    searchBar.attach(SearchBar.Source.of(controller.repos().search()), controller.worker());
    searchBar.setEnabled(true);
    if (journalSidebar != null) journalSidebar.setReady(true);
    Async.run(controller.worker(), () -> RailIcons.load(controller.repos().assets()::uiIcon), icons -> {
      RailIcons.install(icons);
      if (journalSidebar != null) journalSidebar.refreshTheme();
    }, e -> LOG.warn("Loading navigation icons failed; the journal keeps its own glyphs", e));
  }

  /** The About text: the bundled NOTICE.md, then the database meta values. */
  public static String aboutText(Map<String, String> meta) {
    StringBuilder text = new StringBuilder(APP_NAME).append("\n")
        .append(dev.wowforever.app.Branding.TAGLINE).append("\n\n")
        .append(dev.wowforever.app.Branding.DESCRIPTION).append("\n\n")
        .append(dev.wowforever.app.Branding.CREDIT).append("\n\n")
        .append(notice().strip()).append("\n\n# Database\n\n");
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
    return new WindowMenus.Actions(ThemeManager::isDark, () -> toggleTheme(), this::showShortcuts,
        this::showAbout, this::showSources);
  }

  /** The compact layout's context menu: what its missing menu bar would offer, and the way back. */
  private JPopupMenu compactMenu() {
    JMenuItem full = new JMenuItem("Full window (F10)"); // no accelerator: F10 is the window's own key
    full.addActionListener(e -> toggleCompactMode());
    JPopupMenu menu = new JPopupMenu();
    {
      JMenu browse = new JMenu("Browse the journal");
      for (RailTarget target : RailTarget.values()) {
        JMenuItem item = new JMenuItem(target.label());
        item.addActionListener(e -> { if (controller != null) controller.openRail(target); });
        browse.add(item);
      }
      menu.add(browse);
      JMenuItem catalog = new JMenuItem("Game catalog…");
      catalog.setEnabled(catalogSession != null);
      catalog.addActionListener(event -> showGameCatalog());
      menu.add(catalog);
      JMenuItem sounds = new JMenuItem("Interface sounds…");
      sounds.setEnabled(catalogSession != null);
      sounds.addActionListener(event -> showInterfaceSounds());
      menu.add(sounds);
      menu.addSeparator();
    }
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
    {
      JMenu settings = new JMenu("Settings");
      gameCatalog.setName("game-catalog-menu");
      gameCatalog.setEnabled(false);
      gameCatalog.addActionListener(event -> showGameCatalog());
      settings.add(gameCatalog);
      interfaceSounds.setName("interface-sounds-menu");
      interfaceSounds.setEnabled(false);
      interfaceSounds.addActionListener(event -> showInterfaceSounds());
      settings.add(interfaceSounds);
      bar.add(settings);
    }
    bar.add(help);
    // F10 is compact mode (KeyMap), not the look and feel's "focus the menu bar"; Alt still reaches the menus
    bar.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("F10"), "none");
    return bar;
  }

  /** Called after worker-loaded catalog identity is verified, independently of interface selection. */
  public void configureCatalogSelection(dev.wowforever.app.catalog.EditionRegistry editions,
      dev.wowforever.app.catalog.CatalogSession session) {
    this.editions = java.util.Objects.requireNonNull(editions);
    this.catalogSession = java.util.Objects.requireNonNull(session);
    gameCatalog.setEnabled(true);
    interfaceSounds.setEnabled(true);
    gameCatalog.setToolTipText("Current game: WoW Forever · " + session.token().context().clientBuild());
  }

  private void showInterfaceSounds() {
    if (catalogSession == null) return;
    var effects = dev.wowforever.app.sound.UiSoundService.shared();
    JPanel panel = new JPanel(new BorderLayout(8, 8));
    panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    JTextArea explanation = new JTextArea("Optional feedback for navigation, windows and tabs. City music has separate controls.");
    explanation.setEditable(false);explanation.setLineWrap(true);explanation.setWrapStyleWord(true);
    explanation.setOpaque(false);explanation.setFont(panel.getFont());
    explanation.setPreferredSize(new Dimension(360,54));
    panel.add(explanation, BorderLayout.NORTH);
    panel.add(new dev.wowforever.app.sound.UiSoundPanel(effects), BorderLayout.CENTER);
    effects.play(dev.wowforever.app.sound.UiSoundService.Cue.OPEN);
    try { JOptionPane.showMessageDialog(this, panel, "Interface sounds", JOptionPane.PLAIN_MESSAGE); }
    finally { effects.play(dev.wowforever.app.sound.UiSoundService.Cue.CLOSE); }
  }

  private void showGameCatalog() {
    if (catalogSession == null) return;
    var picker = new dev.wowforever.app.catalog.EditionPicker(editions, catalogSession, token -> {
      // Only Forever is installed. Future switching must rebuild scoped repositories and navigation.
      status.setMessage("Game catalog: WoW Forever · " + token.context().clientBuild());
    });
    picker.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
    JOptionPane.showMessageDialog(this, picker, "Game catalog", JOptionPane.PLAIN_MESSAGE);
  }

  private void showShortcuts() {
    JTable table = KeyMap.helpTable();
    JScrollPane scroll = new JScrollPane(table);
    scroll.setPreferredSize(new Dimension(580, table.getRowHeight() * (table.getRowCount() + 2)));
    JOptionPane.showMessageDialog(this, scroll, "Keyboard shortcuts", JOptionPane.PLAIN_MESSAGE);
  }

  private void showSources() {
    if (controller == null) return;
    controller.loadSourceCatalog(catalog -> {
      if (!isDisplayable()) return;
      var panel = new SourceCatalogPanel(catalog, uri -> {
        try {
          java.awt.Desktop.getDesktop().browse(uri);
        } catch (java.io.IOException | UnsupportedOperationException failure) {
          throw new IllegalStateException(failure.getMessage(), failure);
        }
      });
      var screen = getGraphicsConfiguration().getBounds();
      panel.setPreferredSize(new Dimension(Math.min(900, Math.max(320, screen.width - 120)),
          Math.min(450, Math.max(200, screen.height - 160))));
      JOptionPane.showMessageDialog(this, panel, "Data sources", JOptionPane.PLAIN_MESSAGE);
    }, failure -> {
      if (isDisplayable()) JOptionPane.showMessageDialog(this, "Could not load source archive metadata.",
          "Data sources", JOptionPane.ERROR_MESSAGE);
      LOG.warn("Loading source archive metadata failed", failure);
    });
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

  @Override
  public long searchEditRevision() {
    return searchBar.editRevision();
  }

  @Override
  public void followPageName(String displayName) {
    searchBar.followPageName(displayName);
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
    researchTarget=null;
    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    boolean keepFocusInPage = focusStaysInPage(owner, detailHolder);
    detailHolder.removeAll();
    detailHolder.add(c, BorderLayout.CENTER);
    installTabSounds(c, cue -> dev.wowforever.app.sound.UiSoundService.shared().play(cue));
    detailHolder.revalidate();
    detailHolder.repaint();
    if (keepFocusInPage) {
      // Removing the focused page still hands the focus on by traversal (to the search box, as it happens); focus
      // requests are queued, and this last one wins. SearchBar ignores that transient traversal gain, so the recent
      // searches never pop open.
      focusTargetIn(c).requestFocusInWindow();
    }
  }

  /** Only direct tab input qualifies: state restoration and lazy loading stay silent. */
  static boolean userTabSelection(javax.swing.JTabbedPane tabs, java.awt.AWTEvent event) {
    if (event instanceof java.awt.event.MouseEvent mouse && mouse.getSource() == tabs) {
      int index = tabs.indexAtLocation(mouse.getX(), mouse.getY());
      return (mouse.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED
          || mouse.getID() == java.awt.event.MouseEvent.MOUSE_RELEASED)
          && index >= 0 && index == tabs.getSelectedIndex();
    }
    if (event instanceof java.awt.event.KeyEvent key && key.getSource() == tabs
        && key.getID() == java.awt.event.KeyEvent.KEY_PRESSED) {
      return switch (key.getKeyCode()) {
        case java.awt.event.KeyEvent.VK_LEFT, java.awt.event.KeyEvent.VK_RIGHT,
            java.awt.event.KeyEvent.VK_UP, java.awt.event.KeyEvent.VK_DOWN,
            java.awt.event.KeyEvent.VK_PAGE_UP, java.awt.event.KeyEvent.VK_PAGE_DOWN -> true;
        default -> false;
      };
    }
    return false;
  }

  static void installTabSounds(Component component,
      Consumer<dev.wowforever.app.sound.UiSoundService.Cue> feedback) {
    if (!(component instanceof JComponent jc) || Boolean.TRUE.equals(jc.getClientProperty("ui-sound-tabs-installed"))) return;
    jc.putClientProperty("ui-sound-tabs-installed", true);
    if (jc instanceof javax.swing.JTabbedPane tabs) {
      tabs.addChangeListener(event -> {
        if (userTabSelection(tabs, java.awt.EventQueue.getCurrentEvent()))
          feedback.accept(dev.wowforever.app.sound.UiSoundService.Cue.TAB);
      });
    }
    jc.addContainerListener(new java.awt.event.ContainerAdapter() {
      @Override public void componentAdded(java.awt.event.ContainerEvent event) {
        installTabSounds(event.getChild(), feedback);
      }
    });
    for (Component child : jc.getComponents()) installTabSounds(child, feedback);
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
    researchLabel=s;
    String app = APP_NAME;
    setTitle(s == null || s.isEmpty() ? app : app + " — " + s);
    if (journalBreadcrumbs != null) journalBreadcrumbs.setPageTitle(s);
  }

  @Override public void highlightCitySection() {
    if(journalSidebar!=null) journalSidebar.select(new ViewTarget.Forever("cities"));
  }

  @Override public UiExperience uiExperience() { return experience; }

  @Override public void navigationStarted() { researchTarget=null; }

  @Override public void configureResearchScope(String scope) { research=scope==null || scope.isBlank()?null:new dev.wowforever.app.ui.journal.PinnedResearch(uiState,scope); }

  @Override public JComponent pinnedResearchPage(java.util.function.Consumer<ViewTarget> open) {
    if(research==null) return new JLabel("Pinned research requires an identified game and data build.");
    return new dev.wowforever.app.ui.journal.PinnedResearchPage(research,open);
  }

  @Override public void viewTargetChanged(ViewTarget target) {
    researchTarget=target;
    if (journalSidebar != null && target != null) {
      journalSidebar.select(target);
      journalBreadcrumbs.showTarget(target);
    }
  }

  @Override
  public BooleanSetting zonesMap() { return uiState.zonesMapSetting(); }

  @Override
  public void selectRail(RailTarget target) {
    // Journal selection follows the complete ViewTarget, including city and nested map context.
  }

  @Override
  public boolean showsMasterList() {
    return !layout.isCompact();
  }
}
