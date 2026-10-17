package dev.wowforever.app.model;

import java.util.List;

/**
 * The quest givers' pins of a zone map and whether the query that read them was cut at its cap, so the map can say
 * it shows only some of them.
 */
public record QuestPins(List<QuestPin> pins, boolean capped) {
  public QuestPins {
    pins = List.copyOf(pins);
  }

  /** No pins, not cut. */
  public static QuestPins none() {
    return new QuestPins(List.of(), false);
  }
}
