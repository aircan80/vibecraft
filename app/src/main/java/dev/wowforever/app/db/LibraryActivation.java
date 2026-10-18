package dev.wowforever.app.db;

import dev.wowforever.importer.setup.LibraryBuildJob;
import dev.wowforever.importer.setup.LibraryCandidateValidator;
import dev.wowforever.importer.setup.RuntimeLibraryExporter;
import dev.wowforever.importer.setup.SetupPlan;
import dev.wowforever.importer.setup.SetupStorage;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Explicit next-startup activation only. Development candidates retain their recorded coverage warnings. */
public final class LibraryActivation {
  private final Path dataDir;
  private final List<Path> protectedGameRoots;
  private final Optional<Path> activeDatabase;
  public LibraryActivation(Path dataDir,List<Path> protectedGameRoots,Optional<Path> activeDatabase) {
    this.dataDir=Objects.requireNonNull(dataDir);this.protectedGameRoots=List.copyOf(protectedGameRoots);
    this.activeDatabase=Objects.requireNonNull(activeDatabase);
  }

  /** Must be called only after an explicit activation decision, off the EDT. No browser/session is replaced. */
  public LibraryRegistry.Selection activateForNextStartup(LibraryBuildJob.Candidate candidate,SetupPlan plan,
      BooleanSupplier cancelled) throws IOException {
    LibraryRegistry.offEdt(); LibraryRegistry.check(cancelled);
    return activate(candidate,plan,cancelled,new LibraryRegistry(dataDir,gameRoots(plan),activeDatabase));
  }

  /**
   * Activates only an independently verified compact export of the reviewed full candidate. Both inputs
   * must be in app-owned staging. Raw proof is validated on the full source, never required in runtime-v1.
   */
  public LibraryRegistry.Selection activateExportedForNextStartup(LibraryBuildJob.Candidate fullCandidate,
      Path runtime,RuntimeLibraryExporter.Receipt receipt,SetupPlan plan,BooleanSupplier cancelled) throws IOException {
    LibraryRegistry.offEdt(); LibraryRegistry.check(cancelled);
    List<Path> games=gameRoots(plan);
    return activateExported(fullCandidate,runtime,receipt,plan,cancelled,
        new LibraryRegistry(dataDir,games,activeDatabase),new SetupStorage(dataDir,games,activeDatabase));
  }

  private List<Path> gameRoots(SetupPlan plan) {
    List<Path> games=new ArrayList<>(protectedGameRoots);games.addAll(plan.localGameRoots());return games;
  }

  static LibraryRegistry.Selection activate(LibraryBuildJob.Candidate candidate,SetupPlan plan,
      BooleanSupplier cancelled,LibraryRegistry registry) throws IOException {
    var validated=validateCandidate(candidate,plan,cancelled);
    return publishCopy(candidate.database(),candidate.sha256(),Files.size(candidate.database()),validated,target -> {
      var copied=validateFull(target,plan,validated.ingestionId(),cancelled);
      if(!copied.equals(validated))throw new IOException("Managed copy differs from the reviewed validation receipt");
      DatabaseInstaller.validateExternal(target);
    },cancelled,registry);
  }

  static LibraryRegistry.Selection activateExported(LibraryBuildJob.Candidate candidate,Path runtime,
      RuntimeLibraryExporter.Receipt receipt,SetupPlan plan,BooleanSupplier cancelled,LibraryRegistry registry,
      SetupStorage staging) throws IOException {
    LibraryRegistry.offEdt(); LibraryRegistry.check(cancelled);
    staging.verifyDestination(candidate.database().toRealPath());
    staging.verifyDestination(runtime.toRealPath());
    var validated=validateCandidate(candidate,plan,cancelled);
    if(receipt==null || !candidate.sha256().equals(receipt.sourceSha256())
        || receipt.sourceBytes()!=Files.size(candidate.database()) || !validated.buildId().equals(receipt.buildId()))
      throw new IOException("Runtime export receipt does not match the reviewed full candidate");
    RuntimeLibraryExporter.VerifiedExport verified;
    try {verified=new RuntimeLibraryExporter().verifyExport(candidate.database(),runtime,staging,receipt,cancelled);}
    catch(java.sql.SQLException failure) {throw new IOException("Runtime export failed independent verification",failure);}
    // The record is not an authority token: every call above rederives its hashes, rows and omissions.
    var checked=verified.receipt();
    return publishCopy(verified.runtime(),checked.outputSha256(),checked.outputBytes(),validated,
        target -> validateRuntimeIdentity(target,validated),cancelled,registry);
  }

  private static LibraryCandidateValidator.Validated validateCandidate(LibraryBuildJob.Candidate candidate,
      SetupPlan plan,BooleanSupplier cancelled) throws IOException {
    LibraryRegistry.offEdt(); LibraryRegistry.check(cancelled);
    Objects.requireNonNull(candidate); Objects.requireNonNull(plan);
    if(candidate.validation()==null || candidate.sha256()==null || !candidate.sha256().matches("[a-f0-9]{64}"))
      throw new IOException("A validated candidate receipt is required before activation");
    if(!candidate.sha256().equals(LibraryRegistry.hash(candidate.database(),cancelled)))
      throw new IOException("Candidate changed after validation; rebuild before activation");
    var validated=validateFull(candidate.database(),plan,candidate.validation().ingestionId(),cancelled);
    if(!validated.equals(candidate.validation()))throw new IOException("Candidate validation receipt changed; review a fresh candidate");
    DatabaseInstaller.validateExternal(candidate.database());
    return validated;
  }

  private static LibraryCandidateValidator.Validated validateFull(Path file,SetupPlan plan,String ingestion,
      BooleanSupplier cancelled) throws IOException {
    try {return new LibraryCandidateValidator().validate(file,plan,ingestion,cancelled);}
    catch(java.sql.SQLException failure) {throw new IOException("Candidate failed activation validation",failure);}
  }

  private static void validateRuntimeIdentity(Path file,LibraryCandidateValidator.Validated source) throws IOException {
    DatabaseInstaller.validateExternal(file);
    try(var db=Database.open(file)) {
      var meta=db.meta();var identity=source.nativeIdentity();
      if(!source.buildId().equals(meta.get("build_id")) || !source.ingestionId().equals(meta.get("source_ingestion_id"))
          || !identity.product().equals(meta.get("client_product")) || !identity.build().equals(meta.get("client_build"))
          || !identity.locale().equals(meta.getOrDefault("client_locale","enUS"))
          || !"runtime-v1".equals(meta.get("release_dataset_profile")))
        throw new IOException("Managed runtime copy differs from the reviewed source identity");
    } catch(java.sql.SQLException failure) {throw new IOException("Cannot validate managed runtime identity",failure);}
  }

  @FunctionalInterface private interface CopyValidator {void validate(Path file) throws IOException;}

  private static LibraryRegistry.Selection publishCopy(Path sourceFile,String expectedHash,long expectedBytes,
      LibraryCandidateValidator.Validated validation,CopyValidator validator,BooleanSupplier cancelled,
      LibraryRegistry registry) throws IOException {
    Files.createDirectories(registry.checked(registry.root));
    Path lock=registry.checked(registry.root.resolve("selection.lock"));
    try(var channel=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);var held=channel.tryLock()) {
      if(held==null)throw new IOException("Another library activation is in progress");
      Optional<LibraryRegistry.Selection> previous;
      try {previous=registry.resolve(cancelled);} catch(IOException unusable) {previous=Optional.empty();}
      Path versions=registry.checked(registry.root.resolve("versions"));Files.createDirectories(versions);
      Path version=registry.checked(versions.resolve(validation.buildId()+"-"+UUID.randomUUID()));
      Files.createDirectory(version);
      Path target=registry.checked(version.resolve(DatabaseInstaller.FILE_NAME));
      boolean published=false;
      try {
        long copied=0;
        try(var source=Files.newInputStream(sourceFile);var output=FileChannel.open(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
          byte[] bytes=new byte[1024*1024];int count;
          while((count=source.read(bytes))!=-1) {
            LibraryRegistry.check(cancelled);copied+=count;
            if(copied>expectedBytes)throw new IOException("Library source size changed while copying");
            var buffer=java.nio.ByteBuffer.wrap(bytes,0,count);
            while(buffer.hasRemaining())output.write(buffer);
          }
          output.force(true);
        }
        if(copied!=expectedBytes || Files.size(target)!=expectedBytes || !expectedHash.equals(LibraryRegistry.hash(target,cancelled)))
          throw new IOException("Library changed while being copied; activation aborted");
        validator.validate(target);
        if(Files.size(target)!=expectedBytes || !expectedHash.equals(LibraryRegistry.hash(target,cancelled)))
          throw new IOException("Managed copy changed during final validation; activation aborted");
        var selected=new LibraryRegistry.Selection(target,expectedHash,validation);
        registry.publish(selected,previous,cancelled);published=true;
        return selected;
      } finally {
        if(!published) {Files.deleteIfExists(registry.checked(target));Files.deleteIfExists(registry.checked(version));}
      }
    } catch(java.nio.channels.OverlappingFileLockException failure) {throw new IOException("Another library activation is in progress",failure);}
  }
}
