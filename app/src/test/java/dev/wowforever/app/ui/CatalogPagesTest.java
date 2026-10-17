package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.db.Database;
import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.nav.ViewTarget;
import java.nio.file.*;
import java.util.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FixtureDb.class)
class CatalogPagesTest {
  @Test void allCatalogsRenderWithUnavailableSourcesAfterDatabaseCloses(@TempDir Path temp) throws Exception {
    Path file = temp.resolve("catalogs.sqlite"); Files.copy(FixtureDb.path(),file);
    Map<ViewTarget,Object> snapshots = new LinkedHashMap<>();
    try(var db = Database.open(file)) {
      for(String section : List.of("hub","ladder","pvp","races","race","camping","talents","legacy","class-spells")) {
        var target = new ViewTarget.Forever(section,95);
        snapshots.put(target,CatalogPages.load(target,db));
      }
      for(String section : List.of("sets","set","stats","trinkets","recipes","xp-food","comprehension")) {
        var target = new ViewTarget.ItemContent(section,2133);
        snapshots.put(target,CatalogPages.load(target,db));
      }
    }
    SwingUtilities.invokeAndWait(() -> snapshots.forEach((target,data) ->
        assertThat(CatalogPages.render(target,data,ignored -> {})).as(target.toString()).isNotNull()));
  }
}
