package dev.wowforever.importer.serverdb;

import dev.wowforever.importer.clientdata.MiniJson;
import dev.wowforever.importer.drops.DropRates;
import dev.wowforever.importer.report.ImportReport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongPredicate;

/**
 * The importer's VMaNGOS stage: without {@code --server-db-dir}, or when that directory has no world database, it is
 * skipped ({@code serverdb.skipped = 1}, with a note in the second case); otherwise {@link ServerDropRates} adds the
 * community rates and the {@code serverdb_*} meta keys come from the {@code serverdb.lock} copy beside the database.
 */
public final class ServerDbStage {
  private ServerDbStage() {}

  /** Runs the stage and returns the meta keys to write (empty when skipped or without a lock copy). */
  public static Map<String, String> run(Optional<Path> dir, LongPredicate knownNpc, DropRates rates,
      ImportReport report) throws SQLException, IOException {
    if (dir.isEmpty()) {
      report.count("serverdb.skipped", 1);
      return Map.of();
    }
    Optional<ServerDbSource> opened = ServerDbSource.open(dir.get());
    if (opened.isEmpty()) {
      report.count("serverdb.skipped", 1);
      report.note("serverdb: no " + ServerDbSource.WORLD_DB + " in " + dir.get().toAbsolutePath()
          + "; community drop rates skipped (run tools/serverdb/fetch.py)");
      return Map.of();
    }
    report.count("serverdb.skipped", 0);
    try (ServerDbSource db = opened.get()) {
      ServerDropRates.load(db, knownNpc, rates, report);
    }
    return meta(dir.get().resolve(ServerDbSource.LOCK));
  }

  /** {@code serverdb_asset} and {@code serverdb_commit} from the lock copy, when it has them. */
  static Map<String, String> meta(Path lock) throws IOException {
    Map<String, String> meta = new LinkedHashMap<>();
    if (!Files.isRegularFile(lock)) {
      return meta;
    }
    if (MiniJson.parse(Files.readString(lock, StandardCharsets.UTF_8)) instanceof Map<?, ?> root) {
      if (root.get("asset") instanceof String asset) {
        meta.put("serverdb_asset", asset);
      }
      if (root.get("commit") instanceof String commit) {
        meta.put("serverdb_commit", commit);
      }
    }
    return meta;
  }
}
