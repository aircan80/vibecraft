package dev.wowforever.importer.corrections;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.entity.RawEntityLoader;
import dev.wowforever.importer.schema.EntitySchema;
import dev.wowforever.importer.schema.MetaLoader;
import dev.wowforever.lua.ChunkEvaluator;
import dev.wowforever.lua.LuaChunk;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValue;
import dev.wowforever.lua.LuaValues;
import dev.wowforever.lua.ProblemCollector;
import dev.wowforever.lua.Scope;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Shared helpers for the correction tests: fixture schemas, the bound scope and Lua snippets. */
final class CorrectionTestSupport {
  private CorrectionTestSupport() {}

  private static final class Holder {
    static final Map<EntityKind, EntitySchema> SCHEMAS = loadSchemas();
    static final CorrectionScope.Bound BOUND =
        CorrectionScope.build(EnumLoader.load(Fixtures.questieDbRoot(), new ProblemCollector()), SCHEMAS);
  }

  static Map<EntityKind, EntitySchema> schemas() {
    return Holder.SCHEMAS;
  }

  private static Map<EntityKind, EntitySchema> loadSchemas() {
    Map<EntityKind, EntitySchema> m = new EnumMap<>(EntityKind.class);
    for (EntityKind k : EntityKind.values()) {
      m.put(k, MetaLoader.load(Fixtures.questieDbRoot().resolve("src/meta/" + k.metaEntity().toLowerCase() + "Meta.lua")));
    }
    return m;
  }

  static CorrectionScope.Bound bound() {
    return Holder.BOUND;
  }

  /**
   * Evaluates a Lua expression in the correction scope, with {@code itemKeys}, {@code npcKeys},
   * {@code objectKeys} and {@code questKeys} bound as the correction files bind them.
   */
  static LuaValue lua(String expr) {
    return lua(expr, new ProblemCollector());
  }

  static LuaValue lua(String expr, ProblemCollector problems) {
    Scope scope = bound().scope().child();
    LuaTable questieDb = (LuaTable) bound().scope().lookup("QuestieDB").orElseThrow();
    for (EntityKind k : EntityKind.values()) {
      scope.define(k.keysField(), questieDb.get(k.keysField()));
    }
    LuaChunk chunk = LuaChunk.parse("return " + expr, "t.lua");
    return new ChunkEvaluator("t.lua", bound().calls(), problems).run(chunk.statements(), scope).orElseThrow();
  }

  /** The integer elements of an idarray value. */
  static List<Long> arr(LuaValue v) {
    return ((LuaTable) v).array().stream().map(e -> LuaValues.asLong(e).orElseThrow()).toList();
  }

  static Map<EntityKind, EntityTable> loadFixtureTables() {
    Map<EntityKind, EntityTable> m = new EnumMap<>(EntityKind.class);
    for (EntityKind k : EntityKind.values()) {
      m.put(k, RawEntityLoader.load(
          Fixtures.questieDbRoot().resolve("data/Forever/forever" + k.metaEntity() + "DB.lua"),
          k, schemas().get(k), new ProblemCollector()));
    }
    return m;
  }

  /** A table of the given kind holding only the listed fixture rows. */
  static EntityTable tableWith(EntityKind kind, long... ids) {
    EntityTable source = loadFixtureTables().get(kind);
    EntityTable t = new EntityTable(kind, schemas().get(kind));
    for (long id : ids) {
      LuaValue[] row = t.create(id);
      System.arraycopy(source.rows().get(id), 0, row, 0, row.length);
    }
    return t;
  }

  static EntityTable itemsWith(long... ids) {
    return tableWith(EntityKind.ITEM, ids);
  }

  static EntityTable questsWith(long... ids) {
    return tableWith(EntityKind.QUEST, ids);
  }
}
