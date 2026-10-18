package dev.wowforever.app.db;

import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.Spawn;
import java.util.List;

/** Every repository over one {@link Database}. */
public record Repositories(
    SearchRepository search,
    ItemRepository items,
    NpcRepository npcs,
    ObjectRepository objects,
    QuestRepository quests,
    ZoneRepository zones,
    InstanceRepository instances,
    AssetRepository assets,
    QuestLocator questLocator,
    SourceArchiveRepository sourceArchive,
    WorldMapRepository worldMaps,
    Database database) {

  public static Repositories of(Database db) {
    return new Repositories(new SearchRepository(db), new ItemRepository(db), new NpcRepository(db),
        new ObjectRepository(db), new QuestRepository(db), new ZoneRepository(db), new InstanceRepository(db),
        new AssetRepository(db), new QuestLocator(db), new SourceArchiveRepository(db), new WorldMapRepository(db), db);
  }

  /**
   * Context-aware entry point for the legacy single-projection schema. Reject other editions/builds
   * before constructing any repository; enabling another game requires scoped SQL and a validated pack.
   */
  public static Repositories of(Database db, dev.wowforever.app.catalog.CatalogContext context) {
    var installed = dev.wowforever.app.catalog.EditionRegistry.fromLegacyMetadata(db.meta())
        .installed(context.editionId());
    if (installed.isEmpty() || !installed.get().equals(context)) {
      throw new IllegalArgumentException("Catalog context does not match the installed database");
    }
    return of(db);
  }

  /** Travel geography shares this repository set's read-only database. */
  public TravelMapRepository travel() { return new TravelMapRepository(database); }

  /**
   * The spawn points of NPC or object {@code ref} with coordinates, in import order, from its own repository; none
   * for the other kinds, which do not spawn.
   */
  public List<Spawn> spawnsOf(EntityRef ref) {
    return switch (ref.kind()) {
      case NPC -> npcs.spawnsOf(ref.id());
      case OBJECT -> objects.spawnsOf(ref.id());
      default -> List.of();
    };
  }
}
