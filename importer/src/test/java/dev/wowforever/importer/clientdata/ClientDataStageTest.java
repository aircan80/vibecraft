package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.report.ImportReport;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClientDataStageTest {

  @Test
  void disputedItemsWithClientDataAreCountedAndNoted() {
    ImportReport report = new ImportReport();
    ClientDataStage.reportDisputed(ClientItems.load(19019, 25), report);
    assertThat(report.get(ClientDataStage.DISPUTED)).isEqualTo(1);
    assertThat(report.counters()).containsEntry(ClientDataStage.DISPUTED, 1L);
    assertThat(report.notes()).singleElement().asString()
        .startsWith("clientdata.disputed: 19019 (Thunderfury, Blessed Blade of the Windseeker: damage ")
        .contains("44 - 115");
  }

  @Test
  void disputedItemsWithoutClientDataAreNotListed() {
    ImportReport report = new ImportReport();
    ClientDataStage.reportDisputed(Map.of(), report);
    assertThat(report.counters()).containsEntry(ClientDataStage.DISPUTED, 0L);
    assertThat(report.notes()).isEmpty();
  }
}
