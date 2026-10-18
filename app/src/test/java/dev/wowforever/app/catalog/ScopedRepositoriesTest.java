package dev.wowforever.app.catalog;

import static org.junit.jupiter.api.Assertions.*;
import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import org.junit.jupiter.api.Test;

class ScopedRepositoriesTest {
  @Test void wrongEditionAndProjectionAreRejectedBeforeAccess() {
    var db = FixtureDb.repositories().database();
    var installed = EditionRegistry.fromLegacyMetadata(db.meta()).installed("forever").orElseThrow();
    assertSame(db, Repositories.of(db, installed).database());
    assertThrows(IllegalArgumentException.class, () -> Repositories.of(db,
        new CatalogContext("classic-era", installed.projectionId(), installed.clientBuild(), installed.locale())));
    assertThrows(IllegalArgumentException.class, () -> Repositories.of(db,
        new CatalogContext("forever", "another-projection", installed.clientBuild(), installed.locale())));
  }
}
