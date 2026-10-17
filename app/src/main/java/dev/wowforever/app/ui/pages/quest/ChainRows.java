package dev.wowforever.app.ui.pages.quest;

import dev.wowforever.app.model.QuestChain;
import dev.wowforever.app.model.QuestChain.Connector;
import dev.wowforever.app.model.QuestChain.Step;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure presentation of the flattened quest tree; grouping always uses parent identity, never adjacency. */
public final class ChainRows {
  public static final int WINDOW = 2;
  public static final int FAN_MIN = 3;
  public enum Kind { CONNECTOR, STEP, CURRENT, FAN, SHOW_EARLIER, SHOW_LATER, NOTE }
  public record Row(Kind kind, int indent, String text, Step step, String fanKey, int hidden) {}
  private record Group(Long parent, Connector connector) {}
  private record Fan(Group group, String name) {
    String key() { return group.parent() + ":" + group.connector() + ":" + name; }
  }

  private ChainRows() {}

  public static List<Row> of(QuestChain chain, Set<String> expandedFans,
      boolean expandEarlier, boolean expandLater) {
    if (chain.isEmpty()) {
      return List.of();
    }
    if (chain.standalone()) {
      return List.of(new Row(Kind.NOTE, 0, "No prerequisites or follow-ups recorded.", null, null, 0));
    }
    List<Step> visible = chain.steps().stream()
        .filter(s -> (expandEarlier || s.depth() >= -WINDOW) && (expandLater || s.depth() <= WINDOW)).toList();
    Map<Group, List<Step>> groups = new LinkedHashMap<>();
    Map<Fan, List<Step>> fans = new LinkedHashMap<>();
    for (Step step : visible) {
      Group group = new Group(step.parentQuestId(), step.connector());
      groups.computeIfAbsent(group, k -> new ArrayList<>()).add(step);
      if (step.name() != null && !step.loop() && step.connector() != null) {
        fans.computeIfAbsent(new Fan(group, step.name()), k -> new ArrayList<>()).add(step);
      }
    }
    List<Row> rows = new ArrayList<>();
    int earlier = (int) chain.steps().stream().filter(s -> s.depth() < -WINDOW).count();
    int later = (int) chain.steps().stream().filter(s -> s.depth() > WINDOW).count();
    if (!expandEarlier && earlier > 0) {
      rows.add(new Row(Kind.SHOW_EARLIER, 0, "Show " + earlier + " earlier steps", null, null, earlier));
    }
    Set<Group> emitted = new HashSet<>();
    Set<Fan> emittedFans = new HashSet<>();
    for (Step step : visible) {
      Group group = new Group(step.parentQuestId(), step.connector());
      List<Step> members = groups.get(group);
      boolean current = step == chain.steps().get(chain.currentIndex());
      int indent = Math.abs(step.depth());
      if (step.connector() != null && emitted.add(group) && !plainSpine(group, members, chain)) {
        rows.add(new Row(Kind.CONNECTOR, indent, step.connector().label(), null, null, 0));
      }
      Fan fan = new Fan(group, step.name());
      List<Step> siblings = fans.getOrDefault(fan, List.of());
      if (!current && !step.loop() && siblings.size() >= FAN_MIN && !expandedFans.contains(fan.key())) {
        if (emittedFans.add(fan)) {
          rows.add(new Row(Kind.FAN, indent, step.name() + " ×" + siblings.size() + " ›",
              null, fan.key(), siblings.size()));
        }
        continue;
      }
      String text = text(step);
      if (step.connector() == Connector.EXCLUSIVE && !Objects.equals(members.getFirst(), step)) {
        text = "or " + text;
      }
      rows.add(new Row(current ? Kind.CURRENT : Kind.STEP, indent, text, step, null, 0));
    }
    if (!expandLater && later > 0) {
      rows.add(new Row(Kind.SHOW_LATER, 0, "Show " + later + " later steps", null, null, later));
    }
    if (chain.truncated()) {
      rows.add(new Row(Kind.NOTE, 0, "The chain goes on beyond 50 steps.", null, null, 0));
    }
    return List.copyOf(rows);
  }

  private static boolean plainSpine(Group group, List<Step> members, QuestChain chain) {
    return members.size() == 1 && chain.spine().contains(members.getFirst())
        && (group.connector() == Connector.REQUIRES_ONE || group.connector() == Connector.REQUIRES_ALL
            || group.connector() == Connector.UNLOCKS);
  }

  private static String text(Step step) {
    String name = step.name() == null ? "Unknown quest #" + step.questId() : step.name();
    return (step.level() == null ? "" : "Lv " + step.level() + "  ") + name
        + (step.zoneName() == null ? "" : " — " + step.zoneName()) + (step.loop() ? " (loop in data)" : "");
  }
}
