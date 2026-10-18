package dev.wowforever.importer.setup;

import dev.wowforever.importer.ArchiveWorkflow;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.*;

/** Builds only inside one protected job. Does not activate a database or change an open browser session. */
public final class LibraryBuildJob {
  public enum Stage { PREPARING, CAPTURING, PROJECTING, COPYING, VALIDATING, READY }
  public record Progress(Stage stage,String message,long completedBytes,long totalBytes) {}
  public record Candidate(Path database,Path sourceArchive,Path jobLog,String sha256,
      LibraryCandidateValidator.Validated validation) {}
  @FunctionalInterface interface Projector {int project(Path archive,String ingestion,PrintStream report)throws Exception;}
  private final Projector projector;
  private final LibraryCandidateValidator validator;
  public LibraryBuildJob(){this((archive,ingestion,report)->ArchiveWorkflow.project(archive,ingestion,report),new LibraryCandidateValidator());}
  LibraryBuildJob(Projector projector,LibraryCandidateValidator validator){this.projector=projector;this.validator=validator;}

  /** Capture/project stages stop at their next boundary; copying and local hashing also poll between chunks. */
  public Candidate build(SetupPlan plan,SetupPlan.JobContext job,SetupPlan.MaterializedInputs inputs,
      BooleanSupplier cancelled,Consumer<Progress> progress)throws Exception {
    Objects.requireNonNull(cancelled);Objects.requireNonNull(progress);
    SetupStorage storage=job.storage();storage.verifyJob(job.paths());
    Path lock=checked(storage,job.paths().directory().resolve("library-build.lock"));
    Path partial=checked(storage,job.paths().directory().resolve("candidate.sqlite.part"));
    Path candidate=checked(storage,job.paths().candidateDatabase());
    Path log=checked(storage,job.paths().directory().resolve("build-"+UUID.randomUUID()+".log"));
    LibraryCandidateValidator.check(cancelled);
    if(Files.exists(candidate,LinkOption.NOFOLLOW_LINKS)||Files.exists(partial,LinkOption.NOFOLLOW_LINKS))
      throw new IOException("Job already has a candidate or partial output; select a fresh job before rebuilding");
    try(var channel=java.nio.channels.FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);var held=channel.tryLock()) {
      if(held==null)throw new IOException("Another library build is using this job");
      boolean partialCreated=false;
      try(var output=Files.newOutputStream(log,StandardOpenOption.CREATE_NEW);var report=new PrintStream(new BoundedLog(output),true,StandardCharsets.UTF_8)) {
        try {
          stage(progress,cancelled,Stage.PREPARING,"Checking selected inputs and protected staging");
          var prepared=plan.prepareCapture(storage,job.paths(),inputs);
          Path persisted=checked(storage,job.paths().directory().resolve("selected-plan.properties"));
          if(Files.exists(persisted)){
            if(!SetupPlanStore.load(persisted).equals(plan))throw new IOException("Job already belongs to a different source plan");
          }else SetupPlanStore.save(storage,persisted,plan);
          stage(progress,cancelled,Stage.CAPTURING,"Retaining only selected source inputs");
          String ingestion=prepared.capture();
          stage(progress,cancelled,Stage.PROJECTING,"Building tables; cancellation stops after this existing projection stage");
          int code=projector.project(job.paths().archiveDatabase(),ingestion,report);
          LibraryCandidateValidator.check(cancelled); // Never publish after a cancellation during projection.
          if(code!=0)throw new IOException("Selected-source projection failed validation (code "+code+"); inspect "+log.getFileName());
          storage.verifyJob(job.paths());
          stage(progress,cancelled,Stage.COPYING,"Preparing a separate candidate; the active library remains untouched");
          Path source=checked(storage,job.paths().archiveDatabase());
          long total=Files.size(source),copied=0;
          MessageDigest digest=MessageDigest.getInstance("SHA-256");
          try(var from=Files.newInputStream(source);var to=Files.newOutputStream(checked(storage,partial),StandardOpenOption.CREATE_NEW)) {
            partialCreated=true;byte[] buffer=new byte[65536];int count;
            while((count=from.read(buffer))!=-1){LibraryCandidateValidator.check(cancelled);to.write(buffer,0,count);digest.update(buffer,0,count);copied+=count;
              progress.accept(new Progress(Stage.COPYING,"Copying the staged projection",copied,total));}
          }
          stage(progress,cancelled,Stage.VALIDATING,"Validating selected source identity, integrity and browser query shapes");
          var validation=validator.validate(partial,plan,ingestion,cancelled);
          LibraryCandidateValidator.check(cancelled);storage.verifyJob(job.paths());
          if(Files.exists(candidate,LinkOption.NOFOLLOW_LINKS))throw new IOException("Candidate path became occupied; existing output preserved");
          Files.move(checked(storage,partial),checked(storage,candidate),StandardCopyOption.ATOMIC_MOVE);
          partialCreated=false;
          Candidate result=new Candidate(candidate,source,log,HexFormat.of().formatHex(digest.digest()),validation);
          progress.accept(new Progress(Stage.READY,"Validated development candidate; runtime compaction and activation remain separate stages",total,total));
          return result;
        } catch(Exception failure){report.println("Library job stopped: "+failure.getMessage());throw failure;}
        finally {if(partialCreated)Files.deleteIfExists(checked(storage,partial));}
      }
    }catch(java.nio.channels.OverlappingFileLockException failure){throw new IOException("Another library build is using this job",failure);}
  }
  private static void stage(Consumer<Progress> progress,BooleanSupplier cancelled,Stage stage,String message){
    LibraryCandidateValidator.check(cancelled);progress.accept(new Progress(stage,message,0,-1));LibraryCandidateValidator.check(cancelled);
  }
  private static Path checked(SetupStorage storage,Path path)throws IOException {
    Path actual=storage.verifyDestination(path);if(!actual.equals(path))throw new IOException("Library job storage alias changed");return actual;
  }
  /** Report truncation never changes projection success/failure; the job receipt and stage state are separate. */
  private static final class BoundedLog extends OutputStream {
    private final OutputStream output;private int remaining=65536;private boolean truncated;
    BoundedLog(OutputStream output){this.output=output;}
    public void write(int value)throws IOException{if(remaining>0){output.write(value);remaining--;}else truncate();}
    public void write(byte[] bytes,int offset,int count)throws IOException{int accepted=Math.min(remaining,count);output.write(bytes,offset,accepted);remaining-=accepted;if(accepted<count)truncate();}
    private void truncate()throws IOException{if(!truncated){output.write("\n[Further report output truncated]\n".getBytes(StandardCharsets.UTF_8));truncated=true;}}
    public void flush()throws IOException{output.flush();}public void close()throws IOException{output.close();}
  }
}
