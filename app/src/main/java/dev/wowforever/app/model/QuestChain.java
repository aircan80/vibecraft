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
 * repeats a quest to show where the data loops. Only the current quest's own exclusive alternatives are steps.
 *
 * <p>{@code spine} is the linear path through the current quest for "Step 2 of 4": from the earliest prerequisite
 * to the last unlock, one quest per depth, without exclusive alternatives, parents and loop steps. Where a step has
 * several candidates the spine picks, going backward, a prerequisite (a lead-in only when there is no prerequisite)
 * whose nextQuestInChain names the step, else the one with the highest level, then the lowest id; going forward, the
 * unlock the step's nextQuestInChain names, else the one with the lowest level, then the lowest id.
 *
 * <p>{@code truncated} is true when the walk stopped {@link #MAX_DEPTH} steps away from the current quest in either
 * direction with quests left beyond. The {@link #EMPTY} chain (no steps, {@code currentIndex} -1) stands for a quest
 * the database does not have.
 */
public record QuestChain(List<Step> steps, int currentIndex, List<Step> spine, boolean truncated) {
  /** How many steps the chain reaches before and after the current quest. */
  public static final int MAX_DEPTH = 50;

  /** The chain of a quest the database does not have. */
  public static final QuestChain EMPTY = new QuestChain(List.of(), -1, List.of(), false);

  public QuestChain {
    steps = List.copyOf(steps);
    spine = List.copyOf(spine);
    if (steps.isEmpty()) {
      if (currentIndex != -1 || !spine.isEmpty() || truncated) {
        throw new IllegalArgumentException("an empty chain has currentIndex -1, no spine and is not truncated");
      }
    } else {
      if (currentIndex < 0 || currentIndex >= steps.size()) {
        throw new IllegalArgumentException("currentIndex " + currentIndex + " outside " + steps.size() + " steps");
      }
      if (!spine.contains(steps.get(currentIndex))) {
        throw new IllegalArgumentException("the spine must contain the current step");
      }
    }
  }

  /** True for the {@link #EMPTY} chain of an unknown quest. */
  public boolean isEmpty() {
    return steps.isEmpty();
  }

  /** The step of the quest the chain was built for; empty for an unknown quest. */
  public Optional<Step> current() {
    return isEmpty() ? Optional.empty() : Optional.of(steps.get(currentIndex));
  }

  /** True when the quest has no prerequisites, alternatives or follow-ups. */
  public boolean standalone() {
    return steps.size() == 1;
  }

  /** The current quest's position on the {@link #spine} (0-based); -1 for an unknown quest. */
  public int spineIndex() {
    return isEmpty() ? -1 : spine.indexOf(steps.get(currentIndex));
  }

  /** The spine step before the current quest, if any. */
  public Optional<Step> previous() {
    int i = spineIndex();
    return i > 0 ? Optional.of(spine.get(i - 1)) : Optional.empty();
  }

  /** The spine step after the current quest, if any. */
  public Optional<Step> next() {
    int i = spineIndex();
    return i >= 0 && i + 1 < spine.size() ? Optional.of(spine.get(i + 1)) : Optional.empty();
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
   * One row of the chain. {@code name}, {@code level} and {@code zoneName} are null when a related quest is not in
   * the database ({@code name}) or the field is unknown. {@code connector} is null for the current quest. {@code depth}
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
