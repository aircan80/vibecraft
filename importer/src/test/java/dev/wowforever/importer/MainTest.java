package dev.wowforever.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

  @Test
  void noArgumentsPrintsUsageAndReturnsOne() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int code = Main.run(new String[0], print(out), print(err));
    assertThat(code).isEqualTo(1);
    assertThat(err.toString(StandardCharsets.UTF_8)).contains("Usage:").contains("--out");
  }

  @Test
  void successfulImportReturnsZeroAndPrintsReport(@TempDir Path tmp) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int code = Main.run(new String[] {
        "--out", tmp.resolve("o.sqlite").toString(), "--fail-on-unsupported",
        "--questiedb-dir", Fixtures.questieDbRoot().toString(), "--atlasloot-dir", Fixtures.atlasLootRoot().toString()},
        print(out), print(err));
    assertThat(code).isEqualTo(0);
    assertThat(out.toString(StandardCharsets.UTF_8)).contains("== Counters ==").contains("table.item = ");
  }

  @Test
  void failureReturnsOneWithStackTrace(@TempDir Path tmp) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int code = Main.run(new String[] {
        "--out", tmp.resolve("o.sqlite").toString(),
        "--questiedb-dir", tmp.resolve("missing").toString(), "--atlasloot-dir", tmp.resolve("missing").toString()},
        print(out), print(err));
    assertThat(code).isEqualTo(1);
    assertThat(err.toString(StandardCharsets.UTF_8)).contains("\tat ");
  }

  @Test
  void exitCodeIsTwoOnlyWhenFailingOnUiSkips() {
    assertThat(Main.exitCode(true, 3)).isEqualTo(2);
    assertThat(Main.exitCode(false, 3)).isEqualTo(0);
    assertThat(Main.exitCode(true, 0)).isEqualTo(0);
  }

  private static PrintStream print(ByteArrayOutputStream bytes) {
    return new PrintStream(bytes, true, StandardCharsets.UTF_8);
  }
}
