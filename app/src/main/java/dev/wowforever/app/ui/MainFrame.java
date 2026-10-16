package dev.wowforever.app.ui;

import dev.wowforever.app.ui.widgets.MasterList;
import dev.wowforever.app.ui.widgets.Rail;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

/**
 * The main window: back/forward and search on top; the icon rail, the master list and the detail in the middle;
 * status at the bottom. Created before the database is open; {@link #attach} wires in the controller. Never
 * created in tests (they use {@link #buildContent} and the panels directly).
 */
public final class MainFrame extends JFrame implements MainView {
  public static final String APP_NAME = "WoW Forever Item & Drop Browser";
  /** The action key of Ctrl+B, which hides or shows the master list. */
  static final String TOGGLE_LIST = "toggleList";
  private static final String SHOWN_DIVIDER_SIZE = "wowforever.shownDividerSize";

  private final JButton back = new JButton("←");
  private final JButton forward = new JButton("→");
  private final SearchBar searchBar;
  private final Rail rail = new Rail();
  private final MasterList list = new MasterList();
  private final StatusBar status = new StatusBar();
  private final JPanel detailHolder = new JPanel(new BorderLayout());
  private final JPanel content;
  private AppController controller;

  public MainFrame() {
    super(APP_NAME);
    searchBar = new SearchBar(t -> {
      if (controller != null) {
        controller.open(t);
      }
    });
    rail.onSelect(t -> {
      if (controller != null) {
        controller.open(AppController.target(t));
      }
    });
    searchBar.setEnabled(false);
    back.setToolTipText("Back (Alt+Left)");
    forward.setToolTipText("Forward (Alt+Right)");
    back.addActionListener(e -> back());
    forward.addActionListener(e -> forward());
    setHistoryState(false, false);
    detailHolder.add(new JLabel("Loading…", SwingConstants.CENTER), BorderLayout.CENTER);

    JToolBar toolbar = new JToolBar();
    toolbar.setFloatable(false);
    toolbar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 8));
    toolbar.add(back);
    toolbar.add(forward);
    toolbar.add(searchBar);

    content = buildContent(toolbar, rail, list, detailHolder, status, UiState.load());
    setContentPane(content);
    setJMenuBar(menuBar());
    bindKeys();
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    setSize(1280, 800);
    setLocationRelativeTo(null);
  }

  /**
   * The window content: {@code toolbar} on top; {@code rail} at the left of a {@code list | detail} split whose
   * divider starts at, and is saved to, {@code state}; {@code status} below. Ctrl+B ({@link #TOGGLE_LIST}) hides
   * or shows the list.
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
    content.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ctrl B"), TOGGLE_LIST);
    content.getActionMap().put(TOGGLE_LIST, new AbstractAction("Show list") {
      @Override
      public void actionPerformed(ActionEvent e) {
        toggleList(split, list, state);
      }
    });
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

  /** Wires in the controller once the database is open, and enables searching. */
  public void attach(AppController controller) {
    this.controller = controller;
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

  private JMenuBar menuBar() {
    JCheckBoxMenuItem dark = new JCheckBoxMenuItem("Dark theme", ThemeManager.isDark());
    dark.addActionListener(e -> ThemeManager.toggle(this));
    Action toggleList = content.getActionMap().get(TOGGLE_LIST);
    JCheckBoxMenuItem showList = new JCheckBoxMenuItem(toggleList);
    showList.setSelected(true);
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
    JMenuItem about = new JMenuItem("About");
    about.addActionListener(e -> showAbout());
    JMenu help = new JMenu("Help");
    help.add(about);
    JMenuBar bar = new JMenuBar();
    bar.add(view);
    bar.add(help);
    return bar;
  }

  private void showAbout() {
    JTextArea text = new JTextArea(aboutText(controller != null ? controller.meta() : Map.of()), 18, 72);
    text.setEditable(false);
    text.setLineWrap(true);
    text.setWrapStyleWord(true);
    text.setCaretPosition(0);
    JOptionPane.showMessageDialog(this, new JScrollPane(text), "About " + APP_NAME, JOptionPane.INFORMATION_MESSAGE);
  }

  private void bindKeys() {
    bind("ctrl F", "focusSearch", searchBar::focus);
    bind("alt LEFT", "back", this::back);
    bind("alt RIGHT", "forward", this::forward);
  }

  private void bind(String key, String name, Runnable action) {
    getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
    getRootPane().getActionMap().put(name, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        action.run();
      }
    });
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
  }

  @Override
  public StatusBar status() {
    return status;
  }

  @Override
  public void setTitleSuffix(String s) {
    setTitle(s == null || s.isEmpty() ? APP_NAME : APP_NAME + " — " + s);
  }
}
