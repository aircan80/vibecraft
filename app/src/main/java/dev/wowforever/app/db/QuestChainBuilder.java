package dev.wowforever.app.db;

import dev.wowforever.app.model.QuestChain;
import dev.wowforever.app.model.QuestChain.Connector;
import dev.wowforever.app.model.QuestChain.Step;
import dev.wowforever.app.model.QuestRelation;
import dev.wowforever.app.model.QuestRelationEvidence;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
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
 * is a hint, never a prerequisite. Each direction is walked breadth-first and every quest is claimed by the first
 * step that reaches it, so it appears once, at its shallowest depth, under the step that reached it first (steps of
 * one depth take turns in display order). An edge back to a quest on the step's own path (its parent, the parent's
 * parent, up to the current quest) adds a loop step and stops there. The quest's own exclusive alternatives are
 * claimed as leaves beside it (a prerequisite's or an unlock's alternatives are not part of this quest's chain). The
 * walk stops expanding {@link QuestChain#MAX_DEPTH} steps away from the quest and then reports the chain as
 * truncated when a step at the cap still had an unclaimed quest beyond it.
 */
final class QuestChainBuilder {
  private enum Direction { BACKWARD, FORWARD }

  /** A quest's own columns, null fields when the quest is not in the database. */
  private record Head(String name, Integer level, String zoneName) {
    static final Head UNKNOWN = new Head(null, null, null);
  }

  /** One walked step, the step it hangs under ({@code parent}, null for the current quest) and its own steps. */
  private static final class Node {
    final long id;
    final Node parent;
    final Step step;
    final List<Node> before = new ArrayList<>();
    final List<Node> after = new ArrayList<>();
    final List<Node> alternatives = new ArrayList<>();

    Node(long id, Head head, Connector connector, int depth, boolean loop, Node parent) {
      this.id = id;
      this.parent = parent;
      this.step = new Step(id, head.name(), head.level(), head.zoneName(), connector,
          parent == null ? null : parent.id, depth, loop);
    }

    /** True when {@code questId} is this step or one of the steps it hangs under, up to the current quest. */
    boolean onPath(long questId) {
      for (Node a = this; a != null; a = a.parent) {
        if (a.id == questId) {
          return true;
        }
      }
      return false;
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
  private final Map<Long, Optional<Head>> heads = new HashMap<>();
  private final Map<Long, List<QuestRelation>> relations = new HashMap<>();
  private final Set<Long> claimed = new HashSet<>();
  private boolean truncated;

  private QuestChainBuilder(Connection c) {
    this.c = c;
  }

  /** The chain around quest {@code questId}; {@link QuestChain#EMPTY} when the database lacks the quest. */
  static QuestChain build(Connection c, long questId) throws SQLException {
    return new QuestChainBuilder(c).build(questId);
  }

  private QuestChain build(long questId) throws SQLException {
    Optional<Head> rootHead = head(questId);
    if (rootHead.isEmpty()) {
      return QuestChain.EMPTY;
    }
    Node root = new Node(questId, rootHead.get(), null, 0, false, null);
    claimed.add(questId);
    walk(root, Direction.BACKWARD);
    walk(root, Direction.FORWARD);

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
    return new QuestChain(steps, currentIndex, spine(root), truncated, evidence(steps), unsupportedConditions());
  }

  private int unsupportedConditions() throws SQLException {
    try (var s = c.prepareStatement("SELECT value FROM meta WHERE key='att_unsupported_conditions'");
        var r = s.executeQuery()) {
      if (!r.next()) return 0;
      try { return Math.max(0, Integer.parseInt(r.getString(1))); }
      catch (NumberFormatException ignored) { return 0; }
    }
  }

  private List<QuestRelationEvidence> evidence(List<Step> steps) throws SQLException {
    try (var s = c.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name='quest_relation_source'");
        var r = s.executeQuery()) {
      if (!r.next()) return List.of(); // existing shipped databases predate relationship evidence
    }
    var out = new java.util.LinkedHashSet<QuestRelationEvidence>();
    String sql = "SELECT e.quest_id,e.related_id,e.kind,e.source,e.snapshot_id,e.artifact_path,e.source_url,e.evidence_status"
        + " FROM quest_relation_source e JOIN quest_relation r ON r.quest_id=e.quest_id"
        + " AND r.related_id=e.related_id AND r.kind=e.kind"
        + " WHERE (e.quest_id=? AND e.related_id=?) OR (e.quest_id=? AND e.related_id=?)";
    try (var s = c.prepareStatement(sql)) {
      for (Step step : steps) {
        if (step.parentQuestId() == null) continue;
        s.setLong(1, step.questId()); s.setLong(2, step.parentQuestId());
        s.setLong(3, step.parentQuestId()); s.setLong(4, step.questId());
        try (var r = s.executeQuery()) {
          while (r.next()) {
            var evidence = new QuestRelationEvidence(r.getLong(1), r.getLong(2), r.getString(3), r.getString(4),
                r.getString(5), r.getString(6), r.getString(7), r.getString(8));
            if (evidence.supports(step)) out.add(evidence);
          }
        }
      }
    }
    return List.copyOf(out);
  }

  /**
   * Walks breadth-first from the root in {@code dir}: every step one away is claimed before any step two away, and
   * the steps of one depth claim in display order. The backward walk, which runs first, also claims the root's
   * exclusive alternatives right after the root's own prerequisites.
   */
  private void walk(Node root, Direction dir) throws SQLException {
    ArrayDeque<Node> queue = new ArrayDeque<>();
    queue.add(root);
    while (!queue.isEmpty()) {
      Node n = queue.removeFirst();
      List<Node> kids = claimChildren(n, dir);
      if (n == root && dir == Direction.BACKWARD) {
        claimAlternatives(root);
      }
      for (Node kid : kids) {
        if (!kid.loop()) {
          queue.addLast(kid);
        }
      }
    }
  }

  /**
   * Claims the steps one away from {@code n} in {@code dir} and returns them in display order; an edge to a quest on
   * {@code n}'s path becomes a loop step. At the depth cap nothing is claimed; an unclaimed quest beyond the cap marks
   * the chain truncated.
   */
  private List<Node> claimChildren(Node n, Direction dir) throws SQLException {
    List<Node> kids = dir == Direction.BACKWARD ? n.before : n.after;
    int depth = n.step.depth();
    if (Math.abs(depth) >= QuestChain.MAX_DEPTH) {
      if (!truncated) {
        for (long target : edges(n.id, dir).keySet()) {
          if (!claimed.contains(target)) {
            truncated = true;
            break;
          }
        }
      }
      return kids;
    }
    int kidDepth = dir == Direction.BACKWARD ? depth - 1 : depth + 1;
    for (Map.Entry<Long, Connector> e : edges(n.id, dir).entrySet()) {
      long target = e.getKey();
      if (n.onPath(target)) {
        kids.add(new Node(target, headOrUnknown(target), e.getValue(), kidDepth, true, n));
      } else if (claimed.add(target)) {
        kids.add(new Node(target, headOrUnknown(target), e.getValue(), kidDepth, false, n));
      }
    }
    kids.sort(DISPLAY_ORDER);
    return kids;
  }

  /** Claims the root's exclusive alternatives as leaves beside it. */
  private void claimAlternatives(Node root) throws SQLException {
    for (long alt : exclusives(root.id)) {
      if (claimed.add(alt)) {
        root.alternatives.add(new Node(alt, headOrUnknown(alt), Connector.EXCLUSIVE, 0, false, root));
      }
    }
    root.alternatives.sort(DISPLAY_ORDER);
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
            case "next", "child" -> merge(out, r.relatedId(), Connector.UNLOCKS);
            case "breadcrumb_for" -> merge(out, r.relatedId(), Connector.LEADS_TO);
            default -> { }
          }
        }
        if (theirs) {
          switch (r.kind()) {
            case "pre_group", "pre_single", "parent" -> merge(out, r.questId(), Connector.UNLOCKS);
            case "breadcrumb" -> merge(out, r.questId(), Connector.LEADS_TO);
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

  /** The quest's own columns, empty when the database does not have the quest. */
  private Optional<Head> head(long id) throws SQLException {
    Optional<Head> cached = heads.get(id);
    if (cached == null) {
      cached = Sql.one(c, "SELECT q.name, q.quest_level, qz.name AS zone_name FROM quest q "
              + QuestRepository.ZONE_JOIN + " WHERE q.id = ?",
          rs -> new Head(rs.getString("name"), Sql.integer(rs, "quest_level"), rs.getString("zone_name")), id);
      heads.put(id, cached);
    }
    return cached;
  }

  private Head headOrUnknown(long id) throws SQLException {
    return head(id).orElse(Head.UNKNOWN);
  }
}
