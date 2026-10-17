package dev.wowforever.importer.clientdata;

import dev.wowforever.importer.Fixtures;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A copy of the client-data fixture with a hotfix overlay as {@code tools/clientdata/convert.py} writes it:
 * {@code csv/ItemSparse.hotfix.csv}, {@code csv/Item.hotfix.csv}, {@code csv/ItemEffect.hotfix.csv},
 * {@code hotfix.json} and the lock's {@code hotfix} entry. Overlay rows are copies of fixture rows (a template id)
 * with some cells replaced.
 */
public final class HotfixFixture {
  public static final String SHA = "ab".repeat(32);
  static final String LAST_MODIFIED = "Sat, 17 Oct 2026 02:57:00 GMT";
  private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");

  private final Path dir;
  private final Map<String, List<String>> rows = new LinkedHashMap<>();
  private int headerBuild = 70425;
  private int lockHeaderBuild = 70425;
  private String lockSha = SHA;
  private long invalidated = 38;
  private long removed;
  private int snapshotsSkipped;

  private HotfixFixture(Path dir) {
    this.dir = dir;
    for (String t : List.of("ItemSparse", "Item", "ItemEffect")) {
      rows.put(t, new ArrayList<>());
    }
  }

  /** A copy of {@link Fixtures#clientDataRoot()} under {@code tmp}, without an overlay yet. */
  public static HotfixFixture copy(Path tmp) {
    Path dir = tmp.resolve("clientdata");
    Path from = Fixtures.clientDataRoot();
    try (Stream<Path> files = Files.walk(from)) {
      for (Path p : (Iterable<Path>) files::iterator) {
        Path target = dir.resolve(from.relativize(p).toString());
        if (Files.isDirectory(p)) {
          Files.createDirectories(target);
        } else {
          Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return new HotfixFixture(dir);
  }

  public Path dir() {
    return dir;
  }

  /** An overlay row of {@code table}: the fixture row {@code templateId} with {@code cells} replaced. */
  HotfixFixture row(String table, long templateId, Map<String, String> cells) {
    CsvTable base = CsvTable.read(dir.resolve("csv").resolve(table + ".csv"));
    CsvTable.Row template = base.indexByLong("ID").get(templateId);
    if (template == null) {
      throw new IllegalArgumentException(table + " has no row " + templateId);
    }
    List<String> out = new ArrayList<>();
    for (String col : base.columns()) {
      out.add(cell(cells.getOrDefault(col, template.get(col))));
    }
    rows.get(table).add(String.join(",", out));
    return this;
  }

  HotfixFixture headerBuild(int build) {
    this.headerBuild = build;
    return this;
  }

  HotfixFixture lockHeaderBuild(int build) {
    this.lockHeaderBuild = build;
    return this;
  }

  HotfixFixture lockSha(String sha) {
    this.lockSha = sha;
    return this;
  }

  HotfixFixture invalidated(long n) {
    this.invalidated = n;
    return this;
  }

  HotfixFixture removed(long n) {
    this.removed = n;
    return this;
  }

  /** Records {@code n} skipped snapshots in hotfix.json, their SHA-256s {@code dd..}, {@code ee..}. */
  HotfixFixture snapshotsSkipped(int n) {
    this.snapshotsSkipped = n;
    return this;
  }

  /** Writes the overlay CSVs, {@code hotfix.json} and the lock's {@code hotfix} entry. */
  HotfixFixture write() {
    try {
      StringBuilder tables = new StringBuilder();
      for (Map.Entry<String, List<String>> e : rows.entrySet()) {
        String header = Files.readAllLines(dir.resolve("csv").resolve(e.getKey() + ".csv"), StandardCharsets.UTF_8)
            .getFirst();
        String text = header + "\n" + e.getValue().stream().map(r -> r + "\n").reduce("", String::concat);
        Path csv = dir.resolve("csv").resolve(e.getKey() + ".hotfix.csv");
        Files.writeString(csv, text, StandardCharsets.UTF_8);
        if (!tables.isEmpty()) {
          tables.append(", ");
        }
        tables.append("\"").append(e.getKey()).append("\": {\"rows\": ").append(e.getValue().size())
            .append(", \"valid\": ").append(e.getValue().size() + (e.getKey().equals("ItemSparse") ? 1 : 0))
            .append(", \"invalidated\": ").append(e.getKey().equals("ItemSparse") ? invalidated : 0)
            .append(", \"undone\": 0, \"removed\": ").append(e.getKey().equals("ItemSparse") ? removed : 0)
            .append(", \"sha256\": \"").append(sha256(Files.readAllBytes(csv))).append("\"}");
      }
      String build = BuildLock.read(dir.resolve("build.lock")).build();
      Files.writeString(dir.resolve("hotfix.json"), "{\"url\": \"https://example.test/DBCache.bin\", \"sha256\": \""
          + SHA + "\", \"header_build\": " + headerBuild + ", \"last_modified\": \"" + LAST_MODIFIED
          + "\", \"fetched_at\": \"2026-10-17T17:00:00Z\", \"build\": \"" + build + "\", \"snapshots\": 1,"
          + " \"snapshots_skipped\": " + snapshotsSkipped + ", \"skipped\": [" + String.join(", ",
              IntStream.range(0, snapshotsSkipped)
                  .mapToObj(i -> "\"" + "de".substring(i % 2, i % 2 + 1).repeat(64) + "\"").toList())
          + "], \"wowdbdefs_commit\": \"" + "c".repeat(40) + "\", \"tables\": {" + tables
          + "}}\n", StandardCharsets.UTF_8);
      String lock = Files.readString(dir.resolve("build.lock"), StandardCharsets.UTF_8).strip();
      lock = lock.substring(0, lock.length() - 1) + ", \"hotfix\": {\"url\": \"https://example.test/DBCache.bin\","
          + " \"sha256\": \"" + lockSha + "\", \"header_build\": " + lockHeaderBuild + ", \"last_modified\": \""
          + LAST_MODIFIED + "\", \"fetched_at\": \"2026-10-17T17:00:00Z\", \"size\": 3380951}}\n";
      Files.writeString(dir.resolve("build.lock"), lock, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return this;
  }

  /** The usual overlay: a Forever item, an Era item, an icon-only item and one Questie lacks, plus an icon and a charge. */
  public static HotfixFixture standard(Path tmp) {
    return copy(tmp)
        .row("ItemSparse", 25, Map.of("Display_lang", "Worn Shortsword (hotfix)", "ItemLevel", "7"))
        .row("ItemSparse", 25, Map.of("ID", "16805", "Display_lang", "Felheart Gloves", "InventoryType", "10",
            "ItemLevel", "66", "OverallQualityID", "4", "ItemSet", "203", "StatModifier_bonusStat_0", "7",
            "StatPercentEditor_0", "5000", "ItemDelay", "0"))
        .row("ItemSparse", 117, Map.of("ID", "212347", "Display_lang", "Illari's Key"))
        .row("ItemSparse", 117, Map.of("ID", "999999", "Display_lang", "Not in Questie"))
        .row("Item", 25, Map.of("IconFileDataID", "135000"))
        .row("ItemEffect", 98985, Map.of("Charges", "-5"))
        .write();
  }

  private static String cell(String v) {
    return v.isEmpty() || NUMBER.matcher(v).matches() ? v : "\"" + v.replace("\"", "\"\"") + "\"";
  }

  static String sha256(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
