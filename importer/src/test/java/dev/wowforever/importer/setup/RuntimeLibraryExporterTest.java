package dev.wowforever.importer.setup;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeLibraryExporterTest {
  @TempDir Path root;
  private Path source()throws Exception {
    Path source=root.resolve("staged.sqlite");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+source);var s=c.createStatement()) {
      s.execute("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)");
      s.execute("INSERT INTO meta VALUES('schema_version','4'),('build_id','"+"a".repeat(64)+"'),('client_product','wow_classic_beta'),('client_build','1.60.1.70450')");
      s.execute("CREATE TABLE source_record(id INTEGER PRIMARY KEY,payload BLOB)");s.execute("INSERT INTO source_record VALUES(1,zeroblob(8192))");
      s.execute("CREATE TABLE source_archive_blob(sha256 TEXT PRIMARY KEY,bytes BLOB NOT NULL)");s.execute("INSERT INTO source_archive_blob VALUES('native-sha',zeroblob(32768))");
      s.execute("CREATE TABLE source_archive_file(id INTEGER PRIMARY KEY,blob_sha256 TEXT REFERENCES source_archive_blob(sha256))");s.execute("INSERT INTO source_archive_file VALUES(1,'native-sha')");
      s.execute("CREATE TABLE field_knowledge_assertion(id INTEGER,edition TEXT,value TEXT,source TEXT)");s.execute("CREATE UNIQUE INDEX field_knowledge_assertion_identity ON field_knowledge_assertion(id,edition)");
      s.execute("INSERT INTO field_knowledge_assertion VALUES(1,'forever','conflictA','native'),(2,'forever','conflictB','collector'),(1,'era','comparison','era')");
      s.execute("CREATE TABLE typed_values(i INTEGER,r REAL,t TEXT,b BLOB,n TEXT)");s.execute("CREATE INDEX typed_values_index ON typed_values(i)");s.execute("INSERT INTO typed_values VALUES(1,1.25,'text',x'0001',NULL),(1,1.25,'text',x'0001',NULL)");
    }return source;
  }
  private SetupStorage storage()throws Exception{return new SetupStorage(root,List.of(root.resolve("game")),Optional.of(root.resolve("active.sqlite")));}
  private RuntimeLibraryExporter.Receipt export(Path source,Path output)throws Exception{return new RuntimeLibraryExporter().export(source,output,storage(),Files.size(source),RuntimeLibraryExporter.hashFile(source,()->false),()->false);}
  @Test void retainsAllRuntimeTablesAndMetadataAndSourceIsUnchanged()throws Exception {
    Path source=source(),out=root.resolve("runtime.sqlite");String original=RuntimeLibraryExporter.hashFile(source,()->false);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+source);var statement=c.createStatement()) {
      statement.execute("CREATE TABLE spell_text_evidence(owner_kind TEXT,owner_id INTEGER,ordinal INTEGER,status TEXT,spell_id INTEGER,raw_text TEXT,unresolved TEXT,source TEXT,product TEXT,build TEXT,table_sha256 TEXT)");
      statement.execute("INSERT INTO spell_text_evidence VALUES('tooltip',1,0,'INCOMPLETE',42,'Restores $q1','$q1','csv/Spell.csv','wow_classic_beta','1.60.1.70450','recorded-sha')");
    }
    original=RuntimeLibraryExporter.hashFile(source,()->false);
    var receipt=export(source,out);
    assertThat(receipt.retainedTables().get("spell_text_evidence").rows()).isEqualTo(1);
    assertThat(receipt.omittedRawRecords()).isEqualTo(1);assertThat(receipt.omittedArchiveBytes()).isEqualTo(32768);
    assertThat(receipt.retainedTables().get("field_knowledge_assertion").rows()).isEqualTo(3);
    assertThat(receipt.retainedTables().get("typed_values").rows()).isEqualTo(2);
    assertThat(RuntimeLibraryExporter.hashFile(source,()->false)).isEqualTo(original);
    assertThat(receipt.outputSha256()).isEqualTo(RuntimeLibraryExporter.hashFile(out,()->false));
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+out);var s=c.createStatement()) {
      try(var r=s.executeQuery("SELECT bytes,original_size,storage_state FROM source_archive_blob")){assertThat(r.next()).isTrue();assertThat(r.getBytes(1)).isNull();assertThat(r.getInt(2)).isEqualTo(32768);assertThat(r.getString(3)).isEqualTo("omitted-release");}
      try(var r=s.executeQuery("SELECT count(*) FROM source_record")){r.next();assertThat(r.getInt(1)).isZero();}
      try(var r=s.executeQuery("SELECT value FROM meta WHERE key='client_build'")){r.next();assertThat(r.getString(1)).isEqualTo("1.60.1.70450");}
    }
    assertThatThrownBy(()->export(out,root.resolve("again.sqlite"))).isInstanceOf(java.io.IOException.class).hasMessageContaining("already a runtime");
  }
  @Test void refusesIncorrectSourceIdentityAndCleansTemporary()throws Exception {
    Path source=source(),out=root.resolve("runtime.sqlite");
    assertThatThrownBy(()->new RuntimeLibraryExporter().export(source,out,storage(),Files.size(source),"0".repeat(64),()->false)).isInstanceOf(java.io.IOException.class);
    assertThat(out).doesNotExist();assertNoTemporary();
    assertThatThrownBy(()->new RuntimeLibraryExporter().export(source,out,storage(),Files.size(source)+1,RuntimeLibraryExporter.hashFile(source,()->false),()->false)).isInstanceOf(java.io.IOException.class);
    assertNoTemporary();
  }
  @Test void refusesExistingActiveGameAndSidecarDestinations()throws Exception {
    Path source=source(),existing=root.resolve("existing.sqlite");Files.writeString(existing,"keep");
    assertThatThrownBy(()->export(source,existing)).isInstanceOf(java.io.IOException.class);assertThat(Files.readString(existing)).isEqualTo("keep");
    assertThatThrownBy(()->export(source,root.resolve("active.sqlite"))).isInstanceOf(java.io.IOException.class);
    Files.createDirectories(root.resolve("game"));assertThatThrownBy(()->export(source,root.resolve("game/output.sqlite"))).isInstanceOf(java.io.IOException.class);
    Files.writeString(source.resolveSibling("staged.sqlite-wal"),"pending");assertThatThrownBy(()->export(source,root.resolve("runtime.sqlite"))).isInstanceOf(java.io.IOException.class).hasMessageContaining("sidecars");
  }
  @Test void cancellationAfterCopyNeverPublishesOutput()throws Exception {
    Path source=source(),out=root.resolve("runtime.sqlite");String hash=RuntimeLibraryExporter.hashFile(source,()->false);var polls=new AtomicInteger();
    assertThatThrownBy(()->new RuntimeLibraryExporter().export(source,out,storage(),Files.size(source),hash,()->polls.incrementAndGet()>8)).isInstanceOf(CancellationException.class);
    assertThat(out).doesNotExist();assertNoTemporary();
  }
  @Test void foreignKeyFailureNeverPublishes()throws Exception {
    Path source=source();try(var c=DriverManager.getConnection("jdbc:sqlite:"+source);var s=c.createStatement()){s.execute("INSERT INTO source_archive_file VALUES(2,'missing')");}
    assertThatThrownBy(()->export(source,root.resolve("runtime.sqlite"))).isInstanceOf(java.io.IOException.class).hasMessageContaining("foreign keys");assertNoTemporary();
  }
  @Test void readOnlyVerificationReDerivesActualSourceAndRuntime()throws Exception {
    Path source=source(),runtime=root.resolve("runtime.sqlite");var receipt=export(source,runtime);
    var verified=new RuntimeLibraryExporter().verifyExport(source,runtime,storage(),receipt,()->false);
    assertThat(verified.fullSource()).isEqualTo(source.toRealPath());assertThat(verified.runtime()).isEqualTo(runtime.toRealPath());
    assertThat(RuntimeLibraryExporter.hashFile(source,()->false)).isEqualTo(receipt.sourceSha256());
    assertThat(RuntimeLibraryExporter.hashFile(runtime,()->false)).isEqualTo(receipt.outputSha256());assertNoTemporary();
  }
  @Test void forgedUpdatedOutputHashCannotHideChangedRuntimeRowsOrForgedDigests()throws Exception {
    Path source=source(),runtime=root.resolve("runtime.sqlite");var receipt=export(source,runtime);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+runtime);var s=c.createStatement()){s.execute("UPDATE field_knowledge_assertion SET value='forged' WHERE id=2");}
    var forged=new HashMap<>(receipt.retainedTables());forged.put("field_knowledge_assertion",new RuntimeLibraryExporter.Digest(3,"0".repeat(64)));
    var updated=updatedOutput(receipt,runtime,forged);
    assertThatThrownBy(()->new RuntimeLibraryExporter().verifyExport(source,runtime,storage(),updated,()->false)).isInstanceOf(java.io.IOException.class).hasMessageContaining("Retained table");
  }
  @Test void forgedReceiptMissingTablesOrWrongOmissionCountsIsRejected()throws Exception {
    Path source=source(),runtime=root.resolve("runtime.sqlite");var receipt=export(source,runtime);
    var missing=new HashMap<>(receipt.retainedTables());missing.remove("typed_values");
    assertThatThrownBy(()->new RuntimeLibraryExporter().verifyExport(source,runtime,storage(),updatedOutput(receipt,runtime,missing),()->false)).isInstanceOf(java.io.IOException.class).hasMessageContaining("table set");
    var wrongCounts=new RuntimeLibraryExporter.Receipt(receipt.sourceBytes(),receipt.sourceSha256(),receipt.outputBytes(),receipt.outputSha256(),receipt.buildId(),receipt.omittedRawRecords()+1,receipt.omittedArchiveBytes(),receipt.retainedTables());
    assertThatThrownBy(()->new RuntimeLibraryExporter().verifyExport(source,runtime,storage(),wrongCounts,()->false)).isInstanceOf(java.io.IOException.class).hasMessageContaining("counts");
  }
  @Test void forgedOutputHashCannotHideArchiveIdentityOrExtraMetadata()throws Exception {
    Path source=source(),runtime=root.resolve("runtime.sqlite");var receipt=export(source,runtime);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+runtime);var s=c.createStatement()){s.execute("UPDATE source_archive_blob SET original_size=original_size+1");}
    assertThatThrownBy(()->new RuntimeLibraryExporter().verifyExport(source,runtime,storage(),updatedOutput(receipt,runtime,receipt.retainedTables()),()->false)).isInstanceOf(java.io.IOException.class).hasMessageContaining("Archive identities");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+runtime);var s=c.createStatement()){s.execute("UPDATE source_archive_blob SET original_size=original_size-1");s.execute("INSERT INTO meta VALUES('unexpected','forged')");}
    assertThatThrownBy(()->new RuntimeLibraryExporter().verifyExport(source,runtime,storage(),updatedOutput(receipt,runtime,receipt.retainedTables()),()->false)).isInstanceOf(java.io.IOException.class).hasMessageContaining("metadata keys");
  }
  @Test void verificationRejectsActiveInputsAndCancellationWithoutWrites()throws Exception {
    Path source=source(),runtime=root.resolve("runtime.sqlite");var receipt=export(source,runtime);
    var activeStorage=new SetupStorage(root,List.of(),Optional.of(runtime));
    assertThatThrownBy(()->new RuntimeLibraryExporter().verifyExport(source,runtime,activeStorage,receipt,()->false)).isInstanceOf(java.io.IOException.class).hasMessageContaining("active database");
    assertThatThrownBy(()->new RuntimeLibraryExporter().verifyExport(source,runtime,storage(),receipt,()->true)).isInstanceOf(CancellationException.class);
    var polls=new AtomicInteger();
    assertThatThrownBy(()->new RuntimeLibraryExporter().verifyExport(source,runtime,storage(),receipt,()->polls.incrementAndGet()>8)).isInstanceOf(CancellationException.class);
    assertThat(RuntimeLibraryExporter.hashFile(runtime,()->false)).isEqualTo(receipt.outputSha256());assertNoTemporary();
  }
  @Test void forgedOutputHashCannotHideChangedRuntimeSchema()throws Exception {
    Path source=source(),runtime=root.resolve("runtime.sqlite");var receipt=export(source,runtime);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+runtime);var s=c.createStatement()){s.execute("DROP INDEX typed_values_index");}
    assertThatThrownBy(()->new RuntimeLibraryExporter().verifyExport(source,runtime,storage(),updatedOutput(receipt,runtime,receipt.retainedTables()),()->false)).isInstanceOf(java.io.IOException.class).hasMessageContaining("schema differs");
  }
  private RuntimeLibraryExporter.Receipt updatedOutput(RuntimeLibraryExporter.Receipt receipt,Path runtime,Map<String,RuntimeLibraryExporter.Digest> tables)throws Exception {
    return new RuntimeLibraryExporter.Receipt(receipt.sourceBytes(),receipt.sourceSha256(),Files.size(runtime),RuntimeLibraryExporter.hashFile(runtime,()->false),receipt.buildId(),receipt.omittedRawRecords(),receipt.omittedArchiveBytes(),tables);
  }
  private void assertNoTemporary()throws Exception {try(var files=Files.list(root)){assertThat(files.map(p->p.getFileName().toString()).filter(n->n.startsWith(".runtime-")).toList()).isEmpty();}}
}
