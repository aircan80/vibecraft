package dev.wowforever.lua;

import static dev.wowforever.lua.TokenType.*;

import dev.wowforever.lua.ast.Expr;
import dev.wowforever.lua.ast.Stmt;
import java.util.ArrayList;
import java.util.List;

/** Parses statements on top of the expression parser's token cursor. */
final class StatementParser {
  private final Parser p;

  StatementParser(Parser p) {
    this.p = p;
  }

  List<Stmt> parseChunk() {
    List<Stmt> out = new ArrayList<>();
    while (!p.atEnd()) {
      parseStatement(out);
    }
    return out;
  }

  private List<Stmt> parseBlockUntilEnd() {
    List<Stmt> out = new ArrayList<>();
    while (!p.check(END)) {
      if (p.atEnd()) {
        throw p.error(p.peek(), "end");
      }
      parseStatement(out);
    }
    p.advance();
    return out;
  }

  private void parseStatement(List<Stmt> out) {
    Token t = p.peek();
    switch (t.type()) {
      case LOCAL -> out.add(parseLocal());
      case FUNCTION -> out.add(parseFunction(t.line(), false));
      case RETURN -> out.add(parseReturn());
      case IF, DO -> {
        p.advance();
        p.skipBlock(1);
        out.add(new Stmt.Skipped(t.text(), t.line()));
      }
      case FOR, WHILE -> {
        p.advance();
        p.skipBlock(0);
        out.add(new Stmt.Skipped(t.text(), t.line()));
      }
      case REPEAT -> {
        p.advance();
        p.skipBlock(1);
        p.parseExpression(); // the until condition
        out.add(new Stmt.Skipped(t.text(), t.line()));
      }
      case SEMICOLON -> {
        p.advance();
        return;
      }
      default -> parseExpressionStatement(out);
    }
    p.match(SEMICOLON);
  }

  private Stmt parseLocal() {
    Token start = p.advance();
    if (p.check(FUNCTION)) {
      return parseFunction(start.line(), true);
    }
    List<String> names = new ArrayList<>();
    do {
      names.add(p.expect(NAME).text());
    } while (p.match(COMMA));
    List<Expr> values = new ArrayList<>();
    if (p.match(ASSIGN)) {
      do {
        values.add(p.parseExpression());
      } while (p.match(COMMA));
    }
    return new Stmt.Local(names, values, start.line());
  }

  private Stmt parseFunction(int line, boolean isLocal) {
    p.expect(FUNCTION);
    StringBuilder name = new StringBuilder(p.expect(NAME).text());
    if (!isLocal) {
      while (p.check(DOT)) {
        p.advance();
        name.append('.').append(p.expect(NAME).text());
      }
      if (p.match(COLON)) {
        name.append(':').append(p.expect(NAME).text());
      }
    }
    p.expect(LPAREN);
    List<String> params = new ArrayList<>();
    if (!p.check(RPAREN)) {
      do {
        params.add(p.check(ELLIPSIS) ? p.advance().text() : p.expect(NAME).text());
      } while (p.match(COMMA));
    }
    p.expect(RPAREN);
    return new Stmt.FunctionDef(name.toString(), params, parseBlockUntilEnd(), line);
  }

  private Stmt parseReturn() {
    Token t = p.advance();
    Token next = p.peek();
    boolean bare = next.type() == END || next.type() == EOF || next.type() == SEMICOLON;
    Expr value = bare ? new Expr.Literal(LuaNil.NIL, t.line()) : p.parseExpression();
    return new Stmt.Return(value, t.line());
  }

  private void parseExpressionStatement(List<Stmt> out) {
    int line = p.peek().line();
    Expr first = p.parseExpression();
    if (!p.check(ASSIGN) && !p.check(COMMA)) {
      out.add(new Stmt.ExprStmt(first, line));
      return;
    }
    List<Expr> targets = new ArrayList<>();
    targets.add(first);
    while (p.match(COMMA)) {
      targets.add(p.parseExpression());
    }
    p.expect(ASSIGN);
    List<Expr> values = new ArrayList<>();
    do {
      values.add(p.parseExpression());
    } while (p.match(COMMA));
    for (int i = 0; i < targets.size(); i++) {
      Expr target = targets.get(i);
      if (!(target instanceof Expr.Name || target instanceof Expr.Field || target instanceof Expr.Index)) {
        throw p.error(p.peek(), "assignable target");
      }
      Expr value = i < values.size() ? values.get(i) : new Expr.Literal(LuaNil.NIL, line);
      out.add(new Stmt.Assign(target, value, line));
    }
  }
}
