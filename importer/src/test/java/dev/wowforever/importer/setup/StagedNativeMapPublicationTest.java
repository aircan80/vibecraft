package dev.wowforever.importer.setup;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.importer.acquisition.Forever70450Bootstrap;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class StagedNativeMapPublicationTest {
 @TempDir Path temporary;
 SetupPlan plan;SetupPlan.JobContext job,sibling;LibraryBuildJob.Candidate core;NativeMapHierarchyAdapter.Result mapped;
 @BeforeEach void fixture()throws Exception {
  var sources=Set.of(SetupPlan.Source.QUESTIE,SetupPlan.Source.NATIVE_DATA);plan=new SetupPlan(sources,Set.of(SetupPlan.Acquisition.WAGO_EXPORT),Optional.of(Forever70450Bootstrap.wagoManifest().identity()),List.of(),SetupPlan.currentPins(sources));
  var storage=new SetupStorage(temporary.resolve("app"),List.of(),Optional.empty());job=new SetupPlan.JobContext(storage,storage.createJob("catalogue"));sibling=new SetupPlan.JobContext(storage,storage.createJob("maps"));
  Path original=Files.writeString(job.paths().candidateDatabase(),"original staged core");Path copy=Files.writeString(sibling.paths().candidateDatabase(),"validated hierarchy copy");Path archive=Files.writeString(job.paths().archiveDatabase(),"immutable source archive");Path log=Files.writeString(job.paths().directory().resolve("build.log"),"core log");
  var validation=new LibraryCandidateValidator.Validated("core-build",plan.nativeIdentity().orElseThrow(),"ingestion",Map.of(),List.of());core=new LibraryBuildJob.Candidate(original,archive,log,RuntimeLibraryExporter.hashFile(original,()->false),validation);
  String hash=RuntimeLibraryExporter.hashFile(copy,()->false);Properties proof=new Properties();proof.setProperty("sourceSha256",core.sha256());proof.setProperty("candidateSha256",hash);proof.setProperty("mapSnapshot","snapshot");proof.setProperty("transform",NativeMapHierarchyAdapter.TRANSFORM);proof.setProperty("coreBuildId","core-build");proof.setProperty("coreIngestionId","ingestion");proof.setProperty("maps","57");proof.setProperty("placed","51");proof.setProperty("unplaced","5");proof.setProperty("linkedZones","54");Path receipt=sibling.paths().directory().resolve("native-map-receipt.properties");try(var out=Files.newOutputStream(receipt)){proof.store(out,"authored fixture");}
  mapped=new NativeMapHierarchyAdapter.Result(copy,hash,"core-build","ingestion","snapshot",57,51,5,54,receipt,validation);
 }
 StagedNativeMapPublication helper(StagedNativeMapPublication.AtomicReplace move){return new StagedNativeMapPublication(move,(file,p,id,c)->mapped.coreValidation());}
 @Test void successAtomicallyReplacesOnlyStagedCandidateAndReceiptsUseFinalIdentity()throws Exception {
  var result=helper((a,b)->Files.move(a,b,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)).publish(plan,job,sibling,core,mapped,()->false);
  assertThat(core.database()).hasContent("validated hierarchy copy");assertThat(mapped.database()).doesNotExist();assertThat(core.sourceArchive()).hasContent("immutable source archive");assertThat(result.candidate().database()).isEqualTo(job.paths().candidateDatabase());assertThat(result.candidate().sha256()).isEqualTo(RuntimeLibraryExporter.hashFile(core.database(),()->false));
  Properties proof=new Properties();try(var in=Files.newInputStream(result.receipt())){proof.load(in);}assertThat(proof.getProperty("candidate")).isEqualTo(core.database().toString());assertThat(proof.getProperty("candidateSha256")).isEqualTo(result.candidate().sha256());assertThat(proof.getProperty("mapSnapshot")).isEqualTo(result.snapshot());assertThat(mapped.receipt()).exists();
 }
 @Test void atomicReplaceFailurePreservesCoreAndDeletesOnlyDisposableLargeCopy()throws Exception {
  assertThatThrownBy(()->helper((a,b)->{throw new AtomicMoveNotSupportedException(a.toString(),b.toString(),"injected");}).publish(plan,job,sibling,core,mapped,()->false)).isInstanceOf(AtomicMoveNotSupportedException.class);
  assertThat(core.database()).hasContent("original staged core");assertThat(mapped.database()).doesNotExist();assertThat(mapped.receipt()).exists();assertThat(job.paths().directory().resolve("native-map-receipt.properties")).doesNotExist();assertThat(job.paths().directory().resolve("native-map-receipt.properties.part")).doesNotExist();assertThat(core.sourceArchive()).hasContent("immutable source archive");
 }
 @Test void cancellationBeforeCommitPreservesCoreAndDoesNotInvokeMove()throws Exception {
  AtomicBoolean moved=new AtomicBoolean();assertThatThrownBy(()->helper((a,b)->moved.set(true)).publish(plan,job,sibling,core,mapped,()->true)).isInstanceOf(CancellationException.class);assertThat(moved).isFalse();assertThat(core.database()).hasContent("original staged core");assertThat(mapped.database()).doesNotExist();
 }
 @Test void cancellationAtFinalBoundaryPreservesCore()throws Exception {
  AtomicBoolean stop=new AtomicBoolean();var helper=new StagedNativeMapPublication((a,b)->{throw new AssertionError("must not move");},(file,p,id,c)->{stop.set(true);return mapped.coreValidation();});
  assertThatThrownBy(()->helper.publish(plan,job,sibling,core,mapped,stop::get)).isInstanceOf(CancellationException.class);assertThat(core.database()).hasContent("original staged core");assertThat(mapped.database()).doesNotExist();assertThat(job.paths().directory().resolve("native-map-receipt.properties.part")).doesNotExist();
 }
 @Test void changedOriginalOrMappedReceiptFailsWithoutReplacement()throws Exception {
  Files.writeString(core.database(),"unexpected new content");assertThatThrownBy(()->helper((a,b)->{throw new AssertionError();}).publish(plan,job,sibling,core,mapped,()->false)).hasMessageContaining("hash changed");assertThat(core.database()).hasContent("unexpected new content");
 }
 @Test void staleMapReceiptFailsClosed()throws Exception {
  Files.writeString(mapped.receipt(),"candidateSha256=wrong\n");assertThatThrownBy(()->helper((a,b)->{throw new AssertionError();}).publish(plan,job,sibling,core,mapped,()->false)).hasMessageContaining("receipt identity");assertThat(core.database()).hasContent("original staged core");
 }
 @Test void openDatabaseSidecarPreventsReplacement()throws Exception {
  Files.writeString(core.database().resolveSibling("candidate.sqlite-wal"),"in use");assertThatThrownBy(()->helper((a,b)->{throw new AssertionError();}).publish(plan,job,sibling,core,mapped,()->false)).hasMessageContaining("closed candidates");assertThat(core.database()).hasContent("original staged core");
 }
}
