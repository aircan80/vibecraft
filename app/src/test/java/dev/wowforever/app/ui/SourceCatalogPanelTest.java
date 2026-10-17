package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.db.SourceArchiveRepository.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SourceCatalogPanelTest {
  @Test void sourcesAreReadOnlyAndUrlsOpenOnlyAfterExplicitClick() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var opened = new ArrayList<URI>();
      var snapshot = new Snapshot("id", "Questie", "revision", "forever", "123", "enUS", "2026-10-17T00:00:00Z",
          List.of(new SourceFile("a.lua", "https://example.org/a", "hash"),
              new SourceFile("local", "file:///etc/passwd", "hash"),
              new SourceFile("bad", "not a URL", "hash")));
      var panel = new SourceCatalogPanel(new Catalog(true, List.of(snapshot)), opened::add);
      assertThat(opened).isEmpty();
      assertThat(panel.snapshotsTable().isCellEditable(0, 0)).isFalse();
      assertThat(panel.snapshotsTable().getAutoResizeMode()).isEqualTo(javax.swing.JTable.AUTO_RESIZE_OFF);
      assertThat(panel.filesTable().getAutoResizeMode()).isEqualTo(javax.swing.JTable.AUTO_RESIZE_OFF);
      assertThat(panel.snapshotsTable().createToolTip().getClientProperty("html.disable")).isEqualTo(true);
      assertThat(((javax.swing.JComponent) panel.snapshotsTable().prepareRenderer(
          panel.snapshotsTable().getCellRenderer(0, 0), 0, 0)).getClientProperty("html.disable")).isEqualTo(true);
      panel.snapshotsTable().setRowSelectionInterval(0, 0);
      panel.filesTable().setRowSelectionInterval(0, 0);
      assertThat(opened).isEmpty();
      assertThat(panel.openButton().isEnabled()).isTrue();
      panel.openButton().doClick();
      assertThat(opened).containsExactly(URI.create("https://example.org/a"));
      panel.filesTable().setRowSelectionInterval(1, 1);
      assertThat(panel.openButton().isEnabled()).isFalse();
      panel.filesTable().setRowSelectionInterval(2, 2);
      assertThat(panel.openButton().isEnabled()).isFalse();
    });
  }

  @Test void comparisonUsesExplicitBaselineAndRejectsOtherSources() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var baseline = new Snapshot("old", "questie", "v1", "", "", "", "2026-01-01", List.of());
      var selected = new Snapshot("new", "questie", "v2", "", "", "", "2026-02-01", List.of(new SourceFile("q.lua", null, "hash")));
      var other = new Snapshot("other", "atlas", "v3", "", "", "", "2026-03-01", List.of());
      var shown = new ArrayList<javax.swing.JComponent>();
      var panel = new SourceCatalogPanel(new Catalog(true, List.of(baseline, selected, other)), uri -> {}, shown::add);
      assertThat(panel.compareButton().isEnabled()).isFalse();
      panel.snapshotsTable().setRowSelectionInterval(0, 0);
      panel.useBaselineButton().doClick();
      assertThat(panel.compareButton().isEnabled()).isFalse();
      panel.snapshotsTable().setRowSelectionInterval(1, 1);
      assertThat(panel.compareButton().isEnabled()).isTrue();
      assertThat(shown).isEmpty();
      panel.compareButton().doClick();
      assertThat(shown).hasSize(1);
      panel.snapshotsTable().setRowSelectionInterval(2, 2);
      assertThat(panel.compareButton().isEnabled()).isFalse();
      var legacy = new SourceCatalogPanel(new Catalog(false, List.of()), uri -> {}, shown::add);
      assertThat(legacy.useBaselineButton().isEnabled()).isFalse();
      assertThat(legacy.compareButton().isEnabled()).isFalse();
    });
  }

  @Test void legacyRowsAreClearlySeparateFromArchivedSnapshotsAndOpenOnClick() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var opened = new ArrayList<URI>();
      var source = new LegacySource("Client fallback", "wow_classic_era · 1.15.9.70003",
          "Delivery/reference site; not archived source evidence", "https://wago.tools/db2");
      var panel = new SourceCatalogPanel(new Catalog(false, List.of(), java.util.Map.of(), null, List.of(source)), opened::add);
      assertThat(panel.snapshotsTable().getColumnName(1)).isEqualTo("Recorded version/build");
      panel.snapshotsTable().setRowSelectionInterval(0, 0);
      assertThat(panel.filesTable().getColumnName(0)).isEqualTo("Reference");
      assertThat(panel.filesTable().getValueAt(0, 2)).isEqualTo("Original inputs not archived");
      panel.filesTable().setRowSelectionInterval(0, 0);
      assertThat(opened).isEmpty();
      panel.openButton().doClick();
      assertThat(opened).containsExactly(URI.create("https://wago.tools/db2"));
    });
  }

  @Test void oldDatabaseCatalogIsUsableWithoutAnyNetworkOrRecords() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var panel = new SourceCatalogPanel(new Catalog(false, List.of()), uri -> { throw new AssertionError(); });
      assertThat(panel.snapshotsTable().getRowCount()).isZero();
      assertThat(panel.openButton().isEnabled()).isFalse();
    });
  }
}
