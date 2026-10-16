package dev.wowforever.importer.sqlite;

import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.lua.LuaBoolean;
import dev.wowforever.lua.LuaNil;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaUnresolved;
import dev.wowforever.lua.LuaValue;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Converts entity field values to column values. A field holding a {@link LuaUnresolved} anywhere is
 * treated as nil and counted as {@code unresolved.<kind>.<field>}; a value of the wrong shape is
 * treated as nil and counted as {@code badValue.<kind>.<field>}. An empty table means "no rows".
 */
final class LuaColumns {
  private final ImportReport report;

  LuaColumns(ImportReport report) {
    this.report = report;
  }

  /** A Long, Double, String or null. */
  Object scalar(String kind, String field, LuaValue v) {
    if (v instanceof LuaNil) {
      return null;
    }
    if (hasUnresolved(v)) {
      report.count("unresolved." + kind + "." + field, 1);
      return null;
    }
    return switch (v) {
      case LuaNumber n -> n.isInteger() ? (Object) n.asLong() : (Object) n.value();
      case LuaString s -> s.value();
      case LuaBoolean b -> b.value() ? 1L : 0L;
      default -> bad(kind, field);
    };
  }

  /** A string field, or null. */
  String string(String kind, String field, LuaValue v) {
    Object o = scalar(kind, field, v);
    if (o == null || o instanceof String) {
      return (String) o;
    }
    bad(kind, field);
    return null;
  }

  /** A table field, or empty for nil, a non-table or an unresolved value. */
  Optional<LuaTable> table(String kind, String field, LuaValue v) {
    if (v instanceof LuaNil) {
      return Optional.empty();
    }
    if (hasUnresolved(v)) {
      report.count("unresolved." + kind + "." + field, 1);
      return Optional.empty();
    }
    if (v instanceof LuaTable t) {
      return Optional.of(t);
    }
    bad(kind, field);
    return Optional.empty();
  }

  /** The distinct integer ids of an id list field, in list order. */
  List<Long> ids(String kind, String field, LuaValue v) {
    return table(kind, field, v).map(t -> idList(kind, field, t)).orElse(List.of());
  }

  /** The distinct integer ids of an id list already extracted from a field; holes are skipped. */
  List<Long> idList(String kind, String field, LuaValue list) {
    if (!(list instanceof LuaTable t)) {
      if (!(list instanceof LuaNil)) {
        bad(kind, field);
      }
      return List.of();
    }
    Set<Long> ids = new LinkedHashSet<>();
    for (LuaValue e : t.array()) {
      if (e instanceof LuaNumber n && n.isInteger()) {
        ids.add(n.asLong());
      } else if (!(e instanceof LuaNil)) {
        bad(kind, field);
      }
    }
    return new ArrayList<>(ids);
  }

  /** Records a value of the wrong shape and returns null. */
  Object bad(String kind, String field) {
    report.count("badValue." + kind + "." + field, 1);
    return null;
  }

  static boolean hasUnresolved(LuaValue v) {
    if (v instanceof LuaUnresolved) {
      return true;
    }
    if (v instanceof LuaTable t) {
      for (Map.Entry<LuaValue, LuaValue> e : t.entries()) {
        if (hasUnresolved(e.getKey()) || hasUnresolved(e.getValue())) {
          return true;
        }
      }
    }
    return false;
  }
}
