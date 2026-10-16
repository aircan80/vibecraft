package dev.wowforever.importer.source;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;

/**
 * Resolves a {@link SourceSpec} to a local directory: either a directory the user supplied, or the
 * download cache {@code cacheDir/<key>/<commit>}, fetching any file that is not cached yet.
 */
public final class SourceFetcher {
  private static final Duration TIMEOUT = Duration.ofMinutes(2);

  private final Path cacheDir;
  private final HttpClient http;
  private final URI baseUri;

  public SourceFetcher(Path cacheDir, HttpClient http) {
    this(cacheDir, http, SourceSpec.GITHUB_RAW);
  }

  /** For tests: fetch from {@code baseUri} instead of raw.githubusercontent.com. */
  public SourceFetcher(Path cacheDir, HttpClient http, URI baseUri) {
    this.cacheDir = cacheDir;
    this.http = http;
    this.baseUri = baseUri;
  }

  /** {@code ~/.cache/wowforever-importer}. */
  public static Path defaultCacheDir() {
    return Path.of(System.getProperty("user.home"), ".cache", "wowforever-importer");
  }

  public Path resolve(SourceSpec spec, Optional<Path> localDir) throws IOException {
    if (localDir.isPresent()) {
      return localDir.get();
    }
    Path root = cacheDir.resolve(spec.key()).resolve(spec.commit());
    for (String path : spec.paths()) {
      Path target = root.resolve(path);
      if (!Files.isRegularFile(target)) {
        download(spec.rawUrl(baseUri, path), target);
      }
    }
    return root;
  }

  private void download(URI url, Path target) throws IOException {
    Files.createDirectories(target.getParent());
    Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
    try {
      fetchInto(url, tmp);
      Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new IOException("failed to download " + url + ": " + e.getMessage(), e);
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  private void fetchInto(URI url, Path file) throws IOException {
    HttpRequest request = HttpRequest.newBuilder(url).timeout(TIMEOUT).GET().build();
    HttpResponse<InputStream> response;
    try {
      response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted", e);
    }
    try (InputStream body = response.body()) {
      if (response.statusCode() != 200) {
        String text = new String(body.readNBytes(200), StandardCharsets.UTF_8).strip();
        throw new IOException("HTTP " + response.statusCode() + " " + text);
      }
      Files.copy(body, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
