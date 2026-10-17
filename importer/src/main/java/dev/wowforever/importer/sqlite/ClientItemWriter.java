package dev.wowforever.importer.sqlite;

import dev.wowforever.importer.clientdata.ClientItem;
import dev.wowforever.importer.report.ImportReport;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * Merges one Questie item row with its client data (ruling 14) into the values of an {@code item} row, and
 * records the Questie values the client replaced in {@code item_alt_value}.
 *
 * <ul>
 *   <li>With an ItemSparse row ({@link ClientItem#hasSparse()}): the client's name, item level, required level,
 *       class and subclass replace Questie's. A Questie value that differs is written to
 *       {@code item_alt_value(item_id, field, 'questie', value)} and counted as {@code clientdata.alt.<field>}; a
 *       client value that is null keeps Questie's.
 *   <li>Item row only: the class (and subclass) are taken from the Item row only where Questie has none, since
 *       Questie's class may be a deliberate correction; the subclass is filled only when the class agrees.
 *   <li>Every client item sets the icon and the other client columns it has; {@code has_client_data} is 1 iff
 *       there is an ItemSparse row and {@code client_source} names its origin.
 * </ul>
 */
final class ClientItemWriter implements AutoCloseable {
  static final String QUESTIE = "questie";
  static final String ITEM_INSERT = "INSERT INTO item(id, name, item_level, required_level, class_id, subclass_id,"
      + " flags, food_type, ammo_type, start_quest_id, teaches_spell_id, quality, inventory_type, bonding, sell_price,"
      + " buy_price, max_stack, icon_id, item_set_id, description, allowable_class, allowable_race, required_skill,"
      + " required_skill_rank, has_client_data, client_source)"
      + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

  private final Map<Long, ClientItem> client;
  private final ImportReport report;
  private final Batch altValues;

  ClientItemWriter(Connection c, Map<Long, ClientItem> client, ImportReport report) throws SQLException {
    this.client = client;
    this.report = report;
    this.altValues = new Batch(c, "INSERT INTO item_alt_value(item_id, field, source, value) VALUES (?, ?, ?, ?)");
  }

  /** The parameters of {@link #ITEM_INSERT} for one item; the {@code q*} arguments are Questie's values. */
  Object[] row(long id, String qName, Object qItemLevel, Object qRequiredLevel, Object qClass, Object qSubclass,
      Object flags, Object foodType, Object ammoType, Object startQuest, Object teachesSpell) throws SQLException {
    ClientItem ci = client.get(id);
    Object name = qName;
    Object itemLevel = qItemLevel;
    Object requiredLevel = qRequiredLevel;
    Object classId = qClass;
    Object subclassId = qSubclass;
    if (ci != null && ci.hasSparse()) {
      name = pick(id, "name", qName, ci.name());
      itemLevel = pick(id, "item_level", qItemLevel, ci.itemLevel());
      requiredLevel = pick(id, "required_level", qRequiredLevel, ci.requiredLevel());
      classId = pick(id, "class_id", qClass, ci.classId());
      subclassId = pick(id, "subclass_id", qSubclass, ci.subclassId());
    } else if (ci != null) {
      if (qClass == null && ci.classId() != null) {
        classId = ci.classId();
      }
      if (qSubclass == null && sameNumber(classId, ci.classId())) {
        subclassId = ci.subclassId();
      }
    }
    if (ci == null) {
      return new Object[] {id, name, itemLevel, requiredLevel, classId, subclassId, flags, foodType, ammoType,
          startQuest, teachesSpell, null, null, null, null, null, null, null, null, null, null, null, null, null, 0,
          null};
    }
    return new Object[] {id, name, itemLevel, requiredLevel, classId, subclassId, flags, foodType, ammoType,
        startQuest, teachesSpell, ci.quality(), ci.inventoryType(), ci.bonding(), ci.sellPrice(), ci.buyPrice(),
        ci.maxStack(), ci.iconId(), ci.itemSetId(), ci.description(), ci.allowableClass(), ci.allowableRace(),
        ci.requiredSkill(), ci.requiredSkillRank(), ci.hasSparse() ? 1 : 0, ci.hasSparse() ? ci.clientSource() : null};
  }

  /** The client value when it has one, recording a differing Questie value as an alt value. */
  private Object pick(long id, String field, Object questie, Object clientValue) throws SQLException {
    if (clientValue == null) {
      return questie;
    }
    if (questie != null && !same(questie, clientValue)) {
      altValues.add(id, field, QUESTIE, String.valueOf(questie));
      report.count("clientdata.alt." + field, 1);
    }
    return clientValue;
  }

  private static boolean same(Object questie, Object clientValue) {
    if (questie instanceof Number && clientValue instanceof Number) {
      return sameNumber(questie, clientValue);
    }
    if (questie instanceof String q && clientValue instanceof String v) {
      return q.strip().equals(v.strip()); // surrounding whitespace is not a disagreement
    }
    return questie.equals(clientValue);
  }

  /** Numeric equality across Long, Integer and Double; false when either is null or not a number. */
  private static boolean sameNumber(Object a, Object b) {
    if (!(a instanceof Number x && b instanceof Number y)) {
      return false;
    }
    if (x instanceof Double || y instanceof Double) {
      return x.doubleValue() == y.doubleValue();
    }
    return x.longValue() == y.longValue();
  }

  @Override
  public void close() throws SQLException {
    altValues.close();
  }
}
