package dev.wowforever.importer.clientdata;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The {@code build.lock} written by {@code tools/clientdata/fetch.py}: which client build the tables came from.
 * The file also carries {@code file_count} and {@code missing_count}; they describe the last fetch run only and
 * are not kept.
 *
 * @param tableSha SHA-256 (lower-case hex) of each fetched table's CSV, by table name
 */
public record BuildLock(String product, String build, String fetchedAt, String baseUrl, Map<String, String> tableSha) {

  public BuildLock {
    Objects.requireNonNull(product, "product");
    Objects.requireNonNull(build, "build");
    Objects.requireNonNull(fetchedAt, "fetchedAt");
    Objects.requireNonNull(baseUrl, "baseUrl");
    tableSha = Map.copyOf(tableSha);
  }

  /**
   * @throws UncheckedIOException when the file cannot be read
   * @throws IllegalStateException naming the file when it is not JSON of the expected shape
   */
  public static BuildLock read(Path file) {
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
      Map<String, String> tables = new LinkedHashMap<>();
      if (root.get("tables") instanceof Map<?, ?> t) {
        for (Map.Entry<?, ?> e : t.entrySet()) {
          if (!(e.getValue() instanceof String sha)) {
            throw new IllegalArgumentException("tables." + e.getKey() + " is not a string");
          }
          tables.put((String) e.getKey(), sha);
        }
      } else {
        throw new IllegalArgumentException("missing object field tables");
      }
      return new BuildLock(str(root, "product"), str(root, "build"), str(root, "fetched_at"),
          str(root, "base_url"), tables);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(file + ": " + e.getMessage(), e);
    }
  }

  private static String str(Map<?, ?> root, String field) {
    if (root.get(field) instanceof String s) {
      return s;
    }
    throw new IllegalArgumentException("missing string field " + field);
  }
}
