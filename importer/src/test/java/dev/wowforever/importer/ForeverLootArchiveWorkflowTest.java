package dev.wowforever.importer;

import dev.wowforever.importer.archive.SourceArchive;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ForeverLootArchiveWorkflowTest {
  @TempDir Path temporary;
  static final String LICENSE="GNU GENERAL PUBLIC LICENSE\nVersion 2, June 1991\n";
  static String source() {
    var s=new StringBuilder();
    for(String key:new TreeSet<>(ForeverLootArchiveWorkflow.MAPS.keySet())) s.append("data[\"").append(key).append("\"]={items={\n")
        .append("{name=\"Exact boss\",[NORMAL_DIFF]={{1,101},{2,102}}},\n")
        .append("{name=\"Unmatched source name\",[NORMAL_DIFF]={{1,103}}}}}\n");
    return s.toString();
  }
  static byte[] zip(String source,String license,boolean unsafe) throws Exception {
    var bytes=new ByteArrayOutputStream();
    try(var z=new ZipOutputStream(bytes)) {
      for(var e:Map.of(ForeverLootArchiveWorkflow.DATA,source,ForeverLootArchiveWorkflow.LICENSE,license).entrySet()) {
        z.putNextEntry(new ZipEntry(e.getKey()));z.write(e.getValue().getBytes(StandardCharsets.UTF_8));z.closeEntry();
      }
      if(unsafe){z.putNextEntry(new ZipEntry("../outside"));z.write(1);z.closeEntry();}
    }
    return bytes.toByteArray();
  }
  Path database() throws Exception {
    Path db=temporary.resolve("candidate.sqlite");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.createStatement()) {
      ArchiveWorkflow.initialize(c);
      s.execute("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)");
      s.execute("INSERT INTO meta VALUES ('build_id','original'),('source_ingestion_id','old')");
      s.execute("INSERT INTO source_ingestion VALUES ('old','before')");
      s.execute("INSERT INTO source_ingestion_input VALUES ('old','legacy-app','old-source')");
      s.execute("CREATE TABLE instance(id INTEGER PRIMARY KEY,client_map_id INTEGER,source TEXT,loot_state TEXT)");
      s.execute("CREATE TABLE boss(id INTEGER PRIMARY KEY,instance_id INTEGER,name TEXT,npc_id INTEGER,is_extra_list INTEGER,sort_order INTEGER)");
      s.execute("CREATE TABLE item(id INTEGER PRIMARY KEY,stats TEXT)");
      s.execute("INSERT INTO item VALUES (101,'preserved-stat'),(102,'other-stat'),(103,'unmatched-stat')");
      s.execute("CREATE TABLE boss_loot(boss_id INTEGER,item_id INTEGER,difficulty TEXT,slot INTEGER,drop_pct REAL,pct_source TEXT,disagrees INTEGER,source_count INTEGER,PRIMARY KEY(boss_id,item_id,difficulty))");
      s.execute("CREATE TABLE ui_map(id INTEGER PRIMARY KEY,name TEXT)");s.execute("INSERT INTO ui_map VALUES (947,'preserved-map')");
      for(int map:ForeverLootArchiveWorkflow.MAPS.values()) {
        s.execute("INSERT INTO instance VALUES ("+map+","+map+",'client','none')");
        s.execute("INSERT INTO boss VALUES ("+map+","+map+",'Exact boss',NULL,0,1)");
      }
      s.execute("INSERT INTO instance VALUES (1,NULL,'atlasloot','atlasloot')");
      s.execute("INSERT INTO boss VALUES (1,1,'Old boss',22,0,1)");
      s.execute("INSERT INTO boss_loot VALUES (1,101,'NORMAL',1,33.3,'vmangos',1,2)");
    }
    return db;
  }
  static long count(Connection c,String sql) throws Exception {try(var s=c.createStatement();var r=s.executeQuery(sql)){return r.getLong(1);}}
  @Test void capturesOriginalAndLicenseThenReplaysOfflineWithoutChangingCoreOrLosingHistory() throws Exception {
    Path db=database();byte[] zip=zip(source(),LICENSE,false);String hash=ForeverLootArchiveWorkflow.hash(zip);
    String snapshot=ForeverLootArchiveWorkflow.capture(db,zip,hash);
    assertThat(ForeverLootArchiveWorkflow.capture(db,zip,hash)).isEqualTo(snapshot);
    ForeverLootArchiveWorkflow.project(db,snapshot,hash);
    String build;
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db)) {
      assertThat(SourceArchive.artifacts(c,snapshot)).hasSize(4);
      assertThat(count(c,"SELECT count(*) FROM boss_loot")).isEqualTo(13);
      assertThat(count(c,"SELECT count(*) FROM atlasloot_forever_fact WHERE state='reported'")).isEqualTo(12);
      assertThat(count(c,"SELECT count(*) FROM boss WHERE is_extra_list=1 AND npc_id IS NULL")).isEqualTo(4);
      assertThat(count(c,"SELECT count(*) FROM instance WHERE loot_state='reported'")).isEqualTo(4);
      assertThat(count(c,"SELECT count(*) FROM boss_loot WHERE boss_id=1 AND drop_pct=33.3 AND pct_source='vmangos' AND disagrees=1 AND source_count=2")).isEqualTo(1);
      assertThat(count(c,"SELECT count(*) FROM item WHERE id=101 AND stats='preserved-stat'")).isEqualTo(1);
      assertThat(count(c,"SELECT count(*) FROM ui_map WHERE id=947 AND name='preserved-map'")).isEqualTo(1);
      assertThat(count(c,"SELECT count(*) FROM source_ingestion_input WHERE role='legacy-app' AND snapshot_id='old-source'")).isEqualTo(2);
      build=AdditiveProjection.meta(c,"build_id");assertThat(build).isNotEqualTo("original");
    }
    ForeverLootArchiveWorkflow.project(db,snapshot,hash);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db)) {
      assertThat(count(c,"SELECT count(*) FROM boss_loot")).isEqualTo(13);
      assertThat(count(c,"SELECT count(*) FROM boss WHERE is_extra_list=1")).isEqualTo(4);
      assertThat(AdditiveProjection.meta(c,"build_id")).isEqualTo(build);
      assertThat(count(c,"SELECT count(*) FROM source_projection")).isEqualTo(2);
    }
  }
  @Test void failedProjectionPreservesOriginalAndDurableAcquisition() throws Exception {
    Path db=database();byte[] zip=zip(source().replace("103","999"),LICENSE,false);String hash=ForeverLootArchiveWorkflow.hash(zip);
    String snapshot=ForeverLootArchiveWorkflow.capture(db,zip,hash);byte[] before=Files.readAllBytes(db);
    assertThatThrownBy(()->ForeverLootArchiveWorkflow.project(db,snapshot,hash)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown loot item");
    assertThat(Files.readAllBytes(db)).isEqualTo(before);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db)) {assertThat(SourceArchive.artifacts(c,snapshot)).hasSize(4);assertThat(count(c,"SELECT count(*) FROM boss_loot")).isEqualTo(1);}
  }
  @Test void rejectsWrongHashUnsafePathsMissingLicenseAndMissingReviewedMappings() throws Exception {
    Path db=database();byte[] zip=zip(source(),LICENSE,false);
    Path input=temporary.resolve("wrong.zip");Files.write(input,zip);
    assertThatThrownBy(()->ForeverLootArchiveWorkflow.ingest(db,input)).hasMessageContaining("SHA-256 mismatch");
    assertThatThrownBy(()->ForeverLootArchiveWorkflow.capture(db,zip,"wrong")).hasMessageContaining("hash mismatch");
    byte[] unsafe=zip(source(),LICENSE,true);
    assertThatThrownBy(()->ForeverLootArchiveWorkflow.capture(db,unsafe,ForeverLootArchiveWorkflow.hash(unsafe))).isInstanceOf(IllegalArgumentException.class);
    byte[] badLicense=zip(source(),"unknown",false);
    assertThatThrownBy(()->ForeverLootArchiveWorkflow.capture(db,badLicense,ForeverLootArchiveWorkflow.hash(badLicense))).hasMessageContaining("license");
    assertThatThrownBy(()->ForeverLootArchiveWorkflow.rows("data={}".getBytes(StandardCharsets.UTF_8))).hasMessageContaining("four reviewed");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db)){assertThat(count(c,"SELECT count(*) FROM source_archive_snapshot")).isZero();}
  }
  @Test void publicProjectionRejectsUnpinnedArchiveAndAmbiguousNamesAreNotGuessed() throws Exception {
    Path db=database();byte[] zip=zip(source(),LICENSE,false);String hash=ForeverLootArchiveWorkflow.hash(zip);
    String snapshot=ForeverLootArchiveWorkflow.capture(db,zip,hash);
    assertThatThrownBy(()->ForeverLootArchiveWorkflow.project(db,snapshot)).hasMessageContaining("ZIP hash mismatch");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.createStatement()){s.execute("INSERT INTO boss VALUES (9999,2959,'Exact boss',NULL,0,2)");}
    byte[] before=Files.readAllBytes(db);
    assertThatThrownBy(()->ForeverLootArchiveWorkflow.project(db,snapshot,hash)).hasMessageContaining("Ambiguous exact boss name");
    assertThat(Files.readAllBytes(db)).isEqualTo(before);
  }
  @Test void coreRebuildCannotEraseOverlay() throws Exception {
    Path db=database();byte[] zip=zip(source(),LICENSE,false);String hash=ForeverLootArchiveWorkflow.hash(zip);
    String snapshot=ForeverLootArchiveWorkflow.capture(db,zip,hash);ForeverLootArchiveWorkflow.project(db,snapshot,hash);
    assertThatThrownBy(()->ArchiveWorkflow.project(db,"old",System.out)).hasMessageContaining("Forever loot overlay requires explicit replay");
  }
  @Test void literalRowsRetainOriginalItemLineAndSourceKey() {
    var rows=ForeverLootArchiveWorkflow.rows(source().getBytes(StandardCharsets.UTF_8));
    assertThat(rows).hasSize(12);
    assertThat(rows.getFirst().key()).isEqualTo("ExcavationSiteWetlands");
    assertThat(rows.getFirst().line()).isEqualTo(2);
    assertThat(rows.getFirst().item()).isEqualTo(101);
  }
  static String unionSource(boolean continued) {
    var out=new StringBuilder();
    String[] keys=continued?new String[]{"TheHallOfThanes","RuinsOfLordaeron","ExcavationSiteWetlands","CityOfDalaran"}
        :new String[]{"TheHallOfThanes","TheRuinsOfLordaeron","ExcavationSiteWetlands","TheCityOfDalaran"};
    int[] counts={12,20,9,24},extra={1,2,3,2};int id=10000;
    for(int map=0;map<4;map++) {
      out.append("data[\"").append(keys[map]).append("\"]={items={{name=").append(continued?"AL[\"Exact boss\"]":"\"Exact boss\"");
      if(continued)out.append(",npcID=42");out.append(",[NORMAL_DIFF]={");
      for(int n=0;n<counts[map];n++)out.append("{").append(continued?counts[map]-n:n+1).append(",").append(id+n).append("},");
      if(continued)for(int n=0;n<extra[map];n++)out.append("{").append(100+n).append(",").append(id+counts[map]+n).append("},");
      out.append("}}");
      if(continued && map==1){out.append(",{name=AL[\"Trash\"],ExtraList=true,[NORMAL_DIFF]={");for(int n=0;n<5;n++)out.append("{").append(n+1).append(",").append(20000+n).append("},");out.append("}}");}
      out.append("}}\n");id+=100;
    }
    return out.toString();
  }
  static byte[] providerZip(String source,ForeverLootArchiveWorkflow.Provider provider) throws Exception {
    var bytes=new ByteArrayOutputStream();try(var z=new ZipOutputStream(bytes)) {
      for(var e:Map.of(provider.data(),source,provider.license(),LICENSE).entrySet()) {
        z.putNextEntry(new ZipEntry(e.getKey()));z.write(e.getValue().getBytes(StandardCharsets.UTF_8));z.closeEntry();
      }
    }return bytes.toByteArray();
  }
  @Test void sixtyFiveSubsetAndSeventyEightUnionKeepBothSupportsAndReplayOrderIdentity() throws Exception {
    Path first=database(),second=temporary.resolve("second.sqlite");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+first);var s=c.createStatement()) {
      for(int id=10000;id<10400;id++)s.execute("INSERT INTO item VALUES ("+id+",'fixture')");
      for(int id=20000;id<20005;id++)s.execute("INSERT INTO item VALUES ("+id+",'fixture-trash')");
    }
    Files.copy(first,second);
    var a=ForeverLootArchiveWorkflow.SLICCER;var b=ForeverLootArchiveWorkflow.CONTINUED;
    byte[] zipA=providerZip(unionSource(false),a),zipB=providerZip(unionSource(true),b);
    String hashA=ForeverLootArchiveWorkflow.hash(zipA),hashB=ForeverLootArchiveWorkflow.hash(zipB);
    assertThat(ForeverLootArchiveWorkflow.rows(unionSource(false).getBytes(StandardCharsets.UTF_8),a)).hasSize(65);
    assertThat(ForeverLootArchiveWorkflow.rows(unionSource(true).getBytes(StandardCharsets.UTF_8),b)).hasSize(78);
    String snapA=ForeverLootArchiveWorkflow.capture(first,zipA,hashA,a),snapB=ForeverLootArchiveWorkflow.capture(first,zipB,hashB,b);
    ForeverLootArchiveWorkflow.capture(second,zipA,hashA,a);ForeverLootArchiveWorkflow.capture(second,zipB,hashB,b);
    ForeverLootArchiveWorkflow.project(first,snapA,hashA);ForeverLootArchiveWorkflow.project(first,snapB,hashB);
    ForeverLootArchiveWorkflow.project(second,snapB,hashB);ForeverLootArchiveWorkflow.project(second,snapA,hashA);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+first);var other=DriverManager.getConnection("jdbc:sqlite:"+second)) {
      assertThat(count(c,"SELECT count(*) FROM boss_loot")).isEqualTo(79);
      assertThat(count(c,"SELECT count(*) FROM atlasloot_forever_fact")).isEqualTo(143);
      assertThat(count(c,"SELECT count(*) FROM (SELECT boss_id,item_id,difficulty FROM atlasloot_forever_fact GROUP BY 1,2,3 HAVING count(DISTINCT provider)=2)")).isEqualTo(65);
      assertThat(count(c,"SELECT count(*) FROM boss WHERE name='Trash' AND is_extra_list=1 AND npc_id IS NULL")).isEqualTo(1);
      assertThat(count(c,"SELECT count(*) FROM boss WHERE npc_id=42")).isZero();
      assertThat(count(c,"SELECT count(*) FROM atlasloot_forever_fact WHERE claimed_npc_id=42")).isEqualTo(73);
      assertThat(AdditiveProjection.meta(c,"build_id")).isEqualTo(AdditiveProjection.meta(other,"build_id"));
      assertThat(AdditiveProjection.meta(c,"forever_loot_snapshot_id")).isEqualTo(AdditiveProjection.meta(other,"forever_loot_snapshot_id"));
      assertThat(canonical(c)).isEqualTo(canonical(other));
    }
    ForeverLootArchiveWorkflow.project(first,snapB,hashB);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+first)){assertThat(count(c,"SELECT count(*) FROM atlasloot_forever_fact")).isEqualTo(143);}
  }
  static List<String> canonical(Connection c) throws Exception {
    List<String> rows=new ArrayList<>();try(var s=c.createStatement();var r=s.executeQuery("SELECT boss_id,item_id,difficulty,slot FROM boss_loot ORDER BY 1,2,3")) {
      while(r.next())rows.add(r.getLong(1)+":"+r.getLong(2)+":"+r.getString(3)+":"+r.getInt(4));
    }return rows;
  }
  @Test void unrelatedRuntimeSyntaxIsNotParsedAndOriginalModuleLinesArePreserved() {
    String source="local runtime=9 % 4\n"+source()+"\nlocal unrelated=function() unsupported_runtime() end";
    var rows=ForeverLootArchiveWorkflow.rows(source.getBytes(StandardCharsets.UTF_8));
    assertThat(rows).hasSize(12);assertThat(rows.getFirst().line()).isEqualTo(3);
  }

  @Test void unsupportedProjectionStillRetainsOriginalArchiveAndHonestParseDiagnostic() throws Exception {
    Path db=database();byte[] zip=zip("local unsupported = 5 % 2\n",LICENSE,false);String hash=ForeverLootArchiveWorkflow.hash(zip);
    String snapshot=ForeverLootArchiveWorkflow.capture(db,zip,hash);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db)) {
      assertThat(SourceArchive.artifacts(c,snapshot)).hasSize(4);
      assertThat(count(c,"SELECT count(*) FROM source_record_parse WHERE status='error' AND artifact_path='"+ForeverLootArchiveWorkflow.DATA+"'" )).isEqualTo(1);
    }
    assertThatThrownBy(()->ForeverLootArchiveWorkflow.project(db,snapshot,hash)).hasMessageContaining("four reviewed");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db)){assertThat(SourceArchive.artifacts(c,snapshot)).hasSize(4);}
  }

}
