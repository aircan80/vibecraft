package dev.wowforever.app.model;

import java.util.List;

/** Retained assertions, not an inference that an omitted assertion is false. */
public record SourceDifference(String topic, String classification, List<Assertion> assertions) {
  public SourceDifference { assertions=List.copyOf(assertions); }
  public record Assertion(String source, String value, String evidence, boolean selected) {}
}
