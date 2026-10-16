package dev.wowforever.importer;

import dev.wowforever.importer.clientdata.ClientDataOptions;
import dev.wowforever.importer.source.SourceFetcher;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Command-line options of the importer.
 *
 * @param out               the SQLite file to write
 * @param cacheDir          where downloaded upstream files are cached
 * @param questieDbDir      a local QuestieDB checkout to use instead of downloading
 * @param atlasLootDir      a local AtlasLootClassic checkout to use instead of downloading
 * @param failOnUnsupported exit with code 2 when any skip lost a field the UI shows
 * @param buildIdOut        where the build id is written (no trailing newline)
 * @param clientData        {@code --client-data-dir} / {@code --skip-client-data}; empty when neither flag is
 *                          given. Parsed and validated only: the client-data stage that consumes it, and makes one
 *                          of the two flags required, lands in phase 2A Task 9.
 */
public record ImportOptions(
    Path out,
    Path cacheDir,
    Optional<Path> questieDbDir,
    Optional<Path> atlasLootDir,
    boolean failOnUnsupported,
    Path buildIdOut,
    Optional<ClientDataOptions> clientData) {

  public static final String USAGE = """
      Usage: java -jar importer.jar --out <file.sqlite> [options]
        --out <path>             SQLite file to write (required)
        --cache <dir>            download cache (default: ~/.cache/wowforever-importer)
        --questiedb-dir <dir>    use a local QuestieDB checkout instead of downloading
        --atlasloot-dir <dir>    use a local AtlasLootClassic checkout instead of downloading
        --fail-on-unsupported    exit with code 2 when data shown in the UI was skipped
        --build-id-out <path>    where to write the build id (default: build-id.txt beside --out)
        --client-data-dir <dir>  converted client data (tools/clientdata/convert.py output)
        --skip-client-data       import without client data""";

  public ImportOptions {
    Objects.requireNonNull(out, "out");
    Objects.requireNonNull(cacheDir, "cacheDir");
    Objects.requireNonNull(questieDbDir, "questieDbDir");
    Objects.requireNonNull(atlasLootDir, "atlasLootDir");
    Objects.requireNonNull(buildIdOut, "buildIdOut");
    Objects.requireNonNull(clientData, "clientData");
  }

  /** Options without client-data flags. */
  public ImportOptions(Path out, Path cacheDir, Optional<Path> questieDbDir, Optional<Path> atlasLootDir,
      boolean failOnUnsupported, Path buildIdOut) {
    this(out, cacheDir, questieDbDir, atlasLootDir, failOnUnsupported, buildIdOut, Optional.empty());
  }

  /** Options with the build id written to {@code build-id.txt} beside {@code out}. */
  public ImportOptions(
      Path out, Path cacheDir, Optional<Path> questieDbDir, Optional<Path> atlasLootDir, boolean failOnUnsupported) {
    this(out, cacheDir, questieDbDir, atlasLootDir, failOnUnsupported, defaultBuildIdOut(out));
  }

  public static Path defaultBuildIdOut(Path out) {
    return out.toAbsolutePath().getParent().resolve("build-id.txt");
  }

  /** @throws IllegalArgumentException for an unknown flag, a flag without its value or a missing {@code --out} */
  public static ImportOptions parse(String[] args) {
    Path out = null;
    Path cache = SourceFetcher.defaultCacheDir();
    Optional<Path> questieDb = Optional.empty();
    Optional<Path> atlasLoot = Optional.empty();
    boolean failOnUnsupported = false;
    Path buildIdOut = null;
    Optional<Path> clientDataDir = Optional.empty();
    boolean skipClientData = false;
    for (int i = 0; i < args.length; i++) {
      String flag = args[i];
      switch (flag) {
        case "--out" -> out = Path.of(value(args, i++));
        case "--cache" -> cache = Path.of(value(args, i++));
        case "--questiedb-dir" -> questieDb = Optional.of(Path.of(value(args, i++)));
        case "--atlasloot-dir" -> atlasLoot = Optional.of(Path.of(value(args, i++)));
        case "--fail-on-unsupported" -> failOnUnsupported = true;
        case "--build-id-out" -> buildIdOut = Path.of(value(args, i++));
        case "--client-data-dir" -> clientDataDir = Optional.of(Path.of(value(args, i++)));
        case "--skip-client-data" -> skipClientData = true;
        default -> throw usage("unknown option " + flag);
      }
    }
    if (out == null) {
      throw usage("--out is required");
    }
    Optional<ClientDataOptions> clientData = Optional.empty();
    if (clientDataDir.isPresent() || skipClientData) {
      try {
        clientData = Optional.of(new ClientDataOptions(clientDataDir, skipClientData));
      } catch (IllegalArgumentException e) {
        throw usage(e.getMessage());
      }
    }
    return new ImportOptions(out, cache, questieDb, atlasLoot, failOnUnsupported,
        buildIdOut != null ? buildIdOut : defaultBuildIdOut(out), clientData);
  }

  private static String value(String[] args, int flagIndex) {
    if (flagIndex + 1 >= args.length || args[flagIndex + 1].startsWith("--")) {
      throw usage(args[flagIndex] + " needs a value");
    }
    return args[flagIndex + 1];
  }

  private static IllegalArgumentException usage(String problem) {
    return new IllegalArgumentException(problem + "\n" + USAGE);
  }
}
