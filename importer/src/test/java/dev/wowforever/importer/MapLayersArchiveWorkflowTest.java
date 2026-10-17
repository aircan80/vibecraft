package dev.wowforever.importer;

import dev.wowforever.importer.archive.SourceArchive;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class MapLayersArchiveWorkflowTest {
  @TempDir Path temporary;
  private String captureFixture(Path database,boolean incomplete) throws Exception {
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+database);var s=c.createStatement()) {
      c.setAutoCommit(false);ArchiveWorkflow.initialize(c);
      s.execute("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)");s.execute("INSERT INTO meta VALUES ('build_id','fixture-base')");
      s.execute("CREATE TABLE zone(id INTEGER,ui_map_id INTEGER)");s.execute("INSERT INTO zone VALUES (12,1429),(40,1436),(1519,1453)");
      s.execute("CREATE TABLE npc(id INTEGER)");s.execute("INSERT INTO npc VALUES(523),(352)");
      s.execute("CREATE TABLE instance(id INTEGER,client_map_id INTEGER,instance_id INTEGER,name TEXT,content_type TEXT,level_min INTEGER,level_max INTEGER)");
      s.execute("INSERT INTO instance VALUES(3,NULL,36,'The Deadmines','DUNGEON',10,17)");
      var artifacts=new ArrayList<SourceArchive.Artifact>();
      for(String table:List.of("TaxiNodes","TaxiPath","TaxiPathNode","UiMapAssignment","AreaTrigger")) {
        if(incomplete && table.equals("TaxiPathNode"))continue;
        try(var input=getClass().getResourceAsStream("/maplayers/"+table+".csv")) {artifacts.add(new SourceArchive.Artifact("client/"+table+".csv",null,input.readAllBytes()));}
      }
      for(String file:List.of("teleports-derived.csv","flightmasters-derived.csv")) {
        try(var input=getClass().getResourceAsStream("/maplayers/"+file)) {artifacts.add(new SourceArchive.Artifact("vm/"+file,null,input.readAllBytes()));}
      }
      String snapshot=SourceArchive.capture(c,new SourceArchive.Snapshot("map-layers-client-and-vanilla","fixture","forever+vanilla",MapLayersArchiveWorkflow.BUILD,"enUS"),artifacts);
      c.commit();return snapshot;
    }
  }
  @Test void offlineReplayShowsDeadminesThorStormwindAndNoElwynnEntrance() throws Exception {
    Path database=temporary.resolve("travel.sqlite");String snapshot=captureFixture(database,false);
    MapLayersArchiveWorkflow.project(database,snapshot);
    String firstBuild;
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+database);var s=c.createStatement()) {
      try(var r=s.executeQuery("SELECT npc_id,faction FROM map_flight_node WHERE zone_id=40 AND node_id=4")) {
        assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isEqualTo(523);assertThat(r.getString(2)).isEqualTo("ALLIANCE");
      }
      try(var r=s.executeQuery("SELECT count(*) FROM map_flight_node WHERE zone_id=1519 AND node_id=2")){assertThat(r.getInt(1)).isEqualTo(1);}
      try(var r=s.executeQuery("SELECT exit_here FROM map_instance_access WHERE zone_id=40 AND trigger_id=78")){assertThat(r.getInt(1)).isEqualTo(1);}
      try(var r=s.executeQuery("SELECT count(*) FROM map_instance_access WHERE zone_id=12")){assertThat(r.getInt(1)).isZero();}
      try(var r=s.executeQuery("SELECT count(*) FROM map_flight_route WHERE x<0 OR x>100 OR y<0 OR y>100")){assertThat(r.getInt(1)).isZero();}
      try(var r=s.executeQuery("SELECT value FROM meta WHERE key='build_id'")){firstBuild=r.getString(1);assertThat(firstBuild).isNotEqualTo("fixture-base");}
    }
    MapLayersArchiveWorkflow.project(database,snapshot);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+database);var s=c.createStatement();var r=s.executeQuery("SELECT value FROM meta WHERE key='build_id'")){assertThat(r.getString(1)).isEqualTo(firstBuild);}
  }
  @Test void failedOfflineProjectionRetainsCapturedEvidenceAndPriorRuntimeTables() throws Exception {
    Path database=temporary.resolve("failed.sqlite");String snapshot=captureFixture(database,true);
    assertThatThrownBy(() -> MapLayersArchiveWorkflow.project(database,snapshot)).isInstanceOf(NullPointerException.class);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+database);var s=c.createStatement()) {
      assertThat(SourceArchive.artifacts(c,snapshot)).hasSize(6);
      try(var r=s.executeQuery("SELECT value FROM meta WHERE key='build_id'")){assertThat(r.getString(1)).isEqualTo("fixture-base");}
      try(var r=s.executeQuery("SELECT count(*) FROM sqlite_master WHERE name='map_flight_node'")){assertThat(r.getInt(1)).isZero();}
    }
  }
  @Test void clipCrossingSegmentsAndRejectOutsideSegmentsWithoutInventingRoutePoints() {
    assertThat(MapLayersArchiveWorkflow.clip(-10,50,110,50)).containsExactly(0,50,100,50);
    assertThat(MapLayersArchiveWorkflow.clip(-10,-1,110,-1)).isNull();
    assertThat(MapLayersArchiveWorkflow.clip(10,10,20,20)).containsExactly(10,10,20,20);
  }
}
