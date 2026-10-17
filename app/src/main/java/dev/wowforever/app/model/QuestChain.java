package dev.wowforever.app.model;

import java.util.List;
import java.util.Optional;

/**
 * The quest chain around one quest, for the quest page's chain tree and its stepper.
 *
 * <p>{@code steps} is the tree in display order: the prerequisites (earliest first, each quest's own prerequisites
 * before it), the current quest at {@code currentIndex}, then what it unlocks. Each step's {@link Step#connector}
 * says how it relates to the step it hangs off (its neighbour one {@link Step#depth} closer to the current quest,
 * or the same depth for an {@link Connector#EXCLUSIVE} alternative); the UI groups consecutive steps with the same
 * connector and depth under one connector line. Each quest appears once, except a {@link Step#loop} step, which
 * repeats a quest to show where the data loops.
 *
 * <p>{@code spine} is the linear path through the current quest for "Step 2 of 4": from the earliest prerequisite
 * to the last unlock, one quest per depth, without exclusive alternatives, parents and loop steps.
 */
public record QuestChain(List<Step> steps, int currentIndex, List<Step> spine) {
  /** How many steps the chain reaches before and after the current quest. */
  public static final int MAX_DEPTH = 50;

  public QuestChain {
    steps = List.copyOf(steps);
    spine = List.copyOf(spine);
    if (currentIndex < 0 || currentIndex >= steps.size()) {
      throw new IllegalArgumentException("currentIndex " + currentIndex + " outside " + steps.size() + " steps");
    }
    if (!spine.contains(steps.get(currentIndex))) {
      throw new IllegalArgumentException("the spine must contain the current step");
    }
  }

  /** The step of the quest the chain was built for. */
  public Step current() {
    return steps.get(currentIndex);
  }

  /** True when the quest has no prerequisites, alternatives or follow-ups. */
  public boolean standalone() {
    return steps.size() == 1;
  }

  /** The current quest's position on the {@link #spine} (0-based). */
  public int spineIndex() {
    return spine.indexOf(current());
  }

  /** The spine step before the current quest, if any. */
  public Optional<Step> previous() {
    int i = spineIndex();
    return i > 0 ? Optional.of(spine.get(i - 1)) : Optional.empty();
  }

  /** The spine step after the current quest, if any. */
  public Optional<Step> next() {
    int i = spineIndex();
    return i + 1 < spine.size() ? Optional.of(spine.get(i + 1)) : Optional.empty();
  }

  /** How a step relates to the step it hangs off; {@link #label()} is the connector line the quest page shows. */
  public enum Connector {
    /** A member of the quest's preQuestGroup: every member is required. */
    REQUIRES_ALL("Requires all of:"),
    /** A member of the quest's preQuestSingle: any one is enough. */
    REQUIRES_ONE("Requires one of:"),
    /** An exclusiveTo alternative: doing one closes the other. */
    EXCLUSIVE("Choose one (exclusive):"),
    /** A breadcrumb that leads to the quest but is not required. */
    LEAD_IN("Optional lead-in:"),
    /** A quest that follows: it requires this one, is its next quest, its child or the target of its breadcrumb. */
    UNLOCKS("Unlocks:"),
    /** The parent quest this one is done during. */
    DURING("During:");

    private final String label;

    Connector(String label) {
      this.label = label;
    }

    public String label() {
      return label;
    }
  }

  /**
   * One row of the chain. {@code name}, {@code level} and {@code zoneName} are null when the quest is not in the
   * database ({@code name}) or the field is unknown. {@code connector} is null for the current quest. {@code depth}
   * is the signed distance from the current quest: negative before it, 0 for it and its exclusive alternatives,
   * positive after it. {@code loop} marks a step that repeats a quest already on its path ("(loop in data)").
   */
  public record Step(long questId, String name, Integer level, String zoneName, Connector connector, int depth,
      boolean loop) {
    public EntityRef ref() {
      return new EntityRef(EntityKind.QUEST, questId);
    }

    public String displayName() {
      return ref().displayName(name);
    }
  }
}
