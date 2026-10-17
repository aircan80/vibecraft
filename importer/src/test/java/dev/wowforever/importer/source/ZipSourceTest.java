package dev.wowforever.importer.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The AtlasLoot Classic Forever pin (Task 19): a CurseForge zip downloaded by hand into the cache, pinned by version
 * and SHA-256, with the Hoizame pin kept as the baseline the fork is compared with. Swapping AtlasLoot editions is a
 * {@code sources.properties} change only.
 */
class ZipSourceTest {
  private static final List<String> PATHS = List.of("AtlasLootClassic_DungeonsAndRaids/data.lua",
      "AtlasLootClassic_DungeonsAndRaids/droprate.lua", "AtlasLootClassic_DungeonsAndRaids/droprate_override.lua",
      "LICENSE");

  @TempDir Path tmp;

  private static byte[] zip(Map<String, String> entries) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream z = new ZipOutputStream(bytes)) {
      for (Map.Entry<String, String> e : entries.entrySet()) {
        z.putNextEntry(new ZipEntry(e.getKey()));
        z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
      }
    }
    return bytes.toByteArray();
  }

  private static String sha256(byte[] data) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
  }

  private static List<SourceSpec> specs(String sha) throws IOException {
    Properties p = new Properties();
    p.load(new StringReader("""
        questiedb.owner=Questie
        questiedb.repo=QuestieDB
        questiedb.commit=49e7dbca1f18eb3b8e57ee5d9562ac3e5c9adcc4
        questiedb.paths=a.lua
        atlasloot.kind=zip
        atlasloot.name=AtlasLoot Classic Forever
        atlasloot.author=sliccer
        atlasloot.license=GPL-2.0
        atlasloot.version=v9.9.9
        atlasloot.page=https://www.curseforge.com/wow/addons/atlasloot-forever/files
        atlasloot.zipDir=atlasloot-forever-zip
        atlasloot.zip=AtlasLootClassicForever-v9.9.9.zip
        atlasloot.sha256=%s
        atlasloot.rateSource=atlasloot-forever
        atlasloot.paths=AtlasLootClassic_DungeonsAndRaids/data.lua,AtlasLootClassic_DungeonsAndRaids/droprate.lua,\\
          AtlasLootClassic_DungeonsAndRaids/droprate_override.lua,LICENSE
        atlasloot.baseline.owner=Hoizame
        atlasloot.baseline.repo=AtlasLootClassic
        atlasloot.baseline.commit=8e99341e4e779328460bf7684c0d5b22ce50ddf1
        atlasloot.baseline.paths=AtlasLootClassic_DungeonsAndRaids/data.lua
        """.formatted(sha)));
    return SourceSpec.load(p);
  }

  private Path placeZip(byte[] data) throws IOException {
    Path dir = tmp.resolve("atlasloot-forever-zip");
    Files.createDirectories(dir);
    return Files.write(dir.resolve("AtlasLootClassicForever-v9.9.9.zip"), data);
  }

  @Test
  void theZipPinIsReadFromTheProperties() throws Exception {
    SourceSpec fork = specs("ab".repeat(32)).get(1);
    assertThat(fork.key()).isEqualTo("atlasloot");
    assertThat(fork.zip()).isEqualTo(new SourceSpec.Zip("AtlasLoot Classic Forever", "sliccer", "GPL-2.0", "v9.9.9",
        "https://www.curseforge.com/wow/addons/atlasloot-forever/files", "atlasloot-forever-zip",
        "AtlasLootClassicForever-v9.9.9.zip", "ab".repeat(32)));
    assertThat(fork.paths()).isEqualTo(PATHS);
    assertThat(fork.commit()).as("the cache directory name").isEqualTo("ab".repeat(32));
    assertThat(fork.pin()).isEqualTo("v9.9.9 sha256:" + "ab".repeat(32));
    assertThat(fork.rateSource()).isEqualTo("atlasloot-forever");
    assertThat(fork.baseline().commit()).isEqualTo("8e99341e4e779328460bf7684c0d5b22ce50ddf1");
    assertThat(fork.baseline().key()).as("shares the Hoizame cache").isEqualTo("atlasloot");
    assertThat(fork.baseline().paths()).containsExactly("AtlasLootClassic_DungeonsAndRaids/data.lua");

    SourceSpec shipped = SourceSpec.load().get(1);
    assertThat(shipped.zip()).isNull();
    assertThat(shipped.rateSource()).as("default").isEqualTo("atlasloot");
    assertThat(shipped.pin()).isEqualTo(shipped.commit());
  }

  @Test
  void theZipIsCheckedAndOnlyThePinnedFilesAreExtracted() throws Exception {
    byte[] data = zip(Map.of("AtlasLootClassic_DungeonsAndRaids/data.lua", "data", "LICENSE", "GPL",
        "AtlasLootClassic_DungeonsAndRaids/droprate.lua", "rates",
        "AtlasLootClassic_DungeonsAndRaids/droprate_override.lua", "ov", "AtlasLootClassic/Core.lua", "code"));
    placeZip(data);
    SourceSpec fork = specs(sha256(data)).get(1);
    Path root = new SourceFetcher(tmp, HttpClient.newHttpClient()).resolve(fork, Optional.empty());
    assertThat(root).isEqualTo(tmp.resolve("atlasloot").resolve(sha256(data)));
    assertThat(root.resolve("AtlasLootClassic_DungeonsAndRaids/droprate.lua")).content().isEqualTo("rates");
    assertThat(root.resolve("LICENSE")).content().isEqualTo("GPL");
    assertThat(root.resolve("AtlasLootClassic/Core.lua")).as("not pinned, not extracted").doesNotExist();
  }

  @Test
  void aMissingZipNamesThePageAndTheExpectedFile() throws Exception {
    SourceSpec fork = specs("ab".repeat(32)).get(1);
    assertThatThrownBy(() -> new SourceFetcher(tmp, HttpClient.newHttpClient()).resolve(fork, Optional.empty()))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("download AtlasLoot Classic Forever v9.9.9 by hand")
        .hasMessageContaining("https://www.curseforge.com/wow/addons/atlasloot-forever/files")
        .hasMessageContaining(tmp.resolve("atlasloot-forever-zip").resolve("AtlasLootClassicForever-v9.9.9.zip")
            .toString());
  }

  @Test
  void aZipWithAnotherHashOrWithoutAPinnedFileIsRefused() throws Exception {
    placeZip(zip(Map.of("LICENSE", "GPL")));
    SourceFetcher fetcher = new SourceFetcher(tmp, HttpClient.newHttpClient());
    assertThatThrownBy(() -> fetcher.resolve(specs("ab".repeat(32)).get(1), Optional.empty()))
        .hasMessageContaining("does not match the pinned sha256 abababababab");

    byte[] partial = zip(Map.of("LICENSE", "GPL"));
    placeZip(partial);
    assertThatThrownBy(() -> fetcher.resolve(specs(sha256(partial)).get(1), Optional.empty()))
        .hasMessageContaining("has no entry AtlasLootClassic_DungeonsAndRaids/data.lua");
  }

  @Test
  void entriesOutsideTheRootAreRefused() throws Exception {
    Properties p = new Properties();
    byte[] data = zip(Map.of("../evil.lua", "x"));
    placeZip(data);
    p.load(new StringReader("""
        questiedb.owner=Questie
        questiedb.repo=QuestieDB
        questiedb.commit=c
        questiedb.paths=a.lua
        atlasloot.kind=zip
        atlasloot.name=AtlasLoot Classic Forever
        atlasloot.version=v9.9.9
        atlasloot.page=p
        atlasloot.zipDir=atlasloot-forever-zip
        atlasloot.zip=AtlasLootClassicForever-v9.9.9.zip
        atlasloot.sha256=%s
        atlasloot.paths=../evil.lua
        """.formatted(sha256(data))));
    assertThatThrownBy(() -> new SourceFetcher(tmp, HttpClient.newHttpClient())
        .resolve(SourceSpec.load(p).get(1), Optional.empty()))
        .hasMessageContaining("outside");
    assertThat(tmp.resolve("atlasloot/evil.lua")).doesNotExist();
  }
}
