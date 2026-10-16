package dev.wowforever.lua;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Single-pass, linear-time tokenizer for the Lua subset used by addon database files. */
public final class Lexer {

  private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
      Map.entry("nil", TokenType.NIL), Map.entry("true", TokenType.TRUE),
      Map.entry("false", TokenType.FALSE), Map.entry("function", TokenType.FUNCTION),
      Map.entry("local", TokenType.LOCAL), Map.entry("return", TokenType.RETURN),
      Map.entry("end", TokenType.END), Map.entry("if", TokenType.IF),
      Map.entry("then", TokenType.THEN), Map.entry("else", TokenType.ELSE),
      Map.entry("elseif", TokenType.ELSEIF), Map.entry("for", TokenType.FOR),
      Map.entry("while", TokenType.WHILE), Map.entry("do", TokenType.DO),
      Map.entry("repeat", TokenType.REPEAT), Map.entry("until", TokenType.UNTIL),
      Map.entry("in", TokenType.IN), Map.entry("and", TokenType.AND),
      Map.entry("or", TokenType.OR), Map.entry("not", TokenType.NOT));

  private final String src;
  private final String fileName;
  private final int len;
  private int pos;
  private int line = 1;
  private final List<Token> tokens = new ArrayList<>();

  public Lexer(String source, String fileName) {
    this.src = source;
    this.fileName = fileName;
    this.len = source.length();
  }

  public List<Token> tokenize() {
    while (true) {
      skipWhitespaceAndComments();
      if (pos >= len) {
        tokens.add(new Token(TokenType.EOF, "", line));
        return tokens;
      }
      scanToken();
    }
  }

  private LuaSyntaxException error(int atLine, String message) {
    return new LuaSyntaxException(fileName, atLine, message);
  }

  private char peek(int offset) {
    int i = pos + offset;
    return i < len ? src.charAt(i) : '\0';
  }

  private void add(TokenType type, String text, int atLine) {
    tokens.add(new Token(type, text, atLine));
  }

  private void skipWhitespaceAndComments() {
    while (pos < len) {
      char c = src.charAt(pos);
      if (c == '\n') {
        line++;
        pos++;
      } else if (c == ' ' || c == '\t' || c == '\r' || c == '\f' || c == '\u000B') {
        pos++;
      } else if (c == '-' && peek(1) == '-') {
        pos += 2;
        int level = pos < len && src.charAt(pos) == '[' ? longBracketLevel(pos) : -1;
        if (level >= 0) {
          readLongString(level, "comment");
        } else {
          while (pos < len && src.charAt(pos) != '\n') {
            pos++;
          }
        }
      } else {
        return;
      }
    }
  }

  /** If a long bracket opener {@code [=*[} starts at {@code at}, returns its level, else -1. */
  private int longBracketLevel(int at) {
    int i = at + 1;
    int level = 0;
    while (i < len && src.charAt(i) == '=') {
      level++;
      i++;
    }
    return i < len && src.charAt(i) == '[' ? level : -1;
  }

  /** Reads a long string/comment whose opener starts at pos; returns its content. */
  private String readLongString(int level, String what) {
    int startLine = line;
    pos += level + 2;
    if (pos < len && src.charAt(pos) == '\r') {
      pos++;
      if (pos < len && src.charAt(pos) == '\n') {
        pos++;
      }
      line++;
    } else if (pos < len && src.charAt(pos) == '\n') {
      pos++;
      line++;
      if (pos < len && src.charAt(pos) == '\r') {
        pos++;
      }
    }
    int start = pos;
    while (pos < len) {
      char c = src.charAt(pos);
      if (c == ']' && closesLongBracket(pos, level)) {
        String content = src.substring(start, pos);
        pos += level + 2;
        return content;
      }
      if (c == '\n') {
        line++;
      }
      pos++;
    }
    throw error(startLine, "unterminated long " + what);
  }

  private boolean closesLongBracket(int at, int level) {
    int i = at + 1;
    for (int k = 0; k < level; k++, i++) {
      if (i >= len || src.charAt(i) != '=') {
        return false;
      }
    }
    return i < len && src.charAt(i) == ']';
  }

  private void scanToken() {
    char c = src.charAt(pos);
    int startLine = line;
    if (isNameStart(c)) {
      int start = pos++;
      while (pos < len && isNamePart(src.charAt(pos))) {
        pos++;
      }
      String word = src.substring(start, pos);
      add(KEYWORDS.getOrDefault(word, TokenType.NAME), word, startLine);
    } else if (isDigit(c) || (c == '.' && isDigit(peek(1)))) {
      scanNumber(startLine);
    } else if (c == '"' || c == '\'') {
      add(TokenType.STRING, readQuoted(c), startLine);
    } else if (c == '[') {
      int level = longBracketLevel(pos);
      if (level >= 0) {
        add(TokenType.STRING, readLongString(level, "string"), startLine);
      } else {
        single(TokenType.LBRACKET, startLine);
      }
    } else {
      scanOperator(c, startLine);
    }
  }

  private void single(TokenType type, int atLine) {
    add(type, String.valueOf(src.charAt(pos)), atLine);
    pos++;
  }

  private void scanOperator(char c, int atLine) {
    switch (c) {
      case '{' -> single(TokenType.LBRACE, atLine);
      case '}' -> single(TokenType.RBRACE, atLine);
      case ']' -> single(TokenType.RBRACKET, atLine);
      case '(' -> single(TokenType.LPAREN, atLine);
      case ')' -> single(TokenType.RPAREN, atLine);
      case ',' -> single(TokenType.COMMA, atLine);
      case ';' -> single(TokenType.SEMICOLON, atLine);
      case ':' -> single(TokenType.COLON, atLine);
      case '+' -> single(TokenType.PLUS, atLine);
      case '-' -> single(TokenType.MINUS, atLine);
      case '*' -> single(TokenType.STAR, atLine);
      case '/' -> single(TokenType.SLASH, atLine);
      case '#' -> single(TokenType.HASH, atLine);
      case '=' -> twoChar('=', TokenType.EQ, TokenType.ASSIGN, atLine);
      case '<' -> twoChar('=', TokenType.LE, TokenType.LT, atLine);
      case '>' -> twoChar('=', TokenType.GE, TokenType.GT, atLine);
      case '~' -> {
        if (peek(1) != '=') {
          throw error(atLine, "unexpected character '~'");
        }
        add(TokenType.NE, "~=", atLine);
        pos += 2;
      }
      case '.' -> {
        if (peek(1) == '.' && peek(2) == '.') {
          add(TokenType.ELLIPSIS, "...", atLine);
          pos += 3;
        } else if (peek(1) == '.') {
          add(TokenType.CONCAT, "..", atLine);
          pos += 2;
        } else {
          single(TokenType.DOT, atLine);
        }
      }
      default -> throw error(atLine, "unexpected character '" + c + "'");
    }
  }

  private void twoChar(char second, TokenType two, TokenType one, int atLine) {
    if (peek(1) == second) {
      add(two, src.substring(pos, pos + 2), atLine);
      pos += 2;
    } else {
      single(one, atLine);
    }
  }

  private void scanNumber(int atLine) {
    int start = pos;
    if (src.charAt(pos) == '0' && (peek(1) == 'x' || peek(1) == 'X')) {
      pos += 2;
      while (pos < len && isHexDigit(src.charAt(pos))) {
        pos++;
      }
    } else {
      while (pos < len && isDigit(src.charAt(pos))) {
        pos++;
      }
      if (pos < len && src.charAt(pos) == '.' && peek(1) != '.') {
        pos++;
        while (pos < len && isDigit(src.charAt(pos))) {
          pos++;
        }
      }
      char e = pos < len ? src.charAt(pos) : '\0';
      if (e == 'e' || e == 'E') {
        char n = peek(1);
        if (isDigit(n) || ((n == '+' || n == '-') && isDigit(peek(2)))) {
          pos += 2;
          while (pos < len && isDigit(src.charAt(pos))) {
            pos++;
          }
        }
      }
    }
    add(TokenType.NUMBER, src.substring(start, pos), atLine);
  }

  private String readQuoted(char quote) {
    int startLine = line;
    pos++;
    StringBuilder sb = new StringBuilder();
    while (true) {
      if (pos >= len) {
        throw error(startLine, "unterminated string");
      }
      char c = src.charAt(pos);
      if (c == quote) {
        pos++;
        return sb.toString();
      }
      if (c == '\n') {
        throw error(startLine, "unterminated string");
      }
      if (c != '\\') {
        sb.append(c);
        pos++;
        continue;
      }
      pos++;
      if (pos >= len) {
        throw error(startLine, "unterminated string");
      }
      char e = src.charAt(pos);
      switch (e) {
        case 'n' -> { sb.append('\n'); pos++; }
        case 't' -> { sb.append('\t'); pos++; }
        case 'r' -> { sb.append('\r'); pos++; }
        case 'a' -> { sb.append('\u0007'); pos++; }
        case 'b' -> { sb.append('\b'); pos++; }
        case 'f' -> { sb.append('\f'); pos++; }
        case 'v' -> { sb.append('\u000B'); pos++; }
        case '\n' -> { sb.append('\n'); line++; pos++; }
        default -> {
          if (isDigit(e)) {
            int value = 0;
            int count = 0;
            while (count < 3 && pos < len && isDigit(src.charAt(pos))) {
              value = value * 10 + (src.charAt(pos) - '0');
              pos++;
              count++;
            }
            if (value > 255) {
              throw error(line, "decimal escape too large");
            }
            sb.append((char) value);
          } else {
            sb.append(e);
            pos++;
          }
        }
      }
    }
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isHexDigit(char c) {
    return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }

  private static boolean isNameStart(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
  }

  private static boolean isNamePart(char c) {
    return isNameStart(c) || isDigit(c);
  }
}
