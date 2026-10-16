package dev.wowforever.lua;

public record LuaString(String value) implements LuaValue {
  @Override
  public String toString() {
    return "\"" + value + "\"";
  }
}
