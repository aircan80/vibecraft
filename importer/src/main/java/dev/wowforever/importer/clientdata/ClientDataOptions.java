package dev.wowforever.importer.clientdata;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Where the importer takes client data from: a converted directory ({@code --client-data-dir}) or nowhere
 * ({@code --skip-client-data}). Exactly one of the two must be chosen.
 */
public record ClientDataOptions(Optional<Path> dir, boolean skip) {
  static final String CHOOSE_ONE = "either --client-data-dir <dir> or --skip-client-data is required";

  /** @throws IllegalArgumentException naming both flags when neither or both are chosen */
  public ClientDataOptions {
    Objects.requireNonNull(dir, "dir");
    if (dir.isEmpty() && !skip) {
      throw new IllegalArgumentException(CHOOSE_ONE);
    }
    if (dir.isPresent() && skip) {
      throw new IllegalArgumentException("--client-data-dir and --skip-client-data are mutually exclusive");
    }
  }

  public static ClientDataOptions of(Path dir) {
    return new ClientDataOptions(Optional.of(dir), false);
  }

  public static ClientDataOptions skipped() {
    return new ClientDataOptions(Optional.empty(), true);
  }
}
