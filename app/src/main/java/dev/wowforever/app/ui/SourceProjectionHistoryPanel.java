package dev.wowforever.app.ui;

import dev.wowforever.app.db.SourceArchiveRepository.Catalog;
import dev.wowforever.app.db.SourceArchiveRepository.ProjectionInput;
import dev.wowforever.app.ui.widgets.WrappingText;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/** Completed dataset builds and their original input selection; no database work or replay actions. */
final class SourceProjectionHistoryPanel extends JPanel {
  private final JTable runs;
  private final JTable inputs;
  private final JButton showSource = new JButton("Show selected source snapshot");
  private List<ProjectionInput> selectedInputs = List.of();

  SourceProjectionHistoryPanel(Catalog catalog, Consumer<String> selectSnapshot) {
    super(new BorderLayout(8, 8));
    add(new WrappingText(catalog.historyNote() + " These are dataset builds, not field-level lineage.",
        "Build history status"), BorderLayout.NORTH);
    var rows = new ArrayList<Object[]>();
    for (var run : catalog.history()) rows.add(new Object[] {run.active() ? "Active" : "", run.id(),
        run.projectedAt(), run.ingestionId(), recorded(run.schemaVersion()), recorded(run.buildId()),
        recorded(run.validationSummary()), run.transformVersion()});
    runs = SourceCatalogPanel.table(rows.toArray(Object[][]::new), new String[] {"Active", "Execution ID",
        "Completed (UTC)", "Ingestion ID", "Schema", "Build ID", "Validation", "Transform fingerprint"});
    inputs = SourceCatalogPanel.table(new Object[0][], new String[] {"Input role", "Source", "Version", "Product",
        "Build", "Locale", "Captured (UTC)", "Snapshot ID"});
    runs.getSelectionModel().addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) return;
      int row = runs.getSelectedRow();
      selectedInputs = row < 0 ? List.of() : catalog.history().get(runs.convertRowIndexToModel(row)).inputs();
      var model = (DefaultTableModel) inputs.getModel();
      model.setRowCount(0);
      for (var input : selectedInputs) {
        var s = input.snapshot();
        model.addRow(new Object[] {input.role(), s == null ? "Snapshot metadata unavailable" : s.source(),
            s == null ? null : s.version(), s == null ? null : s.product(), s == null ? null : s.build(),
            s == null ? null : s.locale(), s == null ? null : s.capturedAt(), input.snapshotId()});
      }
      showSource.setEnabled(false);
    });
    inputs.getSelectionModel().addListSelectionListener(e -> showSource.setEnabled(selectedInput() != null));
    showSource.addActionListener(e -> {
      var input = selectedInput();
      if (input != null) selectSnapshot.accept(input.snapshotId());
    });
    showSource.setEnabled(false);
    var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(runs), new JScrollPane(inputs));
    split.setResizeWeight(0.5);
    add(split, BorderLayout.CENTER);
    add(showSource, BorderLayout.SOUTH);
  }

  private ProjectionInput selectedInput() {
    int row = inputs.getSelectedRow();
    if (row < 0) return null;
    int model = inputs.convertRowIndexToModel(row);
    if (model < 0 || model >= selectedInputs.size()) return null;
    var input = selectedInputs.get(model);
    return input.snapshot() == null ? null : input;
  }

  private static String recorded(String value) { return value == null || value.isBlank() ? "Not recorded" : value; }
  JTable runsTable() { return runs; }
  JTable inputsTable() { return inputs; }
  JButton showSourceButton() { return showSource; }
}
