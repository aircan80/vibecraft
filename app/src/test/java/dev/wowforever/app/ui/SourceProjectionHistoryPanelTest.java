package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.db.SourceArchiveRepository.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SourceProjectionHistoryPanelTest {
  @Test void pastRunInputsOpenTheirExactSnapshotForExistingFileComparisonWithoutChangingActiveBuild() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var old = new Snapshot("old", "questiedb", "v1", "forever", "70450", "enUS", "2026-01-01", List.of());
      var next = new Snapshot("next", "questiedb", "v2", "forever", "70451", "enUS", "2026-02-01", List.of());
      var active = new ProjectionRun("2", "new-inputs", "2026-04-01", "rules2", "4", "build2", "ok", true,
          List.of(new ProjectionInput("questiedb", "next", next)));
      var past = new ProjectionRun("1", "old-inputs", "2026-03-01", "rules1", null, null, null, false,
          List.of(new ProjectionInput("questiedb", "old", old)));
      var catalog = new Catalog(true, List.of(next, old), Map.of("next", List.of("questiedb")),
          new Projection("new-inputs", "2026-04-01", "rules2"), List.of(), List.of(active, past), "Exact active build recorded.");
      var panels = new ArrayList<javax.swing.JComponent>();
      var panel = new SourceCatalogPanel(catalog, uri -> { throw new AssertionError(); }, panels::add);
      panel.tabs().setSelectedIndex(1);
      var history = (SourceProjectionHistoryPanel) panel.tabs().getComponentAt(1);
      assertThat(history.runsTable().getValueAt(0, 0)).isEqualTo("Active");
      assertThat(history.runsTable().getValueAt(1, 0)).isEqualTo("");
      assertThat(history.runsTable().getValueAt(1, 4)).isEqualTo("Not recorded");
      history.runsTable().setRowSelectionInterval(1, 1);
      assertThat(history.inputsTable().getValueAt(0, 2)).isEqualTo("v1");
      history.inputsTable().setRowSelectionInterval(0, 0);
      history.showSourceButton().doClick();
      assertThat(panel.tabs().getSelectedIndex()).isZero();
      assertThat(panel.snapshotsTable().getSelectedRow()).isEqualTo(1);
      panel.useBaselineButton().doClick();
      panel.snapshotsTable().setRowSelectionInterval(0, 0);
      panel.compareButton().doClick();
      assertThat(panels).hasSize(1);
      assertThat(history.runsTable().getValueAt(0, 0)).isEqualTo("Active");
    });
  }

  @Test void absentHistoryAndMissingSnapshotMetadataCannotNavigate() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var empty = new SourceProjectionHistoryPanel(new Catalog(false, List.of()), id -> { throw new AssertionError(); });
      assertThat(empty.runsTable().getRowCount()).isZero();
      assertThat(empty.showSourceButton().isEnabled()).isFalse();
      var run = new ProjectionRun("1", "inputs", "2026-01-01", "rules", null, null, null, false,
          List.of(new ProjectionInput("questiedb", "missing", null)));
      var panel = new SourceProjectionHistoryPanel(new Catalog(true, List.of(), Map.of(), null, List.of(),
          List.of(run), "Snapshot metadata unavailable."), id -> { throw new AssertionError(); });
      panel.runsTable().setRowSelectionInterval(0, 0);
      panel.inputsTable().setRowSelectionInterval(0, 0);
      assertThat(panel.showSourceButton().isEnabled()).isFalse();
    });
  }
}
