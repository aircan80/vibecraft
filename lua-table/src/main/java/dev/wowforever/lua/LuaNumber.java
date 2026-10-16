package dev.wowforever.lua;

public record LuaNumber(double value) implements LuaValue {

  public static LuaNumber of(long v) {
    return new LuaNumber((double) v);
  }

  public boolean isInteger() {
    return Double.isFinite(value) && value == Math.rint(value);
  }

  public long asLong() {
    if (!isInteger()) {
      throw new IllegalStateException("not an integer: " + value);
    }
    return (long) value;
  }

  @Override
  public String toString() {
    return isInteger() && Math.abs(value) < 1e15 ? Long.toString((long) value) : Double.toString(value);
  }
}
