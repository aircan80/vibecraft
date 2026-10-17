package dev.wowforever.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reads the importer's known-items oracle ({@code importer/src/test/resources/fixtures/clientdata/known-items.csv},
 * described in {@code KnownItemsOracle.md} beside it) for the shipped-data check. The cell formats and the compared
 * stat ids mirror the importer's {@code KnownItemsTest}.
 */
final class KnownItemsCsv {
  static final String RELATIVE = "importer/src/test/resources/fixtures/clientdata/known-items.csv";
  /** Stat ids Wowhead shows as plain {@code +N} lines; ratings and spell power are outside the gate. */
  static final Set<Integer> TOOLTIP_STATS = Set.of(0, 1, 3, 4, 5, 6, 7, 39, 48, 50, 51, 52, 53, 54, 55, 56);
  static final List<String> TEXT_KINDS = List.of("binding", "slot", "req_level", "use", "equip", "proc");

  /**
   * One oracle row.
   *
   * @param stats  stat id to value ({@code id:value;...})
   * @param damage {@code min-max/delayMs/dps/school}
   * @param armor  {@code armor[/block]}, empty for none
   * @param lines  the expected tooltip texts by kind, in tooltip order
   */
  record Row(long id, String name, Set<String> kinds, Map<Integer, Integer> stats, String damage, String armor,
      Map<String, List<String>> lines) {}

  private KnownItemsCsv() {}

  /** The oracle file, found from the working directory (a module or the repository root) upwards. */
  static Path locate() {
    for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
      Path p = dir.resolve(RELATIVE);
      if (Files.isRegularFile(p)) {
        return p;
      }
    }
    throw new IllegalStateException(RELATIVE + " not found above " + Path.of("").toAbsolutePath());
  }

  static List<Row> read(Path file) throws IOException {
    List<List<String>> records = parse(Files.readString(file, StandardCharsets.UTF_8));
    List<String> header = records.get(0);
    List<Row> out = new ArrayList<>();
    for (List<String> rec : records.subList(1, records.size())) {
      if (rec.size() == 1 && rec.get(0).isBlank()) {
        continue;
      }
      Map<String, String> r = new LinkedHashMap<>();
      for (int i = 0; i < header.size(); i++) {
        r.put(header.get(i), i < rec.size() ? rec.get(i) : "");
      }
      Set<String> kinds = new LinkedHashSet<>();
      for (String k : r.get("compare_kinds").split(",")) {
        if (!k.isBlank()) {
          kinds.add(k.strip());
        }
      }
      out.add(new Row(Long.parseLong(r.get("item_id")), r.get("name"), kinds, stats(r.get("stats")), r.get("damage"),
          r.get("armor"), lines(r.get("lines"))));
    }
    return out;
  }

  /** The stats of one kind: ids 51 to 56 are {@code resist}, every other id {@code stat}. */
  static Map<Integer, Integer> only(Map<Integer, Integer> stats, String kind) {
    Map<Integer, Integer> out = new TreeMap<>();
    stats.forEach((id, v) -> {
      if ((id >= 51 && id <= 56 ? "resist" : "stat").equals(kind)) {
        out.put(id, v);
      }
    });
    return out;
  }

  /** The escaped tab of the slot line unescaped, whitespace runs as one space. */
  static String normal(String text) {
    return text.replace("\\t", "\t").replaceAll("[ \\n]+", " ").strip();
  }

  private static Map<Integer, Integer> stats(String cell) {
    Map<Integer, Integer> out = new TreeMap<>();
    for (String pair : cell.split(";")) {
      if (!pair.isBlank()) {
        String[] kv = pair.split(":");
        out.merge(Integer.parseInt(kv[0].strip()), Integer.parseInt(kv[1].strip()), Integer::sum);
      }
    }
    return out;
  }

  /** {@code kind=text|kind=text}. */
  private static Map<String, List<String>> lines(String cell) {
    Map<String, List<String>> out = new LinkedHashMap<>();
    for (String entry : cell.split("\\|")) {
      int eq = entry.indexOf('=');
      if (eq > 0) {
        out.computeIfAbsent(entry.substring(0, eq), k -> new ArrayList<>()).add(entry.substring(eq + 1));
      }
    }
    return out;
  }

  /** RFC 4180 records: quoted fields may hold commas, doubled quotes and line breaks. */
  private static List<List<String>> parse(String text) {
    List<List<String>> records = new ArrayList<>();
    List<String> fields = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (quoted) {
        if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
          field.append('"');
          i++;
        } else if (c == '"') {
          quoted = false;
        } else {
          field.append(c);
        }
      } else if (c == '"') {
        quoted = true;
      } else if (c == ',') {
        fields.add(field.toString());
        field.setLength(0);
      } else if (c == '\n' || c == '\r') {
        if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
          i++;
        }
        fields.add(field.toString());
        field.setLength(0);
        records.add(fields);
        fields = new ArrayList<>();
      } else {
        field.append(c);
      }
    }
    if (field.length() > 0 || !fields.isEmpty()) {
      fields.add(field.toString());
      records.add(fields);
    }
    return records;
  }
}
