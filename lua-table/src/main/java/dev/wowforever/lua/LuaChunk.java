package dev.wowforever.lua;

import dev.wowforever.lua.ast.Expr;
import dev.wowforever.lua.ast.Stmt;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** A parsed Lua source file with lookups for the top-level things callers extract. */
public final class LuaChunk {
  private final List<Stmt> statements;
  private final String fileName;

  private LuaChunk(List<Stmt> statements, String fileName) {
    this.statements = statements;
    this.fileName = fileName;
  }

  public static LuaChunk parse(String source, String fileName) {
    Parser parser = new Parser(new Lexer(source, fileName).tokenize(), fileName);
    return new LuaChunk(parser.parseChunk(), fileName);
  }

  /** Parses a UTF-8 file; problems are reported against the bare file name. */
  public static LuaChunk parse(Path file) {
    try {
      return parse(Files.readString(file, StandardCharsets.UTF_8), file.getFileName().toString());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public List<Stmt> statements() {
    return statements;
  }

  public String fileName() {
    return fileName;
  }

  public Optional<Stmt.FunctionDef> function(String qualifiedName) {
    return statements.stream()
        .filter(s -> s instanceof Stmt.FunctionDef f && f.qualifiedName().equals(qualifiedName))
        .map(s -> (Stmt.FunctionDef) s)
        .findFirst();
  }

  /** Top-level assignments, in source order. */
  public List<Stmt.Assign> assignments() {
    return statements.stream().filter(s -> s instanceof Stmt.Assign).map(s -> (Stmt.Assign) s).toList();
  }

  /** The value of the first top-level assignment whose target renders as given, e.g. data["X"]. */
  public Optional<Expr> assignedValue(String renderedTarget) {
    return assignments().stream()
        .filter(a -> renderedTarget.equals(render(a.target())))
        .map(Stmt.Assign::value)
        .findFirst();
  }

  public Optional<String> longStringAssignedTo(String renderedTarget) {
    return assignedValue(renderedTarget)
        .filter(e -> e instanceof Expr.Literal l && l.value() instanceof LuaString)
        .map(e -> ((LuaString) ((Expr.Literal) e).value()).value());
  }

  /** The value expression of a top-level {@code local name = ...}. */
  public Optional<Expr> localValue(String name) {
    for (Stmt s : statements) {
      if (s instanceof Stmt.Local l) {
        int i = l.names().indexOf(name);
        if (i >= 0) {
          return i < l.values().size() ? Optional.of(l.values().get(i)) : Optional.empty();
        }
      }
    }
    return Optional.empty();
  }

  private static String render(Expr e) {
    return switch (e) {
      case Expr.Name n -> n.name();
      case Expr.Field f -> render(f.target()) + "." + f.name();
      case Expr.Index i -> i.key() instanceof Expr.Literal l && l.value() instanceof LuaString s
          ? render(i.target()) + "[\"" + s.value() + "\"]"
          : render(i.target()) + "[<expr>]";
      default -> "<expr>";
    };
  }
}
