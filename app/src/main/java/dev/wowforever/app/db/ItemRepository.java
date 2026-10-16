package dev.wowforever.app.db;

import dev.wowforever.app.model.DropSource;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceLoot;
import dev.wowforever.app.model.ItemCard;
import dev.wowforever.app.model.ItemClassNode;
import dev.wowforever.app.model.ItemDetail;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.model.TooltipLine;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Items: detail pages, class-filtered lists and the class tree. */
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

  public ItemRepository(Database db) {
    this.db = db;
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
              SELECT d.object_id AS ref_id, o.name FROM item_object_drop d
              LEFT JOIN game_object o ON o.id = d.object_id WHERE d.item_id = ?""", id),
          sources(c, ItemSource.CONTAINER, EntityKind.ITEM, """
              SELECT d.container_item_id AS ref_id, ci.name FROM item_container_drop d
              LEFT JOIN item ci ON ci.id = d.container_item_id WHERE d.item_id = ?""", id),
          sources(c, ItemSource.VENDOR, EntityKind.NPC, """
              SELECT v.npc_id AS ref_id, n.name FROM item_vendor v
              LEFT JOIN npc n ON n.id = v.npc_id WHERE v.item_id = ?""", id),
          sources(c, ItemSource.REWARD, EntityKind.QUEST, """
              SELECT r.quest_id AS ref_id, q.name FROM quest_reward_item r
              LEFT JOIN quest q ON q.id = r.quest_id WHERE r.item_id = ?""", id),
          sources(c, ItemSource.RELATED, EntityKind.QUEST, """
              SELECT r.quest_id AS ref_id, q.name FROM item_related_quest r
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

  private static List<DropSource> droppedBy(Connection c, long itemId) throws SQLException {
    return Sql.list(c, """
        SELECT d.npc_id, n.name, n.min_level, n.max_level, n.zone_id, z.name AS zone_name,
               d.drop_pct, d.pct_source, d.origin, d.disagrees, d.source_count
        FROM item_npc_drop d
        LEFT JOIN npc n ON n.id = d.npc_id
        LEFT JOIN zone z ON z.id = n.zone_id
        WHERE d.item_id = ?
        ORDER BY d.drop_pct IS NULL, d.drop_pct DESC, n.name IS NULL, n.name COLLATE NOCASE, d.npc_id""",
        rs -> new DropSource(new EntityRef(EntityKind.NPC, rs.getLong("npc_id")), rs.getString("name"),
            Sql.integer(rs, "min_level"), Sql.integer(rs, "max_level"), rs.getString("zone_name"),
            Sql.longValue(rs, "zone_id"), Sql.doubleValue(rs, "drop_pct"), rs.getString("pct_source"),
            rs.getString("origin"), rs.getInt("disagrees") != 0, rs.getInt("source_count")),
        itemId);
  }

  /**
   * The AtlasLoot loot tables the item is in, by instance name, then boss (main list before extra lists, each
   * in AtlasLoot order), then slot.
   */
  private static List<InstanceLoot> inInstanceLoot(Connection c, long itemId) throws SQLException {
    return Sql.list(c, """
        SELECT i.id AS instance_id, i.name AS instance_name, b.id AS boss_id, b.name AS boss_name, b.npc_id,
               bl.slot, bl.drop_pct, bl.disagrees, bl.source_count
        FROM boss_loot bl
        JOIN boss b ON b.id = bl.boss_id
        JOIN instance i ON i.id = b.instance_id
        WHERE bl.item_id = ?
        ORDER BY i.name IS NULL, i.name COLLATE NOCASE, i.id, b.is_extra_list, b.sort_order, b.id,
                 bl.slot IS NULL, bl.slot""",
        rs -> new InstanceLoot(new EntityRef(EntityKind.INSTANCE, rs.getLong("instance_id")),
            rs.getString("instance_name"), rs.getLong("boss_id"), rs.getString("boss_name"),
            Sql.longValue(rs, "npc_id"), Sql.integer(rs, "slot"), Sql.doubleValue(rs, "drop_pct"),
            rs.getInt("disagrees") != 0, rs.getInt("source_count")),
        itemId);
  }

  /**
   * The client data of item {@code itemId} with its tooltip lines in {@code ordinal} order, or
   * {@link ItemCard#none()} when the item has none.
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
    List<TooltipLine> lines = Sql.list(c,
        "SELECT kind, text, colour FROM item_tooltip_line WHERE item_id = ? ORDER BY ordinal",
        rs -> new TooltipLine(rs.getString("kind"), rs.getString("text"), rs.getString("colour")), itemId);
    ItemCard h = head.get();
    return new ItemCard(h.quality(), lines, h.iconId(), h.inventoryType(), h.bonding(), h.sellPrice());
  }

  /** Runs {@code sql} (selecting {@code ref_id} and a nullable {@code name}) into sources ordered by name. */
  private static List<ItemSource> sources(Connection c, String kind, EntityKind refKind, String sql, long itemId)
      throws SQLException {
    return Sql.list(c, sql + " ORDER BY name IS NULL, name COLLATE NOCASE, ref_id",
        rs -> new ItemSource(kind, new EntityRef(refKind, rs.getLong("ref_id")), rs.getString("name")), itemId);
  }

  private static ItemSummary summary(ResultSet rs) throws SQLException {
    return new ItemSummary(rs.getLong("id"), rs.getString("name"), Sql.integer(rs, "item_level"),
        Sql.integer(rs, "required_level"), rs.getString("class_name"), rs.getString("subclass_name"),
        rs.getInt("source_count"), Sql.integer(rs, "quality"), Sql.longValue(rs, "icon_id"));
  }
}
