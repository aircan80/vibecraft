package dev.wowforever.app.db;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Literal text research over the small, normalized Questie quest corpus, never native JSON archives. */
public final class LoreSearchRepository {
  public static final int LIMIT = 100;
  public record Hit(long questId, String title, String excerpt, int matchStart, int matchEnd) {}
  public record Snapshot(List<Hit> hits, boolean truncated, String source, String buildId) {
    public Snapshot { hits = List.copyOf(hits); }
  }
  private final Database db;
  public LoreSearchRepository(Database db) { this.db = Objects.requireNonNull(db); }
  public Snapshot search(String query, BooleanSupplier current) {
    String text = query == null ? "" : query.strip();
    if (text.length() < 3 || text.length() > 120) return new Snapshot(List.of(), false, "Questie normalized quest titles and objectives", "");
    check(current);
    return db.read(c -> {
      check(current);
      List<Hit> hits = new ArrayList<>();
      // This intentionally scans only ~5k normalized quest rows. A bounded result avoids loading the corpus.
      try (PreparedStatement statement = c.prepareStatement("""
          SELECT id, name, objectives_text FROM quest
          WHERE name LIKE ? ESCAPE '\\' OR objectives_text LIKE ? ESCAPE '\\'
          ORDER BY id LIMIT 101
          """)) {
        String literal = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        statement.setString(1, literal); statement.setString(2, literal);
        statement.setQueryTimeout(2);
        try (ResultSet rows = statement.executeQuery()) {
          while (rows.next()) {
            check(current);
            String title = Objects.toString(rows.getString("name"), "Quest #" + rows.getLong("id"));
            String objective = Objects.toString(rows.getString("objectives_text"), "");
            String selected = indexOf(objective, text) >= 0 ? objective : title;
            hits.add(excerpt(rows.getLong("id"), title, selected, text));
          }
        }
      }
      check(current);
      Map<String,String> meta = db.meta();
      String commit = meta.getOrDefault("questiedb_refresh_commit", meta.getOrDefault("questiedb_commit", "revision unavailable"));
      return new Snapshot(hits.subList(0, Math.min(LIMIT, hits.size())), hits.size() > LIMIT,
          "Questie normalized quest titles and objectives · revision " + commit,
          meta.getOrDefault("build_id", "unavailable"));
    });
  }
  private static void check(BooleanSupplier current) { if (!current.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException(); }
  private static int indexOf(String value, String query) {
    // regionMatches preserves original UTF-16 offsets, unlike lowercasing expanded Unicode characters.
    for (int i=0;i<=value.length()-query.length();i++) if(value.regionMatches(true,i,query,0,query.length())) return i;
    return -1;
  }
  static Hit excerpt(long id, String title, String value, String query) {
    int match = Math.max(0,indexOf(value,query));
    int begin = Math.max(0,match-70), end = Math.min(value.length(),Math.max(match+query.length()+90,begin+160));
    String prefix = begin>0 ? "…" : "";
    String snippet = prefix+value.substring(begin,end)+(end<value.length()?"…":"");
    int start = prefix.length()+match-begin;
    return new Hit(id,title,snippet,start,Math.min(snippet.length(),start+query.length()));
  }
}
