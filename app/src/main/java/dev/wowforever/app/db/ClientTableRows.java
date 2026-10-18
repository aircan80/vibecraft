package dev.wowforever.app.db;

import com.google.gson.Strictness;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
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
    // Fetch one native record, not one JDBC row per CSV cell. Sorting the expanded json_each
    // result used minutes and a large SQLite temporary file for wide tables such as ItemSparse.
    // Sort the resulting record references in Java instead of making SQLite sort large payloads.
    try(var ps=c.prepareStatement("SELECT row_id,payload,source_path,source_line FROM client_table_row WHERE snapshot_id=? AND table_name=?")) {
      ps.setString(1,snapshotId);ps.setString(2,table);
      try(var rs=ps.executeQuery()) {
        while(rs.next())rows.add(new Row(rs.getString(1),decodePayload(rs.getString(2)),rs.getString(3),rs.getLong(4)));
      }
    }
    rows.sort(java.util.Comparator.comparingLong(Row::sourceLine));
    return List.copyOf(rows);
  }

  /**
   * Decodes the archived JSON object once, retaining column order and the difference between
   * a missing column, JSON null, an empty cell, and the string "0". Native CSV cells are strings;
   * typed scalar values in older fixtures also follow SQLite json_each's text representation.
   */
  public static Map<String,String> decodePayload(String payload) throws SQLException {
    Map<String,String> cells=new LinkedHashMap<>();
    if(payload==null)throw new SQLException("Native row payload is null");
    try(JsonReader json=new JsonReader(new StringReader(payload))) {
      json.setStrictness(Strictness.STRICT);
      json.beginObject();
      while(json.hasNext()) {
        String key=json.nextName();
        String value=switch(json.peek()) {
          case NULL -> {json.nextNull();yield null;}
          case BOOLEAN -> json.nextBoolean()?"1":"0";
          case STRING -> json.nextString();
          case NUMBER -> sqliteNumber(json.nextString());
          case BEGIN_OBJECT,BEGIN_ARRAY -> JsonParser.parseReader(json).toString();
          default -> throw new SQLException("Unsupported native cell token for "+key);
        };
        cells.put(key,value);
      }
      json.endObject();
      if(json.peek()!=JsonToken.END_DOCUMENT)throw new SQLException("Trailing native row payload");
      return cells;
    }catch(IOException|IllegalStateException|com.google.gson.JsonParseException failure) {
      throw new SQLException("Invalid archived native row JSON",failure);
    }
  }
  private static String sqliteNumber(String value) {
    // SQLite exposes integer JSON numbers as longs and real numbers as doubles. Strings never
    // pass through this conversion: IDs, large numeric cells, and blank CSV cells remain exact.
    if(value.indexOf('.')<0&&value.indexOf('e')<0&&value.indexOf('E')<0) {
      try{return Long.toString(Long.parseLong(value));}catch(NumberFormatException ignored){}
    }
    return Double.toString(Double.parseDouble(value));
  }
}
