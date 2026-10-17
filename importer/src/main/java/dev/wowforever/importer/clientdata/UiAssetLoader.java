package dev.wowforever.importer.clientdata;

import dev.wowforever.importer.report.ImportReport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Reads the client UI textures (spec section 5: the navigation rail's icons) from the converted client data's
 * {@code ui_icons/} directory, written by {@code tools/clientdata/convert.py}: {@code <name>.png} at native size and
 * {@code <name>@24.png}, 24 px. The writer stores them in {@code ui_asset} under {@code <name>} and
 * {@code <name>@24}.
 */
public final class UiAssetLoader {
  /** Counts the base names found (a base name with either variant); 6 when every rail icon is present. */
  public static final String COUNTER = "clientdata.uiAssets";
  /** The name suffix of the 24 px variant. */
  public static final String SMALL_SUFFIX = "@24";
  /** The rail icons the browser asks for; absent ones are noted, the browser then draws its own glyph. */
  public static final List<String> RAIL_ICONS =
      List.of("rail.home", "rail.items", "rail.npcs", "rail.quests", "rail.zones", "rail.instances");

  private UiAssetLoader() {}

  /**
   * The PNG bytes (as stored) of every {@code ui_icons/*.png}, by file name without {@code .png}, sorted by name;
   * empty when the directory is absent. Counts {@value #COUNTER} and notes the {@link #RAIL_ICONS} without a file
   * ({@code clientdata.uiAssets.missing}) and the base names lacking one of their two variants.
   */
  public static Map<String, byte[]> load(ClientDataSource source, ImportReport report) {
    Map<String, byte[]> out = new LinkedHashMap<>();
    source.uiIcons().forEach((name, path) -> out.put(name, read(path)));

    TreeSet<String> bases = new TreeSet<>();
    for (String name : out.keySet()) {
      bases.add(name.endsWith(SMALL_SUFFIX) ? name.substring(0, name.length() - SMALL_SUFFIX.length()) : name);
    }
    report.count(COUNTER, bases.size());
    List<String> missingRail = RAIL_ICONS.stream().filter(n -> !bases.contains(n)).toList();
    if (!missingRail.isEmpty()) {
      report.note(COUNTER + ".missing: " + String.join(", ", missingRail));
    }
    List<String> halfPresent = bases.stream()
        .flatMap(b -> Stream.of(b, b + SMALL_SUFFIX))
        .filter(n -> !out.containsKey(n))
        .toList();
    if (!halfPresent.isEmpty()) {
      report.note(COUNTER + ": no file for " + String.join(", ", halfPresent));
    }
    return out;
  }

  private static byte[] read(Path p) {
    try {
      return Files.readAllBytes(p);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + p + ": " + e.getMessage(), e);
    }
  }
}
