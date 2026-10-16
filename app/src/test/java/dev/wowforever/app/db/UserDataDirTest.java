package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UserDataDirTest {
  private static final Path HOME = Path.of("home", "me");

  @Test
  void windowsUsesLocalAppData() {
    assertThat(UserDataDir.resolve(Map.of("LOCALAPPDATA", "C:/Users/me/AppData/Local"), "Windows 11", HOME))
        .isEqualTo(Path.of("C:/Users/me/AppData/Local", "WoWForeverBrowser"));
  }

  @Test
  void macUsesApplicationSupport() {
    assertThat(UserDataDir.resolve(Map.of(), "Mac OS X", HOME))
        .isEqualTo(HOME.resolve("Library").resolve("Application Support").resolve("WoWForeverBrowser"));
  }

  @Test
  void linuxPrefersXdgDataHome() {
    assertThat(UserDataDir.resolve(Map.of("XDG_DATA_HOME", "/data"), "Linux", HOME))
        .isEqualTo(Path.of("/data", "wowforever-browser"));
    assertThat(UserDataDir.resolve(Map.of(), "Linux", HOME))
        .isEqualTo(HOME.resolve(".local").resolve("share").resolve("wowforever-browser"));
  }
}
