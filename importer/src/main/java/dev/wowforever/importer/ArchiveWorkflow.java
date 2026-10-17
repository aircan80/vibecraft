package dev.wowforever.importer;

import dev.wowforever.importer.archive.SourceArchive;
import dev.wowforever.importer.archive.records.SourceRecords;
import dev.wowforever.importer.archive.SourceArchive.Artifact;
import dev.wowforever.importer.archive.SourceArchive.Snapshot;
import dev.wowforever.importer.clientdata.ClientDataOptions;
import dev.wowforever.importer.clientdata.ClientDataStage;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.source.SourceFetcher;
import dev.wowforever.importer.source.SourceSpec;
import java.io.*;
import java.net.http.HttpClient;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Two independently invocable stages. Source capture never deletes app tables or older snapshots. */
public final class ArchiveWorkflow {
  private ArchiveWorkflow() {}

  public static String ingest(ImportOptions options) throws Exception {
    validateOutputs(options);
    Path database = options.out().toAbsolutePath();
    Files.createDirectories(database.getParent());
    try (FileChannel channel = FileChannel.open(database.resolveSibling(database.getFileName()+".lock"),
        StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.tryLock()) {
      if (lock == null) throw new IOException("Another import is using " + database);
      List<SourceSpec> specs = SourceSpec.load();
      var fetcher = new SourceFetcher(options.cacheDir(), HttpClient.newHttpClient());
      Map<String, List<Artifact>> inputs = new LinkedHashMap<>();
      Map<String, Snapshot> descriptions = new LinkedHashMap<>();
      for (SourceSpec spec : specs) {
        Optional<Path> override = spec.key().equals("questiedb") ? options.questieDbDir() : options.atlasLootDir();
        captureSpec(inputs, descriptions, spec.key(), spec, fetcher.resolve(spec, override), override.isPresent());
        if (spec.baseline() != null)
          captureSpec(inputs, descriptions, "atlasloot-baseline", spec.baseline(),
              fetcher.resolve(spec.baseline(), Optional.empty()), false);
      }
      if (options.clientData().dir().isPresent()) {
        inputs.put("client", directory(options.clientData().dir().orElseThrow()));
        descriptions.put("client", metadata("blizzard-client", inputs.get("client"), "build.lock"));
      }
      if (options.serverDbDir().isPresent()) {
        inputs.put("server", directory(options.serverDbDir().orElseThrow()));
        descriptions.put("server", metadata("vmangos", inputs.get("server"), "serverdb.lock"));
      }
      SourceSpec att = null;
      if (!options.skipAllTheThings()) {
        att = attSpec();
        Path root=fetcher.resolve(att,options.allTheThingsDir());
        // ATT projection is intentionally strict: a declared acquisition cannot be partial.
        for(String path:att.paths()) if(!Files.isRegularFile(root.resolve(path)))
          throw new IOException("Missing AllTheThings source input: "+path);
        captureSpec(inputs,descriptions,"allthethings",att,root,options.allTheThingsDir().isPresent());
      }
      byte[] config;
      try (InputStream stream = SourceSpec.class.getResourceAsStream("/dev/wowforever/importer/sources.properties")) {
        config = Objects.requireNonNull(stream).readAllBytes();
      }
      Properties settings = new Properties();
      settings.setProperty("failOnUnsupported", Boolean.toString(options.failOnUnsupported()));
      settings.setProperty("coverageMin", Integer.toString(options.coverageMinPct()));
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      // Properties.store includes a timestamp: remove it so an identical capture remains idempotent.
      settings.store(bytes, null);
      String canonical = bytes.toString(java.nio.charset.StandardCharsets.ISO_8859_1).lines()
          .filter(line -> !line.startsWith("#")).sorted().collect(java.util.stream.Collectors.joining("\n"));
      List<Artifact> configuration=new ArrayList<>(List.of(new Artifact("sources.properties", "", config),
          new Artifact("projection.properties", "", canonical.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1))));
      if(att!=null) configuration.addAll(attConfiguration(att));
      inputs.put("configuration", configuration);
      descriptions.put("configuration", new Snapshot("import-configuration", "1", "", "", ""));
      try (Connection connection = connect(database)) {
        connection.setAutoCommit(false);
        initialize(connection);
        SortedMap<String,String> selection = new TreeMap<>();
        for (var entry : inputs.entrySet())
          selection.put(entry.getKey(), SourceArchive.capture(connection, descriptions.get(entry.getKey()), entry.getValue()));
        String id = hash(selection.toString());
        try (PreparedStatement statement = connection.prepareStatement("INSERT OR IGNORE INTO source_ingestion(id,captured_at) VALUES (?,?)")) {
          statement.setString(1,id); statement.setString(2,Instant.now().toString()); statement.executeUpdate();
        }
        for (var entry : selection.entrySet()) {
          try (PreparedStatement statement = connection.prepareStatement("INSERT OR IGNORE INTO source_ingestion_input VALUES (?,?,?)")) {
            statement.setString(1,id); statement.setString(2,entry.getKey()); statement.setString(3,entry.getValue()); statement.executeUpdate();
          }
        }
        connection.commit(); // exact originals survive even if structured indexing fails
        for (var entry : selection.entrySet()) {
          SourceRecords.capture(connection, entry.getValue(), inputs.get(entry.getKey()).stream()
              .map(a -> new SourceRecords.SourceInput(a.path(), a.bytes())).toList());
        }
        connection.commit();
        return id;
      }
    }
  }

  public static int project(Path databasePath, String ingestion, PrintStream console) throws Exception {
    return project(databasePath, ingestion, console, console);
  }

  public static int project(Path databasePath, String ingestion, PrintStream console, PrintStream errors) throws Exception {
    Path database = databasePath.toAbsolutePath();
    validateDatabaseName(database);
    if (!Files.isRegularFile(database)) throw new IOException("Source database does not exist: " + database);
    try (FileChannel channel = FileChannel.open(database.resolveSibling(database.getFileName()+".lock"),
        StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.tryLock()) {
      if (lock == null) throw new IOException("Another import is using " + database);
      Path work = Files.createTempDirectory(database.getParent(), ".projection-");
      try {
        Map<String,String> selection = new TreeMap<>();
        boolean pinnedAtt=true;
        try (Connection connection = connect(database);
            PreparedStatement statement = connection.prepareStatement("SELECT role,snapshot_id FROM source_ingestion_input WHERE ingestion_id=?")) {
          try(var guard=connection.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name IN ('client_table_dataset','instance_map','map_instance_access','hotfix_item_fact','questie_refresh_change','stat_label_previous') LIMIT 1"); var result=guard.executeQuery()) {
            if(result.next()) throw new IllegalArgumentException("Core projection refused: additive client, map or hotfix data requires explicit replay; existing database preserved");
          }
          statement.setString(1,ingestion);
          try (ResultSet rows = statement.executeQuery()) { while(rows.next()) selection.put(rows.getString(1), rows.getString(2)); }
          try(var overlays=connection.createStatement();var present=overlays.executeQuery(
              "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='collector_observation'")) {
            if(present.next() && present.getInt(1)>0) {
              try(var rows=connection.createStatement();var observations=rows.executeQuery("SELECT 1 FROM collector_observation LIMIT 1")) {
                if(observations.next()) throw new IllegalArgumentException("Core projection refused: collector overlay requires explicit replay; existing database preserved");
              }
            }
          }
          try(var tables=connection.createStatement();var present=tables.executeQuery("SELECT 1 FROM sqlite_master WHERE name='meta'")) {
            if(present.next())try(var metadata=connection.createStatement();var map=metadata.executeQuery("SELECT value FROM meta WHERE key='world_map_snapshot_id'")) {
              if(map.next() && !map.getString(1).isBlank())throw new IllegalArgumentException("Core projection refused: world map overlay requires explicit replay; existing database preserved");
            }
          }
          try(var tables=connection.createStatement();var present=tables.executeQuery("SELECT 1 FROM sqlite_master WHERE name='meta'")) {
            if(present.next())try(var metadata=connection.createStatement();var overlay=metadata.executeQuery("SELECT value FROM meta WHERE key='forever_loot_snapshot_id'")) {
              if(overlay.next() && !overlay.getString(1).isBlank())throw new IllegalArgumentException("Core projection refused: Forever loot overlay requires explicit replay; existing database preserved");
            }
          }
          boolean legacy=selection.containsKey("legacy-app");
          if (selection.keySet().stream().anyMatch(role -> role.equals("collector") || role.startsWith("collector-")))
            throw new IllegalArgumentException("Collector ingestion is archive-only until explicit project-collector; use that command to project observations");
          if (!(legacy ? selection.keySet().containsAll(List.of("configuration","legacy-app","allthethings"))
              : selection.keySet().containsAll(List.of("configuration", "questiedb", "atlasloot"))))
            throw new IllegalArgumentException("Unknown or incomplete ingestion: " + ingestion);
          if (!Set.of("configuration","questiedb","atlasloot","atlasloot-baseline","client","server","allthethings","legacy-app").containsAll(selection.keySet()))
            throw new IOException("Unexpected archived input role");
          if(selection.containsKey("allthethings")) pinnedAtt=!SourceArchive.snapshot(connection,selection.get("allthethings"))
              .metadata().version().contains("(local input;");
          for (var entry : selection.entrySet()) SourceArchive.extract(connection,entry.getValue(),work.resolve(entry.getKey()));
        }
        Properties specs = new Properties(), settings = new Properties();
        boolean legacy=selection.containsKey("legacy-app");
        if(!legacy) try (InputStream stream = Files.newInputStream(work.resolve("configuration/sources.properties"))) { specs.load(stream); }
        try (InputStream stream = Files.newInputStream(work.resolve("configuration/projection.properties"))) { settings.load(stream); }
        if(!Set.of("true","false").contains(settings.getProperty("failOnUnsupported","")) || !settings.containsKey("coverageMin"))
          throw new IOException("Archived projection settings are incomplete or invalid");
        Path candidate = work.resolve("candidate.sqlite");
        ImportOptions options = new ImportOptions(candidate, work.resolve("empty-cache"),
            Optional.of(work.resolve("questiedb")), Optional.of(work.resolve("atlasloot")),
            Boolean.parseBoolean(settings.getProperty("failOnUnsupported")), work.resolve("build-id.txt"),
            selection.containsKey("client") ? ClientDataOptions.of(work.resolve("client")) : ClientDataOptions.skipped(),
            Integer.parseInt(settings.getProperty("coverageMin")),
            selection.containsKey("server") ? Optional.of(work.resolve("server")) : Optional.empty());
        ImportReport report;
        if(legacy) {
          if(!selection.keySet().equals(Set.of("configuration","legacy-app","allthethings")))
            throw new IOException("Legacy ingestion contains unexpected input roles");
          Files.copy(work.resolve("legacy-app/base.sqlite"),candidate);
          try(Connection c=connect(candidate); Statement query=c.createStatement();
              ResultSet rows=query.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name GLOB 'source_*'")) {
            if(rows.next()) throw new IOException("Legacy baseline must predate source archiving");
          }
          report=new ImportReport();
          report.note("Legacy baseline retained; core data was not regenerated or revalidated for client coverage.");
        } else {
          List<SourceSpec> sourceSpecs = SourceSpec.load(specs);
          if (sourceSpecs.stream().anyMatch(x -> x.baseline() != null) && !selection.containsKey("atlasloot-baseline"))
            throw new IOException("Archived AtlasLoot baseline is missing; offline projection refused");
          report = ImportPipeline.runResolved(options, console, sourceSpecs,
              selection.containsKey("atlasloot-baseline") ? work.resolve("atlasloot-baseline") : null);
        }
        if(selection.containsKey("allthethings")) {
          SourceSpec att=archivedAttSpec(work.resolve("configuration"));
          try(Connection connection=connect(candidate)) {
            connection.setAutoCommit(false);
            var stats=dev.wowforever.importer.sqlite.AllTheThingsProjection.apply(connection,
                work.resolve("allthethings"),selection.get("allthethings"),att,pinnedAtt);
            report.count("allthethings.questLinksSelected",stats.selected());
            report.count("allthethings.questGroupsConflicting",stats.conflicts());
            report.count("allthethings.conditionsUnsupported",stats.unsupported());
            String original;
            try(Statement query=connection.createStatement();ResultSet value=query.executeQuery("SELECT value FROM meta WHERE key='build_id'")) {
              if(!value.next())throw new SQLException("Candidate build ID missing");original=value.getString(1);
            }
            String build=hash("att-project-v1\n"+ingestion+"\n"+transformationVersion()+"\n"+(legacy ? original : "core"));
            try(PreparedStatement update=connection.prepareStatement("INSERT OR REPLACE INTO meta(key,value) VALUES ('build_id',?)")) {
              update.setString(1,build);update.executeUpdate();
            }
            connection.commit();Files.writeString(work.resolve("build-id.txt"),build);
          }
        }
        if(selection.containsKey("allthethings") || legacy) Files.writeString(work.resolve("import-report.txt"),report.render());
        long lost = report.skipsOnUiFields()+Main.unsupportedCounters(report)
            +(ClientDataStage.coverageTooLow(report,options.coverageMinPct()) ? 1 : 0);
        if (Main.exitCode(options.failOnUnsupported(), lost) != 0) {
          long skips=report.skipsOnUiFields(), unsupported=Main.unsupportedCounters(report);
          if (skips+unsupported>0) errors.println(skips+" skip(s) and "+unsupported
              +" missingField/unresolved counter(s) lost data shown in the UI; failing because of --fail-on-unsupported");
          if (ClientDataStage.coverageTooLow(report,options.coverageMinPct())) errors.println("client data covers "
              +report.get(ClientDataStage.NAMED_MATCHED)+" of "+report.get(ClientDataStage.NAMED)+" named items ("
              +report.get(ClientDataStage.COVERAGE_PCT)+" %), below --coverage-min "+options.coverageMinPct()
              +" %; see the clientdata.unmatched.ranges note; failing because of --fail-on-unsupported");
          console.println("Projection failed validation; captured sources and previous app tables retained."); return 2;
        }
        // Copy the authoritative archive and full history into the validated candidate, never the reverse.
        try (Connection connection = connect(candidate)) {
          connection.setAutoCommit(false);
          try (PreparedStatement attach = connection.prepareStatement("ATTACH DATABASE ? AS archive")) {
            attach.setString(1,database.toString()); attach.execute();
          }
          List<String[]> tables = new ArrayList<>();
          try (Statement statement=connection.createStatement(); ResultSet rows=statement.executeQuery(
              "SELECT name,sql FROM archive.sqlite_master WHERE type='table' AND name GLOB 'source_*' ORDER BY name")) {
            while(rows.next()) tables.add(new String[]{rows.getString(1),rows.getString(2)});
          }
          for (String[] table:tables) try (Statement statement=connection.createStatement()) {
            statement.execute(table[1]);
            String name="\""+table[0].replace("\"","\"\"")+"\"";
            statement.executeUpdate("INSERT INTO main."+name+" SELECT * FROM archive."+name);
          }
          ensureProjectionColumns(connection);
          try (PreparedStatement statement=connection.prepareStatement(
              "INSERT INTO source_projection(ingestion_id,projected_at,transform_version,schema_version,build_id,validation_summary) VALUES (?,?,?,?,?,?)")) {
            statement.setString(1,ingestion);statement.setString(2,Instant.now().toString());
            statement.setString(3,transformationVersion());statement.setInt(4,dev.wowforever.importer.sqlite.SqliteWriter.SCHEMA_VERSION);
            try (Statement query=connection.createStatement();ResultSet value=query.executeQuery("SELECT value FROM meta WHERE key='build_id'")) {
              if (!value.next()) throw new SQLException("Candidate build ID missing");
              statement.setString(5,value.getString(1));
            }
            statement.setString(6,(legacy ? "Legacy baseline retained; core client coverage not revalidated; " : "")+"UI skips="+report.skipsOnUiFields()+"; unsupported fields="+Main.unsupportedCounters(report)
                +"; client coverage="+report.get(ClientDataStage.COVERAGE_PCT)+"%; client data skipped="+report.get(ClientDataStage.SKIPPED)
                +"; ATT selected="+report.get("allthethings.questLinksSelected")+"; ATT conflicts="+report.get("allthethings.questGroupsConflicting")
                +"; ATT unsupported="+report.get("allthethings.conditionsUnsupported"));
            statement.executeUpdate();
          }
          try (Statement statement=connection.createStatement()) {
            statement.executeUpdate("INSERT OR REPLACE INTO meta(key,value) SELECT 'source_projection_id',CAST(max(id) AS TEXT) FROM source_projection");
          }
          try (PreparedStatement statement=connection.prepareStatement("INSERT OR REPLACE INTO meta(key,value) VALUES ('source_ingestion_id',?)")) {
            statement.setString(1,ingestion);statement.executeUpdate();
          }
          if(legacy) try(PreparedStatement statement=connection.prepareStatement("INSERT OR REPLACE INTO meta(key,value) VALUES ('source_base_snapshot_id',?)")) {
            statement.setString(1,selection.get("legacy-app"));statement.executeUpdate();
          }
          connection.commit();
          try (Statement statement=connection.createStatement(); ResultSet rows=statement.executeQuery("PRAGMA quick_check")) {
            if (!rows.next() || !"ok".equals(rows.getString(1))) throw new SQLException("Candidate database integrity check failed");
          }
        }
        Files.move(candidate,database,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        for (String name : List.of("build-id.txt", "import-report.txt")) {
          try { Files.copy(work.resolve(name), database.resolveSibling(name), StandardCopyOption.REPLACE_EXISTING); }
          catch (IOException failure) { errors.println("Database published successfully; could not export " + name + ": " + failure.getMessage()); }
        }
        return 0;
      } finally { removeTree(work); }
    }
  }

  static void initialize(Connection c) throws SQLException {
    SourceArchive.initialize(c);
    try (Statement s=c.createStatement()) {
      s.execute("CREATE TABLE IF NOT EXISTS source_ingestion(id TEXT PRIMARY KEY,captured_at TEXT NOT NULL)");
      s.execute("CREATE TABLE IF NOT EXISTS source_ingestion_input(ingestion_id TEXT NOT NULL,role TEXT NOT NULL,snapshot_id TEXT NOT NULL,PRIMARY KEY(ingestion_id,role))");
      s.execute("CREATE TABLE IF NOT EXISTS source_projection(id INTEGER PRIMARY KEY,ingestion_id TEXT NOT NULL,projected_at TEXT NOT NULL,transform_version TEXT NOT NULL)");
    }
    ensureProjectionColumns(c);
  }
  private static void ensureProjectionColumns(Connection c) throws SQLException {
    Set<String> columns=new HashSet<>();
    try (Statement s=c.createStatement();ResultSet r=s.executeQuery("PRAGMA table_info(source_projection)")) {
      while(r.next()) columns.add(r.getString("name"));
    }
    for(var column:Map.of("schema_version","INTEGER","build_id","TEXT","validation_summary","TEXT").entrySet())
      if(!columns.contains(column.getKey())) try(Statement s=c.createStatement()) {
        s.execute("ALTER TABLE source_projection ADD COLUMN "+column.getKey()+" "+column.getValue());
      }
  }
  static void captureSpec(Map<String,List<Artifact>> inputs, Map<String,Snapshot> descriptions,
      String role, SourceSpec spec, Path root, boolean local) throws IOException {
    List<Artifact> files = new ArrayList<>();
    List<String> missing = new ArrayList<>();
    for(String path:spec.paths()) {
      Path file = root.resolve(path);
      rejectSymlinks(file);
      if (Files.notExists(file)) { missing.add(path); continue; }
      if (Files.isSymbolicLink(file)) throw new IOException("Symbolic source input refused: "+file);
      files.add(new Artifact(path,local ? "" : spec.zip()!=null ? spec.zip().page() : spec.rawUrl(path).toString(),Files.readAllBytes(file)));
    }
    if (!missing.isEmpty()) files.add(new Artifact("__capture__/missing-inputs.txt", "",
        String.join("\n", missing).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    inputs.put(role,files);
    descriptions.put(role,new Snapshot(role,spec.pin()+(!missing.isEmpty() ? " (partial selection; see missing-inputs.txt)" : "")+(local ? " (local input; content identified by hash)" : ""),
        role.startsWith("atlasloot") ? "classic" : "forever","",""));
  }
  static SourceSpec attSpec() {
    return new SourceSpec("allthethings",dev.wowforever.importer.allthethings.AllTheThingsSource.OWNER,
        dev.wowforever.importer.allthethings.AllTheThingsSource.REPOSITORY,
        dev.wowforever.importer.allthethings.AllTheThingsSource.PIN,
        dev.wowforever.importer.allthethings.AllTheThingsSource.paths());
  }
  static List<Artifact> attConfiguration(SourceSpec spec) {
    String properties="allthethings.owner="+spec.owner()+"\nallthethings.repo="+spec.repo()+"\nallthethings.commit="+spec.commit()+"\n";
    return List.of(new Artifact("allthethings.properties","",properties.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)),
        new Artifact("allthethings-paths.txt","",String.join("\n",spec.paths()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }
  static SourceSpec archivedAttSpec(Path configuration) throws IOException {
    Properties properties=new Properties();
    try(InputStream input=Files.newInputStream(configuration.resolve("allthethings.properties"))) { properties.load(input); }
    List<String> paths=Files.readAllLines(configuration.resolve("allthethings-paths.txt"),java.nio.charset.StandardCharsets.UTF_8);
    if(paths.isEmpty() || paths.stream().anyMatch(String::isBlank))throw new IOException("Empty ATT acquisition manifest");
    for(String key:List.of("owner","repo","commit"))
      if(properties.getProperty("allthethings."+key,"").isBlank())throw new IOException("Missing archived ATT "+key);
    return new SourceSpec("allthethings",properties.getProperty("allthethings.owner"),properties.getProperty("allthethings.repo"),
        properties.getProperty("allthethings.commit"),paths);
  }
  private static List<Artifact> directory(Path directory) throws IOException {
    List<Artifact> files = new ArrayList<>();
    rejectSymlinks(directory);
    try(var paths=Files.walk(directory)) {
      for(Path file:paths.sorted().toList()) {
        if(Files.isSymbolicLink(file)) throw new IOException("Symbolic source input refused: "+file);
        if(Files.isRegularFile(file)) files.add(new Artifact(directory.relativize(file).toString().replace('\\','/'),"",Files.readAllBytes(file)));
      }
    }
    return files;
  }
  private static String transformationVersion() throws Exception {
    var digest = java.security.MessageDigest.getInstance("SHA-256");
    Set<Path> implementations = new TreeSet<>();
    for (Class<?> component : List.of(ImportPipeline.class, dev.wowforever.lua.LuaChunk.class))
      implementations.add(Path.of(component.getProtectionDomain().getCodeSource().getLocation().toURI()));
    for (Path implementation : implementations) {
      if (Files.isDirectory(implementation)) {
        try (var paths = Files.walk(implementation)) {
          for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
            digest.update(implementation.relativize(file).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try (InputStream stream = Files.newInputStream(file)) {
              byte[] buffer = new byte[65536]; int read;
              while ((read = stream.read(buffer)) != -1) digest.update(buffer,0,read);
            }
          }
        }
      } else {
        try (InputStream stream = Files.newInputStream(implementation)) {
          byte[] buffer = new byte[65536]; int read;
          while ((read = stream.read(buffer)) != -1) digest.update(buffer,0,read);
        }
      }
    }
    return "archive-project-v1/sha256:" + HexFormat.of().formatHex(digest.digest());
  }
  private static Snapshot metadata(String source, List<Artifact> captured, String lockName) {
    try {
      byte[] bytes = captured.stream().filter(a -> a.path().equals(lockName)).findFirst().orElseThrow().bytes();
      Object parsed = dev.wowforever.importer.clientdata.MiniJson.parse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
      if (parsed instanceof Map<?,?> values) return new Snapshot(source,
          Objects.toString(values.get("commit"), Objects.toString(values.get("build"), "unrecorded")),
          Objects.toString(values.get("product"), source.equals("vmangos") ? "vanilla" : "unrecorded"),
          Objects.toString(values.get("build"), "unrecorded"), Objects.toString(values.get("locale"), "unrecorded"));
    } catch (RuntimeException ignored) { /* malformed metadata remains archived for diagnosis */ }
    return new Snapshot(source,"unreadable metadata","unrecorded","unrecorded","unrecorded");
  }
  private static void rejectSymlinks(Path path) throws IOException {
    for (Path p=path.toAbsolutePath();p!=null;p=p.getParent())
      if (Files.isSymbolicLink(p)) throw new IOException("Symbolic source input refused: "+p);
  }
  static void validateOutputs(ImportOptions options) throws IOException {
    Path database=options.out().toAbsolutePath().normalize();
    validateDatabaseName(database);
    if (sameFile(database,options.buildIdOut())) throw new IOException("Database and build ID output must be different files");
    if (sameFile(options.buildIdOut(),database.resolveSibling("import-report.txt")))
      throw new IOException("Build ID and report output must be different files");
  }
  private static void validateDatabaseName(Path database) throws IOException {
    for (String sidecar : List.of("build-id.txt","import-report.txt"))
      if (sameFile(database,database.resolveSibling(sidecar)))
        throw new IOException("Database and sidecar output must be different files: "+database);
  }
  private static boolean sameFile(Path a,Path b) throws IOException {
    return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize())
        || (Files.exists(a) && Files.exists(b) && Files.isSameFile(a,b));
  }
  private static Connection connect(Path file) throws SQLException { return DriverManager.getConnection("jdbc:sqlite:"+file); }
  static String hash(String value) {
    try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
    catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
  private static void removeTree(Path directory) throws IOException {
    try(var paths=Files.walk(directory)) { for(Path path:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
  }
}
