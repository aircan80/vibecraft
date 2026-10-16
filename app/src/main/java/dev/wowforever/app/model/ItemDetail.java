package dev.wowforever.app.model;

import java.util.List;

/** Everything the item page shows. */
public record ItemDetail(
    ItemSummary summary,
    Integer classId,
    Integer subclassId,
    Long startQuestId,
    List<DropSource> droppedBy,
    List<ItemSource> containedInObjects,
    List<ItemSource> containedInItems,
    List<ItemSource> soldBy,
    List<ItemSource> rewardFrom,
    List<ItemSource> relatedQuests) {

  public ItemDetail {
    droppedBy = List.copyOf(droppedBy);
    containedInObjects = List.copyOf(containedInObjects);
    containedInItems = List.copyOf(containedInItems);
    soldBy = List.copyOf(soldBy);
    rewardFrom = List.copyOf(rewardFrom);
    relatedQuests = List.copyOf(relatedQuests);
  }
}
