package dev.wowforever.importer.source;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * One pinned upstream and the files the importer reads from it: a GitHub repository at a commit, or (AtlasLoot
 * Classic Forever, Task 19) a zip downloaded by hand, pinned by version and SHA-256.
 *
 * <p>{@code sources.properties} keys, per source {@code <key>}: {@code owner}, {@code repo}, {@code commit},
 * {@code paths} for a GitHub pin; for a zip pin {@code kind=zip}, {@code name}, {@code version}, {@code page} (where to
 * download it), {@code zipDir} (the folder under the importer cache it is placed in), {@code zip} (its file name),
 * {@code sha256} and {@code paths} (zip entries), optionally {@code author} and {@code license}. Optional for either:
 * {@code rateSource} (the drop rate source name of its rates, default the key) and {@code baseline.owner},
 * {@code baseline.repo}, {@code baseline.commit}, {@code baseline.paths} (an earlier GitHub pin it is compared with).
 *
 * @param commit     the GitHub commit; for a zip pin its SHA-256, which names its cache directory
 * @param zip        the zip pin, or null for a GitHub pin
 * @param rateSource the {@code drop_rate_source.source} of its rates
 * @param baseline   the pin it replaced, read for comparison only; null without one
 */
public record SourceSpec(String key, String owner, String repo, String commit, List<String> paths, Zip zip,
    String rateSource, SourceSpec baseline) {
  static final URI GITHUB_RAW = URI.create("https://raw.githubusercontent.com/");
  private static final String RESOURCE = "/dev/wowforever/importer/sources.properties";
  private static final List<String> KEYS = List.of("questiedb", "atlasloot");

  /**
   * A zip downloaded by hand into {@code <importer cache>/<dir>/<file>}.
   *
   * @param page   where the owner downloads it
   * @param sha256 its SHA-256, lower-case hex
   */
  public record Zip(String name, String author, String license, String version, String page, String dir, String file,
      String sha256) {}

  public SourceSpec {
    paths = List.copyOf(paths);
  }

  /** A GitHub pin whose rates (if any) carry the key as their source. */
  public SourceSpec(String key, String owner, String repo, String commit, List<String> paths) {
    this(key, owner, repo, commit, paths, null, key, null);
  }

  /** The sources declared in {@code sources.properties}, QuestieDB first. */
  public static List<SourceSpec> load() {
    Properties p = new Properties();
    try (InputStream in = SourceSpec.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException(RESOURCE + " is missing from the classpath");
      }
      p.load(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return load(p);
  }

  /** The sources declared in {@code p}, QuestieDB first. */
  public static List<SourceSpec> load(Properties p) {
    return KEYS.stream().map(k -> spec(p, k)).toList();
  }

  private static SourceSpec spec(Properties p, String k) {
    List<String> paths = list(required(p, k + ".paths"));
    String rateSource = p.getProperty(k + ".rateSource", k).strip();
    SourceSpec baseline = p.getProperty(k + ".baseline.commit") == null ? null
        : new SourceSpec(k, required(p, k + ".baseline.owner"), required(p, k + ".baseline.repo"),
            required(p, k + ".baseline.commit"), list(required(p, k + ".baseline.paths")));
    if ("zip".equals(p.getProperty(k + ".kind", "github").strip())) {
      String sha = required(p, k + ".sha256").toLowerCase(java.util.Locale.ROOT);
      if (!sha.matches("[0-9a-f]{64}")) {
        throw new IllegalStateException("sources.properties " + k + ".sha256 is not a SHA-256: " + sha);
      }
      Zip zip = new Zip(required(p, k + ".name"), optional(p, k + ".author"), optional(p, k + ".license"),
          required(p, k + ".version"), required(p, k + ".page"), required(p, k + ".zipDir"), required(p, k + ".zip"),
          sha);
      return new SourceSpec(k, zip.author(), zip.name(), sha, paths, zip, rateSource, baseline);
    }
    return new SourceSpec(k, required(p, k + ".owner"), required(p, k + ".repo"), required(p, k + ".commit"), paths,
        null, rateSource, baseline);
  }

  /** The pin as the database's meta records it: the commit, or {@code <version> sha256:<sha>} for a zip. */
  public String pin() {
    return zip == null ? commit : zip.version() + " sha256:" + zip.sha256();
  }

  /** The raw.githubusercontent.com URL of a file at the pinned commit. */
  public URI rawUrl(String path) {
    return rawUrl(GITHUB_RAW, path);
  }

  /** The URL of a file at the pinned commit below {@code base} ({@code base/owner/repo/commit/path}). */
  public URI rawUrl(URI base, String path) {
    return base.resolve(owner + "/" + repo + "/" + commit + "/" + path);
  }

  private static List<String> list(String value) {
    return Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
  }

  private static String required(Properties p, String name) {
    String v = p.getProperty(name);
    if (v == null || v.isBlank()) {
      throw new IllegalStateException("sources.properties lacks " + name);
    }
    return v.strip();
  }

  private static String optional(Properties p, String name) {
    String v = p.getProperty(name);
    return v == null ? "" : v.strip();
  }
}
