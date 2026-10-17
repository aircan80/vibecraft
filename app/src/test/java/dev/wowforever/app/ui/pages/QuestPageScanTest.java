package dev.wowforever.app.ui.pages;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.ui.pages.quest.QuestTestData;
import javax.swing.SwingUtilities;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuestPageScanTest {
  static final int SCAN_WIDTH = 1216;
  static final int SCAN_HEIGHT = 680;

  @ParameterizedTest
  @ValueSource(longs = {176, 166, 6502, 7787, 7791})
  void firstObjectiveAboveTheFold(long id) {
    var loaded = QuestTestData.load(id);
    var page = new QuestPage(loaded.data(), loaded.maps(), loaded.zoneNames(), t -> {}, text -> {});
    page.setSize(SCAN_WIDTH, SCAN_HEIGHT);
    for (int i = 0; i < 5; i++) Tables.layOut(page);
    if (!page.objectiveRows().rows().isEmpty()) {
      var row = page.objectiveRows().rows().getFirst();
      var where = row.whereLink().isVisible() ? row.whereLink() : row.whereLine();
      var bounds = Tables.boundsIn(where, page);
      assertThat(bounds.height).isPositive();
      assertThat(bounds.y + bounds.height).as("first objective location of quest%s", id).isLessThanOrEqualTo(SCAN_HEIGHT);
    } else {
      assertThat(page.objectives().getText()).isNotBlank();
      assertThat(Tables.boundsIn(page.objectivesPanel(), page).y).isLessThan(SCAN_HEIGHT);
    }
  }

  @ParameterizedTest
  @ValueSource(longs = {176, 166, 6502, 7787, 7791})
  void giverAndTurnInAboveTheFold(long id) {
    var loaded = QuestTestData.load(id);
    var page = new QuestPage(loaded.data(), loaded.maps(), target -> {}, text -> {});
    page.setSize(SCAN_WIDTH, SCAN_HEIGHT);
    for (int i = 0; i < 4; i++) Tables.layOut(page);
    assertThat(page.isSideBySide()).isTrue();
    for (var component : java.util.List.of(page.header(), page.stepper(), page.mapPanel())) {
      if (component.isVisible()) {
        var bounds = Tables.boundsIn(component, page);
        assertThat(bounds.y + bounds.height).as("quest %s: %s above fold", id, component.getClass().getSimpleName())
            .isLessThanOrEqualTo(SCAN_HEIGHT);
      }
    }
    var map = page.mapPanel().mapView();
    assertThat(map).isNotNull();
    for (var giver : java.util.stream.Stream.concat(loaded.data().starts().stream(), loaded.data().ends().stream()).toList()) {
      for (var spawn : giver.spawns()) {
        if (spawn.zoneId() == page.mapPanel().shownZone()) {
          var point = map.projection().toComponent(spawn.x(), spawn.y());
          var inPage = SwingUtilities.convertPoint(map, (int) point.x, (int) point.y, page);
          assertThat(inPage.y).isBetween(0, SCAN_HEIGHT - 1);
          assertThat(inPage.x).isBetween(0, SCAN_WIDTH - 1);
        } else {
          assertThat(page.mapPanel().banner().isVisible()).isTrue();
          var bannerBounds = Tables.boundsIn(page.mapPanel().banner(), page);
          assertThat(bannerBounds.y + bannerBounds.height).isLessThanOrEqualTo(SCAN_HEIGHT);
        }
      }
    }
  }
}
