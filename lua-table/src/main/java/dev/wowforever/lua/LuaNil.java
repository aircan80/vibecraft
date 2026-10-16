package dev.wowforever.lua;

/** The Lua nil singleton. */
public final class LuaNil implements LuaValue {
  public static final LuaNil NIL = new LuaNil();

  private LuaNil() {}

  @Override
  public String toString() {
    return "nil";
  }
}
