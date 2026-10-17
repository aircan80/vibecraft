package dev.wowforever.importer.clientdata;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The {@code hotfix.json} that {@code tools/clientdata/convert.py} writes beside the hotfix overlay
 * ({@code csv/ItemSparse.hotfix.csv}, {@code Item.hotfix.csv}, {@code ItemEffect.hotfix.csv}): which snapshot of the
 * client's hotfix cache, mirrored by Raidbots, the overlay is current to, and what each table's overlay holds.
 *
 * @param headerBuild  the snapshot's header build, the last number of a client build (70450)
 * @param lastModified the snapshot's HTTP Last-Modified (RFC 1123), "" when the mirror sent none
 * @param build        the client build the overlay was converted for (build.lock's)
 * @param tables       per overlay table (ItemSparse, Item, ItemEffect)
 */
public record HotfixInfo(String url, String sha256, long headerBuild, String lastModified, String fetchedAt,
    String build, Map<String, Table> tables) {
  public static final String FILE = "hotfix.json";
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

  /**
   * One table's overlay.
   *
   * @param rows        rows written to the overlay CSV
   * @param valid       distinct records with a valid (status 1) row in some snapshot
   * @param invalidated distinct invalidated pushes (status 3) seen
   * @param undone      invalidations that removed a row a snapshot had supplied
   * @param sha256      the overlay CSV's SHA-256
   */
  public record Table(long rows, long valid, long invalidated, long undone, String sha256) {}

  public HotfixInfo {
    Objects.requireNonNull(sha256, "sha256");
    Objects.requireNonNull(build, "build");
    tables = Map.copyOf(tables);
  }

  /** The overlay of {@code table}, or an empty one. */
  public Table table(String name) {
    return tables.getOrDefault(name, new Table(0, 0, 0, 0, ""));
  }

  /** The cache's date, {@code 17 Oct 2026}: Last-Modified in UTC, else the fetch date. */
  public String cacheDate() {
    try {
      return ZonedDateTime.parse(lastModified, DateTimeFormatter.RFC_1123_DATE_TIME)
          .withZoneSameInstant(ZoneOffset.UTC).format(DATE);
    } catch (DateTimeParseException e) {
      try {
        return LocalDate.parse(fetchedAt.substring(0, Math.min(10, fetchedAt.length()))).format(DATE);
      } catch (DateTimeParseException | NullPointerException ignored) {
        return "unknown date";
      }
    }
  }

  /**
   * The tooltip footer of a hotfix item: {@code Forever stats revealed by a Blizzard hotfix · beta 70450 · cache
   * 17 Oct 2026}. Beta hotfix data is never called "live" or "confirmed".
   */
  public String footer() {
    return "Forever stats revealed by a Blizzard hotfix · beta " + headerBuild + " · cache " + cacheDate();
  }

  /**
   * @throws UncheckedIOException when the file cannot be read
   * @throws IllegalStateException naming the file when it is not JSON of the expected shape
   */
  public static HotfixInfo read(Path file) {
    String text;
    try {
      text = Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + file + ": " + e.getMessage(), e);
    }
    try {
      if (!(MiniJson.parse(text) instanceof Map<?, ?> root)) {
        throw new IllegalArgumentException("not a JSON object");
      }
      Map<String, Table> tables = new LinkedHashMap<>();
      if (root.get("tables") instanceof Map<?, ?> t) {
        for (Map.Entry<?, ?> e : t.entrySet()) {
          if (!(e.getValue() instanceof Map<?, ?> m)) {
            throw new IllegalArgumentException("tables." + e.getKey() + " is not an object");
          }
          tables.put((String) e.getKey(), new Table(num(m, "rows"), num(m, "valid"), num(m, "invalidated"),
              num(m, "undone"), str(m, "sha256")));
        }
      }
      return new HotfixInfo(optional(root, "url"), str(root, "sha256"), num(root, "header_build"),
          optional(root, "last_modified"), optional(root, "fetched_at"), str(root, "build"), tables);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(file + ": " + e.getMessage(), e);
    }
  }

  static long num(Map<?, ?> m, String field) {
    if (m.get(field) instanceof Long v) {
      return v;
    }
    throw new IllegalArgumentException("missing integer field " + field);
  }

  static String str(Map<?, ?> m, String field) {
    if (m.get(field) instanceof String s) {
      return s;
    }
    throw new IllegalArgumentException("missing string field " + field);
  }

  private static String optional(Map<?, ?> m, String field) {
    return m.get(field) instanceof String s ? s : "";
  }
}
