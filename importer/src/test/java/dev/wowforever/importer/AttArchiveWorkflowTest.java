package dev.wowforever.importer;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.importer.archive.SourceArchive;
import dev.wowforever.importer.clientdata.ClientDataOptions;
import dev.wowforever.importer.source.SourceSpec;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AttArchiveWorkflowTest {
  @TempDir Path tmp;
  private PrintStream quiet(){return new PrintStream(OutputStream.nullOutputStream());}
  private String scalar(Path db,String sql)throws Exception{
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.createStatement();var r=s.executeQuery(sql)){
      assertThat(r.next()).isTrue();return r.getString(1);
    }
  }
  private Path att()throws Exception{
    Path root=tmp.resolve("att");
    for(String path:ArchiveWorkflow.attSpec().paths()){
      Path file=root.resolve(path);Files.createDirectories(file.getParent());Files.writeString(file,"");
    }
    return root;
  }
  private ImportOptions options(Path db,Path att){
    return new ImportOptions(db,tmp.resolve("cache"),Optional.of(Fixtures.questieDbRoot()),Optional.of(Fixtures.atlasLootRoot()),
        false,tmp.resolve("build-id.txt"),ClientDataOptions.skipped(),85,Optional.empty(),Optional.of(att),false);
  }
  @Test void pinnedUrlsEncodeSpacesUnicodeAndReservedPathCharacters(){
    var spec=new SourceSpec("test","owner","repo","pin",List.of());
    assertThat(spec.rawUrl(".contrib/Cozy Bag/é#1.lua").toASCIIString())
        .isEqualTo("https://raw.githubusercontent.com/owner/repo/pin/.contrib/Cozy%20Bag/%C3%A9%231.lua");
  }
  @Test void normalCaptureRetainsAttAndReplaysOnlyItsArchivedManifestAndPin()throws Exception{
    Path db=tmp.resolve("app.sqlite"),root=att();
    String path=".contrib/.db/forever/Quests/fixture.lua";
    // A manifest may differ from the importer's current pinned manifest in a later build.
    String id=ArchiveWorkflow.ingest(options(db,root));
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db)){
      c.setAutoCommit(false);
      var custom=new SourceSpec("allthethings","archived-owner","archived-repo","old-pin",List.of(path,"LICENSE"));
      String snapshot=SourceArchive.capture(c,new SourceArchive.Snapshot("allthethings","old-pin","forever","",""),List.of(
          new SourceArchive.Artifact(path,"", "q(5561, { [\"sourceQuests\"] = { 5142 } })".getBytes()),
          new SourceArchive.Artifact("LICENSE","", "MIT".getBytes())));
      var config=new ArrayList<>(ArchiveWorkflow.attConfiguration(custom));
      try(var query=c.prepareStatement("SELECT snapshot_id FROM source_ingestion_input WHERE ingestion_id=? AND role='configuration'")){
        query.setString(1,id);try(var r=query.executeQuery()){
          assertThat(r.next()).isTrue();Path extracted=tmp.resolve("configuration");SourceArchive.extract(c,r.getString(1),extracted);
          for(String name:List.of("sources.properties","projection.properties"))config.add(new SourceArchive.Artifact(name,"",Files.readAllBytes(extracted.resolve(name))));
        }
      }
      String configuration=SourceArchive.capture(c,new SourceArchive.Snapshot("import-configuration","1","","",""),config);
      try(var update=c.prepareStatement("UPDATE source_ingestion_input SET snapshot_id=? WHERE ingestion_id=? AND role=?")){
        update.setString(1,snapshot);update.setString(2,id);update.setString(3,"allthethings");update.executeUpdate();
        update.setString(1,configuration);update.setString(3,"configuration");update.executeUpdate();
      }
      c.commit();
    }
    try(var paths=Files.walk(root)){for(Path pathToDelete:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(pathToDelete);}
    assertThat(ArchiveWorkflow.project(db,id,quiet())).isZero();
    assertThat(scalar(db,"SELECT value FROM meta WHERE key='att_commit'")).isEqualTo("old-pin");
    assertThat(scalar(db,"SELECT related_id FROM quest_relation WHERE quest_id=5561 AND kind='pre_single'")).isEqualTo("5142");
    assertThat(scalar(db,"SELECT source_url FROM quest_relation_source WHERE quest_id=5561"))
        .contains("archived-owner/archived-repo/blob/old-pin/").endsWith("#L1");
    String build=scalar(db,"SELECT value FROM meta WHERE key='build_id'");
    assertThat(Files.readString(tmp.resolve("build-id.txt"))).isEqualTo(build);
    assertThat(Files.readString(tmp.resolve("import-report.txt"))).contains("allthethings.questLinksSelected = 1");
    assertThat(scalar(db,"SELECT validation_summary FROM source_projection")).contains("ATT selected=1");
    assertThat(ArchiveWorkflow.project(db,id,quiet())).isZero();
    assertThat(scalar(db,"SELECT value FROM meta WHERE key='build_id'")).isEqualTo(build);
  }
  @Test void localOverrideHasNoClaimedRemoteEvidenceAndChangesBuildIdentity()throws Exception {
    Path db=tmp.resolve("local.sqlite"),root=att();
    String path=ArchiveWorkflow.attSpec().paths().stream()
        .filter(dev.wowforever.importer.allthethings.AllTheThingsSource::isQuestInput).findFirst().orElseThrow();
    Files.writeString(root.resolve(path),"q(5561, { [\"sourceQuests\"] = { 5142 } })");
    String first=ArchiveWorkflow.ingest(options(db,root));assertThat(ArchiveWorkflow.project(db,first,quiet())).isZero();
    assertThat(scalar(db,"SELECT source_url FROM quest_relation_source WHERE quest_id=5561")).isEmpty();
    String build=scalar(db,"SELECT value FROM meta WHERE key='build_id'");
    Files.writeString(root.resolve(path),"\n-- local revision",StandardOpenOption.APPEND);
    String second=ArchiveWorkflow.ingest(options(db,root));assertThat(second).isNotEqualTo(first);
    assertThat(ArchiveWorkflow.project(db,second,quiet())).isZero();
    assertThat(scalar(db,"SELECT value FROM meta WHERE key='build_id'")).isNotEqualTo(build);
  }
  @Test void missingArchivedValidationSettingsRefusesReplayAndKeepsPublishedTables()throws Exception {
    Path db=tmp.resolve("settings.sqlite");
    var options=new ImportOptions(db,tmp.resolve("cache"),Optional.of(Fixtures.questieDbRoot()),Optional.of(Fixtures.atlasLootRoot()),
        false,ClientDataOptions.skipped());
    String id=ArchiveWorkflow.ingest(options);assertThat(ArchiveWorkflow.project(db,id,quiet())).isZero();
    String before=scalar(db,"SELECT value FROM meta WHERE key='build_id'");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+db)) {
      c.setAutoCommit(false);
      Path extracted=tmp.resolve("bad-settings");
      SourceArchive.extract(c,scalar(db,"SELECT snapshot_id FROM source_ingestion_input WHERE role='configuration'"),extracted);
      String snapshot=SourceArchive.capture(c,new SourceArchive.Snapshot("import-configuration","bad","","",""),
          List.of(new SourceArchive.Artifact("sources.properties","",Files.readAllBytes(extracted.resolve("sources.properties")))));
      try(var update=c.prepareStatement("UPDATE source_ingestion_input SET snapshot_id=? WHERE ingestion_id=? AND role='configuration'")) {
        update.setString(1,snapshot);update.setString(2,id);update.executeUpdate();
      }
      c.commit();
    }
    assertThatThrownBy(()->ArchiveWorkflow.project(db,id,quiet())).isInstanceOf(IOException.class);
    assertThat(scalar(db,"SELECT value FROM meta WHERE key='build_id'")).isEqualTo(before);
    assertThat(scalar(db,"SELECT count(*) FROM source_projection")).isEqualTo("1");
  }
  @Test void missingDeclaredAttInputFailsCaptureRatherThanPretendingCompleteness()throws Exception{
    Path root=att();Files.delete(root.resolve(ArchiveWorkflow.attSpec().paths().getFirst()));
    assertThatThrownBy(()->ArchiveWorkflow.ingest(options(tmp.resolve("app.sqlite"),root)))
        .isInstanceOf(IOException.class).hasMessageContaining("Missing AllTheThings source input");
  }
}
