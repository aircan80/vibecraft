package dev.wowforever.importer.setup;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Explicit, bounded cleanup after verified durable activation; runtime and cache files are retained. */
public final class CompletedLibraryJobCleanup {
  @FunctionalInterface interface DeleteFile {void delete(Path file)throws IOException;}
  private final DeleteFile deleteFile;
  public CompletedLibraryJobCleanup(){this(Files::delete);}
  CompletedLibraryJobCleanup(DeleteFile deleteFile){this.deleteFile=Objects.requireNonNull(deleteFile);}
  /** Identity captured from the actual closed source archive; it is not inferred from the candidate. */
  public record ArchiveIdentity(long bytes,String sha256) {}
  public record Report(long deletedBytes,List<Path> deleted,List<String> warnings,boolean complete) {
    public Report {deleted=List.copyOf(deleted);warnings=List.copyOf(warnings);}
  }
  public Report cleanupCompleted(SetupPlan.JobContext job,RuntimeLibraryExporter.Receipt receipt,
      ArchiveIdentity archive,BooleanSupplier cancelled)throws IOException,java.sql.SQLException {
    Objects.requireNonNull(job);Objects.requireNonNull(receipt);Objects.requireNonNull(archive);Objects.requireNonNull(cancelled);
    check(cancelled);job.storage().verifyJob(job.paths());
    Path directory=job.paths().directory().toAbsolutePath().normalize();
    if(!directory.toRealPath().equals(directory)||!directory.getParent().getFileName().toString().equals("jobs"))throw new IOException("Cleanup requires a canonical owned catalogue job");
    Path appRoot=directory.getParent().getParent();
    Path candidate=regular(job,job.paths().candidateDatabase()),source=regular(job,job.paths().archiveDatabase());
    if(Files.isSameFile(candidate,source))throw new IOException("Archive and candidate aliases are not disposable independent staging files");
    Path buildLock=regular(job,directory.resolve("library-build.lock")),archiveLock=regular(job,source.resolveSibling(source.getFileName()+".lock"));
    try(var buildChannel=FileChannel.open(buildLock,StandardOpenOption.WRITE);var heldBuild=buildChannel.tryLock();
        var archiveChannel=FileChannel.open(archiveLock,StandardOpenOption.WRITE);var heldArchive=archiveChannel.tryLock()) {
      if(heldBuild==null||heldArchive==null)throw new IOException("Completed job is still in use");
      rejectSidecars(candidate);rejectSidecars(source);
      var plan=SetupPlanStore.load(regular(job,directory.resolve("selected-plan.properties")));
      Properties prepared=properties(regular(job,directory.resolve("preparation-receipt.properties")));
      if(!"library-preparation-v1".equals(prepared.getProperty("format"))||!receipt.equals(readReceipt(prepared)))throw new IOException("Cleanup receipt differs from completed preparation");
      if(!candidate.toString().equals(prepared.getProperty("fullCandidate"))||!receipt.sourceSha256().equals(prepared.getProperty("fullCandidateSha256")))throw new IOException("Completed preparation belongs to another candidate");
      Path registry=regular(job,appRoot.resolve("libraries/selection.properties"));Properties selection=properties(registry);
      if(!"2".equals(selection.getProperty("format")))throw new IOException("No permission-aware durable activation proof");
      Path managed=selectionPath(job,appRoot,selection,"active.");
      if(!receipt.outputSha256().equals(selection.getProperty("active.sha256"))||!receipt.buildId().equals(selection.getProperty("active.build")))throw new IOException("Active managed library is not this job's verified export");
      verifyConsent(plan,selection,receipt.outputSha256());
      for(String prefix:List.of("active.","previous."))if(selection.containsKey(prefix+"path")) {
        Path retained=selectionPath(job,appRoot,selection,prefix);
        if(Files.isSameFile(source,retained)||Files.isSameFile(candidate,retained))throw new IOException("Staging aliases an active or previous runtime");
      }
      var sourceAttributes=Files.readAttributes(source,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
      var candidateAttributes=Files.readAttributes(candidate,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
      requireIdentity(source,archive.bytes(),archive.sha256(),cancelled);
      new RuntimeLibraryExporter().verifyExport(candidate,managed,job.storage(),receipt,cancelled);
      requireIdentity(source,archive.bytes(),archive.sha256(),cancelled);
      // The activation pointer must remain the identical durable proof throughout preflight.
      if(!selection.equals(properties(registry)))throw new IOException("Managed selection changed during cleanup verification");
      rejectSidecars(source);rejectSidecars(candidate);check(cancelled);
      // Cancellation is honored before this bounded two-delete commit, not between its operations.
      var deleted=new ArrayList<Path>();var warnings=new ArrayList<String>();long removed=0;
      for(var entry:List.of(Map.entry(source,archive.bytes()),Map.entry(candidate,receipt.sourceBytes()))) {
        try {
          Path file=regular(job,entry.getKey());
          var expected=file.equals(source)?sourceAttributes:candidateAttributes;
          var actual=Files.readAttributes(file,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
          if(!Objects.equals(expected.fileKey(),actual.fileKey())||expected.size()!=actual.size()||!expected.lastModifiedTime().equals(actual.lastModifiedTime()))throw new IOException("Staging identity changed before deletion");
          deleteFile.delete(file);deleted.add(file);removed+=entry.getValue();
        }
        catch(IOException failure){warnings.add("Staging cleanup stopped: "+entry.getKey().getFileName()+"; activation remains valid. Inspect the retained job before any manual retry.");break;}
      }
      return new Report(removed,deleted,warnings,deleted.size()==2);
    }catch(OverlappingFileLockException busy){throw new IOException("Completed job is still in use",busy);}
  }
  private static Path regular(SetupPlan.JobContext job,Path requested)throws IOException {
    Path path=requested.toAbsolutePath().normalize(),checked=job.storage().verifyDestination(path);
    if(!checked.equals(path)||Files.isSymbolicLink(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.toRealPath().equals(path))throw new IOException("Cleanup refuses missing, aliased or non-regular proof files");
    return path;
  }
  private static Path selectionPath(SetupPlan.JobContext job,Path appRoot,Properties p,String prefix)throws IOException {
    String relative=p.getProperty(prefix+"path","");
    if(!relative.matches("versions/[a-f0-9]{64}-[a-f0-9-]{36}/wowforever\\.sqlite"))throw new IOException("Managed selection path is invalid");
    return regular(job,appRoot.resolve("libraries").resolve(relative));
  }
  private static void verifyConsent(SetupPlan plan,Properties p,String runtimeSha)throws IOException {
    if(!"managed".equals(p.getProperty("active.assets.mode"))||!"1".equals(p.getProperty("active.assets.format"))||!runtimeSha.equals(p.getProperty("active.assets.database-sha256"))
        ||!Boolean.toString(plan.allows(SetupPlan.Acquisition.WAGO_EXPORT)).equals(p.getProperty("active.assets.wago-export"))
        ||!Boolean.toString(plan.allows(SetupPlan.Acquisition.BLIZZARD_CDN)).equals(p.getProperty("active.assets.blizzard-cdn")))throw new IOException("Durable activation permissions differ from the reviewed plan");
    int count;try{count=Integer.parseInt(p.getProperty("active.assets.game-root-count","-1"));}catch(NumberFormatException invalid){throw new IOException("Invalid activation roots",invalid);}
    if(count!=plan.localGameRoots().size())throw new IOException("Activation root count differs from the reviewed plan");
    for(int i=0;i<count;i++)if(!SetupStorage.canonicalProspective(plan.localGameRoots().get(i)).toString().equals(p.getProperty("active.assets.game-root."+i)))throw new IOException("Activation roots differ from reviewed plan");
  }
  private static RuntimeLibraryExporter.Receipt readReceipt(Properties p)throws IOException {
    try {var tables=new TreeMap<String,RuntimeLibraryExporter.Digest>();
      for(String key:p.stringPropertyNames())if(key.startsWith("retained.")&&key.endsWith(".rows")) {String table=key.substring(9,key.length()-5);tables.put(table,new RuntimeLibraryExporter.Digest(Long.parseLong(p.getProperty(key)),Objects.requireNonNull(p.getProperty("retained."+table+".sha256MultisetSum"))));}
      return new RuntimeLibraryExporter.Receipt(Long.parseLong(p.getProperty("sourceBytes")),p.getProperty("sourceSha256"),Long.parseLong(p.getProperty("runtimeBytes")),p.getProperty("runtimeSha256"),p.getProperty("buildId"),Long.parseLong(p.getProperty("omittedRawRecords")),Long.parseLong(p.getProperty("omittedArchiveBytes")),tables);
    }catch(RuntimeException invalid){throw new IOException("Malformed completed preparation receipt",invalid);}
  }
  private static Properties properties(Path file)throws IOException {
    if(Files.size(file)>1048576)throw new IOException("Cleanup proof exceeds bound");
    byte[] bytes;try(var input=Files.newInputStream(file)){bytes=input.readNBytes(1048577);}
    if(bytes.length>1048576)throw new IOException("Cleanup proof exceeds bound");
    var p=new Properties();try(var input=new ByteArrayInputStream(bytes)){p.load(input);}catch(IllegalArgumentException invalid){throw new IOException("Malformed cleanup proof",invalid);}return p;
  }
  private static void requireIdentity(Path path,long bytes,String sha,BooleanSupplier cancelled)throws IOException {if(bytes<1||sha==null||!sha.matches("[a-f0-9]{64}")||Files.size(path)!=bytes||!RuntimeLibraryExporter.hashFile(path,cancelled).equals(sha))throw new IOException("Closed source archive identity changed");}
  private static void rejectSidecars(Path path)throws IOException {for(String suffix:List.of("-wal","-shm","-journal"))if(Files.exists(path.resolveSibling(path.getFileName()+suffix),LinkOption.NOFOLLOW_LINKS))throw new IOException("Staging database has live SQLite sidecars");}
  private static void check(BooleanSupplier cancelled){LibraryCandidateValidator.check(cancelled);}
}
