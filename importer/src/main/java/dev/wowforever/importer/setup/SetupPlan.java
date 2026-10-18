package dev.wowforever.importer.setup;

import dev.wowforever.importer.*;
import dev.wowforever.importer.clientdata.*;
import dev.wowforever.importer.source.SourceSpec;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** One immutable Forever job, separate from per-edition UI drafts and discovery results. */
public record SetupPlan(Set<Source> sources, Set<Acquisition> acquisitions, Optional<NativeIdentity> nativeIdentity,
    List<Path> localGameRoots, Map<Source,String> sourcePins) {
  public enum Source { QUESTIE, ATLASLOOT, ALL_THE_THINGS, NATIVE_DATA }
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
    if (!sourcePins.keySet().equals(pinned)) throw new IllegalArgumentException("Only selected community sources may have pins");
    if (sourcePins.values().stream().anyMatch(pin -> pin == null || pin.isBlank() || pin.length() > 200))
      throw new IllegalArgumentException("Every selected community source needs an exact pin");
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
    return Map.copyOf(result);
  }

  public record MaterializedClient(Path directory, Acquisition acquiredBy) {}
  public record MaterializedInputs(Path questie, Optional<Path> atlasLoot, Optional<Path> allTheThings,
      Optional<MaterializedClient> client) {}
  public record PreparedImport(ImportOptions options, CoreSourceSelection coreSelection,
      SetupStorage storage, SetupStorage.JobPaths job) {
    /** Capture into the job archive only. Projection/validation/activation are separate subsequent stages. */
    public String capture() throws Exception {
      storage.verifyJob(job);
      if (!options.out().equals(job.archiveDatabase())) throw new IOException("Capture output must be the staged source archive");
      return ArchiveWorkflow.ingest(options,coreSelection);
    }
  }

  /** Concrete bridge to the existing importer. All selected inputs must already be materialized locally. */
  public PreparedImport prepareCapture(SetupStorage storage, SetupStorage.JobPaths job, MaterializedInputs inputs)
      throws IOException {
    if (!sourcePins.equals(currentPins(sources))) throw new IOException("Selected source pins changed; review a new plan before importing");
    if (!sources.contains(Source.NATIVE_DATA))
      throw new IOException("A runnable Forever catalog currently requires genuine native build data; community-only activation is not supported");
    for (Path root : localGameRoots) if (!Files.isDirectory(root)) throw new IOException("Selected WoW installation is unavailable");
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
    MaterializedClient client = inputs.client().orElseThrow(() -> new IOException("Selected native tables are not materialized"));
    if (!allows(client.acquiredBy())) throw new IOException("Native input used an acquisition method that was not selected");
    if (!Files.isDirectory(client.directory())) throw new IOException("Native input directory is unavailable");
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
    if (lock.hotfixSha256() != null || Files.exists(client.directory().resolve("hotfix.json")))
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
    return new PreparedImport(options,coreSelection(),storage,job);
  }

  private static boolean isWago(String baseUrl) {
    try {
      String host = java.net.URI.create(baseUrl).getHost();
      return host != null && (host.equalsIgnoreCase("wago.tools") || host.toLowerCase(Locale.ROOT).endsWith(".wago.tools"));
    } catch (IllegalArgumentException ignored) { return false; }
  }
  private static String hash(Path file) throws IOException {
    try {
      var digest = java.security.MessageDigest.getInstance("SHA-256");
      try (var input=Files.newInputStream(file)) {
        byte[] buffer=new byte[65536];int count;
        while((count=input.read(buffer))!=-1) digest.update(buffer,0,count);
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
