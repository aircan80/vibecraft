package dev.wowforever.importer.archive.records;

/** Deterministic typed AST encoding; source line numbers do not affect semantic record diffs. */
final class Json {
  private Json() {}
  static String encode(Object value) {
    if (value == null) return "null";
    if (value instanceof String s) return quote(s);
    if (value instanceof Double d && !Double.isFinite(d)) {
      throw new IllegalArgumentException("Nonfinite Lua number; inspect original artifact");
    }
    if (value instanceof Number || value instanceof Boolean) return value.toString();
    if (value instanceof Iterable<?> items) {
      var out = new java.util.StringJoiner(",", "[", "]");
      for (Object item : items) out.add(encode(item));
      return out.toString();
    }
    var out = new java.util.StringJoiner(",", "{", "}");
    out.add("\"type\":" + quote(value.getClass().getSimpleName()));
    if (value.getClass().isRecord()) {
      for (var field : value.getClass().getRecordComponents()) {
        if (field.getName().equals("line")) continue;
        try { out.add(quote(field.getName()) + ":" + encode(field.getAccessor().invoke(value))); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
      }
    }
    return out.toString();
  }
  private static String quote(String value) {
    var out = new StringBuilder("\"");
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> { if (c < 32 || Character.isSurrogate(c)) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
      }
    }
    return out.append('"').toString();
  }
}
