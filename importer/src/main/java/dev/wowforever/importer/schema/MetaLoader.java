package dev.wowforever.importer.schema;

import dev.wowforever.lua.CallHandler;
import dev.wowforever.lua.Evaluator;
import dev.wowforever.lua.LuaChunk;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValue;
import dev.wowforever.lua.LuaValues;
import dev.wowforever.lua.ProblemCollector;
import dev.wowforever.lua.Scope;
import dev.wowforever.lua.ast.Expr;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads a QuestieDB {@code src/meta/*Meta.lua} file. Only the {@code local meta = {...}} literal is
 * evaluated, so the leading {@code local _, LibQuestieDB = ...} and the trailing registration block
 * play no part.
 */
public final class MetaLoader {
  private static final Map<String, FieldType> TYPES = Arrays.stream(FieldType.values())
      .collect(Collectors.toMap(t -> t.name().toLowerCase(Locale.ROOT), t -> t));
  private static final Map<String, Shape> SHAPES = Arrays.stream(Shape.values())
      .collect(Collectors.toMap(s -> s.name().toLowerCase(Locale.ROOT), s -> s));

  private MetaLoader() {}

  public static EntitySchema load(Path metaFile) {
    LuaChunk chunk = LuaChunk.parse(metaFile);
    String file = chunk.fileName();
    Expr metaExpr = chunk.localValue("meta").orElseThrow(() -> fail(file, "has no 'local meta = {...}'"));
    ProblemCollector problems = new ProblemCollector();
    LuaValue value = new Evaluator(file, new Scope(), CallHandler.NONE, problems).eval(metaExpr);
    if (problems.size() > 0) {
      throw fail(file, "meta table did not evaluate cleanly: " + problems.problems());
    }
    LuaTable meta = LuaValues.asTable(value).orElseThrow(() -> fail(file, "meta is not a table"));

    String entity = LuaValues.asString(meta.get("entity")).orElseThrow(() -> fail(file, "lacks entity"));
    int fieldCount = (int) LuaValues.asLong(meta.get("fieldCount")).orElseThrow(() -> fail(file, "lacks fieldCount"));
    LuaTable keys = table(meta, "keys", file);
    LuaTable types = table(meta, "types", file);
    LuaTable structures = table(meta, "structures", file);

    String[] names = new String[fieldCount];
    keys.entries().forEach(e -> {
      String name = LuaValues.asString(e.getKey()).orElseThrow(() -> fail(file, "non-string key " + e.getKey()));
      long index = LuaValues.asLong(e.getValue()).orElseThrow(() -> fail(file, "key " + name + " has no index"));
      if (index < 1 || index > fieldCount) {
        throw fail(file, "key " + name + " index " + index + " outside 1.." + fieldCount);
      }
      if (names[(int) index - 1] != null) {
        throw fail(file, "index " + index + " named twice");
      }
      names[(int) index - 1] = name;
    });

    List<FieldType> fieldTypes = new ArrayList<>(fieldCount);
    List<Shape> shapes = new ArrayList<>(fieldCount);
    for (int i = 1; i <= fieldCount; i++) {
      String name = names[i - 1];
      if (name == null) {
        throw fail(file, "no key for field index " + i);
      }
      String type = LuaValues.asString(types.get(i)).orElseThrow(() -> fail(file, "no type for field " + name));
      fieldTypes.add(lookup(TYPES, type, file, "type"));
      shapes.add(LuaValues.asString(structures.get(i)).map(s -> lookup(SHAPES, s, file, "structure")).orElse(null));
    }
    return new EntitySchema(entity, Arrays.asList(names), fieldTypes, shapes);
  }

  private static LuaTable table(LuaTable meta, String field, String file) {
    return LuaValues.asTable(meta.get(field)).orElseThrow(() -> fail(file, "lacks table " + field));
  }

  private static <T> T lookup(Map<String, T> map, String value, String file, String what) {
    T t = map.get(value);
    if (t == null) {
      throw fail(file, "unknown " + what + " '" + value + "'");
    }
    return t;
  }

  private static IllegalStateException fail(String file, String message) {
    return new IllegalStateException(file + ": " + message);
  }
}
