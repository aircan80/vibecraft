package dev.wowforever.importer.entity;

import dev.wowforever.importer.schema.EntitySchema;
import dev.wowforever.lua.CallHandler;
import dev.wowforever.lua.ChunkEvaluator;
import dev.wowforever.lua.LuaChunk;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValue;
import dev.wowforever.lua.LuaValues;
import dev.wowforever.lua.ProblemCollector;
import dev.wowforever.lua.Scope;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Loads a {@code data/Forever/forever*DB.lua} file: the {@code QuestieDB.<kind>Data = [[return {...}]]}
 * long string is parsed as its own chunk and each {@code [id] = {...}} entry becomes a positional row.
 */
public final class RawEntityLoader {
  private RawEntityLoader() {}

  public static EntityTable load(Path dbFile, EntityKind kind, EntitySchema schema, ProblemCollector problems) {
    LuaChunk outer = LuaChunk.parse(dbFile);
    String target = "QuestieDB." + kind.dataField();
    String source = outer.longStringAssignedTo(target)
        .orElseThrow(() -> new IllegalStateException(outer.fileName() + " has no " + target + " long string"));
    String innerName = outer.fileName() + "#" + kind.dataField();
    LuaChunk inner = LuaChunk.parse(source, innerName);
    LuaValue data = new ChunkEvaluator(innerName, CallHandler.NONE, problems).run(inner.statements(), new Scope())
        .orElseThrow(() -> new IllegalStateException(innerName + " does not return a value"));
    LuaTable rows = LuaValues.asTable(data)
        .orElseThrow(() -> new IllegalStateException(innerName + " returns " + data + ", not a table"));

    EntityTable table = new EntityTable(kind, schema);
    for (Map.Entry<LuaValue, LuaValue> e : rows.entries()) {
      OptionalLong id = LuaValues.asLong(e.getKey());
      if (id.isEmpty()) {
        problems.add(innerName, 0, kind + " row key " + e.getKey() + " is not an integer id");
        continue;
      }
      if (!(e.getValue() instanceof LuaTable fields)) {
        problems.add(innerName, 0, kind + " " + id.getAsLong() + " is " + e.getValue() + ", not a table");
        continue;
      }
      LuaValue[] row = table.create(id.getAsLong());
      for (Map.Entry<LuaValue, LuaValue> f : fields.entries()) {
        if (f.getKey() instanceof LuaNumber n && n.isInteger() && n.asLong() >= 1 && n.asLong() <= schema.fieldCount()) {
          row[(int) n.asLong() - 1] = f.getValue();
        } else {
          problems.add(innerName, 0, kind + " " + id.getAsLong() + " has field " + f.getKey()
              + " beyond fieldCount " + schema.fieldCount());
        }
      }
    }
    return table;
  }
}
