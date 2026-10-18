package dev.wowforever.importer;

import dev.wowforever.importer.fs.SourcePaths;

import dev.wowforever.importer.archive.SourceArchive;
import dev.wowforever.importer.archive.SourceArchive.Artifact;
import dev.wowforever.importer.archive.SourceArchive.Snapshot;
import dev.wowforever.importer.archive.records.SourceRecords;
import dev.wowforever.importer.source.SourceFetcher;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/** Captures an existing derived app baseline plus ATT, without pretending to recover its original inputs. */
public final class LegacyAttWorkflow {
  private LegacyAttWorkflow() {}

  public static String ingest(ImportOptions options) throws Exception {
    ArchiveWorkflow.validateOutputs(options);
    if (options.clientData().dir().isPresent() || options.serverDbDir().isPresent()
        || options.questieDbDir().isPresent() || options.atlasLootDir().isPresent()) {
      throw new IOException("ingest-att retains existing app data; use normal ingest to capture these inputs");
    }
    if (options.skipAllTheThings()) throw new IOException("ingest-att requires AllTheThings; omit --skip-att");
    Path database = options.out().toAbsolutePath().normalize();
    rejectSymlinks(database);
    if (!Files.isRegularFile(database)) throw new IOException("Existing application database required: " + database);
    try (var channel = FileChannel.open(database.resolveSibling(database.getFileName() + ".lock"),
        StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.tryLock()) {
      if (lock == null) throw new IOException("Another import is using " + database);
      for (String suffix : List.of("-wal", "-journal")) {
        Path sidecar = database.resolveSibling(database.getFileName() + suffix);
        if (Files.exists(sidecar) && Files.size(sidecar) > 0) throw new IOException("Active SQLite sidecar refused: " + sidecar);
      }
      String existingBase;
      String buildId;
      try (var c = connect(database)) {
        validateApp(c);
        buildId = meta(c, "build_id").orElseThrow(() -> new IOException("Application build ID missing"));
        existingBase = baseline(c);
        if (existingBase == null && hasSourceTables(c))
          throw new IOException("Archived database has no retained prearchive baseline; use normal source projection");
        if (existingBase != null) validateBaseline(database.getParent(), SourceArchive.artifacts(c, existingBase));
      }
      // These are the untouched original bytes, before any archive tables are created.
      byte[] original = existingBase == null ? Files.readAllBytes(database) : null;
      var spec = ArchiveWorkflow.attSpec();
      var fetcher = new SourceFetcher(options.cacheDir(), HttpClient.newHttpClient());
      Path attRoot = fetcher.resolve(spec, options.allTheThingsDir()).toAbsolutePath().normalize();
      rejectSymlinks(attRoot);
      List<Artifact> attFiles = new ArrayList<>();
      for (String path : spec.paths()) {
        Path file = attRoot.resolve(path).normalize();
        if (!file.startsWith(attRoot)) throw new IOException("ATT manifest path escapes root: " + path);
        rejectSymlinks(file);
        if (!Files.isRegularFile(file)) throw new IOException("Missing AllTheThings source input: " + path);
        attFiles.add(new Artifact(path, options.allTheThingsDir().isPresent() ? "" : spec.rawUrl(path).toString(),
            Files.readAllBytes(file)));
      }
      List<Artifact> config = new ArrayList<>(ArchiveWorkflow.attConfiguration(spec));
      String settings = "coverageMin=" + options.coverageMinPct() + "\nfailOnUnsupported=" + options.failOnUnsupported() + "\n";
      config.add(new Artifact("projection.properties", "", settings.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)));
      try (var c = connect(database)) {
        c.setAutoCommit(false);
        try {
          ArchiveWorkflow.initialize(c);
          var selection = new TreeMap<String, String>();
          String base = existingBase;
          if (base == null) base = SourceArchive.capture(c,
              new Snapshot("legacy-app-database", "derived app baseline; build_id=" + buildId, "application", buildId, "unrecorded"),
              List.of(new Artifact("base.sqlite", "", original)));
          selection.put("legacy-app", base);
          selection.put("allthethings", SourceArchive.capture(c,
              new Snapshot("allthethings", options.allTheThingsDir().isPresent()
                  ? spec.pin() + " (local input; content identified by hash)" : spec.pin(),
                  "forever", "", ""), attFiles));
          selection.put("configuration", SourceArchive.capture(c,
              new Snapshot("import-configuration", "legacy-att-1", "", "", ""), config));
          String id = ArchiveWorkflow.hash(selection.toString());
          try (var s = c.prepareStatement("INSERT OR IGNORE INTO source_ingestion(id,captured_at) VALUES (?,?)")) {
            s.setString(1, id); s.setString(2, Instant.now().toString()); s.executeUpdate();
          }
          try (var s = c.prepareStatement("INSERT OR IGNORE INTO source_ingestion_input(ingestion_id,role,snapshot_id) VALUES (?,?,?)")) {
            for (var entry : selection.entrySet()) {
              s.setString(1, id); s.setString(2, entry.getKey()); s.setString(3, entry.getValue()); s.addBatch();
            }
            s.executeBatch();
          }
          c.commit(); // preserve originals even if a structured extractor fails afterward
          SourceRecords.capture(c, selection.get("allthethings"), attFiles.stream()
              .map(a -> new SourceRecords.SourceInput(a.path(), a.bytes())).toList());
          SourceRecords.capture(c, selection.get("configuration"), config.stream()
              .map(a -> new SourceRecords.SourceInput(a.path(), a.bytes())).toList());
          c.commit();
          return id;
        } catch (Exception failure) {
          c.rollback();
          throw failure;
        }
      }
    }
  }

  private static String baseline(Connection c) throws Exception {
    String explicit = meta(c, "source_base_snapshot_id").orElse(null);
    if (explicit != null) { validateBaselineMetadata(c, explicit); return explicit; }
    if (!table(c, "source_ingestion_input")) return null;
    List<String> candidates = new ArrayList<>();
    try (var s = c.createStatement(); var r = s.executeQuery(
        "SELECT DISTINCT snapshot_id FROM source_ingestion_input WHERE role='legacy-app' ORDER BY snapshot_id")) {
      while (r.next()) candidates.add(r.getString(1));
    }
    if (candidates.size() > 1) throw new IOException("Multiple legacy baselines; source_base_snapshot_id is required");
    if (candidates.isEmpty()) return null;
    validateBaselineMetadata(c, candidates.getFirst());
    return candidates.getFirst();
  }

  private static void validateBaselineMetadata(Connection c, String id) throws Exception {
    var snapshot = SourceArchive.snapshot(c, id);
    if (!snapshot.metadata().source().equals("legacy-app-database")) throw new IOException("Snapshot is not a derived app baseline: " + id);
  }

  private static void validateBaseline(Path parent, List<Artifact> artifacts) throws Exception {
    if (artifacts.size() != 1 || !artifacts.getFirst().path().equals("base.sqlite"))
      throw new IOException("Retained baseline must contain only base.sqlite");
    Path file = Files.createTempFile(parent, ".baseline-check-", ".sqlite");
    try {
      Files.write(file, artifacts.getFirst().bytes());
      try (var c = connect(file)) {
        validateApp(c);
        if (hasSourceTables(c)) throw new IOException("Retained baseline contains source archives; recursive baseline refused");
      }
    } finally { Files.deleteIfExists(file); }
  }

  private static void validateApp(Connection c) throws Exception {
    for (String name : List.of("meta", "item", "quest", "quest_relation"))
      if (!table(c, name)) throw new IOException("Application baseline table missing: " + name);
    try (var s = c.createStatement(); var r = s.executeQuery("PRAGMA quick_check")) {
      if (!r.next() || !r.getString(1).equals("ok")) throw new IOException("Application baseline integrity check failed");
    }
  }
  private static boolean hasSourceTables(Connection c) throws Exception {
    try (var s = c.createStatement(); var r = s.executeQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name GLOB 'source_*' LIMIT 1")) {
      return r.next();
    }
  }
  private static boolean table(Connection c, String name) throws Exception {
    try (var s = c.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
      s.setString(1, name); try (var r = s.executeQuery()) { return r.next(); }
    }
  }
  private static Optional<String> meta(Connection c, String key) throws Exception {
    try (var s = c.prepareStatement("SELECT value FROM meta WHERE key=?")) {
      s.setString(1, key); try (var r = s.executeQuery()) { return r.next() ? Optional.ofNullable(r.getString(1)) : Optional.empty(); }
    }
  }
  private static void rejectSymlinks(Path path) throws IOException {
    SourcePaths.rejectSymlinks(path);
  }
  private static Connection connect(Path file) throws Exception {
    return DriverManager.getConnection("jdbc:sqlite:" + file);
  }
}
