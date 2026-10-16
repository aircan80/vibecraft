package dev.wowforever.importer.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Counters and skipped data collected over one import run, renderable as a plain-text report. */
public final class ImportReport {
  private static final int RAW_SKIPS_SHOWN = 50;

  private final Map<String, Long> counters = new TreeMap<>();
  private final List<Skip> skips = new ArrayList<>();

  public void count(String counter, long delta) {
    counters.merge(counter, delta, Long::sum);
  }

  /** The counter's value, 0 if it was never counted. */
  public long get(String counter) {
    return counters.getOrDefault(counter, 0L);
  }

  public void skip(Skip s) {
    skips.add(s);
  }

  /** Every skip, in the order recorded. */
  public List<Skip> skips() {
    return Collections.unmodifiableList(skips);
  }

  /** How many skips lost a field the UI shows; see {@link UiFields}. */
  public long skipsOnUiFields() {
    return skips.stream().filter(s -> UiFields.isUiField(s.kind(), s.field())).count();
  }

  /**
   * The report text: counters sorted by name, skips grouped by layer and field with the largest groups
   * first, then the first {@value #RAW_SKIPS_SHOWN} skips as recorded.
   */
  public String render() {
    StringBuilder sb = new StringBuilder("== Counters ==\n");
    counters.forEach((name, value) -> sb.append(name).append(" = ").append(value).append('\n'));

    sb.append("== Skips by layer/field ==\n");
    Map<String, Long> groups = new LinkedHashMap<>();
    for (Skip s : skips) {
      groups.merge(s.layer() + "  " + s.kind() + "." + s.field(), 1L, Long::sum);
    }
    groups.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
        .forEach(e -> sb.append(e.getKey()).append("  ").append(e.getValue()).append('\n'));

    sb.append("== First ").append(RAW_SKIPS_SHOWN).append(" skips ==\n");
    skips.stream().limit(RAW_SKIPS_SHOWN).forEach(s -> sb.append(s).append('\n'));
    return sb.toString();
  }

  /** Writes {@link #render()} as UTF-8, creating parent directories as needed. */
  public void writeTo(Path file) {
    try {
      Path parent = file.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(file, render(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
