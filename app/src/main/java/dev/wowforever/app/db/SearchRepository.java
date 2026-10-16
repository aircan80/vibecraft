package dev.wowforever.app.db;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.SearchHit;
import dev.wowforever.app.ui.Labels;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Full-text search over item, NPC, object, quest, zone and instance names, ranked by exact name match, then kind
 * (instance, zone, boss or rare NPC, item, quest, NPC, object), then bm25, then name.
 */
public final class SearchRepository {
  private static final Logger LOG = LoggerFactory.getLogger(SearchRepository.class);

  /** Rows fetched per hit asked for, so that merging and collapsing still leave {@code limit} hits. */
  static final int FETCH_FACTOR = 3;

  /**
   * Every match ({@code Zone #id} placeholder names left out, as databases built before the importer stopped
   * indexing them still have them) with the subtitle columns of its own table, best first. {@code sole} is the
   * one instance whose {@code zone_id} a zone hit is, when merging is on (the parameter after the MATCH text and
   * optional kind is 1) and the zone has exactly one instance; the hit then stands for that instance and joins its
   * columns. The ORDER BY takes the normalised search text ({@link #exactText}); every value is a parameter.
   */
  private static final String SQL = """
      WITH s AS MATERIALIZED (
        SELECT kind, id, name, bm25(search_index) AS score FROM search_index
        WHERE search_index MATCH ? AND name IS NOT NULL AND name NOT LIKE 'Zone #%%' %s)
      SELECT s.kind, s.id, s.name,
             sole.id AS sole_id, sole.name AS sole_name,
             ic.name AS class_name, isc.name AS subclass_name, i.item_level, i.required_level,
             n.rank, n.min_level, n.max_level, nz.name AS npc_zone,
             oz.name AS object_zone,
             q.quest_level, qz.name AS quest_zone,
             z.kind AS zone_kind, zp.name AS zone_parent,
             inst.content_type, inst.level_min, inst.level_max,
             CASE WHEN iz.name = inst.name THEN izp.name ELSE iz.name END AS instance_zone
      FROM s
      LEFT JOIN instance sole ON ? = 1 AND s.kind = 'zone'
          AND sole.id = (SELECT CASE WHEN count(*) = 1 THEN min(id) END FROM instance WHERE zone_id = s.id)
      LEFT JOIN item i ON s.kind = 'item' AND i.id = s.id
      LEFT JOIN item_class ic ON ic.id = i.class_id
      LEFT JOIN item_subclass isc ON isc.class_id = i.class_id AND isc.id = i.subclass_id
      LEFT JOIN npc n ON s.kind = 'npc' AND n.id = s.id
      LEFT JOIN zone nz ON nz.id = n.zone_id
      LEFT JOIN game_object o ON s.kind = 'object' AND o.id = s.id
      LEFT JOIN zone oz ON oz.id = o.zone_id
      LEFT JOIN quest q ON s.kind = 'quest' AND q.id = s.id
      LEFT JOIN zone qz ON q.zone_or_sort > 0 AND qz.id = q.zone_or_sort
      LEFT JOIN zone z ON s.kind = 'zone' AND sole.id IS NULL AND z.id = s.id
      LEFT JOIN zone zp ON zp.id = z.parent_id
      LEFT JOIN instance inst ON inst.id = CASE WHEN s.kind = 'instance' THEN s.id ELSE sole.id END
      LEFT JOIN zone iz ON iz.id = inst.zone_id
      LEFT JOIN zone izp ON izp.id = iz.parent_id
      ORDER BY lower(s.name) = ? DESC,
               CASE WHEN s.kind = 'instance' OR sole.id IS NOT NULL THEN 0
                    WHEN s.kind = 'zone' THEN 1
                    WHEN n.rank IN (3, 4) OR n.id IN (SELECT npc_id FROM boss WHERE npc_id IS NOT NULL) THEN 2
                    WHEN s.kind = 'item' THEN 3
                    WHEN s.kind = 'quest' THEN 4
                    WHEN s.kind = 'npc' THEN 5
                    WHEN s.kind = 'object' THEN 6
                    ELSE 7 END,
               s.score, s.name, s.kind, s.id
      LIMIT ?""";

  private final Database db;

  public SearchRepository(Database db) {
    this.db = db;
  }

  /**
   * Hits whose name contains every whitespace-separated term of {@code text} as a word prefix, best first: a name
   * equal to the text (ignoring case and repeated spaces) first, then by kind, bm25 and name. Without a kind
   * filter, a zone that is the zone of exactly one instance stands for that instance (once, if the instance matched
   * too). Hits of the same kind and name collapse into the first, which carries their count. Blank text (or text
   * without any letter or digit) gives no hits; no input makes this throw.
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
    params.add(kind.isEmpty() ? 1 : 0);
    params.add(exactText(text));
    params.add((long) limit * FETCH_FACTOR);
    String sql = SQL.formatted(kind.isPresent() ? "AND kind = ?" : "");
    try {
      List<SearchHit> rows = db.read(c -> Sql.list(c, sql, SearchRepository::hit, params.toArray()));
      return collapse(rows.stream().filter(Objects::nonNull).toList(), limit);
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

  /** {@code text} as an exact name match compares it: stripped, inner whitespace runs as one space, lower case. */
  static String exactText(String text) {
    return text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  /**
   * {@code rows} in order without repeated refs (a merged zone and its instance), hits of the same kind and name
   * folded into the first with their count, cut to {@code limit}.
   */
  static List<SearchHit> collapse(List<SearchHit> rows, int limit) {
    Set<EntityRef> seen = new HashSet<>();
    Map<String, Integer> groupIndex = new HashMap<>();
    List<SearchHit> firsts = new ArrayList<>();
    List<Integer> counts = new ArrayList<>();
    for (SearchHit row : rows) {
      if (!seen.add(row.ref())) {
        continue;
      }
      String key = row.ref().kind() + "\u0000" + row.name();
      Integer index = groupIndex.get(key);
      if (index == null) {
        groupIndex.put(key, firsts.size());
        firsts.add(row);
        counts.add(1);
      } else {
        counts.set(index, counts.get(index) + 1);
      }
    }
    List<SearchHit> hits = new ArrayList<>();
    for (int i = 0; i < firsts.size() && hits.size() < limit; i++) {
      SearchHit first = firsts.get(i);
      hits.add(new SearchHit(first.ref(), first.name(), first.subtitle(), counts.get(i)));
    }
    return hits;
  }

  private static SearchHit hit(ResultSet rs) throws SQLException {
    Long soleId = Sql.longValue(rs, "sole_id");
    if (soleId != null) {
      String name = rs.getString("sole_name");
      return new SearchHit(new EntityRef(EntityKind.INSTANCE, soleId), name != null ? name : rs.getString("name"),
          instanceSubtitle(rs));
    }
    Optional<EntityKind> kind = EntityKind.fromDbKind(rs.getString("kind"));
    if (kind.isEmpty()) {
      return null;
    }
    String facts = switch (kind.get()) {
      case ITEM -> Labels.itemSubtitle(rs.getString("class_name"), rs.getString("subclass_name"),
          Sql.integer(rs, "item_level"), Sql.integer(rs, "required_level"));
      case NPC -> Labels.npcSubtitle(Sql.integer(rs, "rank"), Sql.integer(rs, "min_level"),
          Sql.integer(rs, "max_level"), rs.getString("npc_zone"));
      case OBJECT -> Labels.objectSubtitle(rs.getString("object_zone"));
      case QUEST -> Labels.questSubtitle(Sql.integer(rs, "quest_level"), rs.getString("quest_zone"));
      case ZONE -> Labels.zoneSubtitle(rs.getString("zone_kind"), rs.getString("zone_parent"));
      case INSTANCE -> instanceSubtitle(rs);
      case BOSS -> "";
    };
    return new SearchHit(new EntityRef(kind.get(), rs.getLong("id")), rs.getString("name"),
        Labels.withKind(kind.get(), facts));
  }

  private static String instanceSubtitle(ResultSet rs) throws SQLException {
    return Labels.instanceSubtitle(rs.getString("content_type"), Sql.integer(rs, "level_min"),
        Sql.integer(rs, "level_max"), rs.getString("instance_zone"));
  }
}
