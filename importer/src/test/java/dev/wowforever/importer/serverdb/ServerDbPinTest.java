package dev.wowforever.importer.serverdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The {@code import-data} profile's default {@code serverdb.dir} ({@code importer/pom.xml}) names the cache directory
 * of the asset {@code tools/serverdb/serverdb.lock} pins. The two must change together: with only the lock bumped,
 * the profile reads the old directory and the import skips the community rates.
 */
class ServerDbPinTest {
  @Test
  void thePomDefaultDirectoryIsTheLockedAssetsCacheDirectory() throws Exception {
    // surefire runs in the module directory
    String lock = Files.readString(Path.of("../tools/serverdb/serverdb.lock"), StandardCharsets.UTF_8);
    String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
    String asset = group(Pattern.compile("\"asset\"\\s*:\\s*\"db-sqlite-([0-9a-f]+)\\.zip\""), lock);
    String commit = group(Pattern.compile("\"commit\"\\s*:\\s*\"([0-9a-f]{40})\""), lock);
    String dir = group(Pattern.compile("<serverdb\\.dir>[^<]*/wowforever-serverdb/([0-9a-f]+)</serverdb\\.dir>"),
        pom);
    assertThat(commit).as("the locked commit").startsWith(asset);
    assertThat(dir).as("the pom's serverdb.dir default").isEqualTo(asset);
  }

  private static String group(Pattern p, String text) {
    Matcher m = p.matcher(text);
    assertThat(m.find()).as("%s", p).isTrue();
    return m.group(1);
  }
}
