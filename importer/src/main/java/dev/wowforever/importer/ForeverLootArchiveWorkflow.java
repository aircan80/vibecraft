package dev.wowforever.importer;

import dev.wowforever.importer.archive.SourceArchive;
import dev.wowforever.lua.*;
import dev.wowforever.lua.ast.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.zip.*;

/** Narrow, archive-backed reported loot overlay. Never regenerates client or Classic facts. */
public final class ForeverLootArchiveWorkflow {
  public static final String VERSION="1.2.5-131026-16001";
  public static final String SHA256="ee8a6d68eb14c1ac38372485f03d9e44921e3b7feb1d5728e85db90fc97bc090";
  public static final String PAGE="https://www.curseforge.com/wow/addons/atlasloot-forever/files/9152045";
  public static final String ZIP="AtlasLoot Classic Forever "+VERSION+".zip";
  public static final String DATA="AtlasLootClassic_DungeonsAndRaids/data.lua";
  public static final String LICENSE="AtlasLootClassic/LICENSE";
  public static final String SOURCE="atlasloot-forever";
  public static final String TRANSFORM="forever-loot-v1";
  public static final String DIFFICULTY="NORMAL";
  // Explicitly reviewed source keys, never a fuzzy name or order-based NPC/encounter alias.
  static final Map<String,Integer> MAPS=Map.of("TheHallOfThanes",3065,"TheRuinsOfLordaeron",2999,
      "ExcavationSiteWetlands",2998,"TheCityOfDalaran",2959);
  public static final String CONTINUED_SOURCE="atlasloot-continued-forever";
  public static final String CONTINUED_SHA256="8eaee376a19b26da4a50f4f7f7304f9f41b3eadfd0bd38c4fd98f67495fa82bd";
  static final Provider SLICCER=new Provider(SOURCE,VERSION,SHA256,PAGE,ZIP,DATA,LICENSE,MAPS);
  static final Provider CONTINUED=new Provider(CONTINUED_SOURCE,"12488",CONTINUED_SHA256,"","AtlasLootClassic-Continued_12488.zip",
      "AtlasLootContinued_DungeonsAndRaids/data-forever.lua","AtlasLootContinued/LICENSE",
      Map.of("TheHallOfThanes",3065,"RuinsOfLordaeron",2999,"ExcavationSiteWetlands",2998,"CityOfDalaran",2959));
  record Provider(String source,String version,String sha,String page,String zip,String data,String license,Map<String,Integer> maps) {}
  record Row(String key,String boss,long item,int slot,int line,Long claimedNpc) {}
  private ForeverLootArchiveWorkflow() {}

  public static void main(String[] args) throws Exception {
    if(args.length==3 && args[0].equals("capture")) System.out.println(ingest(Path.of(args[1]),Path.of(args[2])));
    else if(args.length==3 && args[0].equals("project")) { project(Path.of(args[1]),args[2]);System.out.println(args[2]); }
    else throw new IllegalArgumentException("Usage: ForeverLootArchiveWorkflow capture CANDIDATE_DATABASE ZIP | project CANDIDATE_DATABASE SNAPSHOT_ID");
  }

  public static String ingest(Path database,Path zip) throws Exception {
    existing(database);
    byte[] original=Files.readAllBytes(zip);
    Provider provider=providerForHash(hash(original));
    return capture(database,original,provider.sha,provider);
  }

  // Package access permits small independent archive fixtures without weakening the public release pin.
  static String capture(Path database,byte[] original,String expectedHash) throws Exception {
    return capture(database,original,expectedHash,SLICCER);
  }
  static String capture(Path database,byte[] original,String expectedHash,Provider provider) throws Exception {
    existing(database);
    if(!hash(original).equals(expectedHash)) throw new IllegalArgumentException("ZIP hash mismatch");
    Map<String,byte[]> entries=unzip(original);
    byte[] data=require(entries,provider.data),license=require(entries,provider.license);
    if(!new String(license,StandardCharsets.UTF_8).contains("Version 2, June 1991"))
      throw new IllegalArgumentException("AtlasLoot GPLv2 license missing or incompatible");
    String manifest="source="+provider.source+"\nversion="+provider.version+"\nsha256="+expectedHash+"\npage="+provider.page
        +"\ndata="+provider.data+"\nlicense="+provider.license+"\ndata.sha256="+hash(data)+"\nlicense.sha256="+hash(license)+"\n";
    List<SourceArchive.Artifact> artifacts=List.of(new SourceArchive.Artifact("release/"+provider.zip,provider.page,original),
        new SourceArchive.Artifact(provider.data,provider.page,data),new SourceArchive.Artifact(provider.license,provider.page,license),
        new SourceArchive.Artifact("release/manifest.properties",provider.page,manifest.getBytes(StandardCharsets.UTF_8)));
    try(var channel=FileChannel.open(lockPath(database),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.tryLock()) {
      if(lock==null) throw new IOException("Another import is using "+database);
      try(var c=connect(database)) {
        c.setAutoCommit(false);ArchiveWorkflow.initialize(c);
        String snapshot=SourceArchive.capture(c,new SourceArchive.Snapshot(provider.source,provider.version,"forever","1.60.1","enUS"),artifacts);
        c.commit(); // original acquisition survives any later record extraction/projection failure
        dev.wowforever.importer.archive.records.SourceRecords.capture(c,snapshot,artifacts.stream()
            .map(a -> new dev.wowforever.importer.archive.records.SourceRecords.SourceInput(a.path(),a.bytes())).toList());
        c.commit();return snapshot;
      }
    }
  }

  public static void project(Path database,String snapshot) throws Exception {
    project(database,snapshot,null);
  }

  static void project(Path database,String snapshot,String expectedHash) throws Exception {
    database=existing(database);
    try(var channel=FileChannel.open(lockPath(database),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.tryLock()) {
      if(lock==null)throw new IOException("Another import is using "+database);
      Path candidate=Files.createTempFile(database.getParent(),".forever-loot-",".sqlite");
      try {
        try(var c=connect(database);var p=c.prepareStatement("VACUUM INTO ?")) {
          Files.delete(candidate);p.setString(1,candidate.toString());p.execute();
        }
        try(var c=connect(candidate)) {
          c.setAutoCommit(false);ArchiveWorkflow.initialize(c);
          var metadata=SourceArchive.snapshot(c,snapshot).metadata();
          Provider provider=providerForSource(metadata.source());
          if(!provider.version.equals(metadata.version()) || !"forever".equals(metadata.product()))
            throw new IllegalArgumentException("Not a compatible AtlasLoot Forever archive");
          var artifacts=SourceArchive.artifacts(c,snapshot);
          Map<String,byte[]> files=new HashMap<>();for(var a:artifacts)files.put(a.path(),a.bytes());
          if(files.size()!=4)throw new IllegalArgumentException("Expected original ZIP, data, license and manifest");
          var manifest=new Properties();manifest.load(new StringReader(new String(require(files,"release/manifest.properties"),StandardCharsets.UTF_8)));
          byte[] zip=require(files,"release/"+provider.zip);String zipHash=hash(zip);
          if(!zipHash.equals(expectedHash==null?provider.sha:expectedHash) || !zipHash.equals(manifest.getProperty("sha256")))throw new IllegalArgumentException("Archived ZIP hash mismatch");
          if(!provider.source.equals(manifest.getProperty("source")) || !provider.version.equals(manifest.getProperty("version"))
              || !provider.page.equals(manifest.getProperty("page")) || !provider.data.equals(manifest.getProperty("data"))
              || !provider.license.equals(manifest.getProperty("license")))throw new IllegalArgumentException("Manifest identity mismatch");
          var entries=unzip(zip);
          for(String path:List.of(provider.data,provider.license)) {
            if(!Arrays.equals(require(files,path),require(entries,path)))throw new IllegalArgumentException("Archive entry differs from original ZIP: "+path);
            String key=path.equals(provider.data)?"data.sha256":"license.sha256";
            if(!hash(require(files,path)).equals(manifest.getProperty(key)))throw new IllegalArgumentException("Manifest entry hash mismatch");
          }
          if(!new String(require(files,provider.license),StandardCharsets.UTF_8).contains("Version 2, June 1991"))throw new IllegalArgumentException("Invalid GPLv2 license");
          apply(c,snapshot,provider,rows(require(files,provider.data),provider));
          AdditiveProjection.record(c,Map.of("forever-loot-"+provider.source,snapshot),null,"forever_loot",TRANSFORM,
              "Reported AtlasLoot Forever loot only; exact-name encounter mapping; unmatched source lists retained; core data preserved");
          try(var s=c.createStatement()){s.executeUpdate("INSERT OR REPLACE INTO meta SELECT 'forever_loot_snapshot_id',group_concat(snapshot_id,',') FROM (SELECT DISTINCT snapshot_id FROM atlasloot_forever_fact ORDER BY snapshot_id)");}
          c.commit();
          try(var s=c.createStatement();var r=s.executeQuery("PRAGMA quick_check")){if(!r.next() || !"ok".equals(r.getString(1)))throw new SQLException("Candidate integrity failed");}
          try(var s=c.createStatement();var r=s.executeQuery("PRAGMA foreign_key_check")){if(r.next())throw new SQLException("Candidate foreign keys failed");}
        }
        Files.move(candidate,database,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
      } finally {Files.deleteIfExists(candidate);}
    }
  }

  static void apply(Connection c,String snapshot,Provider provider,List<Row> rows) throws SQLException {
    try(var s=c.createStatement()) {
      s.execute("CREATE TABLE IF NOT EXISTS atlasloot_forever_fact(boss_id INTEGER NOT NULL,item_id INTEGER NOT NULL,difficulty TEXT NOT NULL,snapshot_id TEXT NOT NULL,source_path TEXT NOT NULL,source_line INTEGER NOT NULL,source_instance_key TEXT NOT NULL,source_boss_name TEXT NOT NULL,provider TEXT NOT NULL,claimed_npc_id INTEGER,source_slot INTEGER NOT NULL,state TEXT NOT NULL CHECK(state='reported'),PRIMARY KEY(boss_id,item_id,difficulty,snapshot_id))");
    }
    Map<String,Long> bosses=new HashMap<>();
    for(var mapping:provider.maps.entrySet()) {
      long instance;
      try(var p=c.prepareStatement("SELECT id FROM instance WHERE client_map_id=? AND source='client'")) {
        p.setInt(1,mapping.getValue());try(var r=p.executeQuery()) {
          if(!r.next())throw new IllegalArgumentException("Missing client instance "+mapping.getValue());instance=r.getLong(1);
          if(r.next())throw new IllegalArgumentException("Ambiguous client instance "+mapping.getValue());
        }
      }
      var names=rows.stream().filter(r -> r.key.equals(mapping.getKey())).map(Row::boss).distinct().toList();
      for(String name:names) {
        Long boss=null;
        try(var p=c.prepareStatement("SELECT id FROM boss WHERE instance_id=? AND name=?")) {
          p.setLong(1,instance);p.setString(2,name);try(var r=p.executeQuery()) {
            if(r.next()){boss=r.getLong(1);if(r.next())throw new IllegalArgumentException("Ambiguous exact boss name: "+name);}
          }
        }
        if(boss==null) {
          boss=-Long.parseLong(ArchiveWorkflow.hash(instance+"\n"+name).substring(0,15),16);
          try(var p=c.prepareStatement("INSERT INTO boss(id,instance_id,name,npc_id,is_extra_list,sort_order) VALUES (?,?,?,NULL,1,?)")) {
            p.setLong(1,boss);p.setLong(2,instance);p.setString(3,name);p.setInt(4,10000+Math.floorMod(name.hashCode(),10000000));p.executeUpdate();
          }
        }
        bosses.put(mapping.getKey()+"\n"+name,boss);
      }
      if(!names.isEmpty())try(var p=c.prepareStatement("UPDATE instance SET loot_state='reported' WHERE id=?")){p.setLong(1,instance);p.executeUpdate();}
    }
    for(Row row:rows) {
      long boss=bosses.get(row.key+"\n"+row.boss);
      try(var p=c.prepareStatement("SELECT 1 FROM item WHERE id=?")){p.setLong(1,row.item);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Unknown loot item "+row.item);}}
      boolean existing;
      try(var p=c.prepareStatement("SELECT 1 FROM boss_loot WHERE boss_id=? AND item_id=? AND difficulty=?")) {
        p.setLong(1,boss);p.setLong(2,row.item);p.setString(3,DIFFICULTY);try(var r=p.executeQuery()){existing=r.next();}
      }
      if(existing) {
        try(var p=c.prepareStatement("SELECT 1 FROM atlasloot_forever_fact WHERE boss_id=? AND item_id=? AND difficulty=?")) {
          p.setLong(1,boss);p.setLong(2,row.item);p.setString(3,DIFFICULTY);try(var r=p.executeQuery()) {
            if(!r.next())throw new IllegalArgumentException("Existing non-overlay loot differs; explicit overlay migration required");
          }
        }
      } else try(var p=c.prepareStatement("INSERT INTO boss_loot(boss_id,item_id,difficulty,slot,drop_pct,pct_source,disagrees,source_count) VALUES (?,?,?,?,NULL,NULL,0,0)")) {
        p.setLong(1,boss);p.setLong(2,row.item);p.setString(3,DIFFICULTY);p.setInt(4,row.slot);p.executeUpdate();
      }
      try(var p=c.prepareStatement("INSERT OR IGNORE INTO atlasloot_forever_fact VALUES (?,?,?,?,?,?,?,?,?,?,?, 'reported')")) {
        p.setLong(1,boss);p.setLong(2,row.item);p.setString(3,DIFFICULTY);p.setString(4,snapshot);p.setString(5,provider.data);p.setInt(6,row.line);p.setString(7,row.key);p.setString(8,row.boss);p.setString(9,provider.source);
        if(row.claimedNpc==null)p.setNull(10,Types.INTEGER);else p.setLong(10,row.claimedNpc);
        p.setInt(11,row.slot);p.executeUpdate();
      }
      // Canonical ordering is deterministic across replay order; each provider's order remains in its facts.
      try(var p=c.prepareStatement("UPDATE boss_loot SET slot=(SELECT source_slot FROM atlasloot_forever_fact WHERE boss_id=? AND item_id=? AND difficulty=? ORDER BY CASE provider WHEN 'atlasloot-forever' THEN 0 ELSE 1 END,snapshot_id LIMIT 1) WHERE boss_id=? AND item_id=? AND difficulty=?")) {
        p.setLong(1,boss);p.setLong(2,row.item);p.setString(3,DIFFICULTY);p.setLong(4,boss);p.setLong(5,row.item);p.setString(6,DIFFICULTY);p.executeUpdate();
      }
    }
  }

  static List<Row> rows(byte[] bytes) {return rows(bytes,SLICCER);}
  static List<Row> rows(byte[] bytes,Provider provider) {
    var chunk=LuaChunk.parse(selectedTables(new String(bytes,StandardCharsets.UTF_8),provider),provider.data);
    List<Row> rows=new ArrayList<>();Set<String> seen=new HashSet<>();
    for(var assignment:chunk.assignments()) {
      if(!(assignment.target() instanceof Expr.Index index) || !(index.target() instanceof Expr.Name n) || !n.name().equals("data") || !(index.key() instanceof Expr.Literal l) || !(l.value() instanceof LuaString key) || !provider.maps.containsKey(key.value()))continue;
      if(!seen.add(key.value()))throw new IllegalArgumentException("Duplicate Forever instance "+key.value());
      var instance=table(assignment.value());
      for(var bossEntry:table(field(instance,"items")).entries()) {
        if(!(bossEntry instanceof TableEntry.Positional pos))throw new IllegalArgumentException("Expected ordered boss list");
        var boss=table(pos.value());String name=text(field(boss,"name"));
        Expr normal=null;
        for(var entry:boss.entries())if(entry instanceof TableEntry.Keyed k && k.key() instanceof Expr.Name diff && diff.name().equals("NORMAL_DIFF"))normal=k.value();
        if(normal==null)throw new IllegalArgumentException("Missing normal loot for "+name);
        for(var loot:table(normal).entries()) {
          if(!(loot instanceof TableEntry.Positional posLoot))throw new IllegalArgumentException("Expected loot row");
          var values=table(posLoot.value());
          if(values.entries().size()!=2 || !(values.entries().get(0) instanceof TableEntry.Positional slot) || !(values.entries().get(1) instanceof TableEntry.Positional item))throw new IllegalArgumentException("Unsupported loot row");
          long itemId=number(item.value()),order=number(slot.value());
          if(itemId<=0 || order<=0 || order>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid loot ID/order");
          Expr npc=optionalField(boss,"npcID");
          rows.add(new Row(key.value(),name,itemId,(int)order,values.line(),npc==null?null:number(npc)));
        }
      }
    }
    if(!seen.equals(provider.maps.keySet()) || rows.isEmpty())throw new IllegalArgumentException("All four reviewed Forever instances with loot required");
    Set<String> unique=new HashSet<>();for(var row:rows)if(!unique.add(row.key+"\n"+row.boss+"\n"+row.item))throw new IllegalArgumentException("Duplicate boss item");
    return List.copyOf(rows);
  }
  /** Parse only explicitly reviewed static assignments; unrelated addon runtime code is not evaluated.
   * Blanking other characters preserves original module line numbers exactly. */
  private static String selectedTables(String source,Provider provider) {
    char[] selected=source.toCharArray();
    for(int i=0;i<selected.length;i++)if(selected[i]!='\n' && selected[i]!='\r')selected[i]=' ';
    var pattern=java.util.regex.Pattern.compile("(?m)^data\\[\"([^\"]+)\"\\]\\s*=\\s*\\{");
    var matcher=pattern.matcher(source);
    while(matcher.find()) {
      if(!provider.maps.containsKey(matcher.group(1)))continue;
      int depth=1,i=matcher.end();
      for(;i<source.length() && depth>0;i++) {
        char ch=source.charAt(i);
        if(ch=='\"' || ch=='\'') {
          char quote=ch;boolean closed=false;
          for(i++;i<source.length();i++) {
            if(source.charAt(i)=='\\'){i++;continue;}
            if(source.charAt(i)==quote){closed=true;break;}
          }
          if(!closed)throw new IllegalArgumentException("Unterminated Lua string");
        } else if(ch=='-' && i+1<source.length() && source.charAt(i+1)=='-') {
          if(source.startsWith("--[[",i)) {
            int end=source.indexOf("]]",i+4);if(end<0)throw new IllegalArgumentException("Unterminated Lua comment");i=end+1;
          } else {int end=source.indexOf('\n',i+2);i=end<0?source.length():end;}
        } else if(ch=='{')depth++;else if(ch=='}')depth--;
      }
      if(depth!=0)throw new IllegalArgumentException("Unterminated reviewed Lua table");
      source.getChars(matcher.start(),i,selected,matcher.start());
    }
    return new String(selected);
  }
  private static Provider providerForHash(String hash) {
    for(var p:List.of(SLICCER,CONTINUED))if(p.sha.equals(hash))return p;
    throw new IllegalArgumentException("AtlasLoot ZIP SHA-256 mismatch; expected a reviewed pinned release");
  }
  private static Provider providerForSource(String source) {
    for(var p:List.of(SLICCER,CONTINUED))if(p.source.equals(source))return p;
    throw new IllegalArgumentException("Not a compatible AtlasLoot Forever archive");
  }
  private static Expr optionalField(Expr.TableCtor table,String name) {
    return table.entries().stream().filter(e -> e instanceof TableEntry.Named n && n.name().equals(name)).map(e -> ((TableEntry.Named)e).value()).findFirst().orElse(null);
  }
  private static Expr field(Expr.TableCtor table,String name) {
    return table.entries().stream().filter(e -> e instanceof TableEntry.Named n && n.name().equals(name)).map(e -> ((TableEntry.Named)e).value()).findFirst().orElseThrow(() -> new IllegalArgumentException("Missing field "+name));
  }
  private static Expr.TableCtor table(Expr e){if(e instanceof Expr.TableCtor t)return t;throw new IllegalArgumentException("Expected static table");}
  private static String text(Expr e){if(e instanceof Expr.Index i && i.target() instanceof Expr.Name n && n.name().equals("AL"))return text(i.key());if(e instanceof Expr.Literal l && l.value() instanceof LuaString s && !s.value().isBlank())return s.value();throw new IllegalArgumentException("Expected literal name");}
  private static long number(Expr e){if(e instanceof Expr.Literal l && l.value() instanceof LuaNumber n && n.isInteger())return n.asLong();throw new IllegalArgumentException("Expected literal integer");}
  private static Map<String,byte[]> unzip(byte[] bytes) throws IOException {
    Map<String,byte[]> entries=new HashMap<>();long total=0;
    try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry e;while((e=zip.getNextEntry())!=null) {
        String name=e.getName();new SourceArchive.Artifact(name.endsWith("/")?name.substring(0,name.length()-1):name,null,new byte[0]);
        if(e.isDirectory())continue;
        byte[] data=zip.readNBytes(20_000_001);total+=data.length;
        if(data.length>20_000_000 || total>80_000_000)throw new IOException("ZIP exceeds bounded archive size");
        if(entries.put(name,data)!=null)throw new IllegalArgumentException("Duplicate ZIP path "+name);
      }
    }
    return entries;
  }
  private static byte[] require(Map<String,byte[]> files,String path){byte[] bytes=files.get(path);if(bytes==null)throw new IllegalArgumentException("Missing archive entry "+path);return bytes;}
  static String hash(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
  private static Path existing(Path db)throws IOException {db=db.toAbsolutePath().normalize();if(!Files.isRegularFile(db))throw new IOException("Existing candidate database required: "+db);return db;}
  private static Path lockPath(Path db){return db.toAbsolutePath().resolveSibling(db.getFileName()+".lock");}
  private static Connection connect(Path db)throws SQLException{return DriverManager.getConnection("jdbc:sqlite:"+db);}
}
