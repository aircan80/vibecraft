package dev.wowforever.app.db;

import dev.wowforever.app.model.QuestChain;
import dev.wowforever.app.model.QuestChain.Connector;
import dev.wowforever.app.model.QuestChain.Step;
import dev.wowforever.app.model.QuestRelation;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the {@link QuestChain} around one quest from {@code quest_relation} (spec section 4, "Quest chain").
 *
 * <p>The walk starts at the quest and follows prerequisite edges backward (preQuestGroup, preQuestSingle,
 * breadcrumbs, the parent quest) and forward (the quests that require this one, its next quest, child quests and
 * breadcrumb targets), each edge read from whichever side declares it. nextQuestInChain only ever points forward: it
 * is a hint, never a prerequisite. Every quest is claimed by the first step that reaches it, so it appears once;
 * a step's children are all claimed before any of them is expanded, so each quest hangs off the nearest step. An
 * edge back to a quest on the current path adds a loop step and stops there. The quest's own exclusive alternatives
 * are claimed as leaves beside it (a prerequisite's or an unlock's alternatives are not part of this quest's chain).
 * The walk stops expanding {@link QuestChain#MAX_DEPTH} steps away from the quest and then reports the chain as
 * truncated when a step at the cap still had an unclaimed quest beyond it.
 */
final class QuestChainBuilder {
  private enum Direction { BACKWARD, FORWARD }

  /** A quest's own columns, null fields when the quest is not in the database. */
  private record Head(String name, Integer level, String zoneName) {
    static final Head UNKNOWN = new Head(null, null, null);
  }

  /** One walked step and the steps hanging off it. */
  private static final class Node {
    final long id;
    final Step step;
    final List<Node> before = new ArrayList<>();
    final List<Node> after = new ArrayList<>();
    final List<Node> alternatives = new ArrayList<>();

    Node(long id, Head head, Connector connector, int depth, boolean loop) {
      this.id = id;
      this.step = new Step(id, head.name(), head.level(), head.zoneName(), connector, depth, loop);
    }

    boolean loop() {
      return step.loop();
    }
  }

  /** Children in display order: strongest connector first, then by level, name and id. */
  private static final Comparator<Node> DISPLAY_ORDER = Comparator
      .comparing((Node n) -> n.step.connector())
      .thenComparing(n -> n.step.level(), Comparator.nullsLast(Comparator.naturalOrder()))
      .thenComparing(n -> n.step.name(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
      .thenComparingLong(n -> n.id);

  private final Connection c;
  private final Map<Long, Head> heads = new HashMap<>();
  private final Map<Long, List<QuestRelation>> relations = new HashMap<>();
  private final Set<Long> claimed = new HashSet<>();
  private final Set<Long> path = new HashSet<>();
  private boolean truncated;

  private QuestChainBuilder(Connection c) {
    this.c = c;
  }

  /** The chain around quest {@code questId}; {@link QuestChain#EMPTY} when the database lacks the quest. */
  static QuestChain build(Connection c, long questId) throws SQLException {
    return new QuestChainBuilder(c).build(questId);
  }

  private QuestChain build(long questId) throws SQLException {
    if (!exists(questId)) {
      return QuestChain.EMPTY;
    }
    Node root = new Node(questId, head(questId), null, 0, false);
    claimed.add(questId);
    path.add(questId);
    expand(root, Direction.BACKWARD, true);
    expand(root, Direction.FORWARD, false);

    List<Step> steps = new ArrayList<>();
    for (Node kid : root.before) {
      emitBackward(kid, steps);
    }
    int currentIndex = steps.size();
    steps.add(root.step);
    root.alternatives.forEach(a -> steps.add(a.step));
    for (Node kid : root.after) {
      emitForward(kid, steps);
    }
    return new QuestChain(steps, currentIndex, spine(root), truncated);
  }

  /**
   * Claims the node's children in {@code dir} (and, with {@code withAlternatives}, its exclusive alternatives), then
   * expands each child the same way without alternatives.
   */
  private void expand(Node n, Direction dir, boolean withAlternatives) throws SQLException {
    List<Node> kids = dir == Direction.BACKWARD ? n.before : n.after;
    int depth = n.step.depth();
    int kidDepth = dir == Direction.BACKWARD ? depth - 1 : depth + 1;
    if (Math.abs(depth) < QuestChain.MAX_DEPTH) {
      for (Map.Entry<Long, Connector> e : edges(n.id, dir).entrySet()) {
        long target = e.getKey();
        if (path.contains(target)) {
          kids.add(new Node(target, head(target), e.getValue(), kidDepth, true));
        } else if (claimed.add(target)) {
          kids.add(new Node(target, head(target), e.getValue(), kidDepth, false));
        }
      }
      kids.sort(DISPLAY_ORDER);
    } else if (!truncated) {
      for (long target : edges(n.id, dir).keySet()) {
        if (!claimed.contains(target)) {
          truncated = true;
          break;
        }
      }
    }
    if (withAlternatives) {
      for (long alt : exclusives(n.id)) {
        if (claimed.add(alt)) {
          n.alternatives.add(new Node(alt, head(alt), Connector.EXCLUSIVE, depth, false));
        }
      }
      n.alternatives.sort(DISPLAY_ORDER);
    }
    for (Node kid : kids) {
      if (!kid.loop()) {
        path.add(kid.id);
        expand(kid, dir, false);
        path.remove(kid.id);
      }
    }
  }

  /** A prerequisite-side subtree: its own prerequisites first, then the step, then its alternatives. */
  private static void emitBackward(Node n, List<Step> out) {
    for (Node kid : n.before) {
      emitBackward(kid, out);
    }
    out.add(n.step);
    n.alternatives.forEach(a -> out.add(a.step));
  }

  /** An unlock-side subtree: the step, its alternatives, then what it unlocks. */
  private static void emitForward(Node n, List<Step> out) {
    out.add(n.step);
    n.alternatives.forEach(a -> out.add(a.step));
    for (Node kid : n.after) {
      emitForward(kid, out);
    }
  }

  /**
   * One quest per depth through the root. Backward: a prerequisite (a lead-in only when there is none), preferring
   * the one whose next quest is the step, then the highest level. Forward: an unlock, preferring the step's next
   * quest, then the lowest level. Parents, alternatives and loop steps never join the spine.
   */
  private List<Step> spine(Node root) throws SQLException {
    List<Step> back = new ArrayList<>();
    for (Node n = root; ; ) {
      Node pick = pickBefore(n);
      if (pick == null) {
        break;
      }
      back.add(pick.step);
      n = pick;
    }
    List<Step> spine = new ArrayList<>(back.reversed());
    spine.add(root.step);
    for (Node n = root; ; ) {
      Node pick = pickAfter(n);
      if (pick == null) {
        break;
      }
      spine.add(pick.step);
      n = pick;
    }
    return spine;
  }

  private Node pickBefore(Node n) throws SQLException {
    List<Node> candidates = n.before.stream().filter(k -> !k.loop()
        && (k.step.connector() == Connector.REQUIRES_ALL || k.step.connector() == Connector.REQUIRES_ONE)).toList();
    if (candidates.isEmpty()) {
      candidates = n.before.stream().filter(k -> !k.loop() && k.step.connector() == Connector.LEAD_IN).toList();
    }
    Node best = null;
    for (Node k : candidates) {
      if (best == null || compareBefore(k, best, n.id) < 0) {
        best = k;
      }
    }
    return best;
  }

  private int compareBefore(Node a, Node b, long stepId) throws SQLException {
    int hint = Boolean.compare(nextOf(b.id).contains(stepId), nextOf(a.id).contains(stepId));
    if (hint != 0) {
      return hint;
    }
    int level = Comparator.nullsLast(Comparator.<Integer>reverseOrder()).compare(a.step.level(), b.step.level());
    return level != 0 ? level : Long.compare(a.id, b.id);
  }

  private Node pickAfter(Node n) throws SQLException {
    Set<Long> hinted = nextOf(n.id);
    Comparator<Node> order = Comparator.comparing((Node k) -> !hinted.contains(k.id))
        .thenComparing(k -> k.step.level(), Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparingLong(k -> k.id);
    return n.after.stream().filter(k -> !k.loop()).min(order).orElse(null);
  }

  /**
   * The quests one step away from {@code id} in {@code dir}, each with its strongest connector (the lowest
   * {@link Connector} ordinal: a prerequisite beats a lead-in, a lead-in beats a parent).
   */
  private Map<Long, Connector> edges(long id, Direction dir) throws SQLException {
    Map<Long, Connector> out = new LinkedHashMap<>();
    for (QuestRelation r : relationsOf(id)) {
      boolean own = r.questId() == id;
      boolean theirs = r.relatedId() == id;
      if (dir == Direction.BACKWARD) {
        if (own) {
          switch (r.kind()) {
            case "pre_group" -> merge(out, r.relatedId(), Connector.REQUIRES_ALL);
            case "pre_single" -> merge(out, r.relatedId(), Connector.REQUIRES_ONE);
            case "breadcrumb" -> merge(out, r.relatedId(), Connector.LEAD_IN);
            case "parent" -> merge(out, r.relatedId(), Connector.DURING);
            default -> { }
          }
        }
        if (theirs) {
          switch (r.kind()) {
            case "breadcrumb_for" -> merge(out, r.questId(), Connector.LEAD_IN);
            case "child" -> merge(out, r.questId(), Connector.DURING);
            default -> { }
          }
        }
      } else {
        if (own) {
          switch (r.kind()) {
            case "next", "child", "breadcrumb_for" -> merge(out, r.relatedId(), Connector.UNLOCKS);
            default -> { }
          }
        }
        if (theirs) {
          switch (r.kind()) {
            case "pre_group", "pre_single", "parent", "breadcrumb" -> merge(out, r.questId(), Connector.UNLOCKS);
            default -> { }
          }
        }
      }
    }
    return out;
  }

  private static void merge(Map<Long, Connector> out, long target, Connector connector) {
    out.merge(target, connector, (a, b) -> a.ordinal() <= b.ordinal() ? a : b);
  }

  /** The quests exclusive to {@code id}, declared on either side. */
  private Set<Long> exclusives(long id) throws SQLException {
    Set<Long> out = new HashSet<>();
    for (QuestRelation r : relationsOf(id)) {
      if (r.kind().equals("exclusive")) {
        long other = r.questId() == id ? r.relatedId() : r.questId();
        if (other != id) {
          out.add(other);
        }
      }
    }
    return out;
  }

  /** The next quests {@code id} names (nextQuestInChain). */
  private Set<Long> nextOf(long id) throws SQLException {
    Set<Long> out = new HashSet<>();
    for (QuestRelation r : relationsOf(id)) {
      if (r.questId() == id && r.kind().equals("next")) {
        out.add(r.relatedId());
      }
    }
    return out;
  }

  /** Every relation row that names {@code id} on either side. */
  private List<QuestRelation> relationsOf(long id) throws SQLException {
    List<QuestRelation> cached = relations.get(id);
    if (cached == null) {
      cached = Sql.list(c, """
          SELECT quest_id, related_id, kind FROM quest_relation WHERE quest_id = ?
          UNION
          SELECT quest_id, related_id, kind FROM quest_relation WHERE related_id = ?
          ORDER BY 1, 2, 3""",
          QuestRepository::relation, id, id);
      relations.put(id, cached);
    }
    return cached;
  }

  private boolean exists(long id) throws SQLException {
    return Sql.one(c, "SELECT 1 FROM quest WHERE id = ?", rs -> Boolean.TRUE, id).isPresent();
  }

  private Head head(long id) throws SQLException {
    Head cached = heads.get(id);
    if (cached == null) {
      Optional<Head> row = Sql.one(c, "SELECT q.name, q.quest_level, qz.name AS zone_name FROM quest q "
              + QuestRepository.ZONE_JOIN + " WHERE q.id = ?",
          rs -> new Head(rs.getString("name"), Sql.integer(rs, "quest_level"), rs.getString("zone_name")), id);
      cached = row.orElse(Head.UNKNOWN);
      heads.put(id, cached);
    }
    return cached;
  }
}
