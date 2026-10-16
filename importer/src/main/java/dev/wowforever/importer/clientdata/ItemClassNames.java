package dev.wowforever.importer.clientdata;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Item class and subclass names from the client's {@code ItemClass} ({@code ClassID}, {@code ClassName_lang}) and
 * {@code ItemSubClass} ({@code ClassID}, {@code SubClassID}, {@code VerboseName_lang}, falling back to
 * {@code DisplayName_lang} when empty; a row with neither is left out). The verbose name keeps weapon subclasses
 * apart ("One-Handed Axes" / "Two-Handed Axes", where the display name is "Axe" for both). Used instead of the
 * static cmangos names
 * when client data is present. Keys are the class and subclass ids items use, not the tables' row {@code ID}s.
 *
 * @param classes    class id to name, in id order
 * @param subclasses class id to (subclass id to name), in id order
 */
public record ItemClassNames(Map<Integer, String> classes, Map<Integer, Map<Integer, String>> subclasses) {

  public ItemClassNames {
    classes = Collections.unmodifiableMap(new TreeMap<>(classes));
    Map<Integer, Map<Integer, String>> subs = new TreeMap<>();
    subclasses.forEach((cls, names) -> subs.put(cls, Collections.unmodifiableMap(new TreeMap<>(names))));
    subclasses = Collections.unmodifiableMap(subs);
  }

  /** @throws IllegalStateException when a table is missing or lacks a column read here */
  public static ItemClassNames load(ClientDataSource src) {
    CsvTable classTable = require(src.table("ItemClass"), "ItemClass", List.of("ClassID", "ClassName_lang"));
    CsvTable subTable = require(src.table("ItemSubClass"), "ItemSubClass",
        List.of("ClassID", "SubClassID", "DisplayName_lang", "VerboseName_lang"));
    Map<Integer, String> classes = new TreeMap<>();
    for (CsvTable.Row r : classTable.rows()) {
      String name = r.get("ClassName_lang");
      if (r.getInt("ClassID").isPresent() && !name.isEmpty()) {
        classes.putIfAbsent(r.getInt("ClassID").getAsInt(), name);
      }
    }
    Map<Integer, Map<Integer, String>> subclasses = new TreeMap<>();
    for (CsvTable.Row r : subTable.rows()) {
      String name = r.get("VerboseName_lang").isEmpty() ? r.get("DisplayName_lang") : r.get("VerboseName_lang");
      if (r.getInt("ClassID").isPresent() && r.getInt("SubClassID").isPresent() && !name.isEmpty()) {
        subclasses.computeIfAbsent(r.getInt("ClassID").getAsInt(), k -> new TreeMap<>())
            .putIfAbsent(r.getInt("SubClassID").getAsInt(), name);
      }
    }
    return new ItemClassNames(classes, subclasses);
  }

  private static CsvTable require(CsvTable t, String name, List<String> columns) {
    for (String col : columns) {
      if (!t.hasColumn(col)) {
        throw new IllegalStateException("client table " + name + " (" + t.source() + ") has no column " + col);
      }
    }
    return t;
  }
}
