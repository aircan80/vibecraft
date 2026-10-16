package dev.wowforever.lua;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** A chain of name bindings; lookups walk to the parent, definitions shadow. */
public final class Scope {
  private final Scope parent;
  private final Map<String, LuaValue> vars = new HashMap<>();

  public Scope() {
    this(null);
  }

  public Scope(Scope parent) {
    this.parent = parent;
  }

  public void define(String name, LuaValue v) {
    vars.put(name, v);
  }

  public Optional<LuaValue> lookup(String name) {
    LuaValue v = vars.get(name);
    if (v != null) {
      return Optional.of(v);
    }
    return parent == null ? Optional.empty() : parent.lookup(name);
  }

  public Scope child() {
    return new Scope(this);
  }
}
