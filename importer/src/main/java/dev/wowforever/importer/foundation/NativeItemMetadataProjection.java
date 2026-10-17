package dev.wowforever.importer.foundation;

import dev.wowforever.importer.clientdata.MiniJson;
import java.sql.*;
import java.util.*;

/** Opens native-only item IDs without inventing hidden names, stats, quality, or tooltips. */
public final class NativeItemMetadataProjection {
  private NativeItemMetadataProjection() {}
  record Row(String id,Map<String,Object> cells,String payload,String path,int line) {}
  public static int project(Connection c,String snapshot)throws SQLException {
    try(var p=c.prepareStatement("SELECT product,build FROM client_table_dataset WHERE snapshot_id=?")) {
      p.setString(1,snapshot);try(var r=p.executeQuery()) {
        if(!r.next() || !"wow_classic_beta".equals(r.getString(1)) || !r.getString(2).startsWith("1.60."))throw new IllegalArgumentException("Native item metadata requires a Forever client dataset");
      }
    }
    try(var s=c.createStatement()) {
      s.execute("CREATE TABLE IF NOT EXISTS native_item_metadata_fact(item_id INTEGER NOT NULL,snapshot_id TEXT NOT NULL,source_table TEXT NOT NULL,source_path TEXT NOT NULL,source_line INTEGER NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(item_id,snapshot_id,source_table,source_line))");
    }
    List<Row> items=rows(c,snapshot,"Item");if(items.isEmpty())return 0;
    Map<Long,Row> sparse=new HashMap<>();for(var row:rows(c,snapshot,"ItemSparse")){Long id=number(row.id);if(id!=null)sparse.put(id,row);}
    Map<Long,Set<Long>> membership=new HashMap<>();
    for(var row:rows(c,snapshot,"ItemSet")) {
      Long set=number(row.id);if(set==null || set<=0)continue;
      for(var cell:row.cells.entrySet())if(cell.getKey().startsWith("ItemID_")) {
        Long item=number(cell.getValue());if(item!=null && item>0)membership.computeIfAbsent(item,k->new TreeSet<>()).add(set);
      }
    }
    int inserted=0;Map<Long,String> insertedNames=new LinkedHashMap<>();
    try(var p=c.prepareStatement("INSERT OR IGNORE INTO item(id,name,class_id,subclass_id,inventory_type,icon_id,item_set_id,has_client_data) VALUES (?,?,?,?,?,?,?,0)")) {
      for(var row:items) {
        Long id=number(row.id);if(id==null || id<=0)throw new IllegalArgumentException("Invalid native Item ID "+row.id);
        Row detail=sparse.get(id);String name=detail==null?null:text(detail.cells.get("Display_lang"));
        Set<Long> sets=membership.getOrDefault(id,Set.of());
        p.setLong(1,id);p.setString(2,name);nullable(p,3,number(row.cells.get("ClassID")));nullable(p,4,number(row.cells.get("SubclassID")));
        nullable(p,5,number(row.cells.get("InventoryType")));nullable(p,6,number(row.cells.get("IconFileDataID")));
        nullable(p,7,sets.size()==1?sets.iterator().next():null);int added=p.executeUpdate();inserted+=added;if(added>0 && name!=null)insertedNames.put(id,name);
      }
    }
    // Retain exact membership, appearance and effect joins as source claims. Canonical existing rows never change.
    Set<Long> nativeIds=new HashSet<>();for(var row:items)nativeIds.add(number(row.id));
    for(var row:items)fact(c,snapshot,number(row.id),"Item",row);
    for(var row:sparse.values())if(nativeIds.contains(number(row.id)))fact(c,snapshot,number(row.id),"ItemSparse",row);
    for(var row:rows(c,snapshot,"ItemSet"))for(var cell:row.cells.entrySet())if(cell.getKey().startsWith("ItemID_")) {
      Long item=number(cell.getValue());if(item!=null && nativeIds.contains(item))fact(c,snapshot,item,"ItemSet",row);
    }
    for(var row:rows(c,snapshot,"ItemModifiedAppearance")) {
      Long item=number(row.cells.get("ItemID"));if(item!=null && nativeIds.contains(item))fact(c,snapshot,item,"ItemModifiedAppearance",row);
    }
    Map<Long,Row> effects=new HashMap<>();for(var row:rows(c,snapshot,"ItemEffect"))effects.put(number(row.id),row);
    for(var row:rows(c,snapshot,"ItemXItemEffect")) {
      Long item=number(row.cells.get("ItemID"));if(item==null || !nativeIds.contains(item))continue;
      fact(c,snapshot,item,"ItemXItemEffect",row);Row effect=effects.get(number(row.cells.get("ItemEffectID")));
      if(effect!=null)fact(c,snapshot,item,"ItemEffect",effect);
    }
    if(hasSearch(c))try(var p=c.prepareStatement("INSERT INTO search_index(kind,id,name) VALUES ('item',?,?)")) {
      for(var entry:insertedNames.entrySet()){p.setLong(1,entry.getKey());p.setString(2,entry.getValue());p.addBatch();}p.executeBatch();
    }
    return inserted;
  }
  private static void fact(Connection c,String snapshot,long item,String table,Row row)throws SQLException {
    try(var p=c.prepareStatement("INSERT OR IGNORE INTO native_item_metadata_fact VALUES (?,?,?,?,?,?)")) {
      p.setLong(1,item);p.setString(2,snapshot);p.setString(3,table);p.setString(4,row.path);p.setInt(5,row.line);p.setString(6,row.payload);p.executeUpdate();
    }
  }
  private static boolean hasSearch(Connection c)throws SQLException {try(var s=c.createStatement();var r=s.executeQuery("SELECT 1 FROM sqlite_master WHERE name='search_index'")){return r.next();}}
  private static void nullable(PreparedStatement p,int column,Long value)throws SQLException {if(value==null)p.setNull(column,Types.INTEGER);else p.setLong(column,value);}
  private static String text(Object value){return value instanceof String s && !s.isBlank()?s:null;}
  private static Long number(Object value) {
    if(value==null || value.toString().isBlank())return null;
    try{return Long.valueOf(value.toString());}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid integral native field: "+value,e);}
  }
  @SuppressWarnings("unchecked") private static List<Row> rows(Connection c,String snapshot,String table)throws SQLException {
    List<Row> rows=new ArrayList<>();try(var p=c.prepareStatement("SELECT row_id,payload,source_path,source_line FROM client_table_row WHERE snapshot_id=? AND table_name=? ORDER BY row_id")) {
      p.setString(1,snapshot);p.setString(2,table);try(var r=p.executeQuery()){while(r.next())rows.add(new Row(r.getString(1),(Map<String,Object>)MiniJson.parse(r.getString(2)),r.getString(2),r.getString(3),r.getInt(4)));}
    }return rows;
  }
}
