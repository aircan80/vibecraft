package dev.wowforever.importer;

import dev.wowforever.importer.allthethings.AllTheThingsParser;
import dev.wowforever.importer.sqlite.AllTheThingsProjection;
import dev.wowforever.importer.source.SourceSpec;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AllTheThingsProjectionTest {
  private static final SourceSpec SPEC=new SourceSpec("allthethings","ATTWoWAddon","AllTheThings","pin",List.of());
  private Connection db() throws Exception {
    Connection c=DriverManager.getConnection("jdbc:sqlite::memory:");
    try(Statement s=c.createStatement()) {
      s.execute("CREATE TABLE quest(id INTEGER PRIMARY KEY)");
      s.execute("INSERT INTO quest VALUES(1),(2),(3),(4)");
      s.execute("CREATE TABLE quest_relation(quest_id INTEGER,related_id INTEGER,kind TEXT,PRIMARY KEY(quest_id,related_id,kind))");
      s.execute("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)");
    }
    return c;
  }
  private AllTheThingsParser.QuestLink link(long q,long pre,String kind) {
    return new AllTheThingsParser.QuestLink(q,pre,kind,"zones/a b.lua",12,"authored");
  }
  private AllTheThingsProjection.Stats apply(Connection c,AllTheThingsParser.QuestLink... links)throws Exception {
    return AllTheThingsProjection.apply(c,new AllTheThingsParser.Result(List.of(links),List.of(),List.of()),"snapshot",SPEC);
  }
  @Test void fillsGapsWithExactPinnedEvidenceAndEncodedUrl()throws Exception {
    try(var c=db()) {
      assertThat(apply(c,link(3,1,"pre_single"),link(3,2,"pre_single")).selected()).isEqualTo(2);
      try(var s=c.createStatement();var r=s.executeQuery("SELECT * FROM quest_relation_source ORDER BY related_id")) {
        assertThat(r.next()).isTrue();assertThat(r.getString("source_url")).isEqualTo("https://github.com/ATTWoWAddon/AllTheThings/blob/pin/zones/a%20b.lua#L12");
        assertThat(r.getString("snapshot_id")).isEqualTo("snapshot");assertThat(r.getString("evidence_status")).isEqualTo("authored");
        assertThat(r.getInt("source_line")).isEqualTo(12);assertThat(r.getString("evidence")).contains("configured revision=pin");
        assertThat(r.next()).isTrue();assertThat(r.next()).isFalse();
      }
    }
  }
  @Test void conflictingWholeGroupIsNeverUnioned()throws Exception {
    try(var c=db()) {
      c.createStatement().execute("INSERT INTO quest_relation VALUES(3,1,'pre_group')");
      var stats=apply(c,link(3,1,"pre_group"),link(3,2,"pre_group"));
      assertThat(stats.conflicts()).isEqualTo(1);assertThat(stats.selected()).isZero();
      try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM quest_relation")){assertThat(r.getInt(1)).isEqualTo(1);}
    }
  }
  @Test void supportingSingletonKeepsCanonicalKindAndDeduplicatesEvidence()throws Exception {
    try(var c=db()) {
      c.createStatement().execute("INSERT INTO quest_relation VALUES(3,1,'pre_group')");
      assertThat(apply(c,link(3,1,"pre_single"),link(3,1,"pre_single")).selected()).isEqualTo(1);
      try(var s=c.createStatement();var r=s.executeQuery("SELECT kind,count(*) FROM quest_relation_source")) {
        assertThat(r.getString(1)).isEqualTo("pre_group");assertThat(r.getInt(2)).isEqualTo(1);
      }
    }
  }
  @Test void missingTargetSkipsWholeGroupWithoutInventingQuestRows()throws Exception {
    try(var c=db()) {
      var stats=apply(c,link(3,1,"pre_group"),link(3,99,"pre_group"));
      assertThat(stats.unsupported()).isEqualTo(1);assertThat(stats.selected()).isZero();
      try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM quest_relation")){assertThat(r.getInt(1)).isZero();}
    }
  }
  @Test void localModifiedInputNeverClaimsPinnedUpstreamLocation()throws Exception {
    try(var c=db()) {
      AllTheThingsProjection.apply(c,new AllTheThingsParser.Result(List.of(link(3,1,"pre_single")),List.of(),List.of()),"local-snapshot",SPEC,false);
      try(var s=c.createStatement();var r=s.executeQuery("SELECT source_url,source_line,evidence FROM quest_relation_source")) {
        assertThat(r.getString(1)).isEmpty();assertThat(r.getInt(2)).isEqualTo(12);
        assertThat(r.getString(3)).contains("local input identified by snapshot hash");
      }
    }
  }

  @Test void acquisitionPinMatchesRecordedConfiguration() throws Exception {
    var properties=new Properties();
    try(var in=getClass().getResourceAsStream("/dev/wowforever/importer/sources.properties")) {properties.load(in);}
    assertThat(properties.getProperty("allthethings.commit")).isEqualTo(dev.wowforever.importer.allthethings.AllTheThingsSource.PIN);
    assertThat(properties.getProperty("allthethings.owner")).isEqualTo(dev.wowforever.importer.allthethings.AllTheThingsSource.OWNER);
    assertThat(properties.getProperty("allthethings.repo")).isEqualTo(dev.wowforever.importer.allthethings.AllTheThingsSource.REPOSITORY);
  }

}
