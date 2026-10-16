package dev.wowforever.lua;

public record LuaBoolean(boolean value) implements LuaValue {
  @Override
  public String toString() {
    return Boolean.toString(value);
  }
}
