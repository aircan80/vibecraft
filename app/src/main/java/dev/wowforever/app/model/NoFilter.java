package dev.wowforever.app.model;

/** The filter of a list that cannot be narrowed (all zones, all instances). */
public record NoFilter() implements ListFilter {
  public static final NoFilter INSTANCE = new NoFilter();
}
