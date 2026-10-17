package dev.wowforever.app.ui;

import dev.wowforever.app.db.SourceArchiveRepository.Catalog;
import dev.wowforever.app.db.SourceArchiveRepository.SourceFile;
import dev.wowforever.app.db.SourceArchiveRepository.Snapshot;
import dev.wowforever.app.db.SourceArchiveComparison;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JButton;
import dev.wowforever.app.ui.widgets.WrappingText;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/** Dataset archive metadata, deliberately separate from claims about an individual app value. */
public final class SourceCatalogPanel extends JPanel {
  private final JTable snapshots;
  private final JTable files;
  private final JButton open = new JButton("Open selected source URL");
  private List<SourceFile> selectedFiles = List.of();
  private final JButton useBaseline = new JButton("Set baseline");
  private final JButton compare = new JButton("Compare selected");
  private final WrappingText baselineLabel = new WrappingText("Choose a snapshot baseline to compare archived files.", "Comparison baseline");
  private Snapshot baseline;
  private Snapshot selectedSnapshot;

  public SourceCatalogPanel(Catalog catalog, Consumer<URI> opener) {
    this(catalog, opener, null);
  }

  SourceCatalogPanel(Catalog catalog, Consumer<URI> opener, Consumer<JComponent> comparisonPresenter) {
    super(new BorderLayout(8, 8));
    if (!catalog.available()) baselineLabel.setText("Snapshot comparison requires archived source inputs.");
    Consumer<JComponent> presenter = comparisonPresenter == null
        ? panel -> JOptionPane.showMessageDialog(this, panel, "Source snapshot changes", JOptionPane.PLAIN_MESSAGE)
        : comparisonPresenter;
    add(new WrappingText(catalog.available()
        ? "Archived datasets and history; archive presence does not establish which source produced an app value."
        : "Legacy build metadata; original inputs not archived. Links identify recorded revisions or reference sites, not field-level evidence.", "Source archive scope"), BorderLayout.NORTH);
    var rows = new ArrayList<Object[]>();
    for (var s : catalog.snapshots()) rows.add(new Object[] {s.source(), s.version(), s.product(), s.build(),
        s.locale(), s.capturedAt(), String.join(", ", catalog.activeRoles().getOrDefault(s.id(), List.of())), s.id()});
    if (!catalog.available()) for (var source : catalog.legacySources()) rows.add(new Object[] {
        source.source(), source.version(), source.linkType()});
    snapshots = table(rows.toArray(Object[][]::new), catalog.available()
        ? new String[] {"Source", "Version", "Product", "Build", "Locale", "Captured (UTC)", "App input roles", "Snapshot ID"}
        : new String[] {"Source", "Recorded version/build", "Link type"});
    files = table(new Object[0][], catalog.available() ? new String[] {"File", "Source URL", "SHA-256"}
        : new String[] {"Reference", "Source URL", "Archive status"});
    snapshots.getSelectionModel().addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) return;
      int row = snapshots.getSelectedRow();
      if (row < 0) selectedFiles = List.of();
      else if (catalog.available()) selectedFiles = catalog.snapshots().get(snapshots.convertRowIndexToModel(row)).files();
      else {
        var source = catalog.legacySources().get(snapshots.convertRowIndexToModel(row));
        selectedFiles = List.of(new SourceFile(source.linkType(), source.url(), "Original inputs not archived"));
      }
      var model = (DefaultTableModel) files.getModel();
      model.setRowCount(0);
      for (var f : selectedFiles) model.addRow(new Object[] {f.path(), f.url(), f.sha256()});
      open.setEnabled(false);
      selectedSnapshot = row < 0 || !catalog.available() ? null
          : catalog.snapshots().get(snapshots.convertRowIndexToModel(row));
      updateComparison();
    });
    files.getSelectionModel().addListSelectionListener(e -> open.setEnabled(selectedUri() != null));
    open.setEnabled(false);
    open.addActionListener(e -> {
      URI uri = selectedUri();
      if (uri != null) {
        try { opener.accept(uri); }
        catch (RuntimeException failure) { open.setToolTipText("Could not open browser: " + failure.getMessage()); }
      }
    });
    var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(snapshots), new JScrollPane(files));
    split.setResizeWeight(0.5);
    add(split, BorderLayout.CENTER);
    var footer = new JPanel(new BorderLayout());
    var comparison = new JPanel(new BorderLayout(4, 4));
    var buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING, 4, 0));
    buttons.add(useBaseline);
    buttons.add(compare);
    comparison.add(buttons, BorderLayout.NORTH);
    comparison.add(baselineLabel, BorderLayout.CENTER);
    footer.add(comparison, BorderLayout.CENTER);
    useBaseline.addActionListener(e -> {
      baseline = selectedSnapshot;
      if (baseline != null) baselineLabel.setText("Baseline: " + baseline.source() + " · " + baseline.capturedAt()
          + " · " + baseline.id().substring(0, Math.min(12, baseline.id().length())));
      updateComparison();
    });
    compare.addActionListener(e -> {
      if (baseline != null && selectedSnapshot != null && baseline.source().equals(selectedSnapshot.source())) {
        var result = comparisonPanel(baseline, selectedSnapshot);
        result.setPreferredSize(new Dimension(Math.min(800, Math.max(320, getWidth())), 380));
        presenter.accept(result);
      }
    });
    updateComparison();
    if (catalog.projection() != null) {
      var projection = catalog.projection();
      footer.add(new WrappingText("App projection: " + projection.projectedAt() + " · rules "
          + projection.transformVersion() + " · ingestion " + projection.ingestionId(), "App projection provenance"), BorderLayout.NORTH);
    }
    footer.add(open, BorderLayout.SOUTH);
    add(footer, BorderLayout.SOUTH);
    setPreferredSize(new Dimension(900, 450));
  }

  private void updateComparison() {
    useBaseline.setEnabled(selectedSnapshot != null);
    compare.setEnabled(baseline != null && selectedSnapshot != null
        && baseline.source().equals(selectedSnapshot.source()) && !baseline.id().equals(selectedSnapshot.id()));
    compare.setToolTipText("Choose a different snapshot from the baseline's source");
  }

  private static JComponent comparisonPanel(Snapshot baseline, Snapshot current) {
    var changes = SourceArchiveComparison.compare(baseline, current);
    var panel = new JPanel(new BorderLayout(8, 8));
    panel.add(new WrappingText("Baseline: " + baseline.id() + " (" + baseline.capturedAt() + ")\n"
        + "Selected: " + current.id() + " (" + current.capturedAt() + ")\n"
        + changes.size() + " changed files. This compares archived selections; an absent file does not prove upstream removal.",
        "Compared snapshots"), BorderLayout.NORTH);
    var rows = new ArrayList<Object[]>();
    for (var change : changes) rows.add(new Object[] {change.path(), change.description(),
        change.before() == null ? "" : change.before().sha256(), change.after() == null ? "" : change.after().sha256(),
        change.before() == null ? "" : change.before().url(), change.after() == null ? "" : change.after().url()});
    panel.add(new JScrollPane(table(rows.toArray(Object[][]::new), new String[] {"File", "Change", "Baseline SHA-256",
        "Selected SHA-256", "Baseline URL", "Selected URL"})), BorderLayout.CENTER);
    panel.setPreferredSize(new Dimension(800, 380));
    return panel;
  }

  private URI selectedUri() {
    int row = files.getSelectedRow();
    if (row < 0) return null;
    int modelRow = files.convertRowIndexToModel(row);
    if (modelRow < 0 || modelRow >= selectedFiles.size()) return null;
    String url = selectedFiles.get(modelRow).url();
    if (url == null) return null;
    try {
      URI uri = URI.create(url);
      return uri.getHost() != null && ("https".equalsIgnoreCase(uri.getScheme())
          || "http".equalsIgnoreCase(uri.getScheme())) ? uri : null;
    } catch (IllegalArgumentException failure) { return null; }
  }

  private static JTable table(Object[][] rows, String[] columns) {
    JTable table = new JTable(new DefaultTableModel(rows, columns) {
      @Override public boolean isCellEditable(int row, int column) { return false; }
    }) {
      @Override public javax.swing.JToolTip createToolTip() {
        var tip = super.createToolTip();
        tip.putClientProperty("html.disable", Boolean.TRUE);
        return tip;
      }
      @Override public String getToolTipText(java.awt.event.MouseEvent event) {
        int row = rowAtPoint(event.getPoint());
        int column = columnAtPoint(event.getPoint());
        if (row < 0 || column < 0) return null;
        Object value = getValueAt(row, column);
        return value == null ? null : value.toString();
      }
    };
    var renderer = new javax.swing.table.DefaultTableCellRenderer();
    renderer.putClientProperty("html.disable", Boolean.TRUE);
    table.setDefaultRenderer(Object.class, renderer);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    for (int i = 0; i < columns.length; i++) {
      String name = columns[i];
      int width = name.endsWith("URL") || name.endsWith("SHA-256") || name.equals("Snapshot ID") ? 420
          : name.equals("File") || name.equals("Change") || name.equals("Recorded version/build") || name.equals("Link type")
              || name.equals("Reference") || name.startsWith("Captured") ? 220 : 150;
      table.getColumnModel().getColumn(i).setPreferredWidth(width);
    }
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setAutoCreateRowSorter(true);
    return table;
  }

  JTable snapshotsTable() { return snapshots; }
  JTable filesTable() { return files; }
  JButton openButton() { return open; }
  JButton useBaselineButton() { return useBaseline; }
  JButton compareButton() { return compare; }
}
