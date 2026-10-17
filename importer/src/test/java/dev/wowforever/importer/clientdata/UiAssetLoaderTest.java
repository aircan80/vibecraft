package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.report.ImportReport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UiAssetLoaderTest {

  @Test
  void bothVariantsOfEachUiIconAreLoadedAsStored() throws Exception {
    ClientDataSource source = new ClientDataSource(Fixtures.clientDataRoot());
    ImportReport report = new ImportReport();
    Map<String, byte[]> assets = UiAssetLoader.load(source, report);

    assertThat(assets.keySet()).containsExactly("rail.quests", "rail.quests@24");
    Path dir = Fixtures.clientDataRoot().resolve("ui_icons");
    assertThat(assets.get("rail.quests")).isEqualTo(Files.readAllBytes(dir.resolve("rail.quests.png")));
    assertThat(assets.get("rail.quests@24")).isEqualTo(Files.readAllBytes(dir.resolve("rail.quests@24.png")));
    BufferedImage small = ImageIO.read(new ByteArrayInputStream(assets.get("rail.quests@24")));
    assertThat(small.getWidth()).isEqualTo(24);
    assertThat(small.getHeight()).isEqualTo(24);
    assertThat(report.get(UiAssetLoader.COUNTER)).isEqualTo(1);
    assertThat(report.notes()).anyMatch(n -> n.startsWith("clientdata.uiAssets.missing: ")
        && n.contains("rail.home") && n.contains("rail.instances") && !n.contains("rail.quests"));
  }

  @Test
  void everyRailIconPresentCountsSixAndNotesNothing(@TempDir Path tmp) throws Exception {
    Path dir = Files.createDirectories(tmp.resolve("ui_icons"));
    for (String name : UiAssetLoader.RAIL_ICONS) {
      Files.write(dir.resolve(name + ".png"), new byte[] {1});
      Files.write(dir.resolve(name + "@24.png"), new byte[] {2});
    }
    ImportReport report = new ImportReport();
    Map<String, byte[]> assets = UiAssetLoader.load(new ClientDataSource(tmp), report);

    assertThat(assets).hasSize(12);
    assertThat(report.get(UiAssetLoader.COUNTER)).isEqualTo(6);
    assertThat(report.notes()).isEmpty();
  }

  @Test
  void aBaseWithoutItsSmallVariantIsNoted(@TempDir Path tmp) throws Exception {
    Path dir = Files.createDirectories(tmp.resolve("ui_icons"));
    Files.write(dir.resolve("rail.home.png"), new byte[] {1});
    ImportReport report = new ImportReport();
    Map<String, byte[]> assets = UiAssetLoader.load(new ClientDataSource(tmp), report);

    assertThat(assets.keySet()).containsExactly("rail.home");
    assertThat(report.get(UiAssetLoader.COUNTER)).isEqualTo(1);
    assertThat(report.notes()).anyMatch(n -> n.contains("rail.home@24"));
  }

  @Test
  void noUiIconsDirectoryLoadsNothing(@TempDir Path tmp) {
    ImportReport report = new ImportReport();
    assertThat(UiAssetLoader.load(new ClientDataSource(tmp), report)).isEmpty();
    assertThat(report.counters()).containsEntry(UiAssetLoader.COUNTER, 0L);
  }
}
