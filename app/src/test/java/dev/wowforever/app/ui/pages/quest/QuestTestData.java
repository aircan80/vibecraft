package dev.wowforever.app.ui.pages.quest;

import dev.wowforever.app.db.Database;
import dev.wowforever.app.db.DatabaseInstaller;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.ui.detail.DetailPanels;
import java.nio.file.Files;

/** Shared read-only shipped data for quest UI acceptance tests. */
public final class QuestTestData {
  private QuestTestData() {}

  private static final class Loaded {
    static final Database DB = open();
    static final Repositories REPOS = Repositories.of(DB);

    private static Database open() {
      try {
        var dir = Files.createTempDirectory("quest-ui-acceptance");
        Database db = Database.open(new DatabaseInstaller(dir).install());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
          db.close();
          try (var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
              try { Files.deleteIfExists(path); } catch (java.io.IOException ignored) { }
            });
          } catch (java.io.IOException ignored) { }
        }));
        return db;
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  public static DetailPanels.LoadedQuest load(long id) {
    dev.wowforever.app.LocalDatasetSupport.requireAvailable();
    return (DetailPanels.LoadedQuest) DetailPanels.load(new EntityRef(EntityKind.QUEST, id), Loaded.REPOS)
        .orElseThrow();
  }
}
