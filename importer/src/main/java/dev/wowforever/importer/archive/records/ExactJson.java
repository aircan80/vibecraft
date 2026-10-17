package dev.wowforever.importer.archive.records;

import dev.wowforever.importer.clientdata.MiniJson;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/** Strict JSON with decimal values preserved independently of floating-point precision. */
final class ExactJson {
  record NumberValue(String value) {}
  private final String text;
  private int pos;
  private ExactJson(String text) { this.text = text; }

  static Object parse(String text) {
    var parser = new ExactJson(text);
    Object result = parser.value();
    parser.space();
    if (parser.pos != text.length()) throw parser.error("trailing content");
    return result;
  }

  static String encode(Object value) {
    if (value instanceof NumberValue n) return n.value();
    if (value instanceof Map<?, ?> map) {
      var fields = new TreeMap<String, Object>();
      for (var field : map.entrySet()) fields.put((String) field.getKey(), field.getValue());
      var out = new StringJoiner(",", "{", "}");
      for (var field : fields.entrySet()) out.add(Json.encode(field.getKey()) + ":" + encode(field.getValue()));
      return out.toString();
    }
    if (value instanceof List<?> list) {
      var out = new StringJoiner(",", "[", "]");
      for (Object item : list) out.add(encode(item));
      return out.toString();
    }
    return Json.encode(value);
  }

  private Object value() {
    space();
    char next = peek();
    if (next == '"') return string();
    if (next == '{') {
      pos++;
      Map<String, Object> fields = new LinkedHashMap<>();
      space();
      if (take('}')) return fields;
      do {
        space();
        if (peek() != '"') throw error("expected object key");
        String name = string();
        if (fields.containsKey(name)) throw error("duplicate object key " + name);
        space(); require(':');
        fields.put(name, value());
        space();
        if (take('}')) return fields;
        require(',');
      } while (true);
    }
    if (next == '[') {
      pos++;
      List<Object> values = new ArrayList<>();
      space();
      if (take(']')) return values;
      do {
        values.add(value()); space();
        if (take(']')) return values;
        require(',');
      } while (true);
    }
    if (text.startsWith("true", pos)) { pos += 4; return true; }
    if (text.startsWith("false", pos)) { pos += 5; return false; }
    if (text.startsWith("null", pos)) { pos += 4; return null; }
    int start = pos;
    take('-');
    if (!take('0')) digits();
    if (take('.')) digits();
    if (take('e') || take('E')) {
      if (!take('+')) take('-');
      digits();
    }
    if (pos == start) throw error("expected value");
    try {
      return new NumberValue(new BigDecimal(text.substring(start, pos)).stripTrailingZeros().toString());
    } catch (NumberFormatException e) { throw error("invalid number"); }
  }

  private String string() {
    int start = pos++;
    boolean escaped = false;
    while (pos < text.length()) {
      char c = text.charAt(pos++);
      if (c == '"' && !escaped) return (String) MiniJson.parse(text.substring(start, pos));
      if (escaped && c == 'u') {
        if (pos + 4 > text.length()) throw error("short Unicode escape");
        for (int i = pos; i < pos + 4; i++) {
          char hex = text.charAt(i);
          if (!((hex >= '0' && hex <= '9') || (hex >= 'a' && hex <= 'f') || (hex >= 'A' && hex <= 'F'))) {
            throw error("invalid Unicode hex digit");
          }
        }
      }
      if (c == '\\' && !escaped) escaped = true;
      else escaped = false;
    }
    throw error("unterminated string");
  }

  private void digits() {
    int start = pos;
    while (peek() >= '0' && peek() <= '9') pos++;
    if (pos == start) throw error("expected digit");
  }
  private void space() {
    while (peek() == ' ' || peek() == '\n' || peek() == '\r' || peek() == '\t') pos++;
  }
  private char peek() { return pos < text.length() ? text.charAt(pos) : '\0'; }
  private boolean take(char c) {
    if (peek() != c) return false;
    pos++; return true;
  }
  private void require(char c) { if (!take(c)) throw error("expected " + c); }
  private IllegalArgumentException error(String detail) {
    return new IllegalArgumentException("JSON at offset " + pos + ": " + detail);
  }
}
