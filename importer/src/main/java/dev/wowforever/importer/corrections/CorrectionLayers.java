package dev.wowforever.importer.corrections;

import static dev.wowforever.importer.entity.EntityKind.ITEM;
import static dev.wowforever.importer.entity.EntityKind.NPC;
import static dev.wowforever.importer.entity.EntityKind.OBJECT;
import static dev.wowforever.importer.entity.EntityKind.QUEST;

import dev.wowforever.importer.entity.EntityKind;
import java.util.List;

/** The Forever correction layers, in the order QuestieDB composes them. */
public final class CorrectionLayers {
  private static final String ROOT = "src/corrections/Forever/";

  private static final List<CorrectionLayer> FOREVER = List.of(
      layer("legacy", "classicItemFixes", "QuestieItemFixes:Load", ITEM),
      layer("legacy", "classicNPCFixes", "QuestieNPCFixes:Load", NPC),
      layer("legacy", "classicObjectFixes", "QuestieObjectFixes:Load", OBJECT),
      layer("legacy", "classicQuestFixes", "QuestieQuestFixes:Load", QUEST),
      layer("legacy", "classicQuestReputationFixes", "QuestieClassicQuestReputationFixes:Load", QUEST),
      layer("legacy", "itemStartFixes", "QuestieItemStartFixes:LoadAutomaticQuestStarts", ITEM),
      layer("generated", "foreverBaseItem", "ForeverBaseItem:Load", ITEM),
      layer("generated", "foreverBaseNpc", "ForeverBaseNpc:Load", NPC),
      layer("generated", "foreverBaseObject", "ForeverBaseObject:Load", OBJECT),
      layer("generated", "foreverBaseQuest", "ForeverBaseQuest:Load", QUEST),
      layer("traces", "foreverItemTraces", "ForeverItemTraces:Load", ITEM),
      layer("traces", "foreverNpcTraces", "ForeverNpcTraces:Load", NPC),
      layer("traces", "foreverObjectTraces", "ForeverObjectTraces:Load", OBJECT),
      layer("traces", "foreverQuestTraces", "ForeverQuestTraces:Load", QUEST),
      layer("forever", "foreverItemFixes", "ForeverItemFixes:Load", ITEM),
      layer("forever", "foreverNPCFixes", "ForeverNpcFixes:Load", NPC),
      layer("forever", "foreverObjectFixes", "ForeverObjectFixes:Load", OBJECT),
      layer("forever", "foreverQuestFixes", "ForeverQuestFixes:Load", QUEST));

  private CorrectionLayers() {}

  /**
   * Inherited legacy fixes, then the generated delta-base, then trace-derived corrections, then the
   * authored Forever static fixes; later layers win.
   */
  public static List<CorrectionLayer> forever() {
    return FOREVER;
  }

  /** {@code group} "forever" names the authored files that sit directly in the Forever directory. */
  private static CorrectionLayer layer(String group, String file, String function, EntityKind kind) {
    String dir = group.equals("forever") ? "" : group + "/";
    return new CorrectionLayer(group + "/" + file, ROOT + dir + file + ".lua", function, kind);
  }
}
