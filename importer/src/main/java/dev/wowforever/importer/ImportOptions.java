package dev.wowforever.importer;

import dev.wowforever.importer.clientdata.ClientDataOptions;
import dev.wowforever.importer.clientdata.ClientDataStage;
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
 * @param clientData        {@code --client-data-dir <dir>} or {@code --skip-client-data}; exactly one is required
 * @param coverageMinPct    {@code --coverage-min}: with {@code --fail-on-unsupported}, exit with code 2 when fewer
 *                          than this percentage (0 to 100) of the named items have client data
 */
public record ImportOptions(
    Path out,
    Path cacheDir,
    Optional<Path> questieDbDir,
    Optional<Path> atlasLootDir,
    boolean failOnUnsupported,
    Path buildIdOut,
    ClientDataOptions clientData,
    int coverageMinPct) {

  public static final String USAGE = """
      Usage: java -jar importer.jar --out <file.sqlite> (--client-data-dir <dir> | --skip-client-data) [options]
        --out <path>             SQLite file to write (required)
        --cache <dir>            download cache (default: ~/.cache/wowforever-importer)
        --questiedb-dir <dir>    use a local QuestieDB checkout instead of downloading
        --atlasloot-dir <dir>    use a local AtlasLootClassic checkout instead of downloading
        --fail-on-unsupported    exit with code 2 when data shown in the UI was skipped
        --build-id-out <path>    where to write the build id (default: build-id.txt beside --out)
        --client-data-dir <dir>  converted client data (tools/clientdata/convert.py output); this or
                                 --skip-client-data is required
        --skip-client-data       import without client data (client tables are created empty)
        --coverage-min <pct>     with --fail-on-unsupported, exit with code 2 when fewer than <pct> % (0-100) of
                                 the named items have client data (default: 85)""";

  public ImportOptions {
    Objects.requireNonNull(out, "out");
    Objects.requireNonNull(cacheDir, "cacheDir");
    Objects.requireNonNull(questieDbDir, "questieDbDir");
    Objects.requireNonNull(atlasLootDir, "atlasLootDir");
    Objects.requireNonNull(buildIdOut, "buildIdOut");
    Objects.requireNonNull(clientData, "clientData");
    if (coverageMinPct < 0 || coverageMinPct > 100) {
      throw new IllegalArgumentException("--coverage-min must be between 0 and 100, not " + coverageMinPct);
    }
  }

  /** Options with the default {@code --coverage-min} ({@value ClientDataStage#DEFAULT_MIN_COVERAGE_PCT}). */
  public ImportOptions(Path out, Path cacheDir, Optional<Path> questieDbDir, Optional<Path> atlasLootDir,
      boolean failOnUnsupported, Path buildIdOut, ClientDataOptions clientData) {
    this(out, cacheDir, questieDbDir, atlasLootDir, failOnUnsupported, buildIdOut, clientData,
        ClientDataStage.DEFAULT_MIN_COVERAGE_PCT);
  }

  /**
   * Options with the build id written to {@code build-id.txt} beside {@code out} and the default
   * {@code --coverage-min}.
   */
  public ImportOptions(Path out, Path cacheDir, Optional<Path> questieDbDir, Optional<Path> atlasLootDir,
      boolean failOnUnsupported, ClientDataOptions clientData) {
    this(out, cacheDir, questieDbDir, atlasLootDir, failOnUnsupported, defaultBuildIdOut(out), clientData);
  }

  public static Path defaultBuildIdOut(Path out) {
    return out.toAbsolutePath().getParent().resolve("build-id.txt");
  }

  /**
   * @throws IllegalArgumentException for an unknown flag, a flag without its value, a missing {@code --out}, or
   *     neither or both of {@code --client-data-dir} and {@code --skip-client-data}, or a {@code --coverage-min} that
   *     is not an integer from 0 to 100
   */
  public static ImportOptions parse(String[] args) {
    Path out = null;
    Path cache = SourceFetcher.defaultCacheDir();
    Optional<Path> questieDb = Optional.empty();
    Optional<Path> atlasLoot = Optional.empty();
    boolean failOnUnsupported = false;
    Path buildIdOut = null;
    Optional<Path> clientDataDir = Optional.empty();
    boolean skipClientData = false;
    int coverageMin = ClientDataStage.DEFAULT_MIN_COVERAGE_PCT;
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
        case "--coverage-min" -> coverageMin = percent(value(args, i++));
        default -> throw usage("unknown option " + flag);
      }
    }
    if (out == null) {
      throw usage("--out is required");
    }
    ClientDataOptions clientData;
    try {
      clientData = new ClientDataOptions(clientDataDir, skipClientData);
    } catch (IllegalArgumentException e) {
      throw usage(e.getMessage());
    }
    return new ImportOptions(out, cache, questieDb, atlasLoot, failOnUnsupported,
        buildIdOut != null ? buildIdOut : defaultBuildIdOut(out), clientData, coverageMin);
  }

  private static int percent(String text) {
    int pct;
    try {
      pct = Integer.parseInt(text);
    } catch (NumberFormatException e) {
      throw usage("--coverage-min must be an integer from 0 to 100, not " + text);
    }
    if (pct < 0 || pct > 100) {
      throw usage("--coverage-min must be an integer from 0 to 100, not " + text);
    }
    return pct;
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
