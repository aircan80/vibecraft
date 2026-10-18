package dev.wowforever.app.model;

import java.util.List;

/** One known level of geographic ancestry, with sibling choices loaded before constructing the UI. */
public record GeographyStep(String name, long id, Kind kind, List<Sibling> siblings) {
  public GeographyStep { siblings = List.copyOf(siblings); }
  public GeographyStep(String name, long id, Kind kind) { this(name, id, kind, List.of()); }
  public enum Kind { MAP, ZONE, INSTANCE }
  public record Sibling(String name, long id, Kind kind, boolean available, String note) {
    public Sibling { note = note == null ? "" : note; }
  }
}
