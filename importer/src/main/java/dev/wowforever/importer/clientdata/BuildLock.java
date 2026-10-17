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
 * @param tableSha SHA-256 (lower-case hex) of each fetched table's CSV, by table name ({@code ItemSparse.era}
 *     for the fallback table)
 * @param fallbackProduct the product whose ItemSparse was fetched as {@code ItemSparse.era} (ruling 17), or null
 * @param fallbackBuild that product's build, or null
 * @param hotfixSha256 the SHA-256 of the newest hotfix cache snapshot ({@code hotfix.sha256}, fetch.py
 *     --hotfix-cache), or null
 * @param hotfixHeaderBuild that snapshot's header build ({@code hotfix.header_build}), or null
 */
public record BuildLock(String product, String build, String fetchedAt, String baseUrl, Map<String, String> tableSha,
    String fallbackProduct, String fallbackBuild, String hotfixSha256, Long hotfixHeaderBuild) {

  public BuildLock {
    Objects.requireNonNull(product, "product");
    Objects.requireNonNull(build, "build");
    Objects.requireNonNull(fetchedAt, "fetchedAt");
    Objects.requireNonNull(baseUrl, "baseUrl");
    tableSha = Map.copyOf(tableSha);
  }

  /** A lock without a fallback product. */
  public BuildLock(String product, String build, String fetchedAt, String baseUrl, Map<String, String> tableSha) {
    this(product, build, fetchedAt, baseUrl, tableSha, null, null);
  }

  /** A lock without a hotfix snapshot. */
  public BuildLock(String product, String build, String fetchedAt, String baseUrl, Map<String, String> tableSha,
      String fallbackProduct, String fallbackBuild) {
    this(product, build, fetchedAt, baseUrl, tableSha, fallbackProduct, fallbackBuild, null, null);
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
      String hotfixSha = null;
      Long hotfixBuild = null;
      Object hotfix = root.get("hotfix");
      if (hotfix instanceof Map<?, ?> h) {
        hotfixSha = HotfixInfo.str(h, "sha256");
        hotfixBuild = HotfixInfo.num(h, "header_build");
      } else if (hotfix != null) {
        throw new IllegalArgumentException("field hotfix is not an object");
      }
      return new BuildLock(str(root, "product"), str(root, "build"), str(root, "fetched_at"),
          str(root, "base_url"), tables, optionalStr(root, "fallback_product"), optionalStr(root, "fallback_build"),
          hotfixSha, hotfixBuild);
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

  private static String optionalStr(Map<?, ?> root, String field) {
    Object v = root.get(field);
    if (v == null || v instanceof String) {
      return (String) v;
    }
    throw new IllegalArgumentException("field " + field + " is not a string");
  }
}
