package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.report.ImportReport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IconLoaderTest {

  private static ClientItem item(long id, Long iconId) {
    return new ClientItem(id, false, null, null, null, null, null, null, null, null, null, null, iconId, null,
        null, null, null, null, null, null, List.of(), null, List.of(), null, null, null, null, null, null);
  }

  @Test
  void everyFixtureItemsIconIsLoadedAndFallbackIncluded() {
    ClientDataSource source = new ClientDataSource(Fixtures.clientDataRoot());
    ImportReport report = new ImportReport();
    Map<Long, ClientItem> items = ItemSparseLoader.load(source, java.util.Set.of(17204L, 25L, 16800L, 19019L), report);
    Map<Long, byte[]> icons = IconLoader.load(source, items.values(), report);

    assertThat(icons).containsKey(0L);
    for (ClientItem i : items.values()) {
      if (i.iconId() != null) {
        assertThat(icons).containsKey(i.iconId());
      }
    }
    assertThat(icons.get(134124L)).isEqualTo(source.icon(134124).map(p -> {
      try {
        return java.nio.file.Files.readAllBytes(p);
      } catch (java.io.IOException e) {
        throw new java.io.UncheckedIOException(e);
      }
    }).orElseThrow());
    assertThat(report.get("clientdata.icons")).isEqualTo(icons.size());
    assertThat(report.get("clientdata.icons.missing")).isZero();
  }

  @Test
  void missingIconsAreCountedAndNoted() {
    ClientDataSource source = new ClientDataSource(Fixtures.clientDataRoot());
    ImportReport report = new ImportReport();
    List<ClientItem> items = new ArrayList<>(List.of(item(1, 999_999L), item(2, 999_999L), item(3, null)));
    Map<Long, byte[]> icons = IconLoader.load(source, items, report);

    assertThat(icons.keySet()).containsExactly(0L);
    assertThat(report.get("clientdata.icons")).isEqualTo(1);
    assertThat(report.get("clientdata.icons.missing")).isEqualTo(1);
    assertThat(report.notes()).anyMatch(n -> n.contains("clientdata.icons.missing") && n.contains("999999"));
  }
}
