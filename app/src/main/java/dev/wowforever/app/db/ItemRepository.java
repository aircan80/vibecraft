package dev.wowforever.app.db;

import dev.wowforever.app.model.Craft;
import dev.wowforever.app.model.CraftReagent;
import dev.wowforever.app.model.DropSource;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceLoot;
import dev.wowforever.app.model.ItemCard;
import dev.wowforever.app.model.ItemClassNode;
import dev.wowforever.app.model.ItemDetail;
import dev.wowforever.app.model.ItemFilter;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.model.RateSource;
import dev.wowforever.app.model.TooltipLine;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToLongFunction;

/** Items: detail pages, filtered lists, the class tree and drop rate sources. */
public final class ItemRepository {
  /** Summary columns over an {@code item i} row source joined with {@link #JOINS}. */
  private static final String COLUMNS = """
      i.id, i.name, i.item_level, i.required_level, i.class_id, i.subclass_id, i.start_quest_id, i.quality,
      i.icon_id,
      ic.name AS class_name, isc.name AS subclass_name,
      (SELECT count(*) FROM item_npc_drop WHERE item_id = i.id)
        + (SELECT count(*) FROM item_object_drop WHERE item_id = i.id)
        + (SELECT count(*) FROM item_container_drop WHERE item_id = i.id)
        + (SELECT count(*) FROM item_vendor WHERE item_id = i.id)
        + (SELECT count(*) FROM quest_reward_item WHERE item_id = i.id) AS source_count""";
  private static final String JOINS = """
      LEFT JOIN item_class ic ON ic.id = i.class_id
      LEFT JOIN item_subclass isc ON isc.class_id = i.class_id AND isc.id = i.subclass_id""";
  private static final String NAME_ORDER = "name IS NULL, name COLLATE NOCASE, id";
  /** True for an {@code item} row whose subclass is null or not in {@code item_subclass} for its class. */
  private static final String UNKNOWN_SUBCLASS =
      "NOT EXISTS (SELECT 1 FROM item_subclass s WHERE s.class_id = item.class_id AND s.id = item.subclass_id)";

  private final Database db;

  /** Supplemental source comparisons, loaded with the detail off the EDT. */
  public java.util.List<dev.wowforever.app.model.SourceDifference> sourceDifferences(long id) {
    return new SourceDifferenceRepository(db).item(id);
  }

  public java.util.List<dev.wowforever.app.model.FieldKnowledgeView> knowledge(long id) {
    return new FieldKnowledgeRepository(db).item(id);
  }
  public ItemContentRepository.Appearance appearance(long id) {
    return new ItemContentRepository(db).appearance(id);
  }

  public dev.wowforever.app.model.ForeverContent.Context foreverContext(long id) {
    return db.read(c -> {
      if(!Sql.hasTable(c,"forever_diff"))return dev.wowforever.app.model.ForeverContent.Context.empty();
      String build=db.meta().getOrDefault("client_build","unknown");
      String era=Sql.one(c,"SELECT era_build FROM forever_diff WHERE forever_build=? ORDER BY era_build DESC LIMIT 1",
          r->r.getString(1),build).orElse("unknown");
      return new dev.wowforever.app.model.ForeverContent.Context(build,era,
          Sql.list(c,"SELECT kind,id,status,fields FROM forever_diff WHERE forever_build=? AND kind IN ('Item','ItemSparse') AND id=? ORDER BY kind",
              r->new dev.wowforever.app.model.ForeverContent.Change(r.getString(1),r.getString(2),r.getString(3),r.getString(4)),build,Long.toString(id)));
    });
  }

  public dev.wowforever.app.model.NativeItemMetadata nativeMetadata(long id) {
    return new NativeItemMetadataRepository(db).item(id);
  }

  public ItemRepository(Database db) {
    this.db = db;
  }

  /** Explicit comparison from the archived Era native record; never used by the Forever card. */
  public record ClassicComparison(String build, String name, Integer itemLevel, Integer requiredLevel,
      String evidence) {}

  public Optional<ClassicComparison> classicComparison(long id) {
    return db.read(c -> {
      if (!Sql.hasTable(c, "client_table_row") || !Sql.hasTable(c, "client_table_dataset")) return Optional.empty();
      return Sql.one(c, """
          SELECT d.build,json_extract(r.payload,'$.Display_lang') AS name,
                 CAST(json_extract(r.payload,'$.ItemLevel') AS INTEGER) AS item_level,
                 CAST(json_extract(r.payload,'$.RequiredLevel') AS INTEGER) AS required_level,
                 r.snapshot_id,r.source_path,r.source_line
          FROM client_table_row r JOIN client_table_dataset d USING(snapshot_id)
          WHERE d.build LIKE '1.15.%' AND r.table_name='ItemSparse' AND r.row_id=?
          ORDER BY d.build DESC LIMIT 1""", r -> new ClassicComparison(r.getString("build"),
              r.getString("name"), Sql.integer(r,"item_level"), Sql.integer(r,"required_level"),
              r.getString("source_path")+":"+r.getLong("source_line")+" · snapshot "+r.getString("snapshot_id")), Long.toString(id));
    });
  }

  /** The item page, or empty when there is no such item. */
  public Optional<ItemDetail> detail(long id) {
    return db.read(c -> {
      Optional<ItemDetail> head = Sql.one(c, "SELECT " + COLUMNS + " FROM item i " + JOINS + " WHERE i.id = ?",
          rs -> new ItemDetail(summary(rs), Sql.integer(rs, "class_id"), Sql.integer(rs, "subclass_id"),
              Sql.longValue(rs, "start_quest_id"), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
              List.of()),
          id);
      if (head.isEmpty()) {
        return head;
      }
      ItemDetail h = head.get();
      return Optional.of(new ItemDetail(h.summary(), h.classId(), h.subclassId(), h.startQuestId(),
          droppedBy(c, id),
          inInstanceLoot(c, id),
          sources(c, ItemSource.OBJECT, EntityKind.OBJECT, """
              SELECT d.object_id AS ref_id, o.name, NULL AS quality, NULL AS icon_id,
              """ + CollectorFacts.observed(c, "object-drop", "object", "d.object_id") + " AS observed_in_game FROM item_object_drop d " + """
              LEFT JOIN game_object o ON o.id = d.object_id WHERE d.item_id = ?""", id),
          sources(c, ItemSource.CONTAINER, EntityKind.ITEM, """
              SELECT d.container_item_id AS ref_id, ci.name, ci.quality, ci.icon_id FROM item_container_drop d
              LEFT JOIN item ci ON ci.id = d.container_item_id WHERE d.item_id = ?""", id),
          sources(c, ItemSource.VENDOR, EntityKind.NPC, """
              SELECT v.npc_id AS ref_id, n.name, NULL AS quality, NULL AS icon_id,
              """ + CollectorFacts.observed(c, "vendor", "npc", "v.npc_id", "v.item_id") + " AS observed_in_game FROM item_vendor v " + """
              LEFT JOIN npc n ON n.id = v.npc_id WHERE v.item_id = ?""", id),
          sources(c, ItemSource.REWARD, EntityKind.QUEST, """
              SELECT r.quest_id AS ref_id, q.name, NULL AS quality, NULL AS icon_id FROM quest_reward_item r
              LEFT JOIN quest q ON q.id = r.quest_id WHERE r.item_id = ?""", id),
          sources(c, ItemSource.RELATED, EntityKind.QUEST, """
              SELECT r.quest_id AS ref_id, q.name, NULL AS quality, NULL AS icon_id FROM item_related_quest r
              LEFT JOIN quest q ON q.id = r.quest_id WHERE r.item_id = ?""", id),
          card(c, id)));
    });
  }

  /**
   * Items ordered by name (nameless stubs last), at most {@code limit} of them after skipping {@code offset}; a
   * null {@code limit} returns every row. A null {@code classId} lists every item; a null {@code subclassId}
   * lists every subclass; {@link ItemClassNode#OTHER_SUBCLASSES} lists the items whose subclass is null or
   * missing from the subclass table.
   */
  public List<ItemSummary> list(Integer classId, Integer subclassId, Integer limit, int offset) {
    return query(classId, subclassId, null, null, null, null, limit, offset);
  }

  /**
   * Every item {@code filter} lets through, ordered by name (nameless stubs last). Lists are not capped, as in
   * v0.1. A quality filter matches {@code item.quality}, so items without client data (no quality) drop out.
   */
  public List<ItemSummary> list(ItemFilter filter) {
    return query(filter.classId(), filter.subclassId(), filter.minLevel(), filter.maxLevel(), filter.quality(),
        filter.statId(), null, 0);
  }

  /**
   * {@link #list(Integer, Integer, Integer, int)} with item level bounds (null: none; unknown levels fail any) and a
   * quality (null: any).
   */
  private List<ItemSummary> query(Integer classId, Integer subclassId, Integer minLevel, Integer maxLevel,
      Integer quality, Integer statId, Integer limit, int offset) {
    StringBuilder where = new StringBuilder(" WHERE 1 = 1");
    List<Object> params = new ArrayList<>();
    if (classId != null) {
      where.append(" AND class_id = ?");
      params.add(classId);
    }
    if (subclassId != null && subclassId == ItemClassNode.OTHER_SUBCLASSES) {
      where.append(" AND " + UNKNOWN_SUBCLASS);
    } else if (subclassId != null) {
      where.append(" AND subclass_id = ?");
      params.add(subclassId);
    }
    if (minLevel != null) {
      where.append(" AND item_level >= ?");
      params.add(minLevel);
    }
    if (maxLevel != null) {
      where.append(" AND item_level <= ?");
      params.add(maxLevel);
    }
    if (quality != null) {
      where.append(" AND quality = ?");
      params.add(quality);
    }
    if (statId != null) {
      where.append(" AND (client_source IS NULL OR client_source <> 'era') AND EXISTS (SELECT 1 FROM item_stat s WHERE s.item_id=item.id AND s.stat_id=?)");
      params.add(statId);
    }
    params.add(limit == null ? -1 : limit); // a negative LIMIT is no limit in SQLite
    params.add(offset);
    // page first, then compute the source counts for that page only
    String sql = "SELECT " + COLUMNS + " FROM (SELECT * FROM item" + where + " ORDER BY " + NAME_ORDER
        + " LIMIT ? OFFSET ?) i " + JOINS + " ORDER BY i.name IS NULL, i.name COLLATE NOCASE, i.id";
    return db.read(c -> Sql.list(c, sql, ItemRepository::summary, params.toArray()));
  }

  /**
   * Item classes that have items, each with the subclasses that have items, by class id then subclass id; a
   * class with items of an unknown (or null) subclass ends with an {@code "(other)"} child.
   */
  public List<ItemClassNode> classTree() {
    return db.read(c -> {
      List<ItemClassNode> classes = Sql.list(c, """
          SELECT c.id, c.name FROM item_class c
          WHERE EXISTS (SELECT 1 FROM item i WHERE i.class_id = c.id) ORDER BY c.id""",
          rs -> new ItemClassNode(rs.getInt("id"), rs.getString("name"), List.of(), ItemClassNode.CLASS_NODE));
      Map<Integer, List<ItemClassNode>> children = new LinkedHashMap<>();
      for (ItemClassNode sub : Sql.list(c, """
          SELECT s.class_id, s.id, s.name FROM item_subclass s
          WHERE EXISTS (SELECT 1 FROM item i WHERE i.class_id = s.class_id AND i.subclass_id = s.id)
          ORDER BY s.class_id, s.id""",
          rs -> new ItemClassNode(rs.getInt("class_id"), rs.getString("name"), List.of(), rs.getInt("id")))) {
        children.computeIfAbsent(sub.classId(), k -> new ArrayList<>()).add(sub);
      }
      Set<Integer> withOther = new HashSet<>(Sql.list(c,
          "SELECT DISTINCT class_id FROM item WHERE class_id IS NOT NULL AND " + UNKNOWN_SUBCLASS,
          rs -> rs.getInt("class_id")));
      for (int classId : withOther) {
        children.computeIfAbsent(classId, k -> new ArrayList<>())
            .add(new ItemClassNode(classId, "(other)", List.of(), ItemClassNode.OTHER_SUBCLASSES));
      }
      return classes.stream()
          .map(cls -> new ItemClassNode(cls.classId(), cls.name(), children.getOrDefault(cls.classId(), List.of()),
              ItemClassNode.CLASS_NODE))
          .toList();
    });
  }

  /**
   * Every drop rate source's value for NPC {@code npcId} dropping item {@code itemId}, Questie's first, then
   * AtlasLoot's ({@link RateSource#ORDER}); empty when no source gave a rate.
   */
  public List<RateSource> sources(long npcId, long itemId) {
    return db.read(c -> sources(c, npcId, itemId));
  }

  /**
   * The rate sources of NPC {@code npcId}'s drops of the items in {@code itemIds} (a page's rated rows), by pair, each
   * in {@link RateSource#ORDER}: one query over the NPC's sources ({@code WHERE npc_id = ?}). Pairs of other items
   * and pairs without sources are left out; no items, no query.
   */
  public Map<RateSource.Key, List<RateSource>> sourcesOfNpc(long npcId, Collection<Long> itemIds) {
    return grouped("npc_id", npcId, itemIds, RateSource.Key::itemId);
  }

  /**
   * The rate sources of item {@code itemId} dropped by the NPCs in {@code npcIds} (a page's rated rows), by pair, as
   * {@link #sourcesOfNpc}: one query over the item's sources ({@code WHERE item_id = ?}).
   */
  public Map<RateSource.Key, List<RateSource>> sourcesOfItem(long itemId, Collection<Long> npcIds) {
    return grouped("item_id", itemId, npcIds, RateSource.Key::npcId);
  }

  /** The sources {@code WHERE <column> = id}, by pair, of the pairs whose other id ({@code otherId}) is wanted. */
  private Map<RateSource.Key, List<RateSource>> grouped(String column, long id, Collection<Long> wanted,
      ToLongFunction<RateSource.Key> otherId) {
    if (wanted.isEmpty()) {
      return Map.of();
    }
    Set<Long> keep = new HashSet<>(wanted);
    record Row(RateSource.Key key, RateSource source) {}
    List<Row> rows = db.read(c -> Sql.list(c,
        "SELECT npc_id, item_id, source, pct, quest_only FROM drop_rate_source WHERE " + column + " = ?",
        rs -> new Row(new RateSource.Key(rs.getLong("npc_id"), rs.getLong("item_id")), source(rs)), id));
    Map<RateSource.Key, List<RateSource>> byPair = new LinkedHashMap<>();
    for (Row r : rows) {
      if (keep.contains(otherId.applyAsLong(r.key()))) {
        byPair.computeIfAbsent(r.key(), k -> new ArrayList<>()).add(r.source());
      }
    }
    byPair.replaceAll((k, sources) -> sources.stream().sorted(RateSource.ORDER).toList());
    return Map.copyOf(byPair);
  }

  private static List<RateSource> sources(Connection c, long npcId, long itemId) throws SQLException {
    List<RateSource> sources = new ArrayList<>(Sql.list(c,
        "SELECT source, pct, quest_only FROM drop_rate_source WHERE npc_id = ? AND item_id = ?",
        ItemRepository::source, npcId, itemId));
    sources.sort(RateSource.ORDER);
    return List.copyOf(sources);
  }

  /** A {@code drop_rate_source} row's source, rate and quest-only flag. */
  private static RateSource source(ResultSet rs) throws SQLException {
    return new RateSource(rs.getString("source"), Sql.doubleValue(rs, "pct"), rs.getInt("quest_only") != 0);
  }

  private static List<DropSource> droppedBy(Connection c, long itemId) throws SQLException {
    return Sql.list(c, """
        SELECT d.npc_id, n.name, n.min_level, n.max_level, n.zone_id, z.name AS zone_name,
               d.drop_pct, d.pct_source, d.origin, d.disagrees, d.source_count,
        """ + CollectorFacts.observed(c, "npc-drop", "npc", "d.npc_id") + " AS observed_in_game " + """
        FROM item_npc_drop d
        LEFT JOIN npc n ON n.id = d.npc_id
        LEFT JOIN zone z ON z.id = n.zone_id
        WHERE d.item_id = ?
        ORDER BY d.drop_pct IS NULL, d.drop_pct DESC, n.name IS NULL, n.name COLLATE NOCASE, d.npc_id""",
        rs -> new DropSource(new EntityRef(EntityKind.NPC, rs.getLong("npc_id")), rs.getString("name"),
            Sql.integer(rs, "min_level"), Sql.integer(rs, "max_level"), rs.getString("zone_name"),
            Sql.longValue(rs, "zone_id"), Sql.doubleValue(rs, "drop_pct"), rs.getString("pct_source"),
            rs.getString("origin"), rs.getInt("disagrees") != 0, rs.getInt("source_count"), rs.getBoolean("observed_in_game")),
        itemId);
  }

  /**
   * The AtlasLoot loot tables the item is in, by instance name, then boss (main list before extra lists, each
   * in AtlasLoot order), then slot.
   */
  private static List<InstanceLoot> inInstanceLoot(Connection c, long itemId) throws SQLException {
    List<InstanceLoot> rows = new ArrayList<>(Sql.list(c, """
        SELECT i.id AS instance_id, i.name AS instance_name, b.id AS boss_id, b.name AS boss_name, b.npc_id,
               bl.slot, bl.drop_pct, bl.disagrees, bl.source_count, bl.pct_source,
        """ + ForeverLootFacts.origin(c)+" AS presence_origin " + """
        FROM boss_loot bl
        JOIN boss b ON b.id = bl.boss_id
        JOIN instance i ON i.id = b.instance_id
        WHERE bl.item_id = ?
        ORDER BY i.name IS NULL, i.name COLLATE NOCASE, i.id, b.is_extra_list, b.sort_order, b.id,
                 bl.slot IS NULL, bl.slot""",
        rs -> new InstanceLoot(new EntityRef(EntityKind.INSTANCE, rs.getLong("instance_id")),
            rs.getString("instance_name"), rs.getLong("boss_id"), rs.getString("boss_name"),
            Sql.longValue(rs, "npc_id"), Sql.integer(rs, "slot"), Sql.doubleValue(rs, "drop_pct"),
            rs.getInt("disagrees") != 0, rs.getInt("source_count"), rs.getString("pct_source"), false, rs.getString("presence_origin")),
        itemId));
    if (Sql.hasTable(c, "collector_fact")) {
      List<InstanceLoot> observed = Sql.list(c, """
          SELECT DISTINCT i.id AS instance_id, i.name AS instance_name, b.id AS boss_id,
                 b.name AS boss_name, b.npc_id
          FROM collector_fact cf JOIN boss b ON b.id = cf.entity_id JOIN instance i ON i.id = b.instance_id
          WHERE cf.category = 'boss-drop' AND cf.entity_type = 'boss' AND cf.item_id = ?
          ORDER BY i.id, b.id""",
          rs -> new InstanceLoot(new EntityRef(EntityKind.INSTANCE, rs.getLong("instance_id")),
              rs.getString("instance_name"), rs.getLong("boss_id"), rs.getString("boss_name"),
              Sql.longValue(rs, "npc_id"), null, null, false, 0, null, true), itemId);
      var observedBosses = observed.stream().map(InstanceLoot::bossId).collect(java.util.stream.Collectors.toSet());
      rows.replaceAll(l -> new InstanceLoot(l.instance(), l.instanceName(), l.bossId(), l.bossName(), l.bossNpcId(),
          l.slot(), l.dropPct(), l.disagrees(), l.sourceCount(), l.pctSource(), observedBosses.contains(l.bossId()), l.presenceOrigin()));
      var existingBosses = rows.stream().map(InstanceLoot::bossId).collect(java.util.stream.Collectors.toSet());
      observed.stream().filter(l -> !existingBosses.contains(l.bossId())).forEach(rows::add);
    }
    return List.copyOf(rows);
  }

  /**
   * The crafts that create item {@code itemId} ("Created by"), by skill (crafts without one last), then spell name;
   * empty when there are none or the database predates the craft tables.
   */
  public List<Craft> createdBy(long itemId) {
    return db.read(c -> crafts(c, "c.item_id = ?", itemId));
  }

  /** The crafts that consume item {@code itemId} ("Reagent for"), ordered like {@link #createdBy}. */
  public List<Craft> reagentFor(long itemId) {
    return db.read(c -> crafts(c, "c.spell_id IN (SELECT spell_id FROM craft_reagent WHERE item_id = ?)", itemId));
  }

  /**
   * The crafts that item {@code recipeItemId} teaches (any of a craft's recipe items, not only the lowest), ordered
   * like {@link #createdBy}.
   */
  public List<Craft> taughtBy(long recipeItemId) {
    return db.read(c -> crafts(c, "c.spell_id IN (SELECT spell_id FROM craft_recipe_item WHERE item_id = ?)",
        recipeItemId));
  }

  /**
   * The crafts matching {@code where} (over {@code craft c}, one parameter) with their reagents in insert (slot)
   * order and their recipe items in id order, the created, recipe and reagent items resolved against the item table;
   * empty on a database without the craft tables.
   */
  private static List<Craft> crafts(Connection c, String where, long param) throws SQLException {
    if (!Sql.hasTable(c, "craft") || !Sql.hasTable(c, "craft_reagent") || !Sql.hasTable(c, "craft_recipe_item")) {
      return List.of();
    }
    record Recipe(long spellId, long itemId) {}
    Map<Long, List<Long>> recipeItems = new LinkedHashMap<>();
    for (Recipe r : Sql.list(c, "SELECT spell_id, item_id FROM craft_recipe_item"
            + " WHERE spell_id IN (SELECT c.spell_id FROM craft c WHERE " + where + ") ORDER BY spell_id, item_id",
        rs -> new Recipe(rs.getLong("spell_id"), rs.getLong("item_id")), param)) {
      recipeItems.computeIfAbsent(r.spellId(), k -> new ArrayList<>()).add(r.itemId());
    }
    record Reagent(long spellId, CraftReagent reagent) {}
    Map<Long, List<CraftReagent>> reagents = new LinkedHashMap<>();
    for (Reagent r : Sql.list(c, "SELECT r.spell_id, r.item_id, r.count, i.name, i.quality, i.icon_id"
            + " FROM craft_reagent r LEFT JOIN item i ON i.id = r.item_id"
            + " WHERE r.spell_id IN (SELECT c.spell_id FROM craft c WHERE " + where + ") ORDER BY r.rowid",
        rs -> new Reagent(rs.getLong("spell_id"), new CraftReagent(rs.getLong("item_id"), rs.getInt("count"),
            rs.getString("name"), Sql.integer(rs, "quality"), Sql.longValue(rs, "icon_id"))),
        param)) {
      reagents.computeIfAbsent(r.spellId(), k -> new ArrayList<>()).add(r.reagent());
    }
    return Sql.list(c, "SELECT c.spell_id, c.spell_name, c.item_id, i.name AS item_name,"
            + " i.quality AS item_quality, i.icon_id AS item_icon_id, c.quantity_min, c.quantity_max,"
            + " c.skill_line_id, c.skill_name, c.min_skill, c.trivial_low, c.trivial_high, c.recipe_item_id,"
            + " ri.name AS recipe_item_name"
            + " FROM craft c LEFT JOIN item i ON i.id = c.item_id LEFT JOIN item ri ON ri.id = c.recipe_item_id"
            + " WHERE " + where
            + " ORDER BY c.skill_name IS NULL, c.skill_name COLLATE NOCASE, c.spell_name COLLATE NOCASE, c.spell_id",
        rs -> new Craft(rs.getLong("spell_id"), rs.getString("spell_name"), rs.getLong("item_id"),
            rs.getString("item_name"), Sql.integer(rs, "item_quality"), Sql.longValue(rs, "item_icon_id"),
            rs.getInt("quantity_min"), rs.getInt("quantity_max"), Sql.longValue(rs, "skill_line_id"),
            rs.getString("skill_name"), Sql.integer(rs, "min_skill"), Sql.integer(rs, "trivial_low"),
            Sql.integer(rs, "trivial_high"), Sql.longValue(rs, "recipe_item_id"), rs.getString("recipe_item_name"),
            recipeItems.getOrDefault(rs.getLong("spell_id"), List.of()),
            reagents.getOrDefault(rs.getLong("spell_id"), List.of())),
        param);
  }

  /**
   * The client data of item {@code itemId} with its tooltip lines in {@code ordinal} order (set pieces naming
   * their items), or {@link ItemCard#none()} when the item has none.
   */
  private static ItemCard card(Connection c, long itemId) throws SQLException {
    Optional<ItemCard> head = Sql.one(c, """
        SELECT quality, icon_id, inventory_type, bonding, sell_price FROM item
        WHERE id = ? AND has_client_data = 1""",
        rs -> new ItemCard(Sql.integer(rs, "quality"), List.of(), Sql.longValue(rs, "icon_id"),
            Sql.integer(rs, "inventory_type"), Sql.integer(rs, "bonding"), Sql.longValue(rs, "sell_price")),
        itemId);
    if (head.isEmpty()) {
      return ItemCard.none();
    }
    // a set piece line names its item: the piece of the item's set with that name
    boolean hasEvidence = Sql.hasTable(c, "spell_text_evidence");
    String evidenceColumns = hasEvidence ? "e.status, e.spell_id, e.raw_text, e.unresolved, e.source, e.product, e.build, e.table_sha256"
        : "NULL AS status, NULL AS spell_id, NULL AS raw_text, NULL AS unresolved, NULL AS source, NULL AS product, NULL AS build, NULL AS table_sha256";
    List<TooltipLine> lines = Sql.list(c, """
        SELECT l.kind, l.text, l.colour, %s,
               CASE WHEN l.kind = 'set_item' THEN
                 (SELECT si.item_id FROM item_set_item si JOIN item p ON p.id = si.item_id
                  WHERE si.set_id = it.item_set_id AND p.name = l.text ORDER BY si.item_id LIMIT 1)
               END AS piece_id
        FROM item_tooltip_line l JOIN item it ON it.id = l.item_id %s
        WHERE l.item_id = ? ORDER BY l.ordinal""".formatted(evidenceColumns, hasEvidence
            ? "LEFT JOIN spell_text_evidence e ON e.owner_kind = 'tooltip' AND e.owner_id=l.item_id AND e.ordinal=l.ordinal" : ""),
        rs -> new TooltipLine(rs.getString("kind"), rs.getString("text"), rs.getString("colour"),
            Sql.longValue(rs, "piece_id"), new dev.wowforever.app.model.SpellTextEvidence(
                rs.getString("status") == null ? "UNKNOWN" : rs.getString("status"), Sql.longValue(rs,"spell_id"),
                rs.getString("raw_text"), rs.getString("unresolved") == null || rs.getString("unresolved").isEmpty()
                    ? List.of() : List.of(rs.getString("unresolved").split("\\n")),
                rs.getString("source"),rs.getString("product"),rs.getString("build"),rs.getString("table_sha256"))), itemId);
    ItemCard h = head.get();
    return new ItemCard(h.quality(), lines, h.iconId(), h.inventoryType(), h.bonding(), h.sellPrice());
  }

  /**
   * Runs {@code sql} (selecting {@code ref_id} and a nullable {@code name}, {@code quality} and {@code icon_id})
   * into sources ordered by name.
   */
  private static List<ItemSource> sources(Connection c, String kind, EntityKind refKind, String sql, long itemId)
      throws SQLException {
    return Sql.list(c, sql + " ORDER BY name IS NULL, name COLLATE NOCASE, ref_id",
        rs -> new ItemSource(kind, new EntityRef(refKind, rs.getLong("ref_id")), rs.getString("name"),
            Sql.integer(rs, "quality"), Sql.longValue(rs, "icon_id"),
            (ItemSource.OBJECT.equals(kind) || ItemSource.VENDOR.equals(kind)) && rs.getBoolean("observed_in_game")), itemId);
  }

  private static ItemSummary summary(ResultSet rs) throws SQLException {
    return new ItemSummary(rs.getLong("id"), rs.getString("name"), Sql.integer(rs, "item_level"),
        Sql.integer(rs, "required_level"), rs.getString("class_name"), rs.getString("subclass_name"),
        rs.getInt("source_count"), Sql.integer(rs, "quality"), Sql.longValue(rs, "icon_id"));
  }
}
