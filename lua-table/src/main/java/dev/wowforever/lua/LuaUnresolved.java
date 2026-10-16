package dev.wowforever.lua;

/** A value the evaluator could not compute; carries the reason and source line. */
public record LuaUnresolved(String reason, int line) implements LuaValue {
  @Override
  public String toString() {
    return "<unresolved line " + line + ": " + reason + ">";
  }
}
