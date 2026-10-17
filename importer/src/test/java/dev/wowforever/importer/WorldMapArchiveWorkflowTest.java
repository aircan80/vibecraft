package dev.wowforever.importer;

import dev.wowforever.importer.clientdata.CsvTable;
import java.io.StringReader;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class WorldMapArchiveWorkflowTest {
  @TempDir Path temporary;
  private static final String HEADER="ID,UiMapID,OrderIndex,MapID,UiMin_0,UiMin_1,UiMax_0,UiMax_1,Region_0,Region_1,Region_3,Region_4\n";
  @Test void projectsSwappedReversedWorldAxesAndLeavesUnmatchedMapsUnplaced() throws Exception {
    var table=CsvTable.read("assignments",new StringReader(HEADER+"1,1415,0,0,0,0,1,1,0,0,100,200\n2,1436,0,0,0,0,1,1,20,40,40,100\n3,2521,0,2991,0,0,1,1,0,0,100,200\n"));
    var groups=table.groupByLong("UiMapID");
    assertThat(WorldMapArchiveWorkflow.placement(groups.get(1415L),groups.get(1436L))).containsExactly(.5,.6,.8,.8);
    assertThat(WorldMapArchiveWorkflow.placement(groups.get(1415L),groups.get(2521L))).isNull();
  }
  @Test void rootUsesAuthoredContinentUiRectangle() throws Exception {
    var table=CsvTable.read("assignments",new StringReader(HEADER+"1,947,0,0,.55,.1,.9,.87,0,0,100,200\n2,1415,0,0,0,0,1,1,-50,-50,150,250\n"));
    var groups=table.groupByLong("UiMapID");
    assertThat(WorldMapArchiveWorkflow.continentPlacement(groups.get(947L),groups.get(1415L))).containsExactly(.55,.1,.9,.87);
  }
  @Test void projectionReplaysWithoutSourceFilesAndFailedProjectionRetainsCapture() throws Exception {
    Path db=temporary.resolve("replay.sqlite"),maps=temporary.resolve("UiMap.1.60.1.70374.csv"),assignments=temporary.resolve("UiMapAssignment.1.60.1.70374.csv");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var statement=c.createStatement()) {
      statement.execute("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)");
      statement.execute("INSERT INTO meta VALUES ('build_id','original')");
    }
    Files.writeString(maps,"Name_lang,ID,ParentUiMapID,Type\nAzeroth,947,0,1\n");
    Files.writeString(assignments,HEADER);
    String good=WorldMapArchiveWorkflow.ingest(db,maps,assignments,WorldMapArchiveWorkflow.ATT_COMMIT);
    Files.delete(maps);Files.delete(assignments);
    WorldMapArchiveWorkflow.project(db,good);
    Files.writeString(maps,"Name_lang,ID,ParentUiMapID,Type\nMissing root,1,0,1\n");
    Files.writeString(assignments,HEADER);
    String bad=WorldMapArchiveWorkflow.ingest(db,maps,assignments,WorldMapArchiveWorkflow.ATT_COMMIT);
    assertThatThrownBy(() -> WorldMapArchiveWorkflow.project(db,bad)).isInstanceOf(IllegalArgumentException.class);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var statement=c.createStatement()) {
      assertThat(dev.wowforever.importer.archive.SourceArchive.artifacts(c,bad)).hasSize(2);
      try(var r=statement.executeQuery("SELECT value FROM meta WHERE key='world_map_snapshot_id'")){assertThat(r.getString(1)).isEqualTo(good);}
      try(var r=statement.executeQuery("SELECT count(*) FROM ui_map WHERE id=947")){assertThat(r.getInt(1)).isEqualTo(1);}
    }
  }

  @Test void archiveIsIdempotentAndPreservesExistingTablesAndActiveHistory() throws Exception {
    Path db=temporary.resolve("candidate.sqlite"), maps=temporary.resolve("UiMap.1.60.1.70374.csv"), assignments=temporary.resolve("UiMapAssignment.1.60.1.70374.csv");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.createStatement()) {
      s.execute("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)");s.execute("INSERT INTO meta VALUES ('build_id','original'),('source_ingestion_id','old')");
      ArchiveWorkflow.initialize(c);
      s.execute("INSERT INTO source_ingestion VALUES ('old','before')");
      s.execute("INSERT INTO source_ingestion_input VALUES ('old','allthethings','old-att')");
      s.execute("CREATE TABLE sentinel(value TEXT)");s.execute("INSERT INTO sentinel VALUES ('preserved')");
    }
    Files.writeString(maps,"Name_lang,ID,ParentUiMapID,Type\nAzeroth,947,0,1\nEastern Kingdoms,1415,947,2\nWestfall,1436,1415,3\nZephras Isle,2521,947,3\n");
    Files.writeString(assignments,HEADER+"1,947,0,0,.55,.1,.9,.87,0,0,100,200\n2,1415,0,0,0,0,1,1,0,0,100,200\n3,1436,0,0,0,0,1,1,20,40,40,100\n4,2521,0,2991,0,0,1,1,0,0,100,200\n");
    String snapshot=WorldMapArchiveWorkflow.apply(db,maps,assignments,WorldMapArchiveWorkflow.ATT_COMMIT);
    String firstBuild;
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.createStatement();var r=s.executeQuery("SELECT value FROM meta WHERE key='build_id'")) { firstBuild=r.getString(1); }
    assertThat(firstBuild).isNotEqualTo("original");
    assertThat(WorldMapArchiveWorkflow.apply(db,maps,assignments,WorldMapArchiveWorkflow.ATT_COMMIT)).isEqualTo(snapshot);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.createStatement()) {
      try(var r=s.executeQuery("SELECT value FROM sentinel")){assertThat(r.getString(1)).isEqualTo("preserved");}
      try(var r=s.executeQuery("SELECT value FROM meta WHERE key='source_ingestion_id'")){assertThat(r.getString(1)).isNotEqualTo("old");}
      try(var r=s.executeQuery("SELECT value FROM meta WHERE key='build_id'")){assertThat(r.getString(1)).isEqualTo(firstBuild);}
      try(var r=s.executeQuery("SELECT count(*) FROM source_ingestion_input WHERE role='allthethings' AND snapshot_id='old-att'")){assertThat(r.getInt(1)).isEqualTo(2);}
      try(var r=s.executeQuery("SELECT count(*) FROM world_map_projection")){assertThat(r.getInt(1)).isEqualTo(1);}
      try(var r=s.executeQuery("SELECT x0 FROM ui_map_child WHERE child_ui_map_id=2521")){assertThat(r.getObject(1)).isNull();}
      try(var r=s.executeQuery("SELECT count(*) FROM source_archive_file")){assertThat(r.getInt(1)).isEqualTo(2);}
    }
  }
}
