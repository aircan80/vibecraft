package dev.wowforever.lua;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

public final class LuaValues {
  private LuaValues() {}

  /** Structural equality: tables compare by entries, recursively. */
  public static boolean deepEquals(LuaValue a, LuaValue b) {
    if (a instanceof LuaTable ta && b instanceof LuaTable tb) {
      List<Map.Entry<LuaValue, LuaValue>> ea = ta.entries();
      if (ea.size() != tb.entries().size()) {
        return false;
      }
      for (Map.Entry<LuaValue, LuaValue> e : ea) {
        if (!deepEquals(e.getValue(), tb.get(e.getKey()))) {
          return false;
        }
      }
      return true;
    }
    return a.equals(b);
  }

  public static OptionalLong asLong(LuaValue v) {
    return v instanceof LuaNumber n && n.isInteger() ? OptionalLong.of(n.asLong()) : OptionalLong.empty();
  }

  public static Optional<String> asString(LuaValue v) {
    return v instanceof LuaString s ? Optional.of(s.value()) : Optional.empty();
  }

  public static Optional<LuaTable> asTable(LuaValue v) {
    return v instanceof LuaTable t ? Optional.of(t) : Optional.empty();
  }
}
