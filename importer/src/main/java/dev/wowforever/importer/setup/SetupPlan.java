package dev.wowforever.importer.setup;

import dev.wowforever.importer.*;
import dev.wowforever.importer.clientdata.*;
import dev.wowforever.importer.acquisition.PinnedItemSparseOverlayMaterializer;
import dev.wowforever.importer.source.SourceSpec;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** One immutable Forever job, separate from per-edition UI drafts and discovery results. */
public record SetupPlan(Set<Source> sources, Set<Acquisition> acquisitions, Optional<NativeIdentity> nativeIdentity,
    List<Path> localGameRoots, Map<Source,String> sourcePins) {
  public enum Source { QUESTIE, ATLASLOOT, ALL_THE_THINGS, NATIVE_DATA, RAIDBOTS_HOTFIX }
  public enum Acquisition { LOCAL_INSTALLATION, BLIZZARD_CDN, WAGO_EXPORT }
  public record NativeIdentity(String product, String build, String locale) {
    public NativeIdentity {
      if (!"wow_classic_beta".equals(product) || build == null || !build.matches("1\\.60\\.[0-9]+\\.[0-9]+")
          || !"enUS".equals(locale)) throw new IllegalArgumentException("Only exact Forever beta builds in enUS are supported");
    }
  }

  public SetupPlan {
    sources = Set.copyOf(sources);
    acquisitions = Set.copyOf(acquisitions);
    nativeIdentity = Objects.requireNonNull(nativeIdentity);
    localGameRoots = localGameRoots.stream().map(p -> p.toAbsolutePath().normalize()).distinct().toList();
    sourcePins = Map.copyOf(sourcePins);
    if (!sources.contains(Source.QUESTIE)) throw new IllegalArgumentException("Questie is required by the current core projector");
    boolean nativeSelected = sources.contains(Source.NATIVE_DATA);
    if (nativeSelected != nativeIdentity.isPresent()) throw new IllegalArgumentException("Native selection requires its exact build identity");
    if ((nativeSelected && acquisitions.isEmpty()) || (!nativeSelected && !acquisitions.isEmpty())) throw new IllegalArgumentException("Native data requires an explicit acquisition method; methods require native selection");
    if (acquisitions.contains(Acquisition.LOCAL_INSTALLATION) != !localGameRoots.isEmpty())
      throw new IllegalArgumentException("Local acquisition requires selected read-only WoW folders; other methods do not implicitly use them");
    Set<Source> pinned = new HashSet<>(sources);pinned.remove(Source.NATIVE_DATA);
    if (!sourcePins.keySet().equals(pinned)) throw new IllegalArgumentException("Only selected sources may have pins");
    if (sourcePins.values().stream().anyMatch(pin -> pin == null || pin.isBlank() || pin.length() > 200))
      throw new IllegalArgumentException("Every selected source needs an exact pin");
    if(sources.contains(Source.RAIDBOTS_HOTFIX)) {
      if(!nativeSelected || !nativeIdentity.orElseThrow().equals(new NativeIdentity("wow_classic_beta","1.60.1.70450","enUS"))
          || !acquisitions.contains(Acquisition.WAGO_EXPORT)
          || !PinnedItemSparseOverlayMaterializer.SNAPSHOT_SHA.equals(sourcePins.get(Source.RAIDBOTS_HOTFIX)))
        throw new IllegalArgumentException("Raidbots hotfix requires the reviewed exact snapshot, Forever 70450 enUS and explicit Wago selection");
    }
  }

  public record JobContext(SetupStorage storage, SetupStorage.JobPaths paths) {}

  /** Establish the write boundary from this plan before creating any staging directory. */
  public JobContext prepareJob(Path appStorage, Optional<Path> activeDatabase, String jobId,
      List<Path> additionalProtectedGameRoots) throws IOException {
    List<Path> protectedRoots = new ArrayList<>(localGameRoots);
    protectedRoots.addAll(additionalProtectedGameRoots);
    SetupStorage storage = new SetupStorage(appStorage, protectedRoots, activeDatabase);
    return new JobContext(storage, storage.createJob(jobId));
  }

  public boolean allows(Acquisition method) { return acquisitions.contains(method); }
  public CoreSourceSelection coreSelection() { return new CoreSourceSelection(sources.contains(Source.ATLASLOOT)); }

  /** Snapshot the pins used by this application version; persisted plans never silently adopt newer pins. */
  public static Map<Source,String> currentPins(Set<Source> selected) {
    Map<Source,String> result = new EnumMap<>(Source.class);
    for (SourceSpec spec : SourceSpec.load()) {
      Source source = spec.key().equals("questiedb") ? Source.QUESTIE : Source.ATLASLOOT;
      if (selected.contains(source)) result.put(source,spec.pin());
    }
    if (selected.contains(Source.ALL_THE_THINGS)) result.put(Source.ALL_THE_THINGS,
        dev.wowforever.importer.allthethings.AllTheThingsSource.PIN);
    if(selected.contains(Source.RAIDBOTS_HOTFIX))result.put(Source.RAIDBOTS_HOTFIX,PinnedItemSparseOverlayMaterializer.SNAPSHOT_SHA);
    return Map.copyOf(result);
  }

  public record MaterializedClient(Path directory, Acquisition acquiredBy) {}
  public record MaterializedInputs(Path questie, Optional<Path> atlasLoot, Optional<Path> allTheThings,
      Optional<MaterializedClient> client,Optional<PinnedItemSparseOverlayMaterializer.Result> raidbotsOverlay) {
    public MaterializedInputs(Path questie,Optional<Path> atlasLoot,Optional<Path> allTheThings,Optional<MaterializedClient> client) {
      this(questie,atlasLoot,allTheThings,client,Optional.empty());
    }
  }
  public static final class PreparedImport {
    private final ImportOptions options;
    private final CoreSourceSelection coreSelection;
    private final SetupStorage storage;
    private final SetupStorage.JobPaths job;
    private final Map<String,FrozenInput> inputs;
    private PreparedImport(ImportOptions options, CoreSourceSelection coreSelection,
        SetupStorage storage, SetupStorage.JobPaths job, Map<String,FrozenInput> inputs) {
      this.options=options;this.coreSelection=coreSelection;this.storage=storage;this.job=job;this.inputs=Map.copyOf(inputs);
    }
    public ImportOptions options(){return options;}
    public CoreSourceSelection coreSelection(){return coreSelection;}
    public SetupStorage storage(){return storage;}
    public SetupStorage.JobPaths job(){return job;}

    /** Capture verified private copies, then check the actual archived bytes before returning an ingestion ID. */
    public String capture() throws Exception {
      storage.verifyJob(job);
      if (!options.out().equals(job.archiveDatabase())) throw new IOException("Capture output must be the staged source archive");
      for(FrozenInput input:inputs.values()) input.verifyOriginal();
      Path snapshot=storage.verifyDestination(job.directory().resolve("capture-"+UUID.randomUUID()));
      Files.createDirectory(snapshot);
      try {
        for(var entry:inputs.entrySet()) entry.getValue().copyTo(storage,snapshot.resolve(entry.getKey()));
        ImportOptions frozen=new ImportOptions(options.out(),options.cacheDir(),Optional.of(snapshot.resolve("questiedb")),
            inputs.containsKey("atlasloot")?Optional.of(snapshot.resolve("atlasloot")):Optional.empty(),
            options.failOnUnsupported(),options.buildIdOut(),ClientDataOptions.of(snapshot.resolve("client")),
            options.coverageMinPct(),Optional.empty(),inputs.containsKey("allthethings")?Optional.of(snapshot.resolve("allthethings")):Optional.empty(),
            options.skipAllTheThings());
        storage.verifyJob(job);
        if(inputs.get("client").hashes().containsKey("overlay-policy.properties"))
          new PinnedItemSparseOverlayMaterializer().validate(snapshot.resolve("client"),()->Thread.currentThread().isInterrupted());
        String ingestion=ArchiveWorkflow.ingest(frozen,coreSelection);
        verifyArchive(ingestion);
        return ingestion;
      } finally {
        // Only remove paths belonging to our fresh snapshot, never an aliased game/cache directory.
        dev.wowforever.importer.fs.SourcePaths.rejectSymlinks(snapshot);
        try(var files=Files.walk(snapshot)) {
          for(Path path:files.sorted(Comparator.reverseOrder()).toList()) {
            dev.wowforever.importer.fs.SourcePaths.rejectSymlinks(path);
            Files.deleteIfExists(storage.verifyDestination(path));
          }
        }
      }
    }
    private void verifyArchive(String ingestion) throws Exception {
      try(var connection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+job.archiveDatabase())) {
        for(var entry:inputs.entrySet()) {
          Map<String,String> actual=new TreeMap<>();
          try(var query=connection.prepareStatement("SELECT f.path,b.bytes FROM source_ingestion_input i JOIN source_archive_file f ON f.snapshot_id=i.snapshot_id JOIN source_archive_blob b ON b.sha256=f.sha256 WHERE i.ingestion_id=? AND i.role=?")) {
            query.setString(1,ingestion);query.setString(2,entry.getKey());
            try(var rows=query.executeQuery()) {
              while(rows.next())try(var bytes=rows.getBinaryStream(2)){actual.put(rows.getString(1),hash(bytes));}
            }
          }
          if(!actual.equals(entry.getValue().archivedHashes()))
            throw new IOException("Captured input differs from the reviewed setup manifest: "+entry.getKey()+"; do not project this archive");
        }
      }
    }
  }

  /** Hashes are computed before capture; selected community paths exclude unrelated checkout content. */
  private record FrozenInput(Path root,List<String> selected,Map<String,String> hashes) {
    private static final int MAX_FILES=100_000;
    private static final long MAX_FILE_BYTES=512L<<20,MAX_TOTAL_BYTES=16L<<30;
    FrozenInput { selected=List.copyOf(selected);hashes=Map.copyOf(hashes); }
    static FrozenInput inspect(Path root,List<String> selected) throws IOException {
      dev.wowforever.importer.fs.SourcePaths.rejectSymlinks(root);
      List<String> paths=selected;
      if(paths==null)try(var walk=Files.walk(root)) {
        paths=new ArrayList<>();
        var iterator=walk.iterator();int entries=0;
        while(iterator.hasNext()) {
          if(++entries>MAX_FILES*2)throw new IOException("Too many setup input entries");
          Path path=iterator.next();dev.wowforever.importer.fs.SourcePaths.rejectSymlinks(path);
          if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) {
            paths.add(root.relativize(path).toString().replace('\\','/'));
            if(paths.size()>MAX_FILES)throw new IOException("Too many setup input files");
          } else if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Unsupported setup input file");
        }
        paths.sort(String::compareTo);
      }
      if(paths.size()>MAX_FILES)throw new IOException("Too many setup input files");
      Map<String,String> hashes=new TreeMap<>();long total=0;
      for(String name:paths) {
        Path relative=Path.of(name);
        if(relative.isAbsolute()||relative.normalize().startsWith(".."))throw new IOException("Unsafe setup input path");
        Path file=root.resolve(relative);dev.wowforever.importer.fs.SourcePaths.rejectSymlinks(file);
        if(Files.notExists(file,LinkOption.NOFOLLOW_LINKS))continue;
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Setup input is not a regular file: "+name);
        long bytes=Files.size(file);total+=bytes;
        if(bytes>MAX_FILE_BYTES||total>MAX_TOTAL_BYTES)throw new IOException("Setup input exceeds staging byte limit");
        hashes.put(name,hash(file));
      }
      return new FrozenInput(root,selected==null?List.of():selected,hashes);
    }
    void verifyOriginal() throws IOException {
      FrozenInput current=inspect(root,selected.isEmpty()?null:selected);
      if(!hashes.equals(current.hashes))throw new IOException("Setup input changed after review: "+root.getFileName());
    }
    void copyTo(SetupStorage storage,Path destination) throws IOException {
      Files.createDirectory(storage.verifyDestination(destination));
      long copiedTotal=0;
      for(var entry:hashes.entrySet()) {
        Path source=root.resolve(entry.getKey());dev.wowforever.importer.fs.SourcePaths.rejectSymlinks(source);
        Path target=storage.verifyDestination(destination.resolve(entry.getKey()));
        Files.createDirectories(storage.verifyDestination(target.getParent()));
        // Fresh bounded copies never write to the original input, even if it grows while being read.
        try(var input=Files.newInputStream(source);var output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)) {
          byte[] buffer=new byte[65536];int count;long copied=0;
          while((count=input.read(buffer))!=-1) {
            if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Setup capture interrupted");
            copied+=count;copiedTotal+=count;
            if(copied>MAX_FILE_BYTES||copiedTotal>MAX_TOTAL_BYTES)throw new IOException("Setup input exceeds staging byte limit");
            output.write(buffer,0,count);
          }
        }
        if(!entry.getValue().equals(hash(target)))throw new IOException("Setup input changed while staging: "+entry.getKey());
      }
    }
    Map<String,String> archivedHashes() throws IOException {
      Map<String,String> result=new TreeMap<>(hashes);
      List<String> missing=selected.stream().filter(name->!hashes.containsKey(name)).toList();
      if(!missing.isEmpty()) {
        try(var bytes=new java.io.ByteArrayInputStream(String.join("\n",missing).getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
          result.put("__capture__/missing-inputs.txt",hash(bytes));
        }
      }
      return result;
    }
  }

  /** Concrete bridge to the existing importer. All selected inputs must already be materialized locally. */
  public PreparedImport prepareCapture(SetupStorage storage, SetupStorage.JobPaths job, MaterializedInputs inputs)
      throws IOException {
    if (!sourcePins.equals(currentPins(sources))) throw new IOException("Selected source pins changed; review a new plan before importing");
    if (!sources.contains(Source.NATIVE_DATA))
      throw new IOException("A runnable Forever catalog currently requires genuine native build data; community-only activation is not supported");
    storage.verifyJob(job);
    for (Path output : List.of(job.directory(),job.archiveDatabase(),job.candidateDatabase(),job.cache(),job.buildId(),job.report())) {
      if (!storage.verifyDestination(output).equals(output)) throw new IOException("Job paths changed since preparation");
      for (Path game : localGameRoots)
        if (SetupStorage.canonicalProspective(output).startsWith(SetupStorage.canonicalProspective(game)))
          throw new IOException("Job output cannot be inside a selected WoW installation");
    }
    if (!Files.isDirectory(inputs.questie())) throw new IOException("Selected Questie input is not materialized");
    Optional<Path> atlas = selectedDirectory(Source.ATLASLOOT,inputs.atlasLoot());
    Optional<Path> att = selectedDirectory(Source.ALL_THE_THINGS,inputs.allTheThings());
    if (sources.contains(Source.ATLASLOOT) && SourceSpec.load().stream().anyMatch(s -> s.key().equals("atlasloot") && s.baseline() != null))
      throw new IOException("This AtlasLoot pin requires explicit baseline materialization, which setup does not yet support");
    boolean selectedHotfix=sources.contains(Source.RAIDBOTS_HOTFIX);
    MaterializedClient client;
    if(selectedHotfix) {
      if(inputs.client().isPresent())throw new IOException("Select only the typed hotfix client input, not a second native directory");
      var supplied=inputs.raidbotsOverlay().orElseThrow(()->new IOException("Selected Raidbots overlay is not materialized"));
      var validated=new PinnedItemSparseOverlayMaterializer().validate(supplied.directory(),()->Thread.currentThread().isInterrupted());
      if(!validated.validation().equals(supplied.validation()))throw new IOException("Selected hotfix receipt changed after materialization");
      client=new MaterializedClient(validated.directory(),Acquisition.WAGO_EXPORT);
    } else {
      if(inputs.raidbotsOverlay().isPresent())throw new IOException("Raidbots hotfix acquisition was not selected");
      client=inputs.client().orElseThrow(()->new IOException("Selected native tables are not materialized"));
    }
    if (!allows(client.acquiredBy())) throw new IOException("Native input used an acquisition method that was not selected");
    if(client.acquiredBy()==Acquisition.LOCAL_INSTALLATION)
      for(Path root:localGameRoots)if(!Files.isDirectory(root))throw new IOException("Selected WoW installation is unavailable");
    if (!Files.isDirectory(client.directory())) throw new IOException("Native input directory is unavailable");
    String reviewedLockHash=hash(client.directory().resolve("build.lock"));
    BuildLock lock;
    try { lock = BuildLock.read(client.directory().resolve("build.lock")); }
    catch (RuntimeException failure) { throw new IOException("Native build lock is unreadable",failure); }
    Object parsed;
    try { parsed = MiniJson.parse(Files.readString(client.directory().resolve("build.lock"))); }
    catch (RuntimeException failure) { throw new IOException("Native locale metadata is unreadable", failure); }
    if (!(parsed instanceof Map<?,?> metadata) || !"enUS".equals(metadata.get("locale")))
      throw new IOException("Materialized native data must explicitly identify the selected enUS locale");
    if (client.acquiredBy() != Acquisition.WAGO_EXPORT && isWago(lock.baseUrl()))
      throw new IOException("Wago exports require their own explicit acquisition permission");
    NativeIdentity identity = nativeIdentity.orElseThrow();
    if (!identity.product().equals(lock.product()) || !identity.build().equals(lock.build()))
      throw new IOException("Materialized native data does not match the selected Forever build");
    if (lock.fallbackProduct() != null && !lock.fallbackProduct().isBlank())
      throw new IOException("Unselected fallback game data is not permitted in a setup job");
    if (lock.tableSha().keySet().stream().anyMatch(name -> name.endsWith(".era")))
      throw new IOException("Unselected Era tables are not permitted in a setup job");
    if (!selectedHotfix && (lock.hotfixSha256() != null || Files.exists(client.directory().resolve("hotfix.json"))
        || lock.tableSha().keySet().stream().anyMatch(name->name.endsWith(".hotfix"))
        || Files.exists(client.directory().resolve("overlay-policy.properties"))))
      throw new IOException("Hotfix inputs require separately selected acquisition support; no implicit overlay is permitted");
    Path csvDirectory = client.directory().resolve("csv");
    if (Files.isDirectory(csvDirectory)) try (var files = Files.list(csvDirectory)) {
      for (Path file : files.toList()) {
        String name = file.getFileName().toString();
        if (name.endsWith(".csv") && !lock.tableSha().containsKey(name.substring(0,name.length()-4)))
          throw new IOException("Unselected native table in materialized input: " + name);
      }
    }
    if (lock.tableSha().isEmpty()) throw new IOException("Native input has no pinned tables");
    for (var table : lock.tableSha().entrySet()) {
      if (!table.getKey().matches("[A-Za-z][A-Za-z0-9_.]*") || !table.getValue().matches("[a-f0-9]{64}"))
        throw new IOException("Invalid native table hash manifest");
      Path file = client.directory().resolve("csv").resolve(table.getKey() + ".csv");
      if (!Files.isRegularFile(file) || !hash(file).equals(table.getValue()))
        throw new IOException("Native table is missing or its hash changed: " + table.getKey());
    }
    ImportOptions options = new ImportOptions(job.archiveDatabase(),job.cache(),Optional.of(inputs.questie()),atlas,
        true,job.buildId(),ClientDataOptions.of(client.directory()),85,Optional.empty(),att,!sources.contains(Source.ALL_THE_THINGS));
    Map<String,FrozenInput> frozen=new LinkedHashMap<>();
    for(SourceSpec spec:SourceSpec.load()) {
      if(spec.key().equals("questiedb"))frozen.put("questiedb",FrozenInput.inspect(inputs.questie(),spec.paths()));
      else if(spec.key().equals("atlasloot")&&atlas.isPresent())frozen.put("atlasloot",FrozenInput.inspect(atlas.orElseThrow(),spec.paths()));
    }
    if(att.isPresent())frozen.put("allthethings",FrozenInput.inspect(att.orElseThrow(),dev.wowforever.importer.allthethings.AllTheThingsSource.paths()));
    FrozenInput nativeInput=FrozenInput.inspect(client.directory(),null);
    // Ensure the frozen snapshot still matches the lock and table bytes just validated above.
    if(!reviewedLockHash.equals(nativeInput.hashes().get("build.lock")))
      throw new IOException("Native build metadata changed while preparing capture");
    for(String name:nativeInput.hashes().keySet()) {
      if((!selectedHotfix&&name.equals("hotfix.json")) || (name.startsWith("csv/")&&name.endsWith(".csv")
          &&!lock.tableSha().containsKey(name.substring(4,name.length()-4))))
        throw new IOException("Native input selection changed while preparing capture");
    }
    for(var table:lock.tableSha().entrySet())
      if(!table.getValue().equals(nativeInput.hashes().get("csv/"+table.getKey()+".csv")))throw new IOException("Native table changed while preparing capture");
    frozen.put("client",nativeInput);
    return new PreparedImport(options,coreSelection(),storage,job,frozen);
  }

  private static boolean isWago(String baseUrl) {
    try {
      String host = java.net.URI.create(baseUrl).getHost();
      return host != null && (host.equalsIgnoreCase("wago.tools") || host.toLowerCase(Locale.ROOT).endsWith(".wago.tools"));
    } catch (IllegalArgumentException ignored) { return false; }
  }
  private static String hash(Path file) throws IOException {
    try(var input=Files.newInputStream(file)){return hash(input);}
  }
  private static String hash(java.io.InputStream input) throws IOException {
    try {
      var digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] buffer=new byte[65536];int count;
      long total=0;
      while((count=input.read(buffer))!=-1) {
        if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Setup capture interrupted");
        total+=count;if(total>FrozenInput.MAX_FILE_BYTES)throw new IOException("Setup input exceeds staging byte limit");
        digest.update(buffer,0,count);
      }
      return java.util.HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
  }

  private Optional<Path> selectedDirectory(Source source, Optional<Path> input) throws IOException {
    if (!sources.contains(source)) return Optional.empty(); // Never import an unchecked cached source.
    if (input.isEmpty() || !Files.isDirectory(input.orElseThrow())) throw new IOException(source + " input is not materialized");
    return input;
  }
}
