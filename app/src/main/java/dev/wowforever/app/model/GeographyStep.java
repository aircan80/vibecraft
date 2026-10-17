package dev.wowforever.app.model;

/** One known level of geographic ancestry, from broadest map to the current area. */
public record GeographyStep(String name, long id, Kind kind) {
  public enum Kind { MAP, ZONE, INSTANCE }
}
