package dev.wowforever.importer;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.importer.archive.SourceArchive;
import dev.wowforever.importer.clientdata.*;
import dev.wowforever.importer.foundation.ClientDataset;
import dev.wowforever.importer.sqlite.*;
import dev.wowforever.importer.drops.DropRates;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic fixtures exercise genuine native derivation paths; no real-observation acceptance is implied. */
class NativeClientItemCompletionWorkflowTest {
  @TempDir Path temp;
  record Inputs(Path db,String support,String nativeSource) {}
  @SuppressWarnings("unchecked")
  Inputs inputs(boolean mismatch)throws Exception {
    Path input=HotfixFixture.standard(temp.resolve("context")).dir();
    var lock=(Map<String,Object>)MiniJson.parse(Files.readString(input.resolve("build.lock")));
    Map<String,String> hashes=new TreeMap<>();
    try(var files=Files.list(input.resolve("csv"))) {
      for(Path file:files.filter(p->!p.getFileName().toString().contains(".hotfix.")).toList())
        hashes.put(file.getFileName().toString().replace(".csv",""),ClientDataset.sha256(Files.readAllBytes(file)));
    }
    lock.put("tables",hashes);Files.writeString(input.resolve("build.lock"),ClientDataset.json(lock));
    List<SourceArchive.Artifact> context=new ArrayList<>();
    try(var files=Files.walk(input)) {
      for(Path file:files.filter(Files::isRegularFile).toList())context.add(new SourceArchive.Artifact(input.relativize(file).toString().replace('\\','/'),"",Files.readAllBytes(file)));
    }
    Path db=temp.resolve("candidate.sqlite");SqliteWriter.write(db,new ImportModel(Map.of(),new DropRates(),Map.of(),List.of(),Map.of("build_id","baseline")));
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.createStatement()) {
      s.execute("INSERT INTO item(id,name,flags,has_client_data,client_source) VALUES(25,'Old sword',123,0,NULL),(17783,'Item #17783',456,0,NULL),(16806,'Classic control',789,1,'era'),(869,'Hotfix control',987,1,'hotfix')");
      s.execute("INSERT INTO item_stat VALUES(16806,0,7,22),(869,0,4,15)");
      s.execute("INSERT INTO npc(id) VALUES(1)");
      s.execute("INSERT INTO item_npc_drop(item_id,npc_id,drop_pct,pct_source,origin) VALUES(25,1,31,'vmangos','questie')");
      s.execute("INSERT INTO icon VALUES(123,x'010203')");
      c.setAutoCommit(false);ArchiveWorkflow.initialize(c);
      String support=SourceArchive.capture(c,new SourceArchive.Snapshot("blizzard-hotfix",HotfixFixture.SHA,"forever","1.60.1.70425","enUS"),context);
      byte[] nativeBytes=Files.readAllBytes(input.resolve("csv/ItemSparse.csv"));
      if(mismatch){nativeBytes=Arrays.copyOf(nativeBytes,nativeBytes.length+1);nativeBytes[nativeBytes.length-1]=10;}
      String path="tables/ItemSparse.1.60.1.70425.csv";
      var artifact=new SourceArchive.Artifact(path,"",nativeBytes);
      String nativeSource=SourceArchive.capture(c,new SourceArchive.Snapshot(FoundationArchiveWorkflow.SOURCE,"synthetic native CSV","wow_classic_beta","1.60.1.70425","enUS"),List.of(artifact));
      ClientDataset.project(c,nativeSource,"wow_classic_beta","1.60.1.70425","enUS",List.of(new ClientDataset.Table("ItemSparse",path,ClientDataset.sha256(nativeBytes),List.of("ID"))),List.of(artifact));
      AdditiveProjection.record(c,Map.of("client-foundation-wow_classic_beta-1.60.1.70425",nativeSource),null,"client_foundation","synthetic","Synthetic native source");
      c.commit();return new Inputs(db,support,nativeSource);
    }
  }
  String scalar(Path db,String sql)throws Exception {try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.createStatement();var r=s.executeQuery(sql)){assertThat(r.next()).isTrue();return r.getString(1);}}
  @Test void completesNativeCardsIncluding17783AndRetainsSelectedCardsAndUnrelatedData()throws Exception {
    var data=inputs(false);NativeClientItemCompletionWorkflow.project(data.db(),data.support(),data.nativeSource());
    assertThat(scalar(data.db(),"SELECT name||':'||has_client_data||':'||client_source||':'||flags FROM item WHERE id=25")).isEqualTo("Worn Shortsword:1:forever:123");
    assertThat(scalar(data.db(),"SELECT has_client_data||':'||client_source FROM item WHERE id=17783")).isEqualTo("1:forever");
    assertThat(scalar(data.db(),"SELECT value FROM item_stat WHERE item_id=17783 AND stat_id=4")).isEqualTo("13");
    assertThat(scalar(data.db(),"SELECT value FROM item_stat WHERE item_id=17783 AND stat_id=51")).isEqualTo("24");
    assertThat(scalar(data.db(),"SELECT text FROM item_tooltip_line WHERE item_id=17783 AND kind='equip'")).contains("4 Nature damage");
    assertThat(scalar(data.db(),"SELECT client_source FROM item WHERE id=16806")).isEqualTo("era");
    assertThat(scalar(data.db(),"SELECT value FROM item_stat WHERE item_id=16806")).isEqualTo("22");
    assertThat(scalar(data.db(),"SELECT client_source FROM item WHERE id=869")).isEqualTo("hotfix");
    assertThat(scalar(data.db(),"SELECT value FROM item_stat WHERE item_id=869")).isEqualTo("15");
    assertThat(scalar(data.db(),"SELECT drop_pct||':'||pct_source FROM item_npc_drop")).isEqualTo("31.0:vmangos");
    assertThat(scalar(data.db(),"SELECT hex(png) FROM icon WHERE id=123")).isEqualTo("010203");
    assertThat(scalar(data.db(),"SELECT count(*) FROM native_client_completion_fact")).isEqualTo("2");
    String build=scalar(data.db(),"SELECT value FROM meta WHERE key='build_id'");
    NativeClientItemCompletionWorkflow.project(data.db(),data.support(),data.nativeSource());
    assertThat(scalar(data.db(),"SELECT value FROM meta WHERE key='build_id'")).isEqualTo(build);
    assertThat(scalar(data.db(),"SELECT count(*) FROM native_client_completion_fact")).isEqualTo("2");
  }
  @Test void mismatchedNativeSourceRefusesBeforeCanonicalChanges()throws Exception {
    var data=inputs(true);byte[] before=Files.readAllBytes(data.db());
    assertThatThrownBy(()->NativeClientItemCompletionWorkflow.project(data.db(),data.support(),data.nativeSource()))
        .hasMessageContaining("hashes differ");
    assertThat(Files.readAllBytes(data.db())).isEqualTo(before);
  }
}
