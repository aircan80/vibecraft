package dev.wowforever.importer.setup;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;import java.nio.channels.*;import java.sql.*;import java.util.*;import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;

class CompletedLibraryJobCleanupTest {
 @TempDir Path root;
 record Fixture(SetupPlan.JobContext job,RuntimeLibraryExporter.Receipt receipt,CompletedLibraryJobCleanup.ArchiveIdentity archive,Path managed,Path previous){}
 Fixture fixture()throws Exception {
  var sources=Set.of(SetupPlan.Source.QUESTIE,SetupPlan.Source.NATIVE_DATA);
  var plan=new SetupPlan(sources,Set.of(SetupPlan.Acquisition.WAGO_EXPORT),Optional.of(new SetupPlan.NativeIdentity("wow_classic_beta","1.60.1.70450","enUS")),List.of(),SetupPlan.currentPins(sources));
  var job=plan.prepareJob(root,Optional.of(root.resolve("already-open.sqlite")),"completed",List.of(root.resolve("game")));
  Path full=job.paths().candidateDatabase();
  try(var c=DriverManager.getConnection("jdbc:sqlite:"+full);var s=c.createStatement()){
   s.execute("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)");s.execute("INSERT INTO meta VALUES('schema_version','4'),('build_id','"+"a".repeat(64)+"'),('client_product','wow_classic_beta'),('client_build','1.60.1.70450')");
   s.execute("CREATE TABLE source_record(id INTEGER,payload BLOB)");s.execute("INSERT INTO source_record VALUES(1,zeroblob(16384))");s.execute("CREATE TABLE source_archive_blob(sha256 TEXT PRIMARY KEY,bytes BLOB)");s.execute("INSERT INTO source_archive_blob VALUES('blob',zeroblob(8192))");
   s.execute("CREATE TABLE source_archive_file(sha256 TEXT REFERENCES source_archive_blob(sha256))");s.execute("INSERT INTO source_archive_file VALUES('blob')");s.execute("CREATE TABLE conflicts(edition TEXT,value TEXT)");s.execute("INSERT INTO conflicts VALUES('forever','a'),('era','separate')");
  }
  Files.copy(full,job.paths().archiveDatabase());
  // An archive's identity is measured independently; it need not equal the copied candidate.
  try(var c=DriverManager.getConnection("jdbc:sqlite:"+job.paths().archiveDatabase());var s=c.createStatement()){s.execute("INSERT INTO meta VALUES('archive-only','kept-until-success')");}
  var exporter=new RuntimeLibraryExporter();var receipt=exporter.export(full,job.paths().directory().resolve("runtime.sqlite"),job.storage(),Files.size(full),RuntimeLibraryExporter.hashFile(full,()->false),()->false);
  var archive=new CompletedLibraryJobCleanup.ArchiveIdentity(Files.size(job.paths().archiveDatabase()),RuntimeLibraryExporter.hashFile(job.paths().archiveDatabase(),()->false));
  SetupPlanStore.save(job.storage(),job.paths().directory().resolve("selected-plan.properties"),plan);
  var p=new Properties();p.setProperty("format","library-preparation-v1");p.setProperty("fullCandidate",full.toString());p.setProperty("fullCandidateSha256",receipt.sourceSha256());p.setProperty("sourceBytes",""+receipt.sourceBytes());p.setProperty("sourceSha256",receipt.sourceSha256());p.setProperty("runtimeBytes",""+receipt.outputBytes());p.setProperty("runtimeSha256",receipt.outputSha256());p.setProperty("buildId",receipt.buildId());p.setProperty("omittedRawRecords",""+receipt.omittedRawRecords());p.setProperty("omittedArchiveBytes",""+receipt.omittedArchiveBytes());
  for(var entry:receipt.retainedTables().entrySet()){p.setProperty("retained."+entry.getKey()+".rows",""+entry.getValue().rows());p.setProperty("retained."+entry.getKey()+".sha256MultisetSum",entry.getValue().sha256MultisetSum());}save(job.paths().directory().resolve("preparation-receipt.properties"),p);
  Path version=root.resolve("libraries/versions/"+"a".repeat(64)+"-"+UUID.randomUUID());Files.createDirectories(version);Path managed=version.resolve("wowforever.sqlite");Files.copy(job.paths().directory().resolve("runtime.sqlite"),managed);
  Path oldVersion=root.resolve("libraries/versions/"+"b".repeat(64)+"-"+UUID.randomUUID());Files.createDirectories(oldVersion);Path previous=oldVersion.resolve("wowforever.sqlite");Files.writeString(previous,"previous runtime marker");
  p=new Properties();p.setProperty("format","2");p.setProperty("active.path",root.resolve("libraries").relativize(managed).toString());p.setProperty("active.sha256",receipt.outputSha256());p.setProperty("active.build",receipt.buildId());p.setProperty("previous.path",root.resolve("libraries").relativize(previous).toString());p.setProperty("active.assets.format","1");p.setProperty("active.assets.mode","managed");p.setProperty("active.assets.database-sha256",receipt.outputSha256());p.setProperty("active.assets.wago-export","true");p.setProperty("active.assets.blizzard-cdn","false");p.setProperty("active.assets.game-root-count","0");save(root.resolve("libraries/selection.properties"),p);
  Files.createFile(job.paths().directory().resolve("library-build.lock"));Files.createFile(job.paths().archiveDatabase().resolveSibling("source-archive.sqlite.lock"));
  Files.writeString(job.paths().cache().resolve("verified-download"),"keep");Files.writeString(job.paths().report(),"keep diagnostics");return new Fixture(job,receipt,archive,managed,previous);
 }
 @Test void deletesOnlyTwoVerifiedClosedFullDatabases()throws Exception {
  var f=fixture();String active=hash(f.managed()),previous=hash(f.previous());
  var report=cleanup(f);assertThat(report.complete()).isTrue();assertThat(report.deletedBytes()).isEqualTo(f.archive().bytes()+f.receipt().sourceBytes());assertThat(report.deleted()).containsExactly(f.job().paths().archiveDatabase(),f.job().paths().candidateDatabase());
  assertThat(hash(f.managed())).isEqualTo(active);assertThat(hash(f.previous())).isEqualTo(previous);assertThat(f.job().paths().cache().resolve("verified-download")).exists();assertThat(f.job().paths().report()).exists();assertThat(f.job().paths().directory().resolve("runtime.sqlite")).exists();assertThat(f.job().paths().directory().resolve("preparation-receipt.properties")).exists();
  assertThatThrownBy(()->cleanup(f)).isInstanceOf(java.io.IOException.class); // No unproven automatic retry after missing evidence.
 }
 @Test void failedOrCancelledJobsRetainBothDatabases()throws Exception {
  var f=fixture();assertThatThrownBy(()->new CompletedLibraryJobCleanup().cleanupCompleted(f.job(),f.receipt(),f.archive(),()->true)).isInstanceOf(CancellationException.class);retained(f);
  Files.delete(root.resolve("libraries/selection.properties"));assertThatThrownBy(()->cleanup(f)).isInstanceOf(java.io.IOException.class);retained(f);
 }
 @Test void archiveIdentityIsIndependentAndMustMatchActualBytes()throws Exception {
  var f=fixture();assertThat(f.archive().sha256()).isNotEqualTo(f.receipt().sourceSha256());
  assertThatThrownBy(()->new CompletedLibraryJobCleanup().cleanupCompleted(f.job(),f.receipt(),new CompletedLibraryJobCleanup.ArchiveIdentity(f.archive().bytes(),f.receipt().sourceSha256()),()->false)).isInstanceOf(java.io.IOException.class);retained(f);
 }
 @Test void refusesBusyLocksLiveSidecarsAndWrongActivation()throws Exception {
  var f=fixture();try(var channel=FileChannel.open(f.job().paths().directory().resolve("library-build.lock"),StandardOpenOption.WRITE);var lock=channel.lock()){assertThatThrownBy(()->cleanup(f)).isInstanceOf(java.io.IOException.class);retained(f);}
  Path sidecar=f.job().paths().candidateDatabase().resolveSibling("candidate.sqlite-wal");Files.writeString(sidecar,"in use");assertThatThrownBy(()->cleanup(f)).isInstanceOf(java.io.IOException.class);retained(f);Files.delete(sidecar);
  var pointer=load(root.resolve("libraries/selection.properties"));pointer.setProperty("active.sha256","0".repeat(64));save(root.resolve("libraries/selection.properties"),pointer);assertThatThrownBy(()->cleanup(f)).isInstanceOf(java.io.IOException.class);retained(f);
 }
 @Test void refusesSymlinksAndPreviousRuntimeAliases()throws Exception {
  var f=fixture();Path archive=f.job().paths().archiveDatabase(),saved=archive.resolveSibling("untouched.sqlite");Files.move(archive,saved);Files.createSymbolicLink(archive,saved);assertThatThrownBy(()->cleanup(f)).isInstanceOf(java.io.IOException.class);assertThat(saved).exists();Files.delete(archive);Files.move(saved,archive);
  Path candidate=f.job().paths().candidateDatabase();Files.delete(candidate);Files.createLink(candidate,f.previous());assertThatThrownBy(()->cleanup(f)).isInstanceOf(java.io.IOException.class).hasMessageContaining("previous runtime");assertThat(f.previous()).exists();assertThat(archive).exists();
 }
 @Test void missingOneArtifactRequiresManualSafePlanWithoutDeletingOther()throws Exception {
  var f=fixture();Files.delete(f.job().paths().archiveDatabase());assertThatThrownBy(()->cleanup(f)).isInstanceOf(java.io.IOException.class);assertThat(f.job().paths().candidateDatabase()).exists();assertThat(f.managed()).exists();
 }
 @Test void partialDeletionReportsWarningAndDoesNotRetryWithoutEvidence()throws Exception {
  var f=fixture();var removed=new java.util.concurrent.atomic.AtomicInteger();
  var helper=new CompletedLibraryJobCleanup(file->{if(removed.incrementAndGet()==2)throw new java.io.IOException("Injected filesystem failure");Files.delete(file);});
  var report=helper.cleanupCompleted(f.job(),f.receipt(),f.archive(),()->false);
  assertThat(report.complete()).isFalse();assertThat(report.deletedBytes()).isEqualTo(f.archive().bytes());assertThat(report.warnings()).hasSize(1);assertThat(f.job().paths().candidateDatabase()).exists();assertThat(f.managed()).exists();assertThat(f.previous()).exists();
  assertThatThrownBy(()->cleanup(f)).isInstanceOf(java.io.IOException.class);assertThat(f.job().paths().candidateDatabase()).exists();
 }
 @Test void protectedActiveAndGameAliasedJobInputsAreRefused()throws Exception {
  var f=fixture();var guarded=new SetupStorage(root,List.of(root.resolve("game")),Optional.of(f.job().paths().candidateDatabase()));
  assertThatThrownBy(()->new CompletedLibraryJobCleanup().cleanupCompleted(new SetupPlan.JobContext(guarded,f.job().paths()),f.receipt(),f.archive(),()->false)).isInstanceOf(java.io.IOException.class);retained(f);
  Path game=root.resolve("game");Files.createDirectories(game);Path moved=game.resolve("source.sqlite");Files.move(f.job().paths().archiveDatabase(),moved);Files.createSymbolicLink(f.job().paths().archiveDatabase(),moved);
  assertThatThrownBy(()->cleanup(f)).isInstanceOf(java.io.IOException.class);assertThat(moved).exists();assertThat(f.job().paths().candidateDatabase()).exists();
 }
 private CompletedLibraryJobCleanup.Report cleanup(Fixture f)throws Exception{return new CompletedLibraryJobCleanup().cleanupCompleted(f.job(),f.receipt(),f.archive(),()->false);}
 private static void retained(Fixture f){assertThat(f.job().paths().archiveDatabase()).exists();assertThat(f.job().paths().candidateDatabase()).exists();}
 private static String hash(Path p)throws Exception{return RuntimeLibraryExporter.hashFile(p,()->false);}
 private static Properties load(Path file)throws Exception{var p=new Properties();try(var in=Files.newInputStream(file)){p.load(in);}return p;}
 private static void save(Path file,Properties p)throws Exception{try(var out=Files.newOutputStream(file)){p.store(out,"Synthetic completed-job test evidence");}}
}
