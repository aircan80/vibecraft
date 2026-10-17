package dev.wowforever.importer.archive.records;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/** Semantic field paths for matched records. Missing values differ from JSON null and empty strings. */
final class FieldDiff {
  private FieldDiff() {}

  static List<SourceRecords.FieldChange> compare(SourceRecords.Record before, SourceRecords.Record after) {
    Object a = ExactJson.parse(before.payload()), b = ExactJson.parse(after.payload());
    if (before.format().equals("csv-json") && !before.key().equals("@header")) {
      a = csv(a); b = csv(b);
    } else if (before.format().equals("lua-ast-json")) {
      a = lua(a); b = lua(b);
    }
    List<SourceRecords.FieldChange> out = new ArrayList<>();
    walk(before.path(), before.key(), "", a, true, b, true, out);
    return List.copyOf(out);
  }

  private static Object csv(Object value) {
    if (!(value instanceof List<?> pair) || pair.size() != 2
        || !(pair.get(0) instanceof List<?> headers) || !(pair.get(1) instanceof List<?> cells)
        || headers.size() != cells.size()) throw new IllegalArgumentException("Invalid archived CSV row payload");
    Map<String, Object> fields = new LinkedHashMap<>();
    Map<String, Integer> occurrences = new LinkedHashMap<>();
    for (int i = 0; i < headers.size(); i++) {
      String name = (String) headers.get(i);
      int count = occurrences.merge(name, 1, Integer::sum);
      fields.put(name + "#" + count, cells.get(i));
    }
    return fields;
  }

  private static Object lua(Object value) {
    if (value instanceof Map<?, ?> object) {
      Map<String, Object> fields = new LinkedHashMap<>();
      for (var field : object.entrySet()) {
        if ("TableCtor".equals(object.get("type")) && field.getKey().equals("entries")) continue;
        fields.put((String) field.getKey(), lua(field.getValue()));
      }
      if ("TableCtor".equals(object.get("type")) && object.get("entries") instanceof List<?> entries) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        int ordinal = 0;
        for (Object raw : entries) {
          if (!(raw instanceof Map<?, ?> entry)) throw new IllegalArgumentException("Invalid Lua table entry");
          String key = switch ((String) entry.get("type")) {
            case "Named" -> "name:" + entry.get("name");
            case "Keyed" -> "key:" + ExactJson.encode(entry.get("key"));
            case "Positional" -> "index:" + (++ordinal);
            default -> throw new IllegalArgumentException("Unknown Lua table entry");
          };
          int occurrence = occurrences.merge(key, 1, Integer::sum);
          mapped.put(key + "#" + occurrence, lua(entry.get("value")));
        }
        fields.put("entries", mapped);
      }
      return fields;
    }
    if (value instanceof List<?> list) {
      List<Object> out = new ArrayList<>();
      for (Object item : list) out.add(lua(item));
      return out;
    }
    return value;
  }

  private static void walk(String artifact, String record, String path, Object a, boolean hasA,
      Object b, boolean hasB, List<SourceRecords.FieldChange> out) {
    if (!hasA || !hasB) {
      out.add(new SourceRecords.FieldChange(artifact, record, path, hasA ? "removed" : "added",
          hasA ? ExactJson.encode(a) : null, hasB ? ExactJson.encode(b) : null));
    } else if (a instanceof Map<?, ?> left && b instanceof Map<?, ?> right) {
      var keys = new TreeSet<String>();
      for (Object key : left.keySet()) keys.add((String) key);
      for (Object key : right.keySet()) keys.add((String) key);
      for (String key : keys) walk(artifact, record, path + "/" + pointer(key), left.get(key), left.containsKey(key),
          right.get(key), right.containsKey(key), out);
    } else if (a instanceof List<?> left && b instanceof List<?> right) {
      for (int i = 0; i < Math.max(left.size(), right.size()); i++) {
        walk(artifact, record, path + "/" + i, i < left.size() ? left.get(i) : null, i < left.size(),
            i < right.size() ? right.get(i) : null, i < right.size(), out);
      }
    } else if (!Objects.equals(a, b)) {
      out.add(new SourceRecords.FieldChange(artifact, record, path, "changed", ExactJson.encode(a), ExactJson.encode(b)));
    }
  }

  private static String pointer(String key) { return key.replace("~", "~0").replace("/", "~1"); }
}
