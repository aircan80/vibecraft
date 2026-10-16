package dev.wowforever.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wowforever.importer.source.SourceFetcher;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ImportOptionsTest {

  @Test
  void parsesEveryFlag() {
    ImportOptions o = ImportOptions.parse(new String[] {
        "--out", "x/db.sqlite", "--cache", "c", "--questiedb-dir", "q", "--atlasloot-dir", "a",
        "--fail-on-unsupported", "--build-id-out", "b/id.txt"});
    assertThat(o).isEqualTo(new ImportOptions(Path.of("x/db.sqlite"), Path.of("c"), Optional.of(Path.of("q")),
        Optional.of(Path.of("a")), true, Path.of("b/id.txt")));
  }

  @Test
  void appliesDefaults() {
    ImportOptions o = ImportOptions.parse(new String[] {"--out", "x/db.sqlite"});
    assertThat(o.cacheDir()).isEqualTo(SourceFetcher.defaultCacheDir());
    assertThat(o.questieDbDir()).isEmpty();
    assertThat(o.atlasLootDir()).isEmpty();
    assertThat(o.failOnUnsupported()).isFalse();
    assertThat(o.buildIdOut()).isEqualTo(Path.of("x/db.sqlite").toAbsolutePath().getParent().resolve("build-id.txt"));
  }

  @Test
  void fiveArgumentConstructorDefaultsBuildIdBesideOut() {
    ImportOptions o = new ImportOptions(Path.of("d/out.sqlite"), Path.of("c"), Optional.empty(), Optional.empty(), false);
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
  void unknownFlagIsRejectedWithUsage() {
    assertThatThrownBy(() -> ImportOptions.parse(new String[] {"--out", "o", "--bogus"}))
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
