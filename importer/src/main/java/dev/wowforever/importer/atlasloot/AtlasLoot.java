package dev.wowforever.importer.atlasloot;

/** One loot row: the difficulty table it came from, its slot in the list and the item id. */
public record AtlasLoot(String difficulty, int slot, long itemId) {}
