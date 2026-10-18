package dev.wowforever.app.db;

import dev.wowforever.importer.setup.LibraryBuildJob;
import dev.wowforever.importer.setup.LibraryCandidateValidator;
import dev.wowforever.importer.setup.SetupPlan;
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
    List<Path> games=new ArrayList<>(protectedGameRoots); games.addAll(plan.localGameRoots());
    var registry=new LibraryRegistry(dataDir,games,activeDatabase);
    return activate(candidate,plan,cancelled,registry);
  }

  static LibraryRegistry.Selection activate(LibraryBuildJob.Candidate candidate,SetupPlan plan,
      BooleanSupplier cancelled,LibraryRegistry registry) throws IOException {
    LibraryRegistry.offEdt(); LibraryRegistry.check(cancelled);
    Objects.requireNonNull(candidate); Objects.requireNonNull(plan);
    if(candidate.validation()==null || candidate.sha256()==null || !candidate.sha256().matches("[a-f0-9]{64}"))
      throw new IOException("A validated candidate receipt is required before activation");
    if(!candidate.sha256().equals(LibraryRegistry.hash(candidate.database(),cancelled)))
      throw new IOException("Candidate changed after validation; rebuild before activation");
    LibraryCandidateValidator.Validated validated;
    try {validated=new LibraryCandidateValidator().validate(candidate.database(),plan,candidate.validation().ingestionId(),cancelled);}
    catch(java.sql.SQLException failure) {throw new IOException("Candidate failed activation validation",failure);}
    if(!validated.equals(candidate.validation())) throw new IOException("Candidate validation receipt changed; review a fresh candidate");
    DatabaseInstaller.validateExternal(candidate.database());
    Files.createDirectories(registry.checked(registry.root));
    Path lock=registry.checked(registry.root.resolve("selection.lock"));
    try(var channel=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);var held=channel.tryLock()) {
      if(held==null)throw new IOException("Another library activation is in progress");
      Optional<LibraryRegistry.Selection> previous;
      try {previous=registry.resolve(cancelled);} catch(IOException unusable) {previous=Optional.empty();}
      Path versions=registry.checked(registry.root.resolve("versions"));Files.createDirectories(versions);
      Path version=registry.checked(versions.resolve(validated.buildId()+"-"+UUID.randomUUID()));
      Files.createDirectory(version);
      Path target=registry.checked(version.resolve(DatabaseInstaller.FILE_NAME));
      boolean published=false;
      try {
        try(var source=Files.newInputStream(candidate.database());var output=FileChannel.open(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
          byte[] bytes=new byte[1024*1024];int count;
          while((count=source.read(bytes))!=-1) {
            LibraryRegistry.check(cancelled);var buffer=java.nio.ByteBuffer.wrap(bytes,0,count);
            while(buffer.hasRemaining())output.write(buffer);
          }
          output.force(true);
        }
        if(!candidate.sha256().equals(LibraryRegistry.hash(target,cancelled)))throw new IOException("Candidate changed while being copied; activation aborted");
        LibraryCandidateValidator.Validated copiedValidation;
        try {copiedValidation=new LibraryCandidateValidator().validate(target,plan,validated.ingestionId(),cancelled);}
        catch(java.sql.SQLException failure) {throw new IOException("Managed copy failed activation validation",failure);}
        if(!copiedValidation.equals(validated))throw new IOException("Managed copy differs from the reviewed validation receipt");
        DatabaseInstaller.validateExternal(target);
        if(!candidate.sha256().equals(LibraryRegistry.hash(target,cancelled)))
          throw new IOException("Managed copy changed during final validation; activation aborted");
        var selected=new LibraryRegistry.Selection(target,candidate.sha256(),copiedValidation);
        registry.publish(selected,previous,cancelled);published=true;
        return selected;
      } finally {
        if(!published) {Files.deleteIfExists(registry.checked(target));Files.deleteIfExists(registry.checked(version));}
      }
    } catch(java.nio.channels.OverlappingFileLockException failure) {throw new IOException("Another library activation is in progress",failure);}
  }
}
