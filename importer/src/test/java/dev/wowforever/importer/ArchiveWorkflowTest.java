package dev.wowforever.importer;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.importer.clientdata.ClientDataOptions;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveWorkflowTest {
  @TempDir Path tmp;
  private ImportOptions options(Path database, Path questie) {
    return new ImportOptions(database,tmp.resolve("cache"),Optional.of(questie),Optional.of(Fixtures.atlasLootRoot()),
        false,ClientDataOptions.skipped());
  }
  private Path questie() throws IOException {
    Path copy=tmp.resolve("questie");
    try(var paths=Files.walk(Fixtures.questieDbRoot())) {
      for(Path path:paths.toList()) {
        Path target=copy.resolve(Fixtures.questieDbRoot().relativize(path));
        if(Files.isDirectory(path)) Files.createDirectories(target); else Files.copy(path,target);
      }
    }
    return copy;
  }
  private String scalar(Path db,String sql) throws SQLException {
    try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db);Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)) {
      assertThat(r.next()).isTrue(); return r.getString(1);
    }
  }
  private PrintStream quiet() { return new PrintStream(OutputStream.nullOutputStream()); }

  @Test void independentCaptureAndOfflineProjectionRetainHistoryAndCanReplayOlderInput() throws Exception {
    Path db=tmp.resolve("app.sqlite"),root=questie();
    var opts=options(db,root);
    String first=ArchiveWorkflow.ingest(opts);
    assertThat(scalar(db,"SELECT count(*) FROM sqlite_master WHERE name='quest'")).isEqualTo("0");
    assertThat(ArchiveWorkflow.ingest(opts)).isEqualTo(first);
    assertThat(ArchiveWorkflow.project(db,first,quiet())).isZero();
    String count=scalar(db,"SELECT count(*) FROM quest");
    Path input=root.resolve("data/Forever/foreverQuestDB.lua");
    Files.writeString(input,"\n-- Changed original input, without changing app facts\n",StandardOpenOption.APPEND);
    String second=ArchiveWorkflow.ingest(opts);
    assertThat(second).isNotEqualTo(first);
    // No original input or cache can be used during replay.
    try(var paths=Files.walk(root)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}
    assertThat(ArchiveWorkflow.project(db,second,quiet())).isZero();
    assertThat(ArchiveWorkflow.project(db,first,quiet())).isZero();
    assertThat(scalar(db,"SELECT count(*) FROM source_ingestion")).isEqualTo("2");
    assertThat(scalar(db,"SELECT count(*) FROM source_projection")).isEqualTo("3");
    assertThat(scalar(db,"SELECT value FROM meta WHERE key='source_projection_id'")).isEqualTo("3");
    assertThat(scalar(db,"SELECT schema_version FROM source_projection WHERE id=3")).isEqualTo("4");
    assertThat(scalar(db,"SELECT build_id FROM source_projection WHERE id=3"))
        .isEqualTo(scalar(db,"SELECT value FROM meta WHERE key='build_id'"));
    assertThat(scalar(db,"SELECT validation_summary FROM source_projection WHERE id=3"))
        .contains("UI skips=", "unsupported fields=", "client data skipped=");
    assertThat(scalar(db,"SELECT value FROM meta WHERE key='source_ingestion_id'")).isEqualTo(first);
    assertThat(scalar(db,"SELECT count(*) FROM quest")).isEqualTo(count);
    String beforeSnapshot=scalar(db,"SELECT snapshot_id FROM source_ingestion_input WHERE role='questiedb' AND ingestion_id='"+first+"'");
    String afterSnapshot=scalar(db,"SELECT snapshot_id FROM source_ingestion_input WHERE role='questiedb' AND ingestion_id='"+second+"'");
    var output=new ByteArrayOutputStream();
    assertThat(Main.run(new String[]{"diff","--database",db.toString(),"--before",beforeSnapshot,"--after",afterSnapshot},
        new PrintStream(output),quiet())).isZero();
    assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8)).contains("file\tCHANGED\tdata/Forever/foreverQuestDB.lua")
        .doesNotContain("record\t"); // a source comment changed; quest facts did not
    assertThat(Files.readString(tmp.resolve("build-id.txt"))).hasSize(64);
    assertThat(Files.exists(tmp.resolve("import-report.txt"))).isTrue();
  }

  @Test void failedProjectionKeepsBadSourceAndPreviousUsableAppTables() throws Exception {
    Path db=tmp.resolve("app.sqlite"),root=questie();var opts=options(db,root);
    String good=ArchiveWorkflow.ingest(opts);
    assertThat(ArchiveWorkflow.project(db,good,quiet())).isZero();
    String before=scalar(db,"SELECT count(*) FROM item");
    Files.writeString(root.resolve("src/meta/itemMeta.lua"),"not valid metadata");
    String bad=ArchiveWorkflow.ingest(opts);
    assertThatThrownBy(()->ArchiveWorkflow.project(db,bad,quiet())).isInstanceOf(Exception.class);
    assertThat(scalar(db,"SELECT count(*) FROM item")).isEqualTo(before);
    assertThat(scalar(db,"SELECT value FROM meta WHERE key='source_ingestion_id'")).isEqualTo(good);
    assertThat(scalar(db,"SELECT count(*) FROM source_ingestion")).isEqualTo("2");
    assertThat(scalar(db,"SELECT count(*) FROM source_projection")).isEqualTo("1");
  }

  @Test void capturedClientDataIncludesUnusedFilesAndBuildMetadata() throws Exception {
    Path client=tmp.resolve("client");Files.createDirectories(client);
    Files.writeString(client.resolve("build.lock"),"{\"product\":\"wow_classic_beta\",\"build\":\"1.60.1.70450\",\"locale\":\"enUS\"}");
    Files.writeString(client.resolve("not-used.csv"),"ID,UnknownField\n999999,new data\n");
    Path db=tmp.resolve("app.sqlite");
    var opts=new ImportOptions(db,tmp.resolve("cache"),Optional.of(Fixtures.questieDbRoot()),
        Optional.of(Fixtures.atlasLootRoot()),false,ClientDataOptions.of(client));
    String first=ArchiveWorkflow.ingest(opts);
    assertThat(scalar(db,"SELECT build FROM source_archive_snapshot WHERE source='blizzard-client'")).isEqualTo("1.60.1.70450");
    assertThat(scalar(db,"SELECT count(*) FROM source_archive_file WHERE path='not-used.csv'")).isEqualTo("1");
    Files.writeString(client.resolve("build.lock"),"{\"product\":\"wow_classic_beta\",\"build\":\"1.60.1.70451\",\"locale\":\"enUS\"}");
    String second=ArchiveWorkflow.ingest(opts);
    String beforeSnapshot=scalar(db,"SELECT snapshot_id FROM source_ingestion_input WHERE role='client' AND ingestion_id='"+first+"'");
    String afterSnapshot=scalar(db,"SELECT snapshot_id FROM source_ingestion_input WHERE role='client' AND ingestion_id='"+second+"'");
    var output=new ByteArrayOutputStream();
    assertThat(Main.run(new String[]{"diff","--database",db.toString(),"--before",beforeSnapshot,"--after",afterSnapshot},
        new PrintStream(output),quiet())).isZero();
    assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8)).contains("field\tchanged\tbuild.lock\t@json\t/build\t\"1.60.1.70450\"\t\"1.60.1.70451\"");
  }

  @Test void legacyProjectionHistoryIsPreservedWhenNewMetadataColumnsAreAdded() throws Exception {
    Path db=tmp.resolve("app.sqlite");String id=ArchiveWorkflow.ingest(options(db,Fixtures.questieDbRoot()));
    assertThat(ArchiveWorkflow.project(db,id,quiet())).isZero();
    try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db);Statement statement=c.createStatement()) {
      for(String column:List.of("schema_version","build_id","validation_summary"))
        statement.execute("ALTER TABLE source_projection DROP COLUMN "+column);
      statement.execute("DELETE FROM meta WHERE key='source_projection_id'");
    }
    assertThat(ArchiveWorkflow.project(db,id,quiet())).isZero();
    assertThat(scalar(db,"SELECT count(*) FROM source_projection")).isEqualTo("2");
    assertThat(scalar(db,"SELECT schema_version FROM source_projection WHERE id=1")).isNull();
    assertThat(scalar(db,"SELECT schema_version FROM source_projection WHERE id=2")).isEqualTo("4");
    assertThat(scalar(db,"SELECT value FROM meta WHERE key='source_projection_id'")).isEqualTo("2");
  }

  @Test void sidecarExportFailureDoesNotReportThePublishedDatabaseAsFailed() throws Exception {
    Path folder=tmp.resolve("published");Files.createDirectories(folder);
    Path db=folder.resolve("app.sqlite");String id=ArchiveWorkflow.ingest(options(db,Fixtures.questieDbRoot()));
    Files.createDirectories(folder.resolve("build-id.txt"));
    Files.writeString(folder.resolve("build-id.txt/keep.txt"),"existing content");
    var errors=new ByteArrayOutputStream();
    assertThat(ArchiveWorkflow.project(db,id,quiet(),new PrintStream(errors))).isZero();
    assertThat(scalar(db,"SELECT value FROM meta WHERE key='source_ingestion_id'")).isEqualTo(id);
    assertThat(errors.toString(java.nio.charset.StandardCharsets.UTF_8)).contains("Database published successfully; could not export build-id.txt");
    assertThat(Files.readString(folder.resolve("build-id.txt/keep.txt"))).isEqualTo("existing content");
  }

  @Test void outputCollisionsAreRejectedBeforeChangingAnyDatabase() throws Exception {
    Path db=tmp.resolve("app.sqlite");Files.writeString(db,"existing database sentinel");
    var opts=new ImportOptions(db,tmp.resolve("cache"),Optional.of(Fixtures.questieDbRoot()),
        Optional.of(Fixtures.atlasLootRoot()),false,db,ClientDataOptions.skipped());
    assertThatThrownBy(()->ArchiveWorkflow.ingest(opts)).hasMessageContaining("different files");
    assertThat(Files.readString(db)).isEqualTo("existing database sentinel");
    for (String name:List.of("build-id.txt","import-report.txt")) {
      Path collision=tmp.resolve(name);
      assertThatThrownBy(()->ArchiveWorkflow.ingest(options(collision,Fixtures.questieDbRoot())))
          .hasMessageContaining("different files");
      assertThatThrownBy(()->ArchiveWorkflow.project(collision,"unknown",quiet()))
          .hasMessageContaining("different files");
      assertThat(Files.exists(collision)).isFalse();
    }
  }

  @Test void hostileInputRoleIsRejectedBeforeExtractingAnyFiles() throws Exception {
    Path db=tmp.resolve("app.sqlite");String id=ArchiveWorkflow.ingest(options(db,Fixtures.questieDbRoot()));
    try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db);Statement s=c.createStatement()) {
      s.executeUpdate("UPDATE source_ingestion_input SET role='../escaped' WHERE role='configuration'");
    }
    assertThatThrownBy(()->ArchiveWorkflow.project(db,id,quiet())).isInstanceOf(Exception.class);
    assertThat(Files.exists(tmp.resolve("escaped"))).isFalse();
  }
}
