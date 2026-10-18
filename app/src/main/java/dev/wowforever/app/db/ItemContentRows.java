package dev.wowforever.app.db;

import java.sql.*;
import java.util.*;
import static dev.wowforever.app.db.ClientTableRows.*;

/** Bounded native reads for item catalogs; avoids decoding unrelated wide client columns. */
final class ItemContentRows {
  private ItemContentRows() {}
  static List<Row> read(Connection c,Dataset data,String table,String predicate,List<?> parameters) throws SQLException {
    if(data==null || !Sql.hasTable(c,"client_table_row"))return List.of();
    List<Row> result=new ArrayList<>();
    if(!predicate.contains("j.")) {
      try(var statement=c.prepareStatement("SELECT r.row_id,r.payload,r.source_path,r.source_line FROM client_table_row r WHERE r.snapshot_id=? AND r.table_name=?"+predicate+" ORDER BY r.source_line")) {
        statement.setString(1,data.snapshotId());statement.setString(2,table);
        for(int n=0;n<parameters.size();n++)statement.setObject(n+3,parameters.get(n));
        try(var rows=statement.executeQuery()) {
          while(rows.next())result.add(new Row(rows.getString(1),ClientTableRows.decodePayload(rows.getString(2)),rows.getString(3),rows.getLong(4)));
        }
      }
      return List.copyOf(result);
    }
    String sql="SELECT r.row_id,r.source_path,r.source_line,j.key,j.value FROM client_table_row r,json_each(r.payload) j WHERE r.snapshot_id=? AND r.table_name=?"+predicate+" ORDER BY r.source_line,j.id";
    try(var statement=c.prepareStatement(sql)) {
      statement.setString(1,data.snapshotId());statement.setString(2,table);
      for(int n=0;n<parameters.size();n++)statement.setObject(n+3,parameters.get(n));
      try(var rows=statement.executeQuery()) {
        String id=null,path=null;long line=0;Map<String,String> cells=new LinkedHashMap<>();
        while(rows.next()) {
          String next=rows.getString(1);
          if(id!=null&&!id.equals(next)){result.add(new Row(id,cells,path,line));cells=new LinkedHashMap<>();}
          id=next;path=rows.getString(2);line=rows.getLong(3);cells.put(rows.getString(4),rows.getString(5));
        }
        if(id!=null)result.add(new Row(id,cells,path,line));
      }
    }
    return List.copyOf(result);
  }
  static List<Row> ids(Connection c,Dataset data,String table,Collection<Long> ids) throws SQLException {
    if(ids.isEmpty())return List.of();
    List<Row> result=new ArrayList<>();List<Long> values=new ArrayList<>(ids);
    for(int offset=0;offset<values.size();offset+=500) {
      List<Long> page=values.subList(offset,Math.min(values.size(),offset+500));
      result.addAll(read(c,data,table," AND r.row_id IN ("+String.join(",",Collections.nCopies(page.size(),"?"))+")",page.stream().map(String::valueOf).toList()));
    }
    return result;
  }
}
