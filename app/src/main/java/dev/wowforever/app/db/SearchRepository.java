package dev.wowforever.app.db;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.SearchHit;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Full-text search over item, NPC, object, quest, zone and instance names. */
public final class SearchRepository {
  private static final Logger LOG = LoggerFactory.getLogger(SearchRepository.class);

  /** The best {@code limit} matches first, then the subtitle columns of each hit's own table. */
  private static final String SQL = """
      SELECT s.kind, s.id, s.name,
             ic.name AS class_name, isc.name AS subclass_name, i.item_level,
             n.min_level, n.max_level, nz.name AS npc_zone,
             oz.name AS object_zone,
             q.quest_level,
             z.kind AS zone_kind,
             inst.content_type, inst.level_min, inst.level_max
      FROM (SELECT kind, id, name, bm25(search_index) AS score FROM search_index
            WHERE search_index MATCH ? AND name IS NOT NULL %s
            ORDER BY score, name LIMIT ?) s
      LEFT JOIN item i ON s.kind = 'item' AND i.id = s.id
      LEFT JOIN item_class ic ON ic.id = i.class_id
      LEFT JOIN item_subclass isc ON isc.class_id = i.class_id AND isc.id = i.subclass_id
      LEFT JOIN npc n ON s.kind = 'npc' AND n.id = s.id
      LEFT JOIN zone nz ON nz.id = n.zone_id
      LEFT JOIN game_object o ON s.kind = 'object' AND o.id = s.id
      LEFT JOIN zone oz ON oz.id = o.zone_id
      LEFT JOIN quest q ON s.kind = 'quest' AND q.id = s.id
      LEFT JOIN zone z ON s.kind = 'zone' AND z.id = s.id
      LEFT JOIN instance inst ON s.kind = 'instance' AND inst.id = s.id
      ORDER BY s.score, s.name""";

  private final Database db;

  public SearchRepository(Database db) {
    this.db = db;
  }

  /**
   * Hits whose name contains every whitespace-separated term of {@code text} as a word prefix, best first.
   * Blank text (or text without any letter or digit) gives no hits; no input makes this throw.
   */
  public List<SearchHit> search(String text, Optional<EntityKind> kind, int limit) {
    String match = matchExpression(text);
    if (match.isEmpty() || limit <= 0) {
      return List.of();
    }
    if (kind.isPresent() && kind.get().dbKind() == null) {
      return List.of(); // not indexed (bosses)
    }
    List<Object> params = new ArrayList<>();
    params.add(match);
    kind.ifPresent(k -> params.add(k.dbKind()));
    params.add(limit);
    String sql = SQL.formatted(kind.isPresent() ? "AND kind = ?" : "");
    try {
      return db.read(c -> Sql.list(c, sql, SearchRepository::hit, params.toArray()))
          .stream().filter(Objects::nonNull).toList();
    } catch (Database.QueryException e) {
      LOG.warn("Search for {} failed: {}", match, e.getMessage());
      return List.of();
    }
  }

  /**
   * The FTS5 query for {@code text}: each whitespace-separated term as a quoted prefix phrase
   * ({@code "term"*}, inner double quotes doubled), joined by spaces (implicit AND). Terms without a letter
   * or digit are dropped, since the tokenizer would turn them into empty phrases.
   */
  static String matchExpression(String text) {
    if (text == null) {
      return "";
    }
    StringJoiner terms = new StringJoiner(" ");
    for (String term : text.strip().split("\\s+")) {
      if (term.codePoints().anyMatch(Character::isLetterOrDigit)) {
        terms.add("\"" + term.replace("\"", "\"\"") + "\"*");
      }
    }
    return terms.toString();
  }

  private static SearchHit hit(ResultSet rs) throws SQLException {
    Optional<EntityKind> kind = EntityKind.fromDbKind(rs.getString("kind"));
    if (kind.isEmpty()) {
      return null;
    }
    StringJoiner subtitle = new StringJoiner(" · ");
    subtitle.add(kind.get().label());
    switch (kind.get()) {
      case ITEM -> {
        String cls = rs.getString("class_name");
        String sub = rs.getString("subclass_name");
        if (cls != null) {
          subtitle.add(sub != null ? cls + "/" + sub : cls);
        }
        Integer ilvl = Sql.integer(rs, "item_level");
        if (ilvl != null) {
          subtitle.add("iLvl " + ilvl);
        }
      }
      case NPC -> {
        String levels = range(Sql.integer(rs, "min_level"), Sql.integer(rs, "max_level"));
        if (levels != null) {
          subtitle.add("Level " + levels);
        }
        addIfPresent(subtitle, rs.getString("npc_zone"));
      }
      case OBJECT -> addIfPresent(subtitle, rs.getString("object_zone"));
      case QUEST -> {
        Integer level = Sql.integer(rs, "quest_level");
        if (level != null) {
          subtitle.add("Level " + level);
        }
      }
      case ZONE -> addIfPresent(subtitle, rs.getString("zone_kind"));
      case INSTANCE -> {
        String type = rs.getString("content_type");
        String levels = range(Sql.integer(rs, "level_min"), Sql.integer(rs, "level_max"));
        String hint = type == null ? levels : levels == null ? type : type + " " + levels;
        addIfPresent(subtitle, hint);
      }
      default -> { }
    }
    return new SearchHit(new EntityRef(kind.get(), rs.getLong("id")), rs.getString("name"), subtitle.toString());
  }

  /** {@code "20–21"}, {@code "20"} when equal or only one is known, or null when neither is. */
  private static String range(Integer min, Integer max) {
    if (min == null && max == null) {
      return null;
    }
    if (min == null || max == null || min.equals(max)) {
      return String.valueOf(min != null ? min : max);
    }
    return min + "–" + max;
  }

  private static void addIfPresent(StringJoiner joiner, String s) {
    if (s != null && !s.isBlank()) {
      joiner.add(s);
    }
  }
}
