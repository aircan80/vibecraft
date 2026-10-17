package dev.wowforever.app.ui;

import dev.wowforever.app.db.SourceArchiveRepository.Catalog;
import dev.wowforever.app.db.SourceArchiveRepository.SourceFile;
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

  public SourceCatalogPanel(Catalog catalog, Consumer<URI> opener) {
    super(new BorderLayout(8, 8));
    add(new WrappingText(catalog.available()
        ? "Archived datasets and history; archive presence does not establish which source produced an app value."
        : "This database has no source archive. Existing app data remains available.", "Source archive scope"), BorderLayout.NORTH);
    var rows = new ArrayList<Object[]>();
    for (var s : catalog.snapshots()) rows.add(new Object[] {s.source(), s.version(), s.product(), s.build(),
        s.locale(), s.capturedAt(), String.join(", ", catalog.activeRoles().getOrDefault(s.id(), List.of())), s.id()});
    snapshots = table(rows.toArray(Object[][]::new), new String[] {"Source", "Version", "Product", "Build",
        "Locale", "Captured (UTC)", "App input roles", "Snapshot ID"});
    files = table(new Object[0][], new String[] {"File", "Source URL", "SHA-256"});
    snapshots.getSelectionModel().addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) return;
      int row = snapshots.getSelectedRow();
      selectedFiles = row < 0 ? List.of() : catalog.snapshots().get(snapshots.convertRowIndexToModel(row)).files();
      var model = (DefaultTableModel) files.getModel();
      model.setRowCount(0);
      for (var f : selectedFiles) model.addRow(new Object[] {f.path(), f.url(), f.sha256()});
      open.setEnabled(false);
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
    if (catalog.projection() != null) {
      var projection = catalog.projection();
      footer.add(new WrappingText("App projection: " + projection.projectedAt() + " · rules "
          + projection.transformVersion() + " · ingestion " + projection.ingestionId(), "App projection provenance"), BorderLayout.NORTH);
    }
    footer.add(open, BorderLayout.SOUTH);
    add(footer, BorderLayout.SOUTH);
    setPreferredSize(new Dimension(900, 450));
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
    });
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    for (int i = 0; i < columns.length; i++) {
      String name = columns[i];
      int width = name.equals("Source URL") || name.equals("SHA-256") || name.equals("Snapshot ID") ? 420
          : name.equals("File") || name.startsWith("Captured") ? 220 : 150;
      table.getColumnModel().getColumn(i).setPreferredWidth(width);
    }
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setAutoCreateRowSorter(true);
    return table;
  }

  JTable snapshotsTable() { return snapshots; }
  JTable filesTable() { return files; }
  JButton openButton() { return open; }
}
