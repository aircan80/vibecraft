package dev.wowforever.lua.ast;

import java.util.List;

/** Statement AST. Control-flow blocks are not modelled; they are recorded as {@link Skipped}. */
public sealed interface Stmt {

  record Local(List<String> names, List<Expr> values, int line) implements Stmt {}

  /** Target is a {@link Expr.Name}, {@link Expr.Field} or {@link Expr.Index}. */
  record Assign(Expr target, Expr value, int line) implements Stmt {}

  record FunctionDef(String qualifiedName, List<String> params, List<Stmt> body, int line) implements Stmt {}

  record Return(Expr value, int line) implements Stmt {}

  record ExprStmt(Expr expr, int line) implements Stmt {}

  /** An if/for/while/do/repeat block that was skipped over. */
  record Skipped(String keyword, int line) implements Stmt {}
}
