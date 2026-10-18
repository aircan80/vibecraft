package dev.wowforever.importer.setup;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;

/** App-owned write boundary. Game roots are read-only, including through existing symlink aliases. */
public final class SetupStorage {
  private final Path root;
  private final List<Path> gameRoots;
  private final Optional<Path> activeDatabase;

  public SetupStorage(Path appStorage, List<Path> protectedGameRoots, Optional<Path> activeDatabase) throws IOException {
    this.root = canonicalProspective(appStorage);
    if (root.getParent() == null) throw new IOException("Choose a dedicated application storage directory");
    var games = new java.util.ArrayList<Path>();
    for (Path path : protectedGameRoots) games.add(canonicalProspective(path));
    this.gameRoots = List.copyOf(games);
    this.activeDatabase = activeDatabase.isPresent()
        ? Optional.of(canonicalProspective(activeDatabase.orElseThrow())) : Optional.empty();
    verifyDestination(root);
  }

  /** Returns the canonical write location; callers must use this returned path, not the unchecked input. */
  public Path verifyDestination(Path destination) throws IOException {
    Path actual = canonicalProspective(destination);
    if (!actual.startsWith(root)) throw new IOException("Destination is outside application storage");
    for (Path game : gameRoots) {
      if (actual.startsWith(game)) throw new IOException("WoW installation is read-only; choose application storage outside it");
    }
    if (activeDatabase.isPresent()) {
      Path active = activeDatabase.orElseThrow();
      if (actual.equals(active) || (Files.exists(actual) && Files.exists(active) && Files.isSameFile(actual, active)))
        throw new IOException("The active database cannot be used as job output");
      for (String suffix : List.of(".lock", "-wal", "-shm", "-journal")) {
        if (actual.equals(active.resolveSibling(active.getFileName() + suffix)))
          throw new IOException("Active database sidecars cannot be used as job output");
      }
    }
    return actual;
  }

  /** Creates one fresh staging directory; existing jobs are never overwritten by preparation. */
  public JobPaths createJob(String jobId) throws IOException {
    if (!jobId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,79}")) throw new IOException("Invalid setup job ID");
    Path directory = verifyDestination(root.resolve("jobs").resolve(jobId));
    Path archive = verifyDestination(directory.resolve("source-archive.sqlite"));
    Path candidate = verifyDestination(directory.resolve("candidate.sqlite"));
    Path cache = verifyDestination(directory.resolve("cache"));
    Path buildId = verifyDestination(directory.resolve("build-id.txt"));
    Path report = verifyDestination(directory.resolve("import-report.txt"));
    Files.createDirectories(verifyDestination(directory.getParent()));
    Files.createDirectory(verifyDestination(directory));
    Files.createDirectory(verifyDestination(cache));
    return new JobPaths(directory, archive, candidate, cache, buildId, report);
  }

  public void verifyJob(JobPaths job) throws IOException {
    Path directory = verifyDestination(job.directory());
    if (!directory.getParent().equals(root.resolve("jobs"))) throw new IOException("Job is not in application staging storage");
    Path[] outputs = {job.archiveDatabase(), job.candidateDatabase(), job.cache(), job.buildId(), job.report()};
    String[] names = {"source-archive.sqlite", "candidate.sqlite", "cache", "build-id.txt", "import-report.txt"};
    for (int i=0;i<outputs.length;i++) {
      if (!verifyDestination(outputs[i]).equals(directory.resolve(names[i]))) throw new IOException("Invalid staged job output");
    }
    for (Path database : List.of(job.archiveDatabase(), job.candidateDatabase())) {
      for (String suffix : List.of(".lock", "-wal", "-shm", "-journal")) {
        Path sidecar = database.resolveSibling(database.getFileName() + suffix);
        if (!verifyDestination(sidecar).equals(sidecar)) throw new IOException("Staged database sidecar alias refused");
      }
    }
  }

  public record JobPaths(Path directory, Path archiveDatabase, Path candidateDatabase, Path cache,
      Path buildId, Path report) {}

  /** Resolves existing ancestors to inspect aliases without creating directories or following a missing leaf. */
  static Path canonicalProspective(Path path) throws IOException {
    Path absolute = path.toAbsolutePath().normalize();
    Path existing = absolute;
    while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
    if (existing == null) throw new IOException("No existing ancestor for destination");
    return existing.toRealPath().resolve(existing.relativize(absolute)).normalize();
  }
}
