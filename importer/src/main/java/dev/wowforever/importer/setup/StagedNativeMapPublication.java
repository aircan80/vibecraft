package dev.wowforever.importer.setup;

import java.io.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.*;

/** Commits only a fresh catalogue attempt's validated map copy; never activates a user library. */
final class StagedNativeMapPublication {
 record Result(LibraryBuildJob.Candidate candidate,String snapshot,int maps,int placed,int unplaced,int linkedZones,Path receipt) {}
 @FunctionalInterface interface AtomicReplace { void replace(Path from,Path to)throws IOException; }
 @FunctionalInterface interface Validator { LibraryCandidateValidator.Validated validate(Path file,SetupPlan plan,String ingestion,BooleanSupplier cancelled)throws Exception; }
 private final AtomicReplace replace;private final Validator validator;
 StagedNativeMapPublication(){this((from,to)->Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING),(file,plan,id,c)->new LibraryCandidateValidator().validate(file,plan,id,c));}
 StagedNativeMapPublication(AtomicReplace replace,Validator validator){this.replace=replace;this.validator=validator;}
 Result project(SetupPlan plan,SetupPlan.JobContext job,LibraryBuildJob.Candidate core,BooleanSupplier cancelled,Consumer<NativeMapHierarchyAdapter.Progress> progress)throws Exception {
  var sibling=new SetupPlan.JobContext(job.storage(),job.storage().createJob("native-map-"+UUID.randomUUID()));
  try {
   var mapped=new NativeMapHierarchyAdapter().apply(plan,sibling,core,cancelled,progress);
   return publish(plan,job,sibling,core,mapped,cancelled);
  } finally { discardLargeOutput(sibling); }
 }
 Result publish(SetupPlan plan,SetupPlan.JobContext job,SetupPlan.JobContext sibling,LibraryBuildJob.Candidate core,NativeMapHierarchyAdapter.Result mapped,BooleanSupplier cancelled)throws Exception {
  var storage=job.storage();storage.verifyJob(job.paths());storage.verifyJob(sibling.paths());
  if(storage!=sibling.storage()||!core.database().equals(job.paths().candidateDatabase())||!mapped.database().equals(sibling.paths().candidateDatabase())||core.database().equals(mapped.database()))throw new IOException("Map publication requires this attempt's candidate and its protected sibling");
  Path original=checked(job,core.database()),copy=checked(sibling,mapped.database());
  Path proof=checked(job,job.paths().directory().resolve("native-map-receipt.properties")),part=checked(job,proof.resolveSibling(proof.getFileName()+".part"));
  boolean receiptOwned=false;
  try(var channel=FileChannel.open(checked(job,job.paths().directory().resolve("native-map-publication.lock")),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.tryLock()) {
   if(lock==null)throw new IOException("Map publication is already running");
   if(Files.exists(proof,LinkOption.NOFOLLOW_LINKS)||Files.exists(part,LinkOption.NOFOLLOW_LINKS))throw new IOException("Map publication receipt already exists");
   closed(original);closed(copy);check(cancelled);
   if(!core.sha256().equals(RuntimeLibraryExporter.hashFile(original,cancelled))||!mapped.sha256().equals(RuntimeLibraryExporter.hashFile(copy,cancelled)))throw new IOException("Map publication candidate hash changed");
   var validation=validator.validate(copy,plan,core.validation().ingestionId(),cancelled);
   if(!validation.buildId().equals(core.validation().buildId())||!mapped.coreBuildId().equals(validation.buildId())||!mapped.coreIngestionId().equals(validation.ingestionId()))throw new IOException("Map publication changed core identity");
   Properties values=new Properties();try(var in=Files.newInputStream(checked(sibling,mapped.receipt()))){values.load(in);}
   if(!mapped.sha256().equals(values.getProperty("candidateSha256"))||!core.sha256().equals(values.getProperty("sourceSha256"))||!mapped.mapSnapshot().equals(values.getProperty("mapSnapshot")))throw new IOException("Map publication receipt identity mismatch");
   if(!NativeMapHierarchyAdapter.TRANSFORM.equals(values.getProperty("transform"))||!mapped.coreBuildId().equals(values.getProperty("coreBuildId"))||!mapped.coreIngestionId().equals(values.getProperty("coreIngestionId")))throw new IOException("Map publication receipt context mismatch");
   for(var count:Map.of("maps",mapped.maps(),"placed",mapped.placed(),"unplaced",mapped.unplaced(),"linkedZones",mapped.linkedZones()).entrySet())if(!Integer.toString(count.getValue()).equals(values.getProperty(count.getKey())))throw new IOException("Map publication receipt count mismatch");
   values.setProperty("candidate",original.toString());values.setProperty("publication","atomic replacement of this newly generated staged core candidate only");
   try(var out=Files.newOutputStream(part,StandardOpenOption.CREATE_NEW)){receiptOwned=true;values.store(out,"Validated native hierarchy; source archive and active database unchanged");}
   // All cancellable checks precede the commit. Never cancel halfway through receipt finalization.
   closed(original);closed(copy);checked(job,original);checked(sibling,copy);
   if(!core.sha256().equals(RuntimeLibraryExporter.hashFile(original,cancelled))||!mapped.sha256().equals(RuntimeLibraryExporter.hashFile(copy,cancelled)))throw new IOException("Candidate changed immediately before map publication");
   check(cancelled);replace.replace(copy,original);
   Files.move(checked(job,part),checked(job,proof),StandardCopyOption.ATOMIC_MOVE);receiptOwned=false;
   return new Result(new LibraryBuildJob.Candidate(original,core.sourceArchive(),core.jobLog(),mapped.sha256(),validation),mapped.mapSnapshot(),mapped.maps(),mapped.placed(),mapped.unplaced(),mapped.linkedZones(),proof);
  } finally {
   if(receiptOwned)Files.deleteIfExists(checked(job,part));
   discardLargeOutput(sibling);
  }
 }
 private static void discardLargeOutput(SetupPlan.JobContext sibling)throws IOException {
  sibling.storage().verifyJob(sibling.paths());Path output=checked(sibling,sibling.paths().candidateDatabase());
  Files.deleteIfExists(output);Files.deleteIfExists(checked(sibling,output.resolveSibling(output.getFileName()+".part")));
 }
 private static Path checked(SetupPlan.JobContext j,Path path)throws IOException {Path actual=j.storage().verifyDestination(path);if(!actual.equals(path))throw new IOException("Map publication path alias changed");return actual;}
 private static void closed(Path path)throws IOException {if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Map publication candidate is missing");for(String suffix:List.of("-wal","-shm","-journal"))if(Files.exists(path.resolveSibling(path.getFileName()+suffix),LinkOption.NOFOLLOW_LINKS))throw new IOException("Map publication requires closed candidates");}
 private static void check(BooleanSupplier c){if(c.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("Native map publication cancelled before commit");}
}
