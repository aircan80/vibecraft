package dev.wowforever.importer.clientdata;

import java.util.Map;
import java.util.Optional;

/** {@code ItemSparse.Bonding} to the tooltip's binding line. */
public final class BindingNames {
  private static final Map<Integer, String> NAMES = Map.of(
      1, "Binds when picked up",
      2, "Binds when equipped",
      3, "Binds when used",
      4, "Quest Item");

  private BindingNames() {}

  /** Empty for 0 (no binding) and values the classic client does not show. */
  public static Optional<String> name(Integer bonding) {
    return Optional.ofNullable(bonding == null ? null : NAMES.get(bonding));
  }
}
