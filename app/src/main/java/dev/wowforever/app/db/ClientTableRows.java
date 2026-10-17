package dev.wowforever.app.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads archived native CSV rows without interpreting missing cells as zero. Call on a worker thread. */
public final class ClientTableRows {
  private ClientTableRows() {}
  public record Row(String id, Map<String,String> cells, String sourcePath, long sourceLine) {
    public Row { cells = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(cells)); }
    public String text(String column) { return cells.get(column); }
    public String text(String column, String fallback) { String s=text(column); return s==null||s.isBlank()?fallback:s; }
    public Long number(String column) {
      String s=text(column); if(s==null||s.isBlank()) return null;
      try { return Long.valueOf(s); } catch(NumberFormatException e) { return null; }
    }
  }
  public record Dataset(String snapshotId, String product, String build, String locale) {}
  public static List<Dataset> datasets(Connection c) throws SQLException {
    if(!Sql.hasTable(c,"client_table_dataset")) return List.of();
    return Sql.list(c,"SELECT snapshot_id,product,build,locale FROM client_table_dataset ORDER BY build DESC",
      r->new Dataset(r.getString(1),r.getString(2),r.getString(3),r.getString(4)));
  }
  public static List<Row> read(Connection c,String snapshotId,String table) throws SQLException {
    if(!Sql.hasTable(c,"client_table_row")) return List.of();
    List<Row> rows=new ArrayList<>();
    try(var ps=c.prepareStatement("SELECT r.row_id,r.source_path,r.source_line,j.key,j.value FROM client_table_row r, json_each(r.payload) j WHERE r.snapshot_id=? AND r.table_name=? ORDER BY r.source_line,j.id")) {
      ps.setString(1,snapshotId);ps.setString(2,table);
      try(var rs=ps.executeQuery()) {
        String id=null,path=null;long line=0;Map<String,String> cells=new LinkedHashMap<>();
        while(rs.next()) {
          String next=rs.getString(1);
          if(id!=null&&!id.equals(next)){rows.add(new Row(id,cells,path,line));cells=new LinkedHashMap<>();}
          id=next;path=rs.getString(2);line=rs.getLong(3);cells.put(rs.getString(4),rs.getString(5));
        }
        if(id!=null) rows.add(new Row(id,cells,path,line));
      }
    }
    return List.copyOf(rows);
  }
}
