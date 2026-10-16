package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.zones.Zone;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZoneMapLoaderTest {

  @TempDir Path tmp;

  private static Map<Long, Zone> zones() {
    Map<Long, Zone> z = new LinkedHashMap<>();
    z.put(40L, new Zone(40, "Westfall", null, "zone"));
    z.put(12L, new Zone(12, "Elwynn Forest", null, "zone"));
    z.put(20L, new Zone(20, "Moonbrook", 40L, "subzone"));
    z.put(9L, new Zone(9, "Northshire Valley", 12L, "subzone"));
    z.put(1L, new Zone(1, "Dun Morogh", null, "zone"));
    z.put(133L, new Zone(133, "Gnomeregan", 1L, "subzone"));
    z.put(38L, new Zone(38, "Loch Modan", null, "zone"));
    z.put(77L, new Zone(77, "Nowhere", null, "zone"));
    return z;
  }

  @Test
  void zonesResolveByOverrideAssignmentThenParent() {
    ClientDataSource source = new ClientDataSource(Fixtures.clientDataRoot());
    ImportReport report = new ImportReport();
    ZoneMaps maps = ZoneMapLoader.load(source, Fixtures.questieDbRoot(), zones(), report);

    assertThat(maps.zoneToMap()).containsEntry(40L, 1436L)   // override
        .containsEntry(12L, 1429L)                           // UiMapAssignment
        .containsEntry(20L, 1436L)                           // parent 40
        .containsEntry(9L, 1429L)                            // parent 12
        .doesNotContainKeys(1L, 133L, 38L, 77L);
    assertThat(maps.maps().keySet()).containsExactlyInAnyOrder(1429L, 1436L);
    assertThat(maps.maps().get(1436L).w()).isEqualTo(1002);
    assertThat(maps.maps().get(1436L).h()).isEqualTo(668);
    assertThat(report.get("clientdata.zones.mapped.override")).isEqualTo(1);
    assertThat(report.get("clientdata.zones.mapped.assignment")).isEqualTo(1);
    assertThat(report.get("clientdata.zones.mapped.parent")).isEqualTo(2);
    assertThat(report.get("clientdata.zones.unmapped")).isEqualTo(3);  // 1 (override 0), 133, 77
    assertThat(report.get("clientdata.zones.missingArt")).isEqualTo(1); // 38 -> 9999
    assertThat(report.get("clientdata.maps")).isEqualTo(2);
    assertThat(report.get("clientdata.maps.missingTiles")).isZero();
  }

  @Test
  void missingTilesAreCountedNotFatal() throws IOException {
    Path dir = tmp.resolve("client");
    Files.createDirectories(dir.resolve("maps"));
    Files.createDirectories(dir.resolve("csv"));
    Files.copy(Fixtures.clientDataRoot().resolve("maps/1436.jpg"), dir.resolve("maps/1436.jpg"));
    Files.writeString(dir.resolve("maps.json"), "[{\"ui_map_id\": 1436, \"name\": \"Westfall\", \"width\": 1002,"
        + " \"height\": 668, \"tiles\": 12, \"overlays\": 14, \"missing_tiles\": 3}]");
    ImportReport report = new ImportReport();
    ZoneMaps maps = ZoneMapLoader.load(new ClientDataSource(dir), Fixtures.questieDbRoot(), zones(), report);

    assertThat(maps.zoneToMap()).containsEntry(40L, 1436L).containsEntry(20L, 1436L);
    assertThat(report.get("clientdata.maps")).isEqualTo(1);
    assertThat(report.get("clientdata.maps.missingTiles")).isEqualTo(3);
  }

  @Test
  void parentInheritanceStopsAtFiveLevels() {
    Map<Long, Zone> z = new LinkedHashMap<>();
    z.put(40L, new Zone(40, "Westfall", null, "zone"));
    long prev = 40;
    for (long id = 101; id <= 106; id++) {
      z.put(id, new Zone(id, "Sub " + id, prev, "subzone"));
      prev = id;
    }
    ZoneMaps maps = ZoneMapLoader.load(new ClientDataSource(Fixtures.clientDataRoot()), Fixtures.questieDbRoot(), z,
        new ImportReport());
    assertThat(maps.zoneToMap()).containsKeys(40L, 101L, 105L).doesNotContainKey(106L);
  }
}
