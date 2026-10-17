package dev.wowforever.app.model;

import java.util.List;
import java.util.Objects;

/** Everything the item page shows; {@code card} is {@link ItemCard#none()} for an item without client data. */
public record ItemDetail(
    ItemSummary summary,
    Integer classId,
    Integer subclassId,
    Long startQuestId,
    List<DropSource> droppedBy,
    List<InstanceLoot> inInstanceLoot,
    List<ItemSource> containedInObjects,
    List<ItemSource> containedInItems,
    List<ItemSource> soldBy,
    List<ItemSource> rewardFrom,
    List<ItemSource> relatedQuests,
    ItemCard card) {

  public ItemDetail {
    droppedBy = List.copyOf(droppedBy);
    inInstanceLoot = List.copyOf(inInstanceLoot);
    containedInObjects = List.copyOf(containedInObjects);
    containedInItems = List.copyOf(containedInItems);
    soldBy = List.copyOf(soldBy);
    rewardFrom = List.copyOf(rewardFrom);
    relatedQuests = List.copyOf(relatedQuests);
    Objects.requireNonNull(card, "card");
  }

  /** An item page without client data. */
  public ItemDetail(ItemSummary summary, Integer classId, Integer subclassId, Long startQuestId,
      List<DropSource> droppedBy, List<InstanceLoot> inInstanceLoot, List<ItemSource> containedInObjects,
      List<ItemSource> containedInItems, List<ItemSource> soldBy, List<ItemSource> rewardFrom,
      List<ItemSource> relatedQuests) {
    this(summary, classId, subclassId, startQuestId, droppedBy, inInstanceLoot, containedInObjects,
        containedInItems, soldBy, rewardFrom, relatedQuests, ItemCard.none());
  }
}
