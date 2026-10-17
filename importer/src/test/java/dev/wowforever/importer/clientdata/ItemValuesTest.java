package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.report.ImportReport;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemValuesTest {
  @Test
  void computesEveryCategoryAndCountsIt() {
    Map<Long, ClientItem> items = ClientItems.load(19019, 12640, 19321, 4134, 17204, 16800);
    ImportReport report = new ImportReport();
    Map<Long, ItemValues> values = ItemValues.compute(items, ClientItems.fixtureTables(), report);

    assertThat(values.keySet()).as("items with at least one value, in id order")
        .containsExactly(4134L, 12640L, 16800L, 19019L, 19321L);
    assertThat(values.get(19019L).damage()).isNotNull();
    assertThat(values.get(19019L).armor()).isNull();
    assertThat(values.get(4134L).damage()).as("caster weapon: unverified").isNull();
    assertThat(values.get(4134L).stats()).isNotEmpty();

    assertThat(report.get("clientdata.stats.items")).isEqualTo(5);
    assertThat(report.get("clientdata.damage.items")).isEqualTo(1);
    assertThat(report.get("clientdata.armor.items")).isEqualTo(3); // 12640, 16800, 19321
    assertThat(report.get("clientdata.unverified.damage")).isEqualTo(1);
    assertThat(report.get("clientdata.unverified.block")).isEqualTo(1);
    assertThat(report.counters()).containsKeys("clientdata.unverified.stats", "clientdata.unverified.armor",
        "clientdata.stats.unknownId");
  }

  @Test
  void unknownStatIdsAreCountedAndNoted() {
    // 87 (Frost spell damage) is rendered as an Equip line, so only 92 is unknown
    ClientItem item = ClientItems.forever(4, 1, 5, 3, 65, null, 0.0, null, ClientItems.share(87, 5000),
        ClientItems.share(92, 2000), ClientItems.share(7, 3000));
    ImportReport report = new ImportReport();
    ItemValues.compute(Map.of(1L, item), ClientItems.fixtureTables(), report);
    assertThat(report.get("clientdata.stats.unknownId")).isEqualTo(1);
    assertThat(report.notes()).contains("clientdata.stats.unknownIds: 92");
  }
}
