package dev.wowforever.importer.sqlite;

import dev.wowforever.importer.clientdata.*;
import java.sql.*;
import java.util.*;

/** Targeted client item changes; the caller owns the transaction and archive provenance. */
final class ClientItemUpdateWriter {
  private ClientItemUpdateWriter() {}
  static void apply(Connection c, Map<Long,ClientItem> items, Map<Long,ItemValues> values,
      ItemTooltips tooltips, String snapshot, String expectedSource, String previousTable, String historyPrefix) throws SQLException {
    try (var s=c.createStatement()) {
      s.execute("CREATE TABLE IF NOT EXISTS "+previousTable+"(snapshot_id TEXT,item_id INTEGER,field TEXT,value TEXT,PRIMARY KEY(snapshot_id,item_id,field))");
    }
    for (var item:items.values()) {
      if (!expectedSource.equals(item.clientSource()) || !item.hasSparse()) throw new IllegalArgumentException("Unexpected client item source");
      var fields=new LinkedHashMap<String,Object>();
      fields.put("name",item.name());fields.put("item_level",item.itemLevel());fields.put("required_level",item.requiredLevel());
      fields.put("class_id",item.classId());fields.put("subclass_id",item.subclassId());fields.put("quality",item.quality());
      fields.put("inventory_type",item.inventoryType());fields.put("bonding",item.bonding());fields.put("sell_price",item.sellPrice());
      fields.put("buy_price",item.buyPrice());fields.put("max_stack",item.maxStack());fields.put("icon_id",item.iconId());
      fields.put("item_set_id",item.itemSetId());fields.put("description",item.description());fields.put("allowable_class",item.allowableClass());
      fields.put("allowable_race",item.allowableRace());fields.put("required_skill",item.requiredSkill());fields.put("required_skill_rank",item.requiredSkillRank());
      fields.put("has_client_data",1);fields.put("client_source",expectedSource);
      for (var field:fields.entrySet()) {
        try (var previous=c.prepareStatement("INSERT OR IGNORE INTO "+previousTable+" SELECT ?,id,?,CAST("+field.getKey()+" AS TEXT) FROM item WHERE id=?")) {
          previous.setString(1,snapshot);previous.setString(2,field.getKey());previous.setLong(3,item.id());previous.executeUpdate();
        }
        // As in the existing core writer, a missing display/name value does not erase an established value.
        if (field.getValue()==null && Set.of("name","item_level","required_level","class_id","subclass_id").contains(field.getKey()))continue;
        try (var update=c.prepareStatement("UPDATE item SET "+field.getKey()+"=? WHERE id=?")) {
          update.setObject(1,field.getValue());update.setLong(2,item.id());
          if(update.executeUpdate()!=1)throw new SQLException("Client target item missing: "+item.id());
        }
      }
      try(var old=c.prepareStatement("INSERT INTO item_alt_value(item_id,field,source,value) SELECT item_id,field,?,value FROM "+previousTable+" p WHERE snapshot_id=? AND item_id=? AND field='name' AND value IS NOT NULL AND value <> ? AND NOT EXISTS (SELECT 1 FROM item_alt_value a WHERE a.item_id=p.item_id AND a.field=p.field AND a.source=? AND a.value=p.value)")) {
        String source=historyPrefix+snapshot;old.setString(1,source);old.setString(2,snapshot);old.setLong(3,item.id());old.setString(4,item.name());old.setString(5,source);old.executeUpdate();
      }
      for(String table:List.of("item_stat","item_damage","item_armor","item_effect","item_tooltip_line"))
        try(var delete=c.prepareStatement("DELETE FROM "+table+" WHERE item_id=?")){delete.setLong(1,item.id());delete.executeUpdate();}
      ItemValues v=values.getOrDefault(item.id(),new ItemValues(List.of(),null,null));
      try(Batch stats=new Batch(c,"INSERT INTO item_stat VALUES (?,?,?,?)")) {
        int ordinal=0;for(var stat:v.stats())stats.add(item.id(),ordinal++,stat.statId(),stat.value());
      }
      if(v.damage()!=null){var d=v.damage();execute(c,"INSERT INTO item_damage VALUES (?,?,?,?,?,?)",item.id(),d.min(),d.max(),d.school(),d.delayMs()>0?d.delayMs():null,d.dps());}
      if(v.armor()!=null)execute(c,"INSERT INTO item_armor VALUES (?,?,?)",item.id(),v.armor().armor(),v.armor().block());
      try(Batch effects=new Batch(c,"INSERT INTO item_effect VALUES (?,?,?,?,?)");Batch lines=new Batch(c,"INSERT INTO item_tooltip_line VALUES (?,?,?,?,?)")) {
        for(var e:tooltips.effects().getOrDefault(item.id(),List.of()))effects.add(item.id(),e.ordinal(),e.trigger(),e.spellId(),e.text());
        for(var l:tooltips.lines().getOrDefault(item.id(),List.of()))lines.add(item.id(),l.ordinal(),l.kind(),l.text(),l.colour());
      }
      // Search keeps the newly revealed name while unrelated item/quest/drop relationships remain intact.
      execute(c,"UPDATE search_index SET name=(SELECT name FROM item WHERE id=?) WHERE kind='item' AND id=?",item.id(),item.id());
    }
  }
  private static void execute(Connection c,String sql,Object... values)throws SQLException {
    try(var p=c.prepareStatement(sql)){for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);p.executeUpdate();}
  }
}
