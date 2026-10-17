package dev.wowforever.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wowforever.importer.clientdata.ClientDataOptions;
import dev.wowforever.importer.source.SourceFetcher;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ImportOptionsTest {

  @Test
  void parsesEveryFlag() {
    ImportOptions o = ImportOptions.parse(new String[] {
        "--out", "x/db.sqlite", "--cache", "c", "--questiedb-dir", "q", "--atlasloot-dir", "a",
        "--fail-on-unsupported", "--build-id-out", "b/id.txt", "--client-data-dir", "cd", "--coverage-min", "80"});
    assertThat(o).isEqualTo(new ImportOptions(Path.of("x/db.sqlite"), Path.of("c"), Optional.of(Path.of("q")),
        Optional.of(Path.of("a")), true, Path.of("b/id.txt"), ClientDataOptions.of(Path.of("cd")), 80));
    assertThat(o.serverDbDir()).isEmpty();
  }

  @Test
  void parsesTheServerDatabaseDirectory() {
    ImportOptions o = ImportOptions.parse(new String[] {
        "--out", "x/db.sqlite", "--skip-client-data", "--server-db-dir", "sdb"});
    assertThat(o.serverDbDir()).contains(Path.of("sdb"));
    assertThat(o).isEqualTo(new ImportOptions(Path.of("x/db.sqlite"), SourceFetcher.defaultCacheDir(),
        Optional.empty(), Optional.empty(), false, ImportOptions.defaultBuildIdOut(Path.of("x/db.sqlite")),
        ClientDataOptions.skipped(), 85, Optional.of(Path.of("sdb"))));
    assertThat(ImportOptions.USAGE).contains("--server-db-dir");
    assertThatThrownBy(() -> ImportOptions.parse(new String[] {"--out", "o", "--skip-client-data", "--server-db-dir"}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("--server-db-dir needs a value");
  }

  @Test
  void appliesDefaults() {
    ImportOptions o = ImportOptions.parse(new String[] {"--out", "x/db.sqlite", "--skip-client-data"});
    assertThat(o.clientData()).isEqualTo(ClientDataOptions.skipped());
    assertThat(o.cacheDir()).isEqualTo(SourceFetcher.defaultCacheDir());
    assertThat(o.questieDbDir()).isEmpty();
    assertThat(o.atlasLootDir()).isEmpty();
    assertThat(o.failOnUnsupported()).isFalse();
    assertThat(o.coverageMinPct()).isEqualTo(85);
    assertThat(o.serverDbDir()).isEmpty();
    assertThat(o.buildIdOut()).isEqualTo(Path.of("x/db.sqlite").toAbsolutePath().getParent().resolve("build-id.txt"));
  }

  @Test
  void constructorWithoutBuildIdOutDefaultsItBesideOut() {
    ImportOptions o = new ImportOptions(Path.of("d/out.sqlite"), Path.of("c"), Optional.empty(), Optional.empty(), false,
        ClientDataOptions.skipped());
    assertThat(o.buildIdOut()).isEqualTo(Path.of("d/out.sqlite").toAbsolutePath().getParent().resolve("build-id.txt"));
  }

  @Test
  void outIsRequired() {
    assertThatThrownBy(() -> ImportOptions.parse(new String[0]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("--out is required")
        .hasMessageContaining("Usage:");
  }

  @Test
  void clientDataDirOrSkipIsRequired() {
    assertThatThrownBy(() -> ImportOptions.parse(new String[] {"--out", "o.sqlite"}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("--client-data-dir")
        .hasMessageContaining("--skip-client-data")
        .hasMessageContaining("Usage:");
  }

  @Test
  void clientDataDirAndSkipAreMutuallyExclusive() {
    assertThatThrownBy(() -> ImportOptions.parse(
        new String[] {"--out", "o.sqlite", "--skip-client-data", "--client-data-dir", "cd"}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("--client-data-dir")
        .hasMessageContaining("--skip-client-data")
        .hasMessageContaining("mutually exclusive")
        .hasMessageContaining("Usage:");
  }

  @Test
  void coverageMinAcceptsZeroToHundred() {
    assertThat(ImportOptions.parse(new String[] {"--out", "o", "--skip-client-data", "--coverage-min", "0"})
        .coverageMinPct()).isZero();
    assertThat(ImportOptions.parse(new String[] {"--out", "o", "--skip-client-data", "--coverage-min", "100"})
        .coverageMinPct()).isEqualTo(100);
    for (String bad : new String[] {"101", "-1", "eighty", "80.5"}) {
      assertThatThrownBy(() -> ImportOptions.parse(new String[] {"--out", "o", "--skip-client-data", "--coverage-min", bad}))
          .as(bad)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("--coverage-min must be an integer from 0 to 100, not " + bad)
          .hasMessageContaining("Usage:");
    }
    assertThatThrownBy(() -> ImportOptions.parse(new String[] {"--out", "o", "--skip-client-data", "--coverage-min"}))
        .hasMessageContaining("--coverage-min needs a value");
    assertThatThrownBy(() -> new ImportOptions(Path.of("o"), Path.of("c"), Optional.empty(), Optional.empty(), false,
        Path.of("id"), ClientDataOptions.skipped(), 101))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("--coverage-min");
  }

  @Test
  void unknownFlagIsRejectedWithUsage() {
    assertThatThrownBy(() -> ImportOptions.parse(new String[] {"--out", "o", "--skip-client-data", "--bogus"}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("--bogus")
        .hasMessageContaining("Usage:");
  }

  @Test
  void flagWithoutValueIsRejected() {
    assertThatThrownBy(() -> ImportOptions.parse(new String[] {"--out"}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("--out needs a value");
  }
}
