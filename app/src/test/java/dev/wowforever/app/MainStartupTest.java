package dev.wowforever.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import dev.wowforever.app.ui.UiExperience;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The GUI start-up's failure path: nothing may leave the window stuck on "Loading database…". */
class MainStartupTest {

  @Test
  void startupOpensJournalDirectlyAndRejectsRetiredInterface() {
    assertThat(Main.startupExperience(new String[0])).isEqualTo(UiExperience.JOURNAL);
    assertThatThrownBy(() -> Main.startupExperience(new String[] {"--ui=classic"}))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("retired");
    assertThat(Main.startupExperience(new String[] {"--ui=journal"})).isEqualTo(UiExperience.JOURNAL);
    assertThatThrownBy(() -> Main.startupExperience(new String[] {"--ui=other"}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Main.startupExperience(new String[] {"--ui=classic", "--ui=journal"}))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aWiringFailureGoesToTheFailureHandler() {
    List<Exception> failures = new ArrayList<>();
    IllegalStateException boom = new IllegalStateException("boom");
    Main.runOrFail(() -> {
      throw boom;
    }, failures::add);
    assertThat(failures).containsExactly(boom);
    Main.runOrFail(() -> { }, failures::add);
    assertThat(failures).hasSize(1);
  }

  @Test
  void aStartupFailureIsLoggedWithItsStackTrace() {
    PrintStream original = System.err;
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    System.setErr(new PrintStream(buf, true, StandardCharsets.UTF_8));
    try {
      Main.logStartupFailure(new IllegalStateException("database is locked"));
    } finally {
      System.setErr(original);
    }
    assertThat(buf.toString(StandardCharsets.UTF_8)).contains("Startup failed")
        .contains("IllegalStateException: database is locked").contains("\tat ");
  }
}
