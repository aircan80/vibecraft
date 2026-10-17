package dev.wowforever.importer.clientdata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON reader for the two small files the Python tools write ({@code build.lock}, {@code maps.json}).
 * The importer has no JSON library and this does not justify one.
 *
 * <p>Values map to {@code Map<String, Object>} (insertion order), {@code List<Object>}, {@code String},
 * {@code Long} (integers), {@code Double} (numbers with a fraction or exponent), {@code Boolean} and
 * {@code null}. Full RFC 8259 syntax is accepted, including string escapes and {@code \}{@code uXXXX}.
 * Errors are {@link IllegalArgumentException}s carrying the character offset.
 */
final class MiniJson {
  private final String s;
  private int i;

  private MiniJson(String s) {
    this.s = s;
  }

  static Object parse(String json) {
    MiniJson p = new MiniJson(json);
    p.ws();
    Object v = p.value();
    p.ws();
    if (p.i != json.length()) {
      throw p.error("trailing content");
    }
    return v;
  }

  private Object value() {
    if (i >= s.length()) {
      throw error("unexpected end of input");
    }
    char c = s.charAt(i);
    return switch (c) {
      case '{' -> object();
      case '[' -> array();
      case '"' -> string();
      case 't' -> literal("true", Boolean.TRUE);
      case 'f' -> literal("false", Boolean.FALSE);
      case 'n' -> literal("null", null);
      default -> {
        if (c == '-' || (c >= '0' && c <= '9')) {
          yield number();
        }
        throw error("unexpected character '" + c + "'");
      }
    };
  }

  private Map<String, Object> object() {
    Map<String, Object> out = new LinkedHashMap<>();
    i++;
    ws();
    if (peek() == '}') {
      i++;
      return out;
    }
    while (true) {
      ws();
      if (peek() != '"') {
        throw error("expected a string key");
      }
      String key = string();
      ws();
      expect(':');
      ws();
      out.put(key, value());
      ws();
      if (peek() == ',') {
        i++;
      } else {
        expect('}');
        return out;
      }
    }
  }

  private List<Object> array() {
    List<Object> out = new ArrayList<>();
    i++;
    ws();
    if (peek() == ']') {
      i++;
      return out;
    }
    while (true) {
      ws();
      out.add(value());
      ws();
      if (peek() == ',') {
        i++;
      } else {
        expect(']');
        return out;
      }
    }
  }

  private String string() {
    i++; // opening quote
    StringBuilder b = new StringBuilder();
    while (true) {
      if (i >= s.length()) {
        throw error("unterminated string");
      }
      char c = s.charAt(i++);
      if (c == '"') {
        return b.toString();
      }
      if (c < 0x20) {
        throw error("control character in string");
      }
      if (c != '\\') {
        b.append(c);
        continue;
      }
      if (i >= s.length()) {
        throw error("unterminated escape");
      }
      char e = s.charAt(i++);
      switch (e) {
        case '"', '\\', '/' -> b.append(e);
        case 'b' -> b.append('\b');
        case 'f' -> b.append('\f');
        case 'n' -> b.append('\n');
        case 'r' -> b.append('\r');
        case 't' -> b.append('\t');
        case 'u' -> {
          if (i + 4 > s.length()) {
            throw error("short unicode escape");
          }
          try {
            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
          } catch (NumberFormatException ex) {
            throw error("bad unicode escape");
          }
          i += 4;
        }
        default -> throw error("bad escape \\" + e);
      }
    }
  }

  private Object number() {
    int start = i;
    if (peek() == '-') {
      i++;
    }
    boolean integral = true;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (c >= '0' && c <= '9') {
        i++;
      } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
        integral = false;
        i++;
      } else {
        break;
      }
    }
    String text = s.substring(start, i);
    try {
      return integral ? (Object) Long.parseLong(text) : (Object) Double.parseDouble(text);
    } catch (NumberFormatException e) {
      i = start;
      throw error("bad number " + text);
    }
  }

  private Object literal(String word, Object v) {
    if (!s.startsWith(word, i)) {
      throw error("unexpected token");
    }
    i += word.length();
    return v;
  }

  private void expect(char c) {
    if (peek() != c) {
      throw error("expected '" + c + "'");
    }
    i++;
  }

  private char peek() {
    return i < s.length() ? s.charAt(i) : '\0';
  }

  private void ws() {
    while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\n' || s.charAt(i) == '\r' || s.charAt(i) == '\t')) {
      i++;
    }
  }

  private IllegalArgumentException error(String problem) {
    return new IllegalArgumentException("JSON: " + problem + " at offset " + i);
  }
}
