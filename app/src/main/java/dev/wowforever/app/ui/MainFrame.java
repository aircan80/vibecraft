package dev.wowforever.app.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.swing.AbstractAction;
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
 * The main window: back/forward and search on top, browse tree | results | detail in the middle, status
 * at the bottom. Created before the database is open; {@link #attach} wires in the controller and the tree.
 * Never created in tests (they use {@link #buildContent} and the panels directly).
 */
public final class MainFrame extends JFrame implements MainView {
  public static final String APP_NAME = "WoW Forever Item & Drop Browser";

  private final JButton back = new JButton("←");
  private final JButton forward = new JButton("→");
  private final SearchBar searchBar;
  private final ResultsPanel results;
  private final StatusBar status = new StatusBar();
  private final JPanel treeHolder = new JPanel(new BorderLayout());
  private final JPanel detailHolder = new JPanel(new BorderLayout());
  private AppController controller;

  public MainFrame() {
    super(APP_NAME);
    searchBar = new SearchBar(t -> {
      if (controller != null) {
        controller.open(t);
      }
    });
    results = new ResultsPanel(t -> {
      if (controller != null) {
        controller.open(t);
      }
    });
    searchBar.setEnabled(false);
    back.setToolTipText("Back (Alt+Left)");
    forward.setToolTipText("Forward (Alt+Right)");
    back.addActionListener(e -> back());
    forward.addActionListener(e -> forward());
    setHistoryState(false, false);
    treeHolder.add(new JLabel("Loading…", SwingConstants.CENTER), BorderLayout.CENTER);

    JToolBar toolbar = new JToolBar();
    toolbar.setFloatable(false);
    toolbar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 8));
    toolbar.add(back);
    toolbar.add(forward);
    toolbar.add(searchBar);

    setContentPane(buildContent(toolbar, treeHolder, results, detailHolder, status));
    setJMenuBar(menuBar());
    bindKeys();
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    setSize(1280, 800);
    setLocationRelativeTo(null);
  }

  /** The window content: {@code toolbar} on top, tree | (results | detail) splits, {@code status} below. */
  public static JPanel buildContent(JComponent toolbar, JComponent tree, JComponent results, JComponent detail,
      JComponent status) {
    results.setPreferredSize(new Dimension(570, 600));
    detail.setPreferredSize(new Dimension(470, 600));
    JSplitPane right = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, results, detail);
    right.setResizeWeight(0.55);
    right.setContinuousLayout(true);
    tree.setPreferredSize(new Dimension(240, 600));
    JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tree, right);
    main.setDividerLocation(240);
    main.setContinuousLayout(true);
    JPanel content = new JPanel(new BorderLayout());
    content.add(toolbar, BorderLayout.NORTH);
    content.add(main, BorderLayout.CENTER);
    content.add(status, BorderLayout.SOUTH);
    return content;
  }

  /** Wires in the controller and the browse tree once the database is open, and enables searching. */
  public void attach(AppController controller, BrowseTree tree) {
    this.controller = controller;
    treeHolder.removeAll();
    treeHolder.add(tree, BorderLayout.CENTER);
    treeHolder.revalidate();
    treeHolder.repaint();
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
    JMenu view = new JMenu("View");
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
  public ResultsPanel results() {
    return results;
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
