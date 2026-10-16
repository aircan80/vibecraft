package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FixtureDb.class)
class DatabaseInstallerTest {
  private static final FileTime OLD = FileTime.from(Instant.parse("2020-01-01T00:00:00Z"));

  @Test
  void extractsWhenMissing(@TempDir Path dir) throws Exception {
    Path p = install(dir, "b1");
    assertThat(p).exists().isEqualTo(dir.resolve("wowforever.sqlite"));
    try (Database db = Database.open(p)) {
      assertThat(db.meta()).containsKey("build_id");
    }
    try (var files = Files.list(dir)) {
      assertThat(files).containsExactly(p); // no temp file left behind
    }
  }

  @Test
  void replacesWhenBuildIdDiffers(@TempDir Path dir) throws Exception {
    Files.write(dir.resolve("wowforever.sqlite"), new byte[] {1, 2, 3}); // garbage "old" db: unreadable build id counts as different
    Path p = install(dir, "b1");
    assertThat(Files.size(p)).isGreaterThan(1000);
  }

  @Test
  void replacesValidDatabaseWithOtherBuildId(@TempDir Path dir) throws Exception {
    Path p = install(dir, FixtureDb.buildId());
    Files.setLastModifiedTime(p, OLD);
    install(dir, "a-newer-build");
    assertThat(Files.getLastModifiedTime(p)).isNotEqualTo(OLD);
  }

  @Test
  void keepsWhenBuildIdMatches(@TempDir Path dir) throws Exception {
    Path p = install(dir, FixtureDb.buildId());
    Files.setLastModifiedTime(p, OLD);
    Path again = install(dir, FixtureDb.buildId());
    assertThat(again).isEqualTo(p);
    assertThat(Files.getLastModifiedTime(p)).isEqualTo(OLD);
  }

  private static Path install(Path dir, String buildId) throws Exception {
    try (InputStream in = Files.newInputStream(FixtureDb.path())) {
      return new DatabaseInstaller(dir).install(in, buildId);
    }
  }
}
