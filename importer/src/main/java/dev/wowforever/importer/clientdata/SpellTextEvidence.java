package dev.wowforever.importer.clientdata;

import java.util.List;

/** Per-format evidence. Null identities are unknown, never inferred from the library's generic metadata. */
public record SpellTextEvidence(String status, long spellId, String rawText, List<String> unresolved,
    String source, String product, String build, String tableSha256) {
  public SpellTextEvidence {
    if (!List.of("COMPLETE", "INCOMPLETE", "UNKNOWN").contains(status)) throw new IllegalArgumentException("status");
    unresolved = List.copyOf(unresolved);
  }
  public static SpellTextEvidence unknown(long spellId) {
    return new SpellTextEvidence("UNKNOWN", spellId, null, List.of(), null, null, null, null);
  }
}
