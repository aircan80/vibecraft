package dev.wowforever.app.db;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/** The per-user directory the browser keeps its extracted database in. */
public final class UserDataDir {
  private UserDataDir() {}

  /** The data directory for the current user and operating system. */
  public static Path resolve() {
    return resolve(System.getenv(), System.getProperty("os.name", ""), Path.of(System.getProperty("user.home")));
  }

  /**
   * Windows: {@code %LOCALAPPDATA%\WoWForeverBrowser}; macOS: {@code ~/Library/Application Support/WoWForeverBrowser};
   * otherwise {@code $XDG_DATA_HOME/wowforever-browser} or {@code ~/.local/share/wowforever-browser}.
   */
  public static Path resolve(Map<String, String> env, String osName, Path home) {
    String os = osName.toLowerCase(Locale.ROOT);
    if (os.startsWith("windows")) {
      String local = env.get("LOCALAPPDATA");
      Path base = isBlank(local) ? home.resolve("AppData").resolve("Local") : Path.of(local);
      return base.resolve("WoWForeverBrowser");
    }
    if (os.startsWith("mac") || os.startsWith("darwin")) {
      return home.resolve("Library").resolve("Application Support").resolve("WoWForeverBrowser");
    }
    String xdg = env.get("XDG_DATA_HOME");
    Path base = isBlank(xdg) ? home.resolve(".local").resolve("share") : Path.of(xdg);
    return base.resolve("wowforever-browser");
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
