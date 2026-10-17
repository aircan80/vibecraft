package dev.wowforever.importer;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.importer.sqlite.*;
import dev.wowforever.importer.drops.DropRates;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuestieRefreshWorkflowTest {
  @TempDir Path temp;
  private Path db(String name)throws Exception {
    Path path=temp.resolve(name+".sqlite");SqliteWriter.write(path,new ImportModel(Map.of(),new DropRates(),Map.of(),List.of(),Map.of()));
    try(var c=open(path);var s=c.createStatement()) {
      s.executeUpdate("INSERT INTO npc(id,name,min_level) VALUES(1,'Prior NPC',1)");
      s.executeUpdate("INSERT INTO quest(id,name) VALUES(10,'Prior quest')");
      s.executeUpdate("INSERT INTO item(id,name) VALUES(25,'Prior item')");
      s.executeUpdate("INSERT INTO npc_spawn(npc_id,zone_id,x,y) VALUES(1,12,20,30),(1,12,21,31)");
    }
    return path;
  }
  private void changed(Path path)throws Exception {
    try(var c=open(path);var s=c.createStatement()) {
      s.executeUpdate("UPDATE npc SET name='New NPC',min_level=2 WHERE id=1");
      s.executeUpdate("DELETE FROM npc_spawn WHERE npc_id=1");
      s.executeUpdate("INSERT INTO npc_spawn(npc_id,zone_id,x,y) VALUES(1,12,40,50)");
      s.executeUpdate("INSERT INTO item_npc_drop(item_id,npc_id,origin) VALUES(25,1,'questie')");
    }
  }
  private Connection open(Path path)throws Exception{return DriverManager.getConnection("jdbc:sqlite:"+path);}
  private String scalar(Path path,String sql)throws Exception{try(var c=open(path);var s=c.createStatement();var r=s.executeQuery(sql)){assertThat(r.next()).isTrue();return r.getString(1);}}
  @Test void exactPriorGroupsAreReplacedAndUnrelatedCanonicalOverridesSurvive()throws Exception {
    Path old=db("old"),newer=db("new"),target=db("target");changed(newer);
    try(var c=open(target);var s=c.createStatement()) {
      s.executeUpdate("UPDATE npc SET name='Client override' WHERE id=1");
      s.executeUpdate("INSERT INTO item_npc_drop(item_id,npc_id,drop_pct,pct_source,origin) VALUES(25,1,13.5,'vmangos','questie')");
    }
    try(var c=open(target);var a=open(old);var b=open(newer)) {
      c.setAutoCommit(false);QuestieRefreshWorkflow.apply(c,a,b,"synthetic");c.commit();
    }
    assertThat(scalar(target,"SELECT count(*) FROM npc_spawn")).isEqualTo("1");
    assertThat(scalar(target,"SELECT x||':'||y FROM npc_spawn")).isEqualTo("40.0:50.0");
    assertThat(scalar(target,"SELECT name||':'||min_level FROM npc")).isEqualTo("Client override:2");
    assertThat(scalar(target,"SELECT drop_pct||':'||pct_source FROM item_npc_drop")).isEqualTo("13.5:vmangos");
    assertThat(scalar(target,"SELECT status FROM questie_refresh_change WHERE table_name='npc' AND field='name'")).isEqualTo("conflict-retained");
    assertThat(scalar(target,"SELECT old_json FROM questie_refresh_change WHERE table_name='npc_spawn'")).contains("20.0","30.0","21.0","31.0");
    try(var c=open(target);var a=open(old);var b=open(newer)) {
      c.setAutoCommit(false);QuestieRefreshWorkflow.apply(c,a,b,"synthetic");c.commit();
    }
    assertThat(scalar(target,"SELECT count(*) FROM npc_spawn")).isEqualTo("1");
  }
  @Test void nonmatchingGroupIsRetainedRatherThanPartiallyMerged()throws Exception {
    Path old=db("old"),newer=db("new"),target=db("target");changed(newer);
    try(var c=open(target);var s=c.createStatement()) {s.executeUpdate("INSERT INTO npc_spawn(npc_id,zone_id,x,y) VALUES(1,12,99,99)");}
    try(var c=open(target);var a=open(old);var b=open(newer)) {
      c.setAutoCommit(false);QuestieRefreshWorkflow.apply(c,a,b,"synthetic");c.commit();
    }
    assertThat(scalar(target,"SELECT count(*) FROM npc_spawn")).isEqualTo("3");
    assertThat(scalar(target,"SELECT status FROM questie_refresh_change WHERE table_name='npc_spawn'")).isEqualTo("conflict-retained");
  }
  @Test void unsupportedSourceChangesRefuseBeforeAnyCanonicalWrites()throws Exception {
    Path old=db("old"),newer=db("new"),target=db("target");changed(newer);
    try(var c=open(newer);var s=c.createStatement()){s.executeUpdate("INSERT INTO icon VALUES(123,x'0102')");}
    try(var c=open(target);var a=open(old);var b=open(newer)) {
      assertThatThrownBy(()->QuestieRefreshWorkflow.apply(c,a,b,"synthetic")).hasMessageContaining("Unsupported Questie source delta: icon");
    }
    assertThat(scalar(target,"SELECT name FROM npc")).isEqualTo("Prior NPC");
  }
}
