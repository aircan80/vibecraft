package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FixtureDb.class)
class AssetRepositoryTest {
  private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G'};
  /** The icon of item 1307 (Gold Pickup Schedule), which the client data has no blob for. */
  private static final long MISSING_ICON = 134939;
  /** The icon of item 17204 (Eye of Sulfuras). */
  private static final long PRESENT_ICON = 134124;

  private final Repositories repos = FixtureDb.repositories();

  @Test
  void iconIsThePngBlob() {
    assertThat(repos.assets().icon(PRESENT_ICON))
        .hasValueSatisfying(png -> assertThat(png).startsWith(PNG_SIGNATURE));
    assertThat(repos.assets().icon(AssetRepository.FALLBACK_ICON_ID)).isPresent();
    assertThat(repos.assets().icon(MISSING_ICON)).isEmpty();
  }

  @Test
  void iconOrFallbackUsesTheQuestionMarkForMissingIcons() {
    byte[] fallback = repos.assets().icon(AssetRepository.FALLBACK_ICON_ID).orElseThrow();
    assertThat(repos.assets().iconOrFallback(MISSING_ICON)).hasValue(fallback);
    assertThat(repos.assets().iconOrFallback(null)).hasValue(fallback);
    assertThat(repos.assets().iconOrFallback(PRESENT_ICON))
        .hasValueSatisfying(png -> assertThat(png).isNotEqualTo(fallback));
  }

  @Test
  void iconOrFallbackIsEmptyWithoutAFallbackIcon(@TempDir Path dir) throws Exception {
    Path copy = dir.resolve("copy.sqlite");
    Files.copy(FixtureDb.path(), copy);
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement s = c.createStatement()) {
      s.executeUpdate("DELETE FROM icon WHERE id = 0");
    }
    try (Database db = Database.open(copy)) {
      AssetRepository assets = new AssetRepository(db);
      assertThat(assets.iconOrFallback(MISSING_ICON)).isEmpty();
      assertThat(assets.iconOrFallback(null)).isEmpty();
      assertThat(assets.iconOrFallback(PRESENT_ICON)).isPresent();
    }
  }
}
