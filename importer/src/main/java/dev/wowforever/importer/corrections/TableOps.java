package dev.wowforever.importer.corrections;

import dev.wowforever.importer.schema.Shape;
import dev.wowforever.lua.LuaNil;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValue;
import dev.wowforever.lua.LuaValues;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * QuestieDB's table add/remove correction operations ({@code docs/api.md}, "Table add/remove
 * operations"). List additions append absent values in operand order after the surviving existing
 * values; removals delete every structurally equal value and keep the order of the rest; groups and
 * zones the operand does not supply are preserved. Results are new tables, the inputs are never
 * mutated. An existing value that is not a table (normally {@link LuaNil#NIL}) acts as an empty
 * table of the shape.
 */
public final class TableOps {
  private TableOps() {}

  /**
   * @return the combined value, or empty when the shape has no supported operations
   * @throws IllegalArgumentException when the operand does not have the shape's structure
   */
  public static Optional<LuaValue> add(Shape shape, LuaValue existing, LuaTable operand) {
    return apply(shape, existing, operand, true);
  }

  /**
   * @return the reduced value, or empty when the shape has no supported operations
   * @throws IllegalArgumentException when the operand does not have the shape's structure
   */
  public static Optional<LuaValue> remove(Shape shape, LuaValue existing, LuaTable operand) {
    return apply(shape, existing, operand, false);
  }

  private static Optional<LuaValue> apply(Shape shape, LuaValue existing, LuaTable operand, boolean add) {
    if (!supported(shape)) {
      return Optional.empty();
    }
    if (operand.isEmpty()) {
      return Optional.of(existing);
    }
    LuaTable base = existing instanceof LuaTable t ? t : new LuaTable();
    return Optional.of(switch (shape) {
      case IDARRAY, STRINGARRAY, PAIRS ->
          dense(combine(elements(base), list(operand, shape, shape.name().toLowerCase()), add, LuaValues::deepEquals));
      case QUESTGIVERS -> questgivers(base, operand, add);
      case SPAWNLIST -> spawnlist(base, operand, add);
      default -> throw new AssertionError(shape);
    });
  }

  private static boolean supported(Shape shape) {
    return switch (shape) {
      case IDARRAY, STRINGARRAY, PAIRS, QUESTGIVERS, SPAWNLIST -> true;
      case PAIR, OBJECTIVES, WAYPOINTLIST, TRIGGER, EXTRAOBJECTIVES -> false;
    };
  }

  /** Groups 1 (NPCs), 2 (objects) and 3 (items), each an idarray; an emptied group becomes a hole. */
  private static LuaTable questgivers(LuaTable base, LuaTable operand, boolean add) {
    for (Map.Entry<LuaValue, LuaValue> e : operand.entries()) {
      long g = LuaValues.asLong(e.getKey()).orElse(0);
      if (g < 1 || g > 3 || !(e.getValue() instanceof LuaTable)) {
        throw new IllegalArgumentException("questgivers group [" + e.getKey() + "] = " + e.getValue()
            + " is not one of the groups 1-3 holding an id list");
      }
    }
    LuaTable result = copy(base);
    for (long g = 1; g <= 3; g++) {
      if (operand.get(g) instanceof LuaTable ids && !ids.isEmpty()) {
        List<LuaValue> combined = combine(elements(base.get(g)), list(ids, Shape.IDARRAY, "questgivers group " + g),
            add, LuaValues::deepEquals);
        result.put(LuaNumber.of(g), combined.isEmpty() ? LuaNil.NIL : dense(combined));
      }
    }
    return result;
  }

  /** {@code [zoneId] = {tuples}}; tuples compare with phase 0 equal to no phase; an emptied zone is dropped. */
  private static LuaTable spawnlist(LuaTable base, LuaTable operand, boolean add) {
    for (Map.Entry<LuaValue, LuaValue> e : operand.entries()) {
      if (LuaValues.asLong(e.getKey()).isEmpty() || !(e.getValue() instanceof LuaTable)) {
        throw new IllegalArgumentException("spawnlist entry [" + e.getKey() + "] = " + e.getValue()
            + " is not a zone id holding a tuple list");
      }
    }
    LuaTable result = copy(base);
    for (Map.Entry<LuaValue, LuaValue> e : operand.entries()) {
      LuaTable tuples = (LuaTable) e.getValue();
      if (tuples.isEmpty()) {
        continue;
      }
      List<LuaValue> combined = combine(elements(base.get(e.getKey())),
          list(tuples, Shape.SPAWNLIST, "spawnlist zone " + e.getKey()), add, TableOps::sameSpawn);
      result.put(e.getKey(), combined.isEmpty() ? LuaNil.NIL : dense(combined));
    }
    return result;
  }

  private static List<LuaValue> combine(List<LuaValue> existing, List<LuaValue> operand, boolean add,
      BiPredicate<LuaValue, LuaValue> equal) {
    List<LuaValue> out = new ArrayList<>();
    if (add) {
      out.addAll(existing);
      for (LuaValue v : operand) {
        if (out.stream().noneMatch(o -> equal.test(o, v))) {
          out.add(v);
        }
      }
    } else {
      for (LuaValue o : existing) {
        if (operand.stream().noneMatch(v -> equal.test(o, v))) {
          out.add(o);
        }
      }
    }
    return out;
  }

  private static boolean sameSpawn(LuaValue a, LuaValue b) {
    return LuaValues.deepEquals(withoutZeroPhase(a), withoutZeroPhase(b));
  }

  private static LuaValue withoutZeroPhase(LuaValue tuple) {
    if (tuple instanceof LuaTable t && t.get(3) instanceof LuaNumber phase && phase.value() == 0) {
      LuaTable c = copy(t);
      c.put(LuaNumber.of(3), LuaNil.NIL);
      return c;
    }
    return tuple;
  }

  /**
   * The elements of a dense operand list, each checked against the shape's element type: integers for
   * id lists, strings for string lists, tables for pairs and spawn tuples (with numeric x and y).
   */
  private static List<LuaValue> list(LuaTable operand, Shape shape, String what) {
    List<LuaValue> values = operand.array();
    if (values.contains(LuaNil.NIL) || values.size() != operand.entries().size()) {
      throw new IllegalArgumentException(what + " operand " + operand + " is not a dense list");
    }
    for (int i = 0; i < values.size(); i++) {
      LuaValue v = values.get(i);
      boolean ok = switch (shape) {
        case IDARRAY -> LuaValues.asLong(v).isPresent();
        case STRINGARRAY -> v instanceof LuaString;
        case PAIRS -> v instanceof LuaTable;
        case SPAWNLIST -> v instanceof LuaTable t && t.get(1) instanceof LuaNumber && t.get(2) instanceof LuaNumber;
        default -> throw new AssertionError(shape);
      };
      if (!ok) {
        throw new IllegalArgumentException(what + " element " + (i + 1) + " is " + v + ", not a valid "
            + shape.name().toLowerCase() + " element");
      }
    }
    return values;
  }

  /** The non-nil array elements of an existing value; anything but a table has none. */
  private static List<LuaValue> elements(LuaValue existing) {
    if (!(existing instanceof LuaTable t)) {
      return List.of();
    }
    return t.array().stream().filter(v -> v != LuaNil.NIL).toList();
  }

  private static LuaTable dense(List<LuaValue> values) {
    LuaTable t = new LuaTable();
    for (int i = 0; i < values.size(); i++) {
      t.put(LuaNumber.of(i + 1), values.get(i));
    }
    return t;
  }

  private static LuaTable copy(LuaTable source) {
    LuaTable t = new LuaTable();
    for (Map.Entry<LuaValue, LuaValue> e : source.entries()) {
      t.put(e.getKey(), e.getValue());
    }
    return t;
  }
}
