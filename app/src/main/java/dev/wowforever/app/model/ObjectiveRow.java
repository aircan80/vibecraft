package dev.wowforever.app.model;

import java.util.List;

/** Immutable quest-page data assembled off the EDT. */
public record ObjectiveRow(int number, ObjectiveVerb verb, List<EntityRef> targets, String targetName, String text, List<ObjectiveSource> sources, List<QuestObjective.Point> points, InstanceSummary instance) {
  public ObjectiveRow {
    targets = List.copyOf(targets);
    sources = List.copyOf(sources);
    points = List.copyOf(points);
  }
}
