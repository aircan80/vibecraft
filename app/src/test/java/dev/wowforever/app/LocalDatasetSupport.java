package dev.wowforever.app;

import dev.wowforever.app.db.DatabaseInstaller;
import org.junit.jupiter.api.Assumptions;

/** Qualifies real-data tests without disguising an explicitly requested broken dataset as a skip. */
public final class LocalDatasetSupport {
  private LocalDatasetSupport() {}

  public static void requireAvailable() {
    String configured = System.getProperty(DatabaseInstaller.EXTERNAL_DATABASE_PROPERTY);
    Assumptions.assumeTrue((configured != null && !configured.isBlank())
        || DatabaseInstaller.class.getResource("/data/wowforever.sqlite") != null,
        "Real gameplay dataset unavailable: use -Plocal-data -Dwowforever.localDataDir=/directory/with/database-and-sidecars");
  }
}
