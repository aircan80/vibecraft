package dev.wowforever.app.ui;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WayCommandTest {
  @Test void formatsCoordinatesWithOptionalZone() {
    assertThat(WayCommand.of("Westfall", 42.34, 67.45)).isEqualTo("/way Westfall 42.3 67.5");
    assertThat(WayCommand.of(null, 42.34, 67.45)).isEqualTo("/way 42.3 67.5");
  }
}
