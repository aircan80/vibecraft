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
    AssetRepository assets) {

  public static Repositories of(Database db) {
    return new Repositories(new SearchRepository(db), new ItemRepository(db), new NpcRepository(db),
        new ObjectRepository(db), new QuestRepository(db), new ZoneRepository(db), new InstanceRepository(db),
        new AssetRepository(db));
  }

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
