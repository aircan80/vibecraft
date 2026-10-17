package dev.wowforever.app.model;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One related quest, with all the roles played by the entity whose page is open. */
public record QuestInvolvement(QuestSummary quest, Set<Role> roles, String viaItemName) {
  public enum Role { STARTS, ENDS, OBJECTIVE, DROPS_OBJECTIVE_ITEM, PROVIDED, REQUIRED, RELATED }

  public QuestInvolvement {
    Objects.requireNonNull(quest, "quest");
    roles = Set.copyOf(roles);
  }

  /** A bounded query result; exactly the limit is not itself evidence of overflow. */
  public record Result(List<QuestInvolvement> rows, boolean capped) {
    public Result { rows = List.copyOf(rows); }
    public static Result empty() { return new Result(List.of(), false); }
  }

  public String glyphs() {
    return roles.contains(Role.STARTS) ? (roles.contains(Role.ENDS) ? "! ?" : "!")
        : roles.contains(Role.ENDS) ? "?" : "";
  }

  /** Generic role text: the three-field model alone does not identify an objective's target kind. */
  public String rolesText() { return rolesText(null); }

  /** Contextual objective wording supplied by the entity page, without quantities. */
  public String rolesText(EntityKind kind) {
    List<String> text = new ArrayList<>();
    for (Role role : Role.values()) {
      if (!roles.contains(role)) continue;
      text.add(switch (role) {
        case STARTS -> "Starts";
        case ENDS -> "Ends";
        case OBJECTIVE -> kind == EntityKind.NPC ? "Kill objective"
            : kind == EntityKind.ITEM ? "Collect objective"
            : kind == EntityKind.OBJECT ? "Interact objective" : "Objective";
        case DROPS_OBJECTIVE_ITEM -> viaItemName == null ? "Drops objective item" : "Drops " + viaItemName;
        case PROVIDED -> "Given at start";
        case REQUIRED -> "Needed";
        case RELATED -> "Related";
      });
    }
    return String.join(" · ", text);
  }

  /** Compatibility for callers holding only legacy item relationships. */
  public static Result fromRelated(List<ItemSource> related) {
    Map<Long, QuestInvolvement> rows = new LinkedHashMap<>();
    for (ItemSource source : related) {
      QuestSummary quest = new QuestSummary(source.ref().id(), source.name(), null, null, null);
      rows.putIfAbsent(quest.id(), new QuestInvolvement(quest, Set.of(Role.RELATED), null));
    }
    return new Result(List.copyOf(rows.values()), false);
  }

  /** Compatibility for callers holding only a pre-loaded legacy giver detail, with no database reads. */
  public static Result fromGivers(List<QuestSummary> starts, List<QuestSummary> ends) {
    Map<Long, QuestSummary> quests = new LinkedHashMap<>();
    Map<Long, Set<Role>> roles = new LinkedHashMap<>();
    for (QuestSummary quest : starts) {
      quests.putIfAbsent(quest.id(), quest);
      roles.computeIfAbsent(quest.id(), k -> EnumSet.noneOf(Role.class)).add(Role.STARTS);
    }
    for (QuestSummary quest : ends) {
      quests.putIfAbsent(quest.id(), quest);
      roles.computeIfAbsent(quest.id(), k -> EnumSet.noneOf(Role.class)).add(Role.ENDS);
    }
    return new Result(quests.values().stream().map(q -> new QuestInvolvement(q, roles.get(q.id()), null)).toList(), false);
  }
}
