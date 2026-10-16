package dev.wowforever.importer.entity;

/** The four QuestieDB entity kinds and the names their data and key tables use upstream. */
public enum EntityKind {
  ITEM("Item", "itemData", "itemKeys"),
  NPC("Npc", "npcData", "npcKeys"),
  OBJECT("Object", "objectData", "objectKeys"),
  QUEST("Quest", "questData", "questKeys");

  private final String metaEntity;
  private final String dataField;
  private final String keysField;

  EntityKind(String metaEntity, String dataField, String keysField) {
    this.metaEntity = metaEntity;
    this.dataField = dataField;
    this.keysField = keysField;
  }

  /** The {@code entity} value in the kind's meta file. */
  public String metaEntity() {
    return metaEntity;
  }

  /** The field of {@code QuestieDB} holding the data long string, e.g. {@code itemData}. */
  public String dataField() {
    return dataField;
  }

  /** The field of {@code QuestieDB} holding the key enum, e.g. {@code itemKeys}. */
  public String keysField() {
    return keysField;
  }
}
