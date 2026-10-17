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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Resolves a {@link SourceSpec} to a local directory: either a directory the user supplied, or the
 * download cache {@code cacheDir/<key>/<commit>}, fetching any file that is not cached yet. A zip pin is never
 * downloaded (CurseForge refuses automation): the owner places it in {@code cacheDir/<zipDir>/<zip>}, its SHA-256 is
 * checked, and only the pinned entries are extracted into {@code cacheDir/<key>/<sha256>}.
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
    if (spec.zip() != null) {
      extract(spec, root);
      return root;
    }
    for (String path : spec.paths()) {
      Path target = root.resolve(path);
      if (!Files.isRegularFile(target)) {
        download(spec.rawUrl(baseUri, path), target);
      }
    }
    return root;
  }

  private void extract(SourceSpec spec, Path root) throws IOException {
    SourceSpec.Zip zip = spec.zip();
    Path file = cacheDir.resolve(zip.dir()).resolve(zip.file());
    if (!Files.isRegularFile(file)) {
      throw new IOException("download " + zip.name() + " " + zip.version() + " by hand from " + zip.page()
          + " (CurseForge refuses automated downloads) and place the zip, unopened, at " + file
          + "; its sha256 must be " + zip.sha256());
    }
    String actual = sha256(file);
    if (!actual.equals(zip.sha256())) {
      throw new IOException(file + " (sha256 " + actual + ") does not match the pinned sha256 "
          + zip.sha256().substring(0, 12) + "...; a different download, or sources.properties needs the new pin");
    }
    Path base = root.toAbsolutePath().normalize();
    try (ZipFile z = new ZipFile(file.toFile())) {
      for (String path : spec.paths()) {
        Path target = base.resolve(path).normalize();
        if (!target.startsWith(base) || target.equals(base)) {
          throw new IOException("zip entry " + path + " points outside " + base);
        }
        if (Files.isRegularFile(target)) {
          continue;
        }
        ZipEntry entry = z.getEntry(path);
        if (entry == null || entry.isDirectory()) {
          throw new IOException(file + " has no entry " + path);
        }
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
        try (InputStream in = z.getInputStream(entry)) {
          Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
          Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
          Files.deleteIfExists(tmp);
        }
      }
    }
  }

  private static String sha256(Path file) throws IOException {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      try (InputStream in = Files.newInputStream(file)) {
        byte[] buf = new byte[1 << 16];
        for (int n; (n = in.read(buf)) > 0; ) {
          md.update(buf, 0, n);
        }
      }
      return HexFormat.of().formatHex(md.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
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
