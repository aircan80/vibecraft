package dev.wowforever.lua.ast;

/** One entry of a table constructor. */
public sealed interface TableEntry {

  record Positional(Expr value) implements TableEntry {}

  record Keyed(Expr key, Expr value) implements TableEntry {}

  record Named(String name, Expr value) implements TableEntry {}
}
