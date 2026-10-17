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

  @Test void oldDatabaseCatalogIsUsableWithoutAnyNetworkOrRecords() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var panel = new SourceCatalogPanel(new Catalog(false, List.of()), uri -> { throw new AssertionError(); });
      assertThat(panel.snapshotsTable().getRowCount()).isZero();
      assertThat(panel.openButton().isEnabled()).isFalse();
    });
  }
}
