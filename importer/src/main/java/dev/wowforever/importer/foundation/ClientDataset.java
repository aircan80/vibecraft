package dev.wowforever.importer.foundation;

import dev.wowforever.importer.archive.SourceArchive;
import dev.wowforever.importer.clientdata.CsvTable;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Lossless source-native client tables. Dataset identity never implies a newer build or complete coverage. */
public final class ClientDataset {
  private ClientDataset() {}
  public record Table(String name,String path,String sha256,List<String> keys) {
    public Table {
      if(name==null || !name.matches("[A-Za-z][A-Za-z0-9_]*"))throw new IllegalArgumentException("Invalid table name");
      keys=List.copyOf(keys);if(keys.isEmpty() || new HashSet<>(keys).size()!=keys.size())throw new IllegalArgumentException("Unique key columns required");
    }
  }
  public static void initialize(Connection c)throws SQLException {
    try(var s=c.createStatement()) {
      s.execute("CREATE TABLE IF NOT EXISTS client_table_dataset(snapshot_id TEXT PRIMARY KEY,product TEXT NOT NULL,build TEXT NOT NULL,locale TEXT NOT NULL)");
      s.execute("CREATE TABLE IF NOT EXISTS client_table_manifest(snapshot_id TEXT NOT NULL,table_name TEXT NOT NULL,key_columns TEXT NOT NULL,column_names TEXT NOT NULL,row_count INTEGER NOT NULL,source_path TEXT NOT NULL,sha256 TEXT NOT NULL,PRIMARY KEY(snapshot_id,table_name))");
      s.execute("CREATE TABLE IF NOT EXISTS client_table_row(snapshot_id TEXT NOT NULL,table_name TEXT NOT NULL,row_id TEXT NOT NULL,payload TEXT NOT NULL,source_path TEXT NOT NULL,source_line INTEGER NOT NULL,PRIMARY KEY(snapshot_id,table_name,row_id))");
    }
  }
  public static void project(Connection c,String snapshot,String product,String build,String locale,
      List<Table> manifest,List<SourceArchive.Artifact> artifacts)throws Exception {
    initialize(c);
    if(product==null || product.isBlank() || build==null || !build.matches("[0-9]+(?:\\.[0-9]+){3}") || locale==null || locale.isBlank())throw new IllegalArgumentException("Explicit product, four-part build and locale required");
    var files=new HashMap<String,byte[]>();for(var a:artifacts)if(files.put(a.path(),a.bytes())!=null)throw new IllegalArgumentException("Duplicate artifact path");
    try(var p=c.prepareStatement("SELECT product,build,locale FROM client_table_dataset WHERE snapshot_id=?")) {
      p.setString(1,snapshot);try(var r=p.executeQuery()){if(r.next() && (!product.equals(r.getString(1)) || !build.equals(r.getString(2)) || !locale.equals(r.getString(3))))throw new IllegalArgumentException("Dataset metadata differs from prior projection");}
    }
    try(var p=c.prepareStatement("INSERT OR IGNORE INTO client_table_dataset VALUES (?,?,?,?)")){p.setString(1,snapshot);p.setString(2,product);p.setString(3,build);p.setString(4,locale);p.executeUpdate();}
    Set<String> names=new HashSet<>();
    for(var table:manifest) {
      if(!names.add(table.name))throw new IllegalArgumentException("Duplicate manifest table "+table.name);
      byte[] bytes=files.get(table.path);if(bytes==null || !sha256(bytes).equals(table.sha256))throw new IllegalArgumentException("Missing/mismatched archived CSV "+table.path);
      var csv=CsvTable.read(table.path,new StringReader(new String(bytes,StandardCharsets.UTF_8)));
      for(String key:table.keys)if(!csv.hasColumn(key))throw new IllegalArgumentException("Missing key column "+key+" in "+table.name);
      var rowIds=new HashSet<String>();
      try(var p=c.prepareStatement("INSERT OR IGNORE INTO client_table_row VALUES (?,?,?,?,?,?)")) {
        for(var row:csv.rows()) {
          var keys=table.keys.stream().map(row::get).toList();if(keys.stream().anyMatch(String::isBlank))throw new IllegalArgumentException("Blank native row key "+table.name+":"+row.line());
          String id=keys.size()==1?keys.getFirst():json(keys);
          if(!rowIds.add(id))throw new IllegalArgumentException("Duplicate native row key "+table.name+":"+id);
          var cells=new TreeMap<String,String>();for(String column:csv.columns())cells.put(column,row.get(column));
          p.setString(1,snapshot);p.setString(2,table.name);p.setString(3,id);p.setString(4,json(cells));p.setString(5,table.path);p.setInt(6,row.line());p.addBatch();
        }
        p.executeBatch();
      }
      try(var p=c.prepareStatement("INSERT OR IGNORE INTO client_table_manifest VALUES (?,?,?,?,?,?,?)")) {
        p.setString(1,snapshot);p.setString(2,table.name);p.setString(3,json(table.keys));p.setString(4,json(new TreeSet<>(csv.columns())));p.setInt(5,rowIds.size());p.setString(6,table.path);p.setString(7,table.sha256);p.executeUpdate();
      }
      try(var p=c.prepareStatement("SELECT row_count,key_columns,column_names,sha256,(SELECT count(*) FROM client_table_row r WHERE r.snapshot_id=m.snapshot_id AND r.table_name=m.table_name) FROM client_table_manifest m WHERE snapshot_id=? AND table_name=?")) {
        p.setString(1,snapshot);p.setString(2,table.name);try(var r=p.executeQuery()) {
          if(!r.next() || r.getLong(1)!=rowIds.size() || r.getLong(5)!=rowIds.size() || !json(table.keys).equals(r.getString(2)) || !json(new TreeSet<>(csv.columns())).equals(r.getString(3)) || !table.sha256.equals(r.getString(4)))throw new IllegalArgumentException("Existing dataset projection differs from archived CSV "+table.name);
        }
      }
    }
    if(names.isEmpty())throw new IllegalArgumentException("Dataset needs at least one genuine table");
  }
  public static String sha256(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
  /** All CSV cells remain strings, including empty strings, zero and exact decimal spelling. */
  public static String json(Object value) {
    if(value==null)return "null";
    if(value instanceof String s) {
      var b=new StringBuilder("\"");for(int i=0;i<s.length();i++){char ch=s.charAt(i);switch(ch){case '\\'->b.append("\\\\");case '"'->b.append("\\\"");case '\n'->b.append("\\n");case '\r'->b.append("\\r");case '\t'->b.append("\\t");default->{if(ch<32)b.append(String.format("\\u%04x",(int)ch));else b.append(ch);}}}return b.append('"').toString();
    }
    if(value instanceof Map<?,?> m){var join=new StringJoiner(",","{","}");new TreeMap<>(m).forEach((k,v)->join.add(json(k)+":"+json(v)));return join.toString();}
    if(value instanceof Iterable<?> values){var join=new StringJoiner(",","[","]");for(var v:values)join.add(json(v));return join.toString();}
    if(value instanceof Number || value instanceof Boolean)return value.toString();
    throw new IllegalArgumentException("Unsupported JSON type "+value.getClass());
  }
}
