package dev.wowforever.lua.ast;

import dev.wowforever.lua.LuaValue;
import java.util.List;

/** Expression AST. Every node records the source line it started on. */
public sealed interface Expr {

  record Literal(LuaValue value, int line) implements Expr {}

  record Name(String name, int line) implements Expr {}

  record Field(Expr target, String name, int line) implements Expr {}

  record Index(Expr target, Expr key, int line) implements Expr {}

  record Call(Expr fn, List<Expr> args, int line) implements Expr {}

  record MethodCall(Expr target, String method, List<Expr> args, int line) implements Expr {}

  record Binary(String op, Expr left, Expr right, int line) implements Expr {}

  record Unary(String op, Expr operand, int line) implements Expr {}

  record Paren(Expr inner, int line) implements Expr {}

  record TableCtor(List<TableEntry> entries, int line) implements Expr {}

  record Vararg(int line) implements Expr {}
}
