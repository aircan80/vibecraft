package dev.wowforever.importer;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.importer.archive.SourceArchive;
import dev.wowforever.importer.archive.records.SourceRecords;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CollectorArchiveWorkflowTest {
  @TempDir Path tmp;
  private static final String SAMPLE = "WowForeverCollectorDB = {schema=1, session={addonVersion='1.1.0',build='1.60.1',buildNumber='70450',interface=16001,locale='enUS'}, loot={{lootKind='pickpocket',source={spawnKey='invented'},future={raw='kept'}}}, futureField={x=42}}";
  private Path input(String text) throws IOException { return Files.writeString(tmp.resolve("observations.lua"),text); }
  private Connection connect(Path db) throws SQLException { return DriverManager.getConnection("jdbc:sqlite:"+db); }
  private String scalar(Connection c,String sql) throws SQLException {
    try(var s=c.createStatement();var r=s.executeQuery(sql)){assertThat(r.next()).isTrue();return r.getString(1);}
  }
  @Test void archivesExactlyAndIdempotentlyWithoutChangingApplicationData() throws Exception {
    Path db=tmp.resolve("app.sqlite"),file=input(SAMPLE);
    try(var c=connect(db);var s=c.createStatement()) {
      s.execute("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)");
      s.execute("INSERT INTO meta VALUES ('build_id','existing-build')");
      s.execute("CREATE TABLE quest(id INTEGER PRIMARY KEY,title TEXT)");
      s.execute("INSERT INTO quest VALUES (1,'Preserve me')");
    }
    String first=CollectorArchiveWorkflow.ingest(db,file);
    assertThat(CollectorArchiveWorkflow.ingest(db,file)).isEqualTo(first);
    try(var c=connect(db)) {
      assertThat(scalar(c,"SELECT value FROM meta WHERE key='build_id'")).isEqualTo("existing-build");
      assertThat(scalar(c,"SELECT title FROM quest WHERE id=1")).isEqualTo("Preserve me");
      assertThat(scalar(c,"SELECT count(*) FROM source_projection")).isEqualTo("0");
      assertThat(scalar(c,"SELECT count(*) FROM source_ingestion")).isEqualTo("1");
      var snapshot=SourceArchive.snapshots(c).getFirst();
      assertThat(first).isEqualTo(ArchiveWorkflow.hash("collector="+snapshot.id()));
      assertThat(snapshot.metadata().build()).isEqualTo("1.60.1.70450");
      assertThat(SourceArchive.artifacts(c,snapshot.id()).getFirst().bytes()).isEqualTo(Files.readAllBytes(file));
      assertThat(SourceArchive.artifacts(c,snapshot.id()).getFirst().path()).isEqualTo("WowForeverCollector.lua");
      assertThat(SourceRecords.records(c,snapshot.id()).toString()).contains("futureField","pickpocket","kept");
    }
    assertThatThrownBy(() -> ArchiveWorkflow.project(db,first,System.out)).hasMessageContaining("archive-only");
    Files.writeString(file,"\r\n-- another capture\r\n",StandardOpenOption.APPEND);
    assertThat(CollectorArchiveWorkflow.ingest(db,file)).isNotEqualTo(first);
    try(var c=connect(db)) { assertThat(scalar(c,"SELECT count(*) FROM source_archive_snapshot")).isEqualTo("2"); }
  }
  @Test void multipleCapturesAreOrderIndependentAndPreserveEachSnapshot() throws Exception {
    Path first=input(SAMPLE), second=Files.writeString(tmp.resolve("second.lua"),
        SAMPLE.replace("70450","70451").replace("enUS","deDE"));
    Path db=tmp.resolve("multi.sqlite");
    String id=CollectorArchiveWorkflow.ingest(db,java.util.List.of(first,second));
    assertThat(CollectorArchiveWorkflow.ingest(db,java.util.List.of(second,first,first))).isEqualTo(id);
    try(var c=connect(db)) {
      assertThat(scalar(c,"SELECT count(*) FROM source_ingestion")).isEqualTo("1");
      assertThat(scalar(c,"SELECT count(*) FROM source_ingestion_input WHERE role LIKE 'collector-%'" )).isEqualTo("2");
      var snapshots=SourceArchive.snapshots(c);
      assertThat(snapshots).hasSize(2);
      assertThat(snapshots).extracting(snap -> snap.metadata().build()).containsExactlyInAnyOrder("1.60.1.70450","1.60.1.70451");
      for(var snapshot:snapshots) {
        byte[] expected=Files.readAllBytes(snapshot.metadata().locale().equals("enUS") ? first : second);
        assertThat(SourceArchive.artifacts(c,snapshot.id()).getFirst().bytes()).isEqualTo(expected);
        assertThat(SourceRecords.statuses(c,snapshot.id()).getFirst().status()).isEqualTo("complete");
      }
    }
    Path single=tmp.resolve("single.sqlite");
    assertThat(CollectorArchiveWorkflow.ingest(single,java.util.List.of(first,first)))
        .isEqualTo(CollectorArchiveWorkflow.ingest(single,first));
  }
  @Test void invalidLaterCaptureDoesNotCreateOrModifyDatabase() throws Exception {
    Path first=input(SAMPLE), bad=Files.writeString(tmp.resolve("bad.lua"),SAMPLE.replace("schema=1","schema=2"));
    Path db=tmp.resolve("all-or-none.sqlite");
    assertThatThrownBy(() -> CollectorArchiveWorkflow.ingest(db,java.util.List.of(first,bad))).hasMessageContaining("schema");
    assertThat(db).doesNotExist();
    CollectorArchiveWorkflow.ingest(db,first);
    byte[] before=Files.readAllBytes(db);
    assertThatThrownBy(() -> CollectorArchiveWorkflow.ingest(db,java.util.List.of(first,bad))).hasMessageContaining("schema");
    assertThat(Files.readAllBytes(db)).isEqualTo(before);
  }
  @Test void preservesBadUtf8AndIndexesWithExplicitPartialDiagnostic() throws Exception {
    Path file=input(SAMPLE.replace("kept","badBYTE"));
    byte[] bytes=Files.readAllBytes(file);
    for(int i=0;i<bytes.length;i++) if(bytes[i]=='B' && i+3<bytes.length && bytes[i+1]=='Y') bytes[i]=(byte)0xff;
    Files.write(file,bytes);
    Path db=tmp.resolve("research.sqlite");
    CollectorArchiveWorkflow.ingest(db,file);
    try(var c=connect(db)) {
      String snapshot=SourceArchive.snapshots(c).getFirst().id();
      assertThat(SourceArchive.artifacts(c,snapshot).getFirst().bytes()).isEqualTo(bytes);
      assertThat(SourceRecords.statuses(c,snapshot).getFirst().status()).isEqualTo("partial");
      assertThat(SourceRecords.statuses(c,snapshot).getFirst().detail()).contains("malformed UTF-8");
      assertThat(SourceRecords.records(c,snapshot).toString()).contains("futureField");
    }
  }
  @Test void rejectsUnsupportedOrExecutableInputsBeforeCreatingDatabase() throws Exception {
    Path db=tmp.resolve("no.sqlite");
    for(String invalid:new String[]{SAMPLE.replace("schema=1","schema=2"), SAMPLE.replace("1.60.1","1.15.0"),
        SAMPLE.replace("interface=16001","interface=11500"), SAMPLE.replace(",locale='enUS'",""),
        "WowForeverCollectorDB = {}", "WrongDB = {}", SAMPLE+"; os.execute('anything')",
        SAMPLE.replace("x=42","x=unknown()")}) {
      assertThatThrownBy(() -> CollectorArchiveWorkflow.ingest(db,input(invalid))).isInstanceOf(Exception.class);
      assertThat(db).doesNotExist();
    }
  }
  @Test void refusesAliasesAndSymlinksWithoutTouchingInput() throws Exception {
    Path file=input(SAMPLE);
    assertThatThrownBy(() -> CollectorArchiveWorkflow.ingest(file,file)).hasMessageContaining("must differ");
    Path alias=tmp.resolve("alias.sqlite");Files.createLink(alias,file);
    assertThatThrownBy(() -> CollectorArchiveWorkflow.ingest(alias,file)).hasMessageContaining("must differ");
    Path link=tmp.resolve("link.lua");Files.createSymbolicLink(link,file);
    assertThatThrownBy(() -> CollectorArchiveWorkflow.ingest(tmp.resolve("out.sqlite"),link)).hasMessageContaining("Symbolic");
    Path lock=tmp.resolve("out.sqlite.lock");Files.createSymbolicLink(lock,file);
    assertThatThrownBy(() -> CollectorArchiveWorkflow.ingest(tmp.resolve("out.sqlite"),file)).hasMessageContaining("Symbolic");
    assertThat(Files.readString(file)).isEqualTo(SAMPLE);
  }
  @Test void cliRequiresExplicitInputAndReportsCaptureOnly() throws Exception {
    var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();
    Path file=input(SAMPLE),db=tmp.resolve("cli.sqlite");
    assertThat(Main.run(new String[]{"ingest-collector","--database",db.toString(),"--collector-file",file.toString()},
        new PrintStream(output),new PrintStream(error))).isZero();
    assertThat(output.toString(StandardCharsets.UTF_8)).contains("archive-only","No application facts projected");
    Path second=Files.writeString(tmp.resolve("cli-second.lua"),SAMPLE.replace("70450","70451"));
    assertThat(Main.run(new String[]{"ingest-collector","--database",db.toString(),"--collector-file",file.toString(),
        "--collector-file",second.toString()},new PrintStream(output),new PrintStream(error))).isZero();
    assertThat(Main.run(new String[]{"ingest-collector"},new PrintStream(output),new PrintStream(error))).isEqualTo(1);
  }
}
