package dev.wowforever.importer;

import dev.wowforever.importer.archive.SourceArchive;
import dev.wowforever.importer.archive.records.SourceRecords;
import dev.wowforever.lua.*;
import dev.wowforever.lua.ast.Expr;
import dev.wowforever.lua.ast.Stmt;
import dev.wowforever.lua.ast.TableEntry;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.List;

/** Explicit, local, archive-only capture. No observation is promoted into application facts. */
public final class CollectorArchiveWorkflow {
  private static final String ARTIFACT = "WowForeverCollector.lua";
  private CollectorArchiveWorkflow() {}

  public static String ingest(Path databasePath, Path inputPath) throws Exception {
    Path database = databasePath.toAbsolutePath().normalize();
    Path input = inputPath.toAbsolutePath().normalize();
    rejectSymlinks(input);
    rejectSymlinks(database);
    if (!Files.isRegularFile(input)) throw new IOException("Collector input must be an explicit regular file");
    for (Path reserved : List.of(database, database.resolveSibling(database.getFileName()+".lock"),
        database.resolveSibling(database.getFileName()+"-wal"), database.resolveSibling(database.getFileName()+"-shm"),
        database.resolveSibling(database.getFileName()+"-journal"),
        database.resolveSibling("build-id.txt"), database.resolveSibling("import-report.txt"))) {
      rejectSymlinks(reserved);
      if (sameFile(input,reserved)) throw new IOException("Collector input must differ from database and sidecars");
    }
    for (String suffix : List.of(".lock","-wal","-shm","-journal"))
      if (sameFile(database,input.resolveSibling(input.getFileName()+suffix)))
        throw new IOException("Database must differ from collector input sidecars");
    for (String name : List.of("build-id.txt","import-report.txt"))
      if (database.getFileName().toString().equals(name)) throw new IOException("Database and sidecar output must differ");
    byte[] bytes = Files.readAllBytes(input);
    // SavedVariables occasionally contains bad UTF-8. Decode leniently; authoritative bytes remain exact.
    String decoded = new String(bytes,StandardCharsets.UTF_8);
    LuaTable root = read(decoded);
    if (LuaValues.asLong(root.get("schema")).orElse(-1L) != 1L)
      throw new IllegalArgumentException("Unsupported collector schema; expected 1");
    LuaTable session = LuaValues.asTable(root.get("session"))
        .orElseThrow(() -> new IllegalArgumentException("Collector session table is required"));
    String build = required(session,"build"), number = required(session,"buildNumber");
    if (!build.matches("1\\.60\\.[0-9]+") || !number.matches("[0-9]+")
        || LuaValues.asLong(session.get("interface")).orElse(-1L) != 16001L)
      throw new IllegalArgumentException("Collector input must identify a Forever 1.60 client (interface 16001)");
    String addon = required(session,"addonVersion"), locale = required(session,"locale");
    Files.createDirectories(database.getParent());
    Path lockPath = database.resolveSibling(database.getFileName()+".lock");
    rejectSymlinks(lockPath);
    try (FileChannel channel = FileChannel.open(lockPath,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
         var lock = channel.tryLock()) {
      if (lock == null) throw new IOException("Another import is using " + database);
      try (Connection connection = DriverManager.getConnection("jdbc:sqlite:"+database)) {
        connection.setAutoCommit(false);
        ArchiveWorkflow.initialize(connection);
        String snapshot = SourceArchive.capture(connection,
            new SourceArchive.Snapshot("wowforever-collector","schema 1; addon "+addon,"forever",build+"."+number,locale),
            List.of(new SourceArchive.Artifact(ARTIFACT,"",bytes)));
        String ingestion = ArchiveWorkflow.hash("collector="+snapshot);
        try (var statement = connection.prepareStatement("INSERT OR IGNORE INTO source_ingestion(id,captured_at) VALUES (?,?)")) {
          statement.setString(1,ingestion); statement.setString(2,Instant.now().toString()); statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("INSERT OR IGNORE INTO source_ingestion_input VALUES (?,?,?)")) {
          statement.setString(1,ingestion); statement.setString(2,"collector"); statement.setString(3,snapshot); statement.executeUpdate();
        }
        connection.commit();
        SourceRecords.capture(connection,snapshot,List.of(new SourceRecords.SourceInput(ARTIFACT,bytes,true)));
        connection.commit();
        return ingestion;
      }
    }
  }

  private static LuaTable read(String text) {
    var chunk = LuaChunk.parse(text,ARTIFACT);
    if (chunk.statements().size()!=1 || !(chunk.statements().getFirst() instanceof Stmt.Assign assignment)
        || !(assignment.target() instanceof Expr.Name name) || !name.name().equals("WowForeverCollectorDB")
        || !(assignment.value() instanceof Expr.TableCtor) || !literal(assignment.value()))
      throw new IllegalArgumentException("Expected one literal WowForeverCollectorDB table assignment");
    var scope = new Scope(); var problems = new ProblemCollector();
    new ChunkEvaluator(ARTIFACT,CallHandler.NONE,problems).run(chunk.statements(),scope);
    if (!problems.problems().isEmpty()) throw new IllegalArgumentException("Invalid collector literal table");
    return LuaValues.asTable(scope.lookup("WowForeverCollectorDB").orElseThrow()).orElseThrow();
  }
  private static boolean literal(Expr expression) {
    if (expression instanceof Expr.Literal) return true;
    if (expression instanceof Expr.Unary u) return u.op().equals("-") && u.operand() instanceof Expr.Literal l && l.value() instanceof LuaNumber;
    if (!(expression instanceof Expr.TableCtor table)) return false;
    return table.entries().stream().allMatch(entry -> switch(entry) {
      case TableEntry.Named n -> literal(n.value());
      case TableEntry.Positional p -> literal(p.value());
      case TableEntry.Keyed k -> literal(k.key()) && literal(k.value());
    });
  }
  private static String required(LuaTable table,String key) {
    return LuaValues.asString(table.get(key)).filter(value -> !value.isBlank() && !value.contains("\ufffd"))
        .orElseThrow(() -> new IllegalArgumentException("Collector session requires valid " + key));
  }
  private static boolean sameFile(Path a,Path b) throws IOException {
    return a.equals(b) || (Files.exists(a) && Files.exists(b) && Files.isSameFile(a,b));
  }
  private static void rejectSymlinks(Path path) throws IOException {
    for (Path part=path;part!=null;part=part.getParent())
      if(Files.isSymbolicLink(part)) throw new IOException("Symbolic collector input/output refused");
  }
}
