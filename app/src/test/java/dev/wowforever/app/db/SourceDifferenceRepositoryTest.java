package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceDifferenceRepositoryTest {
  @TempDir Path temp;
  @Test void retainsValuesScopesOrderingAndMissingCoverageWithoutInventingNegativeClaims()throws Exception {
    Path file=temp.resolve("compare.sqlite");Files.copy(FixtureDb.path(),file);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      s.execute("DELETE FROM item_alt_value WHERE item_id=2874");
      s.execute("INSERT INTO item_alt_value VALUES(2874,'name','questie','Alternate name')");
      s.execute("DELETE FROM drop_rate_source WHERE item_id=2874");
      s.execute("INSERT INTO drop_rate_source VALUES(639,2874,'questie-wowhead',20,0),(639,2874,'vmangos',30,1),(126,2874,'questie',NULL,0),(126,2874,'atlasloot',5,0)");
      s.execute("CREATE TABLE source_archive_snapshot(id TEXT PRIMARY KEY,version TEXT)");
      s.execute("INSERT INTO source_archive_snapshot VALUES('a','1.2'),('b','2.0')");
      s.execute("CREATE TABLE atlasloot_forever_fact(boss_id INTEGER,item_id INTEGER,difficulty TEXT,snapshot_id TEXT,source_path TEXT,source_line INTEGER,source_instance_key TEXT,source_boss_name TEXT,provider TEXT,claimed_npc_id INTEGER,source_slot INTEGER,state TEXT)");
      s.execute("INSERT INTO atlasloot_forever_fact VALUES(1,2874,'NORMAL','a','data.lua',20,'x','Boss','first-addon',NULL,1,'reported'),(1,2874,'NORMAL','b','continued.lua',30,'x','Boss','second-addon',NULL,3,'reported'),(1,14149,'NORMAL','b','continued.lua',31,'x','Boss','second-addon',NULL,4,'reported')");
    }
    try(var db=Database.open(file)) {
      var comparisons=new SourceDifferenceRepository(db).item(2874);
      assertThat(comparisons).anySatisfy(d -> {
        assertThat(d.topic()).isEqualTo("Name");assertThat(d.classification()).isEqualTo("Values differ");
        assertThat(d.assertions()).anySatisfy(a -> assertThat(a.value()).isEqualTo("Alternate name"));
        assertThat(d.assertions().getFirst().selected()).isTrue();
      });
      assertThat(comparisons).anySatisfy(d -> {
        assertThat(d.classification()).isEqualTo("Different rate scopes");
        assertThat(d.assertions()).extracting(a->a.value()).contains("20.0 % (per kill)","30.0 % (quest only)");
      });
      assertThat(comparisons).anySatisfy(d -> {
        assertThat(d.classification()).isEqualTo("Incomplete rate coverage");
        assertThat(d.assertions()).extracting(a->a.value()).contains("No rate","5.0 % (per kill)");
      });
      assertThat(comparisons).anySatisfy(d -> {
        assertThat(d.classification()).isEqualTo("List order differs");
        assertThat(d.assertions()).anySatisfy(a -> {assertThat(a.source()).contains("1.2");assertThat(a.evidence()).contains("data.lua:20","snapshot a");});
      });
      var single=new SourceDifferenceRepository(db).item(14149);
      assertThat(single).anySatisfy(d -> {
        assertThat(d.classification()).isEqualTo("Single-source coverage");
        assertThat(d.assertions()).anySatisfy(a -> assertThat(a.evidence()).contains("not a claim"));
      });
      assertThat(new SourceDifferenceRepository(db).boss(1)).isNotEmpty();
    }
  }
  @Test void retractionsRemainInspectableAndDoNotBecomeActiveConflicts()throws Exception {
    Path file=temp.resolve("assertions.sqlite");Files.copy(FixtureDb.path(),file);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file)) {
      dev.wowforever.importer.foundation.FieldKnowledge.initialize(c);
      long prior=dev.wowforever.importer.foundation.FieldKnowledge.assertEvidence(c,new dev.wowforever.importer.foundation.FieldKnowledge.Evidence(
          "item","2874","stats",dev.wowforever.importer.foundation.FieldKnowledge.State.REPORTED,"addon","1","snapshot","data.lua",7,"old"));
      dev.wowforever.importer.foundation.FieldKnowledge.retractEvidence(c,prior,"source corrected its claim");
      dev.wowforever.importer.foundation.FieldKnowledge.assertEvidence(c,new dev.wowforever.importer.foundation.FieldKnowledge.Evidence(
          "item","2874","stats",dev.wowforever.importer.foundation.FieldKnowledge.State.KNOWN,"forever","70450","snapshot2","ItemSparse.csv",8,"0"));
    }
    try(var db=Database.open(file)) {
      assertThat(new SourceDifferenceRepository(db).item(2874)).anySatisfy(d->{
        assertThat(d.topic()).isEqualTo("stats");assertThat(d.classification()).isEqualTo("Retained knowledge evidence");
        assertThat(d.assertions()).anySatisfy(a->{assertThat(a.value()).contains("retracted: source corrected");assertThat(a.selected()).isFalse();});
        assertThat(d.assertions()).anySatisfy(a->{assertThat(a.value()).isEqualTo("KNOWN · 0");assertThat(a.selected()).isTrue();});
      });
    }
  }
  @Test void optionalAddonFactsAreNotRequiredForLegacyItems() throws Exception {
    try(var db=Database.open(FixtureDb.path())) {
      assertThat(new SourceDifferenceRepository(db).item(-1)).isEmpty();
    }
  }
}
