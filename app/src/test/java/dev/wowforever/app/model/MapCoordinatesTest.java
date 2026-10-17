package dev.wowforever.app.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MapCoordinatesTest {
  @Test
  void theMapRunsFromZeroToAHundredInclusive() {
    assertThat(MapCoordinates.onMap(0, 0)).isTrue();
    assertThat(MapCoordinates.onMap(100, 100)).isTrue();
    assertThat(MapCoordinates.onMap(42.3, 67.4)).isTrue();
    assertThat(MapCoordinates.onMap(-1, -1)).as("instance placeholder").isFalse();
    assertThat(MapCoordinates.onMap(100.5, 50)).isFalse();
    assertThat(MapCoordinates.onMap(50, 100.01)).isFalse();
    assertThat(MapCoordinates.onMap(-0.01, 50)).isFalse();
  }

  @Test
  void theSqlConditionUsesTheSameBounds() {
    assertThat(MapCoordinates.sqlOnMap("s"))
        .isEqualTo(" AND s.x BETWEEN 0.0 AND 100.0 AND s.y BETWEEN 0.0 AND 100.0");
  }
}
