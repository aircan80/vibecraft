package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.ImportOptions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientDataSourceTest {

  @TempDir Path tmp;

  @Test
  void readsLockTablesIconsAndMaps() {
    ClientDataSource src = new ClientDataSource(Fixtures.clientDataRoot());

    BuildLock lock = src.lock();
    assertThat(lock.product()).isEqualTo("wow_classic_beta");
    assertThat(lock.build()).isEqualTo("1.60.1.70425");
    assertThat(lock.baseUrl()).isEqualTo("https://wago.tools");
    assertThat(lock.fetchedAt()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
    assertThat(lock.tableSha()).hasSize(43).containsKeys("ItemSparse", "ItemSparse.era");
    assertThat(lock.fallbackProduct()).isEqualTo("wow_classic_era");
    assertThat(lock.fallbackBuild()).isEqualTo("1.15.9.70003");
    assertThat(lock.tableSha().get("ItemSparse")).matches("[0-9a-f]{64}");

    CsvTable items = src.table("Item");
    assertThat(src.table("Item")).isSameAs(items);
    assertThat(items.indexByLong("ID")).containsKey(17204L);
    assertThat(src.hasTable("Item")).isTrue();
    assertThat(src.hasTable("NoSuchTable")).isFalse();
    assertThatThrownBy(() -> src.table("NoSuchTable"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("NoSuchTable");

    assertThat(src.icon(134124)).hasValueSatisfying(p -> assertThat(p).isRegularFile().hasFileName("134124.png"));
    assertThat(src.icon(0)).isPresent();
    assertThat(src.icon(999_999_999)).isEmpty();
    assertThat(src.map(1436)).hasValueSatisfying(p -> assertThat(p).isRegularFile().hasFileName("1436.jpg"));
    assertThat(src.map(1)).isEmpty();

    assertThat(src.maps()).hasSize(2);
    assertThat(src.maps()).extracting(ClientDataSource.MapInfo::uiMapId).containsExactly(1429L, 1436L);
    ClientDataSource.MapInfo westfall = src.maps().get(1);
    assertThat(westfall).isEqualTo(new ClientDataSource.MapInfo(1436, "Westfall", 1002, 668, 12, 14, 0));
  }

  @Test
  void mapsJsonIsOptionalAndLockIsRequired() {
    ClientDataSource empty = new ClientDataSource(tmp);
    assertThat(empty.maps()).isEmpty();
    assertThatThrownBy(empty::lock)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("build.lock");
  }

  @Test
  void buildLockParsesTheFetchToolShape() throws IOException {
    Path p = tmp.resolve("build.lock");
    Files.writeString(p, """
        {
          "product": "wow_classic_beta",
          "build": "1.60.1.70425",
          "fetched_at": "2026-10-16T20:21:06Z",
          "base_url": "https://wago.tools",
          "tables": {
            "Item": "aa",
            "Spell\\u00e9": "bb"
          },
          "file_count": 92,
          "missing_count": 0
        }
        """, StandardCharsets.UTF_8);

    assertThat(BuildLock.read(p)).isEqualTo(new BuildLock("wow_classic_beta", "1.60.1.70425",
        "2026-10-16T20:21:06Z", "https://wago.tools", java.util.Map.of("Item", "aa", "Spellé", "bb")));

    Files.writeString(p, "{\"product\": \"x\"}");
    assertThatThrownBy(() -> BuildLock.read(p))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("build.lock")
        .hasMessageContaining("build");
  }

  @Test
  void optionsRequireDirOrSkip() {
    assertThatThrownBy(() -> new ClientDataOptions(Optional.empty(), false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("--client-data-dir")
        .hasMessageContaining("--skip-client-data");
    assertThatThrownBy(() -> new ClientDataOptions(Optional.of(Path.of("d")), true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("--client-data-dir")
        .hasMessageContaining("--skip-client-data");

    ImportOptions withDir = ImportOptions.parse(new String[] {"--out", "o.sqlite", "--client-data-dir", "cd"});
    assertThat(withDir.clientData()).isEqualTo(new ClientDataOptions(Optional.of(Path.of("cd")), false));

    ImportOptions skipped = ImportOptions.parse(new String[] {"--out", "o.sqlite", "--skip-client-data"});
    assertThat(skipped.clientData()).isEqualTo(new ClientDataOptions(Optional.empty(), true));

    assertThatThrownBy(() -> ImportOptions.parse(
        new String[] {"--out", "o.sqlite", "--client-data-dir", "cd", "--skip-client-data"}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("--skip-client-data")
        .hasMessageContaining("Usage:");
    assertThatThrownBy(() -> ImportOptions.parse(new String[] {"--out", "o.sqlite", "--client-data-dir"}))
        .hasMessageContaining("--client-data-dir needs a value");
    assertThatThrownBy(() -> ImportOptions.parse(new String[] {"--out", "o.sqlite"}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("--client-data-dir")
        .hasMessageContaining("--skip-client-data");
  }

  @Test
  void optionalTableIsEmptyWhenTheFileIsAbsent() {
    ClientDataSource src = new ClientDataSource(Fixtures.clientDataRoot());
    assertThat(src.optionalTable("ItemSparse.era")).hasValueSatisfying(t -> {
      assertThat(t.indexByLong("ID")).containsKey(16800L);
      assertThat(t).isSameAs(src.table("ItemSparse.era"));
    });
    assertThat(src.optionalTable("NoSuchTable")).isEmpty();
  }

  @Test
  void buildLockFallbackFieldsAreOptional() throws IOException {
    Path p = tmp.resolve("build.lock");
    Files.writeString(p, """
        {"product": "p", "build": "b", "fetched_at": "f", "base_url": "u", "tables": {"ItemSparse": "aa"}}
        """, StandardCharsets.UTF_8);
    assertThat(BuildLock.read(p).fallbackProduct()).isNull();
    assertThat(BuildLock.read(p).fallbackBuild()).isNull();
    assertThat(new ClientDataSource(tmp).lock().fallbackProduct()).as("no Era file: no fallback needed").isNull();

    Files.writeString(p, """
        {"product": "wow_classic_beta", "build": "1.60.1.70425", "fetched_at": "2026-10-16T20:21:06Z",
         "base_url": "https://wago.tools", "tables": {"ItemSparse.era": "cc"},
         "fallback_product": "wow_classic_era", "fallback_build": "1.15.9.70003"}
        """, StandardCharsets.UTF_8);
    BuildLock lock = BuildLock.read(p);
    assertThat(lock.fallbackProduct()).isEqualTo("wow_classic_era");
    assertThat(lock.fallbackBuild()).isEqualTo("1.15.9.70003");
    assertThat(lock.tableSha()).containsEntry("ItemSparse.era", "cc");

    Files.writeString(p, """
        {"product": "p", "build": "b", "fetched_at": "f", "base_url": "u", "tables": {}, "fallback_build": 7}
        """, StandardCharsets.UTF_8);
    assertThatThrownBy(() -> BuildLock.read(p))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fallback_build");
  }

  @Test
  void staleEraFileWithoutLockEntryFails() throws IOException {
    Files.createDirectories(tmp.resolve("csv"));
    Files.writeString(tmp.resolve("csv/ItemSparse.era.csv"), "ID\n1\n", StandardCharsets.UTF_8);
    String base = "\"product\": \"wow_classic_beta\", \"build\": \"1.60.1.70425\", \"fetched_at\": \"f\","
        + " \"base_url\": \"u\"";
    Path lock = tmp.resolve("build.lock");

    Files.writeString(lock, "{" + base + ", \"tables\": {\"ItemSparse\": \"aa\"}}", StandardCharsets.UTF_8);
    assertThatThrownBy(() -> new ClientDataSource(tmp).lock())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ItemSparse.era.csv present but build.lock has no fallback entry")
        .hasMessageContaining("fetch.py --fallback-product");

    // the fallback product and build without the table's sha is just as stale
    Files.writeString(lock, "{" + base + ", \"tables\": {\"ItemSparse\": \"aa\"},"
        + " \"fallback_product\": \"wow_classic_era\", \"fallback_build\": \"1.15.9.70003\"}",
        StandardCharsets.UTF_8);
    assertThatThrownBy(() -> new ClientDataSource(tmp).lock())
        .hasMessageContaining("ItemSparse.era.csv present but build.lock has no fallback entry");

    // a table sha without the product and build too
    Files.writeString(lock, "{" + base + ", \"tables\": {\"ItemSparse\": \"aa\", \"ItemSparse.era\": \"cc\"}}",
        StandardCharsets.UTF_8);
    assertThatThrownBy(() -> new ClientDataSource(tmp).lock())
        .hasMessageContaining("ItemSparse.era.csv present but build.lock has no fallback entry");

    Files.writeString(lock, "{" + base + ", \"tables\": {\"ItemSparse\": \"aa\", \"ItemSparse.era\": \"cc\"},"
        + " \"fallback_product\": \"wow_classic_era\", \"fallback_build\": \"1.15.9.70003\"}",
        StandardCharsets.UTF_8);
    assertThat(new ClientDataSource(tmp).lock().fallbackBuild()).isEqualTo("1.15.9.70003");
  }
}
