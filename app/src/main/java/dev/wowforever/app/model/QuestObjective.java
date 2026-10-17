package dev.wowforever.app.model;

import java.util.List;

/**
 * One quest objective row, in Questie's order. {@code kind} is {@code creature}, {@code object}, {@code item},
 * {@code reputation}, {@code spell} or {@code explore}; {@code targetId} is the creature, object, item, faction or
 * spell id (null for explore rows). {@code text} is Questie's objective text: the triggerEnd text of an explore row,
 * the value of a reputation row. Rows with the same {@code ordinal} and a non-null {@code creditGroup} are one
 * killCredit objective, the base creature first. {@code points} are an explore objective's area points.
 */
public record QuestObjective(
    long questId,
    int ordinal,
    String kind,
    Long targetId,
    String text,
    Integer creditGroup,
    List<Point> points) {

  public QuestObjective {
    points = List.copyOf(points);
  }

  /** A point on a zone's map, in percent of the map (0..100). */
  public record Point(long zoneId, double x, double y) {}
}
