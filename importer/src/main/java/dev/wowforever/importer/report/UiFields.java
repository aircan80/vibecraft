package dev.wowforever.importer.report;

import dev.wowforever.importer.entity.EntityKind;
import java.util.Map;
import java.util.Set;

/**
 * The fields the browser shows, per the spec's acceptance bar: losing one of these during import is a
 * visible data error, so every skip on them is counted by {@link ImportReport#skipsOnUiFields()}.
 */
public final class UiFields {
  private static final Map<EntityKind, Set<String>> FIELDS = Map.of(
      EntityKind.ITEM, Set.of("name", "npcDrops", "objectDrops", "itemDrops", "vendors", "questRewards",
          "relatedQuests", "startQuest", "class", "subClass", "itemLevel", "requiredLevel"),
      EntityKind.NPC, Set.of("name", "subName", "minLevel", "maxLevel", "rank", "zoneID", "spawns",
          "questStarts", "questEnds", "npcFlags"),
      EntityKind.OBJECT, Set.of("name", "zoneID", "spawns", "questStarts", "questEnds"),
      EntityKind.QUEST, Set.of("name", "startedBy", "finishedBy", "requiredLevel", "questLevel", "zoneOrSort",
          "objectivesText", "questFlags", "specialFlags",
          // schema v3 (phase 3A): objectives, source items, relations and requirements on the quest page
          "objectives", "triggerEnd", "sourceItemId", "requiredSourceItems", "extraObjectives", "preQuestGroup",
          "preQuestSingle", "childQuests", "inGroupWith", "exclusiveTo", "nextQuestInChain", "parentQuest",
          "breadcrumbForQuestId", "breadcrumbs", "requiredSkill", "requiredMinRep"));

  private UiFields() {}

  /**
   * Whether a field is shown in the UI. The {@link Skip#ROW} and {@link Skip#LAYER} pseudo-fields count
   * for every kind, since a dropped row or layer always loses whatever UI fields it carried.
   */
  public static boolean isUiField(EntityKind kind, String field) {
    return Skip.ROW.equals(field) || Skip.LAYER.equals(field) || FIELDS.get(kind).contains(field);
  }
}
