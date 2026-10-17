package dev.wowforever.app.model;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

/** Attribution for an actual projected relationship, rather than a claim about every quest field. */
public record QuestRelationEvidence(long questId, long relatedId, String kind, String source,
    String snapshotId, String artifactPath, String sourceUrl, String evidenceStatus) {
  public String sourceLabel() {
    return switch (source) {
      case "allthethings-forever", "allthethings", "att-forever" -> "AllTheThings Forever";
      case "questie", "questiedb" -> "QuestieDB";
      default -> source;
    };
  }

  public boolean partial() { return evidenceStatus != null && evidenceStatus.contains("partial"); }

  public String qualification() {
    return partial() ? "Conditional requirements may be incomplete; not in-game verified"
        : "Authored source data; not in-game verified";
  }

  public Optional<URI> uri() {
    if (sourceUrl == null || sourceUrl.isBlank()) return Optional.empty();
    try {
      URI uri = URI.create(sourceUrl);
      return uri.getHost() != null && Set.of("http", "https").contains(uri.getScheme())
          ? Optional.of(uri) : Optional.empty();
    } catch (IllegalArgumentException e) { return Optional.empty(); }
  }

  /** The chain may reverse a native relationship; only evidence for the displayed connector qualifies. */
  public boolean supports(QuestChain.Step step) {
    if (step.parentQuestId() == null || step.connector() == null) return false;
    boolean outgoing = questId == step.parentQuestId() && relatedId == step.questId();
    boolean incoming = questId == step.questId() && relatedId == step.parentQuestId();
    return switch (step.connector()) {
      case REQUIRES_ALL -> outgoing && kind.equals("pre_group");
      case REQUIRES_ONE -> outgoing && kind.equals("pre_single");
      case EXCLUSIVE -> (outgoing || incoming) && kind.equals("exclusive");
      case LEAD_IN -> outgoing && kind.equals("breadcrumb") || incoming && kind.equals("breadcrumb_for");
      case LEADS_TO -> outgoing && kind.equals("breadcrumb_for") || incoming && kind.equals("breadcrumb");
      case DURING -> outgoing && kind.equals("parent") || incoming && kind.equals("child");
      case UNLOCKS -> outgoing && Set.of("next", "child").contains(kind)
          || incoming && Set.of("pre_group", "pre_single", "parent").contains(kind);
    };
  }
}
