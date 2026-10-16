package dev.wowforever.lua;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A mutable Lua table with insertion-ordered entries. Keys compare by value. */
public final class LuaTable implements LuaValue {
  private final Map<LuaValue, LuaValue> map = new LinkedHashMap<>();
  private final boolean identity;

  public LuaTable() {
    this(false);
  }

  private LuaTable(boolean identity) {
    this.identity = identity;
  }

  /** A table where every string key maps to itself and which holds no entries. */
  public static LuaTable identity() {
    return new LuaTable(true);
  }

  public void put(LuaValue key, LuaValue value) {
    if (identity) {
      throw new UnsupportedOperationException("identity table is read-only");
    }
    if (key instanceof LuaNil) {
      throw new IllegalArgumentException("table key is nil");
    }
    if (value instanceof LuaNil) {
      map.remove(key);
    } else {
      map.put(key, value);
    }
  }

  public LuaValue get(LuaValue key) {
    if (identity) {
      return key instanceof LuaString ? key : LuaNil.NIL;
    }
    LuaValue v = map.get(key);
    return v == null ? LuaNil.NIL : v;
  }

  public LuaValue get(long index) {
    return get(LuaNumber.of(index));
  }

  public LuaValue get(String key) {
    return get(new LuaString(key));
  }

  /** Largest positive integer key, or 0 if there is none. */
  public int arrayLength() {
    int max = 0;
    for (LuaValue k : map.keySet()) {
      if (k instanceof LuaNumber n && n.isInteger() && n.value() >= 1 && n.value() <= Integer.MAX_VALUE) {
        max = Math.max(max, (int) n.value());
      }
    }
    return max;
  }

  /** Values at indices 1..arrayLength, with NIL for holes. */
  public List<LuaValue> array() {
    int n = arrayLength();
    List<LuaValue> out = new ArrayList<>(n);
    for (int i = 1; i <= n; i++) {
      out.add(get(i));
    }
    return out;
  }

  public List<Map.Entry<LuaValue, LuaValue>> entries() {
    return List.copyOf(map.entrySet().stream().map(e -> Map.entry(e.getKey(), e.getValue())).toList());
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  @Override
  public String toString() {
    if (identity) {
      return "{identity}";
    }
    StringBuilder sb = new StringBuilder("{");
    int shown = 0;
    for (Map.Entry<LuaValue, LuaValue> e : map.entrySet()) {
      if (shown == 10) {
        sb.append(", …");
        break;
      }
      if (shown++ > 0) {
        sb.append(", ");
      }
      sb.append('[').append(e.getKey()).append("]=").append(e.getValue());
    }
    return sb.append('}').toString();
  }
}
