package dev.wowforever.lua;

import static dev.wowforever.lua.TokenType.*;

import dev.wowforever.lua.ast.Expr;
import dev.wowforever.lua.ast.Expr.*;
import dev.wowforever.lua.ast.Stmt;
import dev.wowforever.lua.ast.TableEntry;
import java.util.ArrayList;
import java.util.List;

/** Recursive-descent parser for Lua expressions. Statement parsing is layered on top later. */
public final class Parser {
  private final List<Token> tokens;
  private final String fileName;
  private int pos;

  public Parser(List<Token> tokens, String fileName) {
    this.tokens = tokens;
    this.fileName = fileName;
  }

  public boolean atEnd() {
    return peek().type() == EOF;
  }

  // ---- token cursor helpers (package-private for statement parsing) ----

  Token peek() {
    return tokens.get(pos);
  }

  Token advance() {
    Token t = tokens.get(pos);
    if (t.type() != EOF) {
      pos++;
    }
    return t;
  }

  boolean check(TokenType type) {
    return peek().type() == type;
  }

  boolean match(TokenType type) {
    if (check(type)) {
      advance();
      return true;
    }
    return false;
  }

  Token expect(TokenType type) {
    if (!check(type)) {
      throw error(peek(), type.toString());
    }
    return advance();
  }

  LuaSyntaxException error(Token found, String expected) {
    String f = found.type() == EOF ? "end of input" : found.type() + " \"" + found.text() + "\"";
    return new LuaSyntaxException(fileName, found.line(), "expected " + expected + " but found " + f);
  }

  // ---- statements ----

  /** Parses statements until end of input. */
  public List<Stmt> parseChunk() {
    return new StatementParser(this).parseChunk();
  }

  /**
   * Skips tokens until the block opened with {@code depth} unclosed openers is closed. {@code if},
   * {@code function}, {@code do} and {@code repeat} open a block; {@code end} and {@code until} close
   * one. {@code for} and {@code while} open through their {@code do}.
   */
  void skipBlock(int depth) {
    int open = depth;
    boolean started = depth > 0;
    while (!atEnd()) {
      TokenType type = advance().type();
      switch (type) {
        case IF, FUNCTION, DO, REPEAT -> {
          open++;
          started = true;
        }
        case END, UNTIL -> {
          open--;
          if (started && open == 0) {
            return;
          }
        }
        default -> { }
      }
    }
    throw error(peek(), "end");
  }

  // ---- expressions ----

  public Expr parseExpression() {
    return parseOr();
  }

  private Expr parseOr() {
    Expr left = parseAnd();
    while (check(OR)) {
      Token op = advance();
      left = new Binary("or", left, parseAnd(), op.line());
    }
    return left;
  }

  private Expr parseAnd() {
    Expr left = parseComparison();
    while (check(AND)) {
      Token op = advance();
      left = new Binary("and", left, parseComparison(), op.line());
    }
    return left;
  }

  private Expr parseComparison() {
    Expr left = parseConcat();
    while (true) {
      String op = comparisonOp(peek().type());
      if (op == null) {
        return left;
      }
      Token t = advance();
      left = new Binary(op, left, parseConcat(), t.line());
    }
  }

  private static String comparisonOp(TokenType t) {
    return switch (t) {
      case LT -> "<";
      case GT -> ">";
      case LE -> "<=";
      case GE -> ">=";
      case NE -> "~=";
      case EQ -> "==";
      default -> null;
    };
  }

  private Expr parseConcat() {
    Expr left = parseAdditive();
    if (check(CONCAT)) {
      Token op = advance();
      return new Binary("..", left, parseConcat(), op.line()); // right associative
    }
    return left;
  }

  private Expr parseAdditive() {
    Expr left = parseMultiplicative();
    while (check(PLUS) || check(MINUS)) {
      Token op = advance();
      left = new Binary(op.type() == PLUS ? "+" : "-", left, parseMultiplicative(), op.line());
    }
    return left;
  }

  private Expr parseMultiplicative() {
    Expr left = parseUnary();
    while (check(STAR) || check(SLASH)) {
      Token op = advance();
      left = new Binary(op.type() == STAR ? "*" : "/", left, parseUnary(), op.line());
    }
    return left;
  }

  private Expr parseUnary() {
    if (check(NOT) || check(MINUS) || check(HASH)) {
      Token op = advance();
      String text = op.type() == NOT ? "not" : op.type() == MINUS ? "-" : "#";
      return new Unary(text, parseUnary(), op.line());
    }
    return parseSuffixed();
  }

  private Expr parseSuffixed() {
    Expr e = parsePrimary();
    while (true) {
      Token t = peek();
      switch (t.type()) {
        case DOT -> {
          advance();
          e = new Field(e, expect(NAME).text(), t.line());
        }
        case LBRACKET -> {
          advance();
          Expr key = parseExpression();
          expect(RBRACKET);
          e = new Index(e, key, t.line());
        }
        case COLON -> {
          advance();
          String method = expect(NAME).text();
          e = new MethodCall(e, method, parseCallArgs(), t.line());
        }
        case LPAREN, STRING, LBRACE -> e = new Call(e, parseCallArgs(), t.line());
        default -> {
          return e;
        }
      }
    }
  }

  /** Parses {@code (a, b)}, or the sugar forms {@code "str"} and {@code {...}}. */
  private List<Expr> parseCallArgs() {
    Token t = peek();
    if (t.type() == STRING) {
      advance();
      return List.of(new Literal(new LuaString(t.text()), t.line()));
    }
    if (t.type() == LBRACE) {
      return List.of(parseTable());
    }
    expect(LPAREN);
    List<Expr> args = new ArrayList<>();
    if (!check(RPAREN)) {
      do {
        args.add(parseExpression());
      } while (match(COMMA));
    }
    expect(RPAREN);
    return args;
  }

  private Expr parsePrimary() {
    Token t = peek();
    switch (t.type()) {
      case NUMBER -> {
        advance();
        return new Literal(new LuaNumber(parseNumber(t)), t.line());
      }
      case STRING -> {
        advance();
        return new Literal(new LuaString(t.text()), t.line());
      }
      case NIL -> {
        advance();
        return new Literal(LuaNil.NIL, t.line());
      }
      case TRUE -> {
        advance();
        return new Literal(new LuaBoolean(true), t.line());
      }
      case FALSE -> {
        advance();
        return new Literal(new LuaBoolean(false), t.line());
      }
      case NAME -> {
        advance();
        return new Name(t.text(), t.line());
      }
      case FUNCTION -> {
        advance();
        skipBlock(1);
        return new Literal(new LuaUnresolved("function expression", t.line()), t.line());
      }
      case ELLIPSIS -> {
        advance();
        return new Vararg(t.line());
      }
      case LPAREN -> {
        advance();
        Expr inner = parseExpression();
        expect(RPAREN);
        return new Paren(inner, t.line());
      }
      case LBRACE -> {
        return parseTable();
      }
      default -> throw error(t, "expression");
    }
  }

  private double parseNumber(Token t) {
    String s = t.text();
    try {
      if (s.startsWith("0x") || s.startsWith("0X")) {
        return (double) Long.parseLong(s.substring(2), 16);
      }
      return Double.parseDouble(s);
    } catch (NumberFormatException e) {
      throw new LuaSyntaxException(fileName, t.line(), "malformed number " + s);
    }
  }

  private Expr parseTable() {
    Token open = expect(LBRACE);
    List<TableEntry> entries = new ArrayList<>();
    while (!check(RBRACE)) {
      entries.add(parseTableEntry());
      if (!match(COMMA) && !match(SEMICOLON)) {
        break;
      }
    }
    expect(RBRACE);
    return new TableCtor(entries, open.line());
  }

  private TableEntry parseTableEntry() {
    if (match(LBRACKET)) {
      Expr key = parseExpression();
      expect(RBRACKET);
      expect(ASSIGN);
      return new TableEntry.Keyed(key, parseExpression());
    }
    if (check(NAME) && tokens.get(pos + 1).type() == ASSIGN) {
      String name = advance().text();
      advance();
      return new TableEntry.Named(name, parseExpression());
    }
    return new TableEntry.Positional(parseExpression());
  }
}
