package dev.wowforever.importer.clientdata;

import dev.wowforever.importer.report.ImportReport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Reads the item icons the items reference from the converted client data's {@code icons/} directory. */
public final class IconLoader {
  private static final int NOTE_IDS = 50;

  private IconLoader() {}

  /**
   * The PNG bytes (as stored) of every distinct {@code iconId} the items carry that has a file, plus id 0, the
   * fallback icon, when {@code icons/0.png} exists. Counts {@code clientdata.icons} (icons returned) and
   * {@code clientdata.icons.missing} (referenced ids without a file, listed in a note, first 50).
   */
  public static Map<Long, byte[]> load(ClientDataSource source, Collection<ClientItem> items, ImportReport report) {
    TreeSet<Long> wanted = new TreeSet<>();
    wanted.add(0L);
    for (ClientItem item : items) {
      if (item.iconId() != null) {
        wanted.add(item.iconId());
      }
    }
    Map<Long, byte[]> out = new LinkedHashMap<>();
    TreeSet<Long> missing = new TreeSet<>();
    for (long id : wanted) {
      Optional<Path> file = source.icon(id);
      if (file.isPresent()) {
        out.put(id, read(file.get()));
      } else if (id != 0) {
        missing.add(id);
      } else {
        report.note("client data has no icons/0.png fallback icon");
      }
    }
    report.count("clientdata.icons", out.size());
    report.count("clientdata.icons.missing", missing.size());
    if (!missing.isEmpty()) {
      report.note("clientdata.icons.missing: "
          + missing.stream().limit(NOTE_IDS).map(String::valueOf).collect(Collectors.joining(", "))
          + (missing.size() > NOTE_IDS ? ", ... (" + missing.size() + " in all)" : ""));
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
