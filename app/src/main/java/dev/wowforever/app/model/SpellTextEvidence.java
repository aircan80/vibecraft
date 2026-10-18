package dev.wowforever.app.model;

import java.util.List;

/** Immutable formatter evidence loaded with the item card; no UI-thread queries. */
public record SpellTextEvidence(String status, Long spellId, String rawText, List<String> unresolved,
    String source, String product, String build, String tableSha256) {
  public SpellTextEvidence { unresolved = List.copyOf(unresolved); }
  public static SpellTextEvidence unknown() {
    return new SpellTextEvidence("UNKNOWN", null, null, List.of(), null, null, null, null);
  }
}
