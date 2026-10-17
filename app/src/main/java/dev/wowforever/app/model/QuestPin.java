package dev.wowforever.app.model;

import java.util.List;
import java.util.Objects;

/**
 * One spawn point of a quest giver (an NPC or object) to pin on a zone map: the giver, its name (null when unknown),
 * the point as map percentages (0 to 100) and the quests it starts and ends there.
 */
public record QuestPin(EntityRef giver, String giverName, double x, double y, List<QuestRef> quests) {
  public QuestPin {
    Objects.requireNonNull(giver, "giver");
    quests = List.copyOf(quests);
  }

  /** The giver's name, else {@code "Unknown <kind> #<id>"}. */
  public String displayName() {
    return giver.displayName(giverName);
  }

  /** Whether the giver starts any of its quests here (else it only ends them). */
  public boolean starts() {
    return quests.stream().anyMatch(QuestRef::starts);
  }
}
