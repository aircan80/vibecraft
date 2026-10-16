package dev.wowforever.importer.source;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/** One pinned upstream repository and the files the importer reads from it. */
public record SourceSpec(String key, String owner, String repo, String commit, List<String> paths) {
  static final URI GITHUB_RAW = URI.create("https://raw.githubusercontent.com/");
  private static final String RESOURCE = "/dev/wowforever/importer/sources.properties";
  private static final List<String> KEYS = List.of("questiedb", "atlasloot");

  public SourceSpec {
    paths = List.copyOf(paths);
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
    return KEYS.stream()
        .map(k -> new SourceSpec(k, required(p, k + ".owner"), required(p, k + ".repo"), required(p, k + ".commit"),
            Arrays.stream(required(p, k + ".paths").split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList()))
        .toList();
  }

  /** The raw.githubusercontent.com URL of a file at the pinned commit. */
  public URI rawUrl(String path) {
    return rawUrl(GITHUB_RAW, path);
  }

  /** The URL of a file at the pinned commit below {@code base} ({@code base/owner/repo/commit/path}). */
  public URI rawUrl(URI base, String path) {
    return base.resolve(owner + "/" + repo + "/" + commit + "/" + path);
  }

  private static String required(Properties p, String name) {
    String v = p.getProperty(name);
    if (v == null || v.isBlank()) {
      throw new IllegalStateException("sources.properties lacks " + name);
    }
    return v.strip();
  }
}
