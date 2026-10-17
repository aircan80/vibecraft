package dev.wowforever.importer.clientdata;

import java.util.List;

/**
 * A dungeon of the client's own tables ({@link ClientInstanceLoader}), such as the Forever dungeons AtlasLoot lacks.
 *
 * @param mapId         the {@code Map} row's {@code ID}
 * @param name          its {@code MapName_lang} ("The Hall of Thanes")
 * @param tunedMinLevel the {@code ContentTuning.MinLevelSquish} of its LFGDungeons row; null when 0 or absent
 * @param areaTableId   its {@code AreaTableID}; null when 0
 * @param encounters    its bosses ({@code DungeonEncounter} rows of {@code DifficultyID} 0) by {@code OrderIndex},
 *                      then {@code ID}
 */
public record ClientInstance(long mapId, String name, Integer tunedMinLevel, Integer areaTableId,
    List<ClientEncounter> encounters) {

  public ClientInstance {
    encounters = encounters == null ? List.of() : List.copyOf(encounters);
  }
}
