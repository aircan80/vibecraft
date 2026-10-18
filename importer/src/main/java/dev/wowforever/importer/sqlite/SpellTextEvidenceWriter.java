package dev.wowforever.importer.sqlite;

import dev.wowforever.importer.clientdata.SpellTextEvidence;
import java.sql.*;

/** Optional additive metadata; participates in the caller's transaction. */
final class SpellTextEvidenceWriter {
  private SpellTextEvidenceWriter() {}
  static void ensure(Connection c) throws SQLException {
    try (var s = c.createStatement()) { s.execute("""
CREATE TABLE IF NOT EXISTS spell_text_evidence (
  owner_kind TEXT NOT NULL CHECK(owner_kind IN ('effect','set_bonus','tooltip')),
  owner_id INTEGER NOT NULL, ordinal INTEGER NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('COMPLETE','INCOMPLETE','UNKNOWN')),
  spell_id INTEGER NOT NULL, raw_text TEXT, unresolved TEXT NOT NULL,
  source TEXT, product TEXT, build TEXT, table_sha256 TEXT,
  PRIMARY KEY(owner_kind,owner_id,ordinal)
)
        """); }
  }
  static void deleteItem(Connection c, long item) throws SQLException {
    try (var p = c.prepareStatement("DELETE FROM spell_text_evidence WHERE owner_id=? AND owner_kind IN ('effect','tooltip')")) {
      p.setLong(1,item); p.executeUpdate();
    }
  }
  static void write(Connection c, String kind, long owner, int ordinal, SpellTextEvidence e) throws SQLException {
    if (e == null) return;
    try (var p = c.prepareStatement("INSERT OR REPLACE INTO spell_text_evidence VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
      Object[] values = {kind,owner,ordinal,e.status(),e.spellId(),e.rawText(),String.join("\n",e.unresolved()),
          e.source(),e.product(),e.build(),e.tableSha256()};
      for (int i=0;i<values.length;i++) p.setObject(i+1,values[i]);
      p.executeUpdate();
    }
  }
}
