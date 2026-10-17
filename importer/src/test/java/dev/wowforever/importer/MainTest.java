package dev.wowforever.importer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.clientdata.ClientDataStage;
import dev.wowforever.importer.report.ImportReport;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

  @Test
  void noArgumentsPrintsUsageAndReturnsOne() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int code = run(new String[0], print(out), print(err));
    assertThat(code).isEqualTo(1);
    assertThat(err.toString(StandardCharsets.UTF_8)).contains("Usage:").contains("--out");
  }

  @Test
  void successfulImportReturnsZeroAndPrintsReport(@TempDir Path tmp) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int code = run(new String[] {
        "--out", tmp.resolve("o.sqlite").toString(), "--fail-on-unsupported",
        "--questiedb-dir", Fixtures.questieDbRoot().toString(), "--atlasloot-dir", Fixtures.atlasLootRoot().toString(),
        "--client-data-dir", Fixtures.clientDataRoot().toString(), "--coverage-min", "75"},
        print(out), print(err));
    assertThat(code).as(err.toString(StandardCharsets.UTF_8)).isEqualTo(0);
    assertThat(out.toString(StandardCharsets.UTF_8)).contains("== Counters ==").contains("table.item = ")
        .contains("clientdata.coverage.pct = 75").contains("table.item_tooltip_line = ");
  }

  @Test
  void failureReturnsOneWithStackTrace(@TempDir Path tmp) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int code = run(new String[] {
        "--out", tmp.resolve("o.sqlite").toString(),
        "--questiedb-dir", tmp.resolve("missing").toString(), "--atlasloot-dir", tmp.resolve("missing").toString(),
        "--skip-client-data"},
        print(out), print(err));
    assertThat(code).isEqualTo(1);
    assertThat(err.toString(StandardCharsets.UTF_8)).contains("\tat ");
  }

  @Test
  void neitherClientDataFlagReturnsOneWithUsage(@TempDir Path tmp) {
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int code = run(new String[] {"--out", tmp.resolve("o.sqlite").toString()}, print(new ByteArrayOutputStream()),
        print(err));
    assertThat(code).isEqualTo(1);
    assertThat(err.toString(StandardCharsets.UTF_8)).contains("--client-data-dir").contains("--skip-client-data")
        .contains("Usage:");
  }

  /** The real path: the fixture's client data with every ItemSparse row removed matches no Questie item. */
  @Test
  void coverageGateExits2(@TempDir Path tmp) throws IOException {
    Path client = copyTree(Fixtures.clientDataRoot(), tmp.resolve("clientdata"));
    for (String table : List.of("ItemSparse", "ItemSparse.era")) {
      Path csv = client.resolve("csv/" + table + ".csv");
      Files.writeString(csv, Files.readAllLines(csv, StandardCharsets.UTF_8).get(0) + "\n", StandardCharsets.UTF_8);
    }
    String[] base = {"--out", tmp.resolve("o.sqlite").toString(),
        "--questiedb-dir", Fixtures.questieDbRoot().toString(), "--atlasloot-dir", Fixtures.atlasLootRoot().toString(),
        "--client-data-dir", client.toString()};

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int code = run(append(base, "--fail-on-unsupported"), print(out), print(err));
    assertThat(code).isEqualTo(2);
    assertThat(err.toString(StandardCharsets.UTF_8)).contains("named items (0 %), below --coverage-min 85 %")
        .contains("clientdata.unmatched.ranges").doesNotContain("missingField/unresolved");
    assertThat(out.toString(StandardCharsets.UTF_8)).contains("clientdata.coverage.pct = 0")
        .contains("clientdata.unmatched.ranges: ");

    assertThat(run(base, print(new ByteArrayOutputStream()), print(new ByteArrayOutputStream())))
        .as("without --fail-on-unsupported").isEqualTo(0);
  }

  /**
   * The fixture matches 12 of its 16 named items (75 %); three nameless correction stubs also match, which must not
   * count, so the default 85 % gate fails.
   */
  @Test
  void fixtureFailsTheDefaultCoverageGateOnNamedItems(@TempDir Path tmp) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int code = run(new String[] {
        "--out", tmp.resolve("o.sqlite").toString(), "--fail-on-unsupported",
        "--questiedb-dir", Fixtures.questieDbRoot().toString(), "--atlasloot-dir", Fixtures.atlasLootRoot().toString(),
        "--client-data-dir", Fixtures.clientDataRoot().toString()},
        print(out), print(err));
    assertThat(code).isEqualTo(2);
    assertThat(err.toString(StandardCharsets.UTF_8))
        .contains("client data covers 12 of 16 named items (75 %), below --coverage-min 85 %");
    assertThat(out.toString(StandardCharsets.UTF_8)).contains("clientdata.items.matched = 15")
        .contains("clientdata.items.named.matched = 12").contains("clientdata.coverage.pct = 75");
  }

  @Test
  void skippedClientDataNeverFailsTheCoverageGate(@TempDir Path tmp) {
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int code = run(new String[] {
        "--out", tmp.resolve("o.sqlite").toString(), "--fail-on-unsupported", "--skip-client-data",
        "--questiedb-dir", Fixtures.questieDbRoot().toString(), "--atlasloot-dir", Fixtures.atlasLootRoot().toString()},
        print(new ByteArrayOutputStream()), print(err));
    assertThat(code).as(err.toString(StandardCharsets.UTF_8)).isEqualTo(0);
  }

  @Test
  void coverageGateComparesNamedMatchedWithNamedItems() {
    ImportReport r = new ImportReport();
    assertThat(ClientDataStage.coverageTooLow(r, 85)).as("no client stage counters").isFalse();
    r.count(ClientDataStage.NAMED, 100);
    r.count(ClientDataStage.MATCHED, 100);
    assertThat(ClientDataStage.coverageTooLow(r, 85)).as("matches of nameless items do not count").isTrue();
    r.count(ClientDataStage.NAMED_MATCHED, 85);
    assertThat(ClientDataStage.coverageTooLow(r, 85)).as("exactly 85 %").isFalse();
    r.count(ClientDataStage.NAMED, 1);
    assertThat(ClientDataStage.coverageTooLow(r, 85)).as("85 of 101").isTrue();
    assertThat(ClientDataStage.coverageTooLow(r, 84)).as("85 of 101 against 84 %").isFalse();
    assertThat(ClientDataStage.coverageTooLow(r, 0)).as("0 % never fails").isFalse();
    assertThat(ClientDataStage.coverageTooLow(r, 100)).isTrue();
    r.count(ClientDataStage.SKIPPED, 1);
    assertThat(ClientDataStage.coverageTooLow(r, 100)).as("skipped").isFalse();
  }

  @Test
  void exitCodeIsTwoOnlyWhenFailingOnUiSkips() {
    assertThat(Main.exitCode(true, 3)).isEqualTo(2);
    assertThat(Main.exitCode(false, 3)).isEqualTo(0);
    assertThat(Main.exitCode(true, 0)).isEqualTo(0);
  }

  @Test
  void missingFieldsAndUnresolvedUiFieldsCountAsUnsupported() {
    ImportReport r = new ImportReport();
    r.count("unresolved.npc.minLevelHealth", 4); // not shown in the UI
    r.count("missingField.item.name", 0);
    assertThat(Main.unsupportedCounters(r)).isZero();
    r.count("missingField.item.someRenamedField", 1);
    assertThat(Main.unsupportedCounters(r)).isEqualTo(1);
    r.count("unresolved.npc.name", 2);
    r.count("unresolved.quest.objectivesText", 1);
    assertThat(Main.unsupportedCounters(r)).isEqualTo(4);
    r.count("unresolved.zone.name", 5); // not an entity kind: ignored rather than thrown on
    assertThat(Main.unsupportedCounters(r)).isEqualTo(4);
    assertThat(Main.exitCode(true, 0 + Main.unsupportedCounters(r))).isEqualTo(2);
  }

  private static int run(String[] args, PrintStream out, PrintStream err) {
    return Main.run(append(args, "--skip-att"), out, err);
  }

  private static String[] append(String[] args, String extra) {
    String[] out = java.util.Arrays.copyOf(args, args.length + 1);
    out[args.length] = extra;
    return out;
  }

  private static Path copyTree(Path from, Path to) throws IOException {
    try (Stream<Path> files = Files.walk(from)) {
      for (Path p : (Iterable<Path>) files::iterator) {
        Path target = to.resolve(from.relativize(p).toString());
        if (Files.isDirectory(p)) {
          Files.createDirectories(target);
        } else {
          Files.copy(p, target);
        }
      }
    }
    return to;
  }

  private static PrintStream print(ByteArrayOutputStream bytes) {
    return new PrintStream(bytes, true, StandardCharsets.UTF_8);
  }
}
