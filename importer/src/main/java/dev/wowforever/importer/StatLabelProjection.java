package dev.wowforever.importer;

import dev.wowforever.importer.archive.SourceArchive;
import dev.wowforever.importer.clientdata.StatNames;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

/** Replaces legacy tooltip label placeholders using a byte-pinned original enum; numeric stats stay unchanged. */
public final class StatLabelProjection {
  private static final String SHA="3d2989626ca5b22d8de77c649607e81c7bb3b2b574fc91b686d6f1720fe25b9f";
  private static final String URL="https://raw.githubusercontent.com/wowdev/WoWDBDefs/master/meta/enums/ItemStatType.dbde";
  private StatLabelProjection() {}
  public static void main(String[] args)throws Exception {
    if(args.length!=2||!Files.isRegularFile(Path.of(args[0])))throw new IllegalArgumentException("Usage: StatLabelProjection EXISTING_CANDIDATE ITEM_STAT_TYPE_ENUM");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+Path.of(args[0]).toAbsolutePath())) {
      c.setAutoCommit(false);
      try {System.out.println("Updated legacy stat labels: "+apply(c,Files.readAllBytes(Path.of(args[1]))));c.commit();}
      catch(Exception e){c.rollback();throw e;}
    }
  }
  /** Caller owns candidate publication, transaction and integrity checks. */
  public static int apply(Connection c,byte[] enumBytes)throws Exception {
    String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(enumBytes));
    if(!SHA.equals(hash))throw new IllegalArgumentException("Unreviewed ItemStatType enum bytes");
    Set<Integer> supported=new HashSet<>();
    for(String line:new String(enumBytes,StandardCharsets.UTF_8).split("\\R")) {
      Matcher id=Pattern.compile("^(\\d+)\\s+").matcher(line);if(id.find())supported.add(Integer.parseInt(id.group(1)));
    }
    ArchiveWorkflow.initialize(c);
    String snapshot=SourceArchive.capture(c,new SourceArchive.Snapshot("wowdbdefs-stat-labels","sha256:"+hash,null,null,null),
        List.of(new SourceArchive.Artifact("ItemStatType.dbde",URL,enumBytes)));
    try(var s=c.createStatement()) {
      s.execute("CREATE TABLE IF NOT EXISTS stat_label_previous(snapshot_id TEXT,item_id INTEGER,ordinal INTEGER,previous_text TEXT,replacement_text TEXT,PRIMARY KEY(snapshot_id,item_id,ordinal))");
    }
    record Change(long item,int ordinal,String before,String after) {}
    List<Change> changes=new ArrayList<>();Pattern placeholder=Pattern.compile("Stat #(\\d+)");
    try(var s=c.createStatement();var r=s.executeQuery("SELECT item_id,ordinal,text FROM item_tooltip_line WHERE text LIKE '%Stat #%'")) {
      while(r.next()) {
        String before=r.getString(3);Matcher match=placeholder.matcher(before);StringBuffer result=new StringBuffer();
        while(match.find()) {
          int id=Integer.parseInt(match.group(1));String name=StatNames.name(id);
          match.appendReplacement(result,Matcher.quoteReplacement(supported.contains(id)&&!name.startsWith("Stat #")?name:match.group()));
        }
        match.appendTail(result);String after=result.toString();
        if(!before.equals(after))changes.add(new Change(r.getLong(1),r.getInt(2),before,after));
      }
    }
    for(var change:changes) {
      try(var p=c.prepareStatement("INSERT OR IGNORE INTO stat_label_previous VALUES(?,?,?,?,?)")) {
        p.setString(1,snapshot);p.setLong(2,change.item());p.setInt(3,change.ordinal());p.setString(4,change.before());p.setString(5,change.after());p.executeUpdate();
      }
      try(var p=c.prepareStatement("UPDATE item_tooltip_line SET text=? WHERE item_id=? AND ordinal=?")) {
        p.setString(1,change.after());p.setLong(2,change.item());p.setInt(3,change.ordinal());p.executeUpdate();
      }
    }
    AdditiveProjection.record(c,Map.of("stat-type-labels",snapshot),"stat-type-labels","stat_type_labels","stat-type-labels-v1",
        "Known legacy stat label placeholders refreshed from archived ItemStatType enum; no numeric values changed");
    return changes.size();
  }
}
