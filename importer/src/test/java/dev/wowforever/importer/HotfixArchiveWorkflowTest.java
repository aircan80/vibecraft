package dev.wowforever.importer;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.importer.clientdata.*;
import dev.wowforever.importer.sqlite.*;
import dev.wowforever.importer.drops.DropRates;
import java.nio.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic protocol tests; these never establish genuine beta-capture acceptance. */
class HotfixArchiveWorkflowTest {
  @TempDir Path temp;
  private static String sha(byte[] data)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));}
  private Path input()throws Exception {
    Path dir=HotfixFixture.standard(temp.resolve("input")).dir();
    BuildLock original=BuildLock.read(dir.resolve("build.lock"));
    byte[] raw=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).put(new byte[]{'X','F','T','H'}).putInt(9).putInt(70425).array();
    Files.createDirectories(dir.resolve("raw"));Files.write(dir.resolve("raw/DBCache.bin"),raw);
    Files.createDirectories(dir.resolve("definitions"));
    String definitionSha=sha("synthetic definition".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    StringJoiner definitions=new StringJoiner(",","{","}");
    for(String table:List.of("Item","ItemSparse","ItemEffect")) {
      Files.writeString(dir.resolve("definitions/"+table+".dbd"),"synthetic definition");
      definitions.add("\""+table+"\":\""+definitionSha+"\"");
    }
    StringJoiner tables=new StringJoiner(",","{","}");
    try(var paths=Files.list(dir.resolve("csv"))) {
      for(Path p:paths.filter(p->!p.getFileName().toString().contains(".hotfix.")).toList()) {
        String name=p.getFileName().toString().replace(".csv","");tables.add("\""+name+"\":\""+sha(Files.readAllBytes(p))+"\"");
      }
    }
    String rawSha=sha(raw);
    Files.writeString(dir.resolve("hotfix.json"),Files.readString(dir.resolve("hotfix.json")).replace(HotfixFixture.SHA,rawSha));
    Files.writeString(dir.resolve("build.lock"),"{\"product\":\"wow_classic_beta\",\"build\":\"1.60.1.70425\","
        +"\"fetched_at\":\"synthetic\",\"base_url\":\"https://example.test\",\"tables\":"+tables+","
        +"\"fallback_product\":\""+original.fallbackProduct()+"\",\"fallback_build\":\""+original.fallbackBuild()+"\","
        +"\"hotfix\":{\"sha256\":\""+rawSha+"\",\"header_build\":70425,\"dbd\":"+definitions+"}}");
    return dir;
  }
  private Path database()throws Exception {
    Path db=temp.resolve("candidate.sqlite");
    SqliteWriter.write(db,new ImportModel(Map.of(),new DropRates(),Map.of(),List.of(),Map.of("build_id","baseline")));
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.createStatement()) {
      s.executeUpdate("INSERT INTO item(id,name,item_level,flags,client_source,has_client_data) VALUES(25,'Baseline sword',2,123,'forever',1),(16806,'Classic control',66,987,'era',1),(888888,NULL,NULL,0,NULL,0)");
      s.executeUpdate("INSERT INTO item_stat VALUES(16806,0,7,22)");
      s.executeUpdate("INSERT INTO item_tooltip_line VALUES(16806,0,'footer','Classic Era values. Forever stats are still hidden.','#9d9d9d')");
      s.executeUpdate("INSERT INTO npc(id,name) VALUES(639,'Baseline NPC')");
      s.executeUpdate("INSERT INTO item_npc_drop(item_id,npc_id,drop_pct,pct_source,origin) VALUES(25,639,13.5,'vmangos','questie')");
      s.executeUpdate("INSERT INTO icon VALUES(123,x'010203')");
    }
    return db;
  }
  private String scalar(Path db,String sql)throws Exception{try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.createStatement();var r=s.executeQuery(sql)){assertThat(r.next()).isTrue();return r.getString(1);}}
  @Test void archiveThenOfflineProjectionChangesOnlyRevealedItemsAndRetainsHistory()throws Exception {
    Path db=database(),input=input();
    String snapshot=HotfixArchiveWorkflow.ingest(db,input);
    assertThat(scalar(db,"SELECT name FROM item WHERE id=25")).isEqualTo("Baseline sword");
    HotfixArchiveWorkflow.project(db,snapshot);
    assertThat(scalar(db,"SELECT name||':'||client_source||':'||flags FROM item WHERE id=25")).isEqualTo("Worn Shortsword (hotfix):hotfix:123");
    assertThat(scalar(db,"SELECT name||':'||client_source||':'||flags FROM item WHERE id=16806")).isEqualTo("Classic control:era:987");
    assertThat(scalar(db,"SELECT value FROM item_stat WHERE item_id=16806")).isEqualTo("22");
    assertThat(scalar(db,"SELECT text FROM item_tooltip_line WHERE item_id=16806")).startsWith("Classic Era values");
    assertThat(scalar(db,"SELECT drop_pct||':'||pct_source||':'||origin FROM item_npc_drop")).isEqualTo("13.5:vmangos:questie");
    assertThat(scalar(db,"SELECT hex(png) FROM icon WHERE id=123")).isEqualTo("010203");
    assertThat(scalar(db,"SELECT state FROM field_knowledge WHERE id='25' AND field='stats'")).isEqualTo("KNOWN");
    assertThat(scalar(db,"SELECT text FROM item_tooltip_line WHERE item_id=25 AND kind='source_note'")).contains("Blizzard hotfix").doesNotContain("live");
    String build=scalar(db,"SELECT value FROM meta WHERE key='build_id'");
    String archivedCount=scalar(db,"SELECT count(*) FROM source_archive_snapshot");
    HotfixArchiveWorkflow.project(db,snapshot);
    assertThat(scalar(db,"SELECT count(*) FROM source_archive_snapshot")).isEqualTo(archivedCount);
    assertThat(scalar(db,"SELECT value FROM meta WHERE key='build_id'")).isEqualTo(build);
    assertThat(scalar(db,"SELECT count(*) FROM hotfix_item_fact")).isEqualTo("1");
    assertThat(scalar(db,"SELECT value FROM hotfix_previous_item WHERE item_id=25 AND field='name'")).isEqualTo("Baseline sword");
    assertThat(scalar(db,"SELECT count(*) FROM source_archive_snapshot WHERE source='blizzard-hotfix'")).isEqualTo("1");
  }
  @Test void wrongRawBytesRefuseAcquisitionWithoutChangingCanonicalData()throws Exception {
    Path db=database(),input=input();byte[] before=Files.readAllBytes(db);
    Files.write(input.resolve("raw/DBCache.bin"),new byte[44]);
    assertThatThrownBy(()->HotfixArchiveWorkflow.ingest(db,input)).hasMessageContaining("hash mismatch");
    assertThat(Files.readAllBytes(db)).isEqualTo(before);
  }
  @Test void missingNativeSupportFailsProjectionButRetainsExactAcquisition()throws Exception {
    Path db=database(),input=input();Files.delete(input.resolve("csv/RandPropPoints.csv"));
    // The hash-pinned acquisition itself refuses incomplete support before recording any fabricated result.
    assertThatThrownBy(()->HotfixArchiveWorkflow.ingest(db,input)).hasMessageContaining("supporting CSV missing");
    assertThat(scalar(db,"SELECT name FROM item WHERE id=25")).isEqualTo("Baseline sword");
  }
}
