package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.Test;

class ExternalDatabaseSetupTest {
  @Test void firstSelectionPersistsOnlyAfterValidation() throws Exception {
    Preferences prefs = preferences();
    try {
      AtomicInteger prompts = new AtomicInteger();
      Path result = ExternalDatabaseSetup.resolve(prefs, error -> {
        int n = prompts.incrementAndGet();
        if(n == 1) { assertThat(error).isNull(); return Path.of("bad.sqlite"); }
        assertThat(error).contains("Not compatible");
        assertThat(prefs.get(ExternalDatabaseSetup.PATH_KEY, "")).isEmpty();
        return Path.of("good.sqlite");
      }, file -> { if(file.toString().startsWith("bad")) throw new IOException("Not compatible"); return file.toAbsolutePath(); });
      assertThat(prompts.get()).isEqualTo(2);
      assertThat(prefs.get(ExternalDatabaseSetup.PATH_KEY, "")).isEqualTo(result.toString());
    } finally { prefs.removeNode(); }
  }
  @Test void restartReusesValidatedSelectionWithoutPrompt() throws Exception {
    Preferences prefs = preferences();
    try {
      prefs.put(ExternalDatabaseSetup.PATH_KEY, "/synthetic/catalog.sqlite");
      Path result = ExternalDatabaseSetup.resolve(prefs, error -> { throw new AssertionError("Unexpected chooser"); }, Path::toAbsolutePath);
      assertThat(result).isEqualTo(Path.of("/synthetic/catalog.sqlite"));
    } finally { prefs.removeNode(); }
  }
  @Test void movedDatabaseCanBeReplacedAndCancelKeepsPreviousPreference() throws Exception {
    Preferences prefs = preferences();
    try {
      prefs.put(ExternalDatabaseSetup.PATH_KEY, "/missing/catalog.sqlite");
      assertThatThrownBy(() -> ExternalDatabaseSetup.resolve(prefs, error -> {
        assertThat(error).contains("previous database"); return null;
      }, file -> { throw new IOException("Missing"); })).isInstanceOf(CancellationException.class);
      assertThat(prefs.get(ExternalDatabaseSetup.PATH_KEY, "")).isEqualTo("/missing/catalog.sqlite");
    } finally { prefs.removeNode(); }
  }
  private static Preferences preferences() { return Preferences.userRoot().node("wowforever-test/setup-" + UUID.randomUUID()); }
}
