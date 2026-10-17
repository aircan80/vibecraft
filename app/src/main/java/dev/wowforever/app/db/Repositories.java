package dev.wowforever.app.db;

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
}
