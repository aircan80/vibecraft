package dev.wowforever.importer.clientdata;

/**
 * A boss of a client dungeon ({@link ClientInstanceLoader}): a {@code DungeonEncounter} row's {@code ID},
 * {@code Name_lang} and {@code OrderIndex} (the client's boss order, in steps such as 0, 1000, 1500, 2000).
 */
public record ClientEncounter(long id, String name, int orderIndex) {}
