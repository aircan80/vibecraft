package dev.wowforever.app.model;

import java.util.List;

/** Immutable client facts for the camping browser; absent values remain unknown. */
public record CampingData(String build, String snapshot, List<Camp> camps,
    List<Blueprint> blueprints, List<String> missingTables) {
  public CampingData {
    camps = List.copyOf(camps);
    blueprints = List.copyOf(blueprints);
    missingTables = List.copyOf(missingTables);
  }
  public record Camp(long spellId, String name, long objectId, String description,
      String sourcePath, int sourceLine, String tier, String profession, Integer requiredRank) {}
  public record Blueprint(long itemId, String name, Integer requiredSkill, Integer requiredRank,
      String sourcePath, int sourceLine) {}

  /** Names and descriptions alone do not establish profession, tier, or stacking relationships. */
  public String coverageNote() {
    return "Coverage lists source-linked camp features whose placing-item rank requirements are met. "
        + "It does not establish recipe ownership, simultaneous placement, buff values, or stacking.";
  }
}
