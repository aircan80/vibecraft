package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The README's keyboard table is {@link KeyMap#markdownTable}, so the documented keys never drift from the bound
 * ones.
 */
class ReadmeKeyMapTest {
  private static final String START = "<!-- keymap:start -->";
  private static final String END = "<!-- keymap:end -->";

  /** The repository's README: the tests run in the app module, one level down. */
  private static String readme() throws IOException {
    Path here = Path.of("").toAbsolutePath();
    Path readme = Files.exists(here.resolve("README.md")) && Files.exists(here.resolve("app"))
        ? here.resolve("README.md") : here.resolveSibling("README.md");
    return Files.readString(readme, StandardCharsets.UTF_8).replace("\r\n", "\n");
  }

  @Test
  void theReadmeKeyboardTableIsTheKeyMap() throws IOException {
    String readme = readme();
    int start = readme.indexOf(START);
    int end = readme.indexOf(END);
    assertThat(start).as("the README marks its keyboard table").isNotNegative();
    assertThat(end).isGreaterThan(start);
    String table = readme.substring(start + START.length(), end).strip() + "\n";
    assertThat(table).isEqualTo(KeyMap.markdownTable());
  }

  @Test
  void theTableHasARowPerBinding() {
    assertThat(KeyMap.markdownTable().lines()).hasSize(2 + KeyMap.BINDINGS.size())
        .first().isEqualTo("| Keys | Action |");
    assertThat(KeyMap.markdownTable()).contains("| F10, Ctrl+Shift+T | Compact mode, always on top |");
  }
}
