package dev.wowforever.importer.corrections;

import dev.wowforever.importer.corrections.CorrectionKey.Op;
import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.report.Skip;
import dev.wowforever.importer.schema.EntitySchema;
import dev.wowforever.importer.schema.FieldType;
import dev.wowforever.importer.schema.Shape;
import dev.wowforever.lua.ChunkEvaluator;
import dev.wowforever.lua.LuaChunk;
import dev.wowforever.lua.LuaNil;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaSyntaxException;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaUnresolved;
import dev.wowforever.lua.LuaValue;
import dev.wowforever.lua.ProblemCollector;
import dev.wowforever.lua.Scope;
import dev.wowforever.lua.ast.Stmt;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Applies QuestieDB correction tables ({@code [id] = { [key] = value }}) to entity tables and records in
 * the {@link ImportReport} everything applied and everything that could not be.
 *
 * <p>Within one row the fields are applied in index order; for one field a replacement comes first,
 * then a removal, then an addition. Nothing here throws on bad correction data.
 */
public final class CorrectionApplier {
  private final ImportReport report;

  public CorrectionApplier(ImportReport report) {
    this.report = report;
  }

  /**
   * Parses the layer's file, runs its top-level statements and then its correction function in a
   * child of the bound scope, and applies the returned table. A missing file or function, a parse
   * error or a non-table result is reported as a {@link Skip#LAYER} skip.
   */
  public void applyLayer(CorrectionLayer layer, Path questieDbRoot, Map<EntityKind, EntityTable> tables,
      CorrectionScope.Bound scope, ProblemCollector problems) {
    Path file = questieDbRoot.resolve(layer.relativePath());
    if (!Files.isRegularFile(file)) {
      layerSkip(layer, "layer.missing", "file or function missing: " + layer.relativePath() + " does not exist");
      return;
    }
    LuaChunk chunk;
    try {
      chunk = LuaChunk.parse(file);
    } catch (LuaSyntaxException e) {
      layerSkip(layer, "layer.failed", "parse error: " + e.getMessage());
      return;
    }
    Optional<Stmt.FunctionDef> function = chunk.function(layer.function());
    if (function.isEmpty()) {
      layerSkip(layer, "layer.missing", "file or function missing: " + layer.relativePath() + " has no " + layer.function());
      return;
    }
    EntityTable table = tables.get(layer.kind());
    if (table == null) {
      layerSkip(layer, "layer.failed", "no " + layer.kind() + " table to apply to");
      return;
    }
    int problemsBefore = problems.size();
    Scope fileScope = scope.scope().child();
    ChunkEvaluator evaluator = new ChunkEvaluator(chunk.fileName(), scope.calls(), problems);
    evaluator.run(chunk.statements(), fileScope);
    LuaValue result = evaluator.runFunction(function.get(), fileScope);
    // Evaluation problems (e.g. an entry dropped for a nil key) never reach a row, so surface their count.
    if (problems.size() > problemsBefore) {
      report.count("problems." + layer.name(), problems.size() - problemsBefore);
    }
    if (!(result instanceof LuaTable corrections)) {
      layerSkip(layer, "layer.failed", "function did not return a table: " + result);
      return;
    }
    apply(table, corrections, layer.name());
  }

  /** Applies one correction table; rows for missing entities create them. */
  public void apply(EntityTable table, LuaTable corrections, String layerName) {
    EntityKind kind = table.kind();
    for (Map.Entry<LuaValue, LuaValue> entry : corrections.entries()) {
      if (!(entry.getKey() instanceof LuaNumber n && n.isInteger() && n.value() >= 1)) {
        report.skip(new Skip(layerName, kind, 0, Skip.ROW, "non-integer entity id"));
        continue;
      }
      long id = n.asLong();
      if (!(entry.getValue() instanceof LuaTable fields)) {
        String reason = entry.getValue() instanceof LuaUnresolved u ? u.reason() : "row is not a table";
        report.skip(new Skip(layerName, kind, id, Skip.ROW, reason));
        continue;
      }
      if (!table.contains(id)) {
        table.create(id);
        report.count("created." + kind, 1);
      }
      report.count("rows." + layerName, 1);
      applyRow(table, id, fields, layerName);
    }
  }

  private void applyRow(EntityTable table, long id, LuaTable fields, String layer) {
    EntitySchema schema = table.schema();
    SortedMap<Integer, Map<Op, LuaValue>> byField = new TreeMap<>();
    for (Map.Entry<LuaValue, LuaValue> e : fields.entries()) {
      Optional<CorrectionKey> key = CorrectionKey.parse(e.getKey())
          .filter(k -> k.fieldIndex() >= 1 && k.fieldIndex() <= schema.fieldCount());
      if (key.isEmpty()) {
        report.skip(new Skip(layer, table.kind(), id, "<key " + e.getKey() + ">", "unknown key"));
        continue;
      }
      byField.computeIfAbsent(key.get().fieldIndex(), i -> new EnumMap<>(Op.class)).put(key.get().op(), e.getValue());
    }
    byField.forEach((index, ops) -> {
      Field f = new Field(table, id, index, layer);
      if (ops.containsKey(Op.REPLACE)) {
        replace(f, ops.get(Op.REPLACE));
      }
      if (ops.containsKey(Op.REMOVE)) {
        operation(f, Op.REMOVE, ops.get(Op.REMOVE));
      }
      if (ops.containsKey(Op.ADD)) {
        operation(f, Op.ADD, ops.get(Op.ADD));
      }
      if (ops.containsKey(Op.REPLACE) && (ops.containsKey(Op.ADD) || ops.containsKey(Op.REMOVE))) {
        report.count("replace+op", 1);
      }
    });
  }

  private void replace(Field f, LuaValue value) {
    Optional<LuaUnresolved> unresolved = firstUnresolved(value);
    if (unresolved.isPresent()) {
      f.skip(unresolved.get().reason());
      return;
    }
    FieldType type = f.type();
    if (value instanceof LuaTable t) {
      if (t.isEmpty()) {
        f.set(LuaNil.NIL);
        report.count("deleted", 1);
        return;
      }
      if (type != FieldType.TABLE) {
        f.skip("table into scalar");
        return;
      }
    } else if (type == FieldType.TABLE) {
      f.skip("scalar into table field");
      return;
    } else if (!(type == FieldType.NUMBER && value instanceof LuaNumber || type == FieldType.STRING && value instanceof LuaString)) {
      report.count("coerced", 1);
    }
    f.set(value);
    report.count("replaced", 1);
  }

  private void operation(Field f, Op op, LuaValue value) {
    Optional<LuaUnresolved> unresolved = firstUnresolved(value);
    if (unresolved.isPresent()) {
      f.skip(unresolved.get().reason());
      return;
    }
    if (!(value instanceof LuaTable operand)) {
      f.skip("operand is not a table");
      return;
    }
    if (f.type() != FieldType.TABLE) {
      f.skip("unsupported op on scalar field");
      return;
    }
    Optional<Shape> shape = f.shape();
    if (shape.isEmpty()) {
      f.skip("unsupported op on field without shape");
      return;
    }
    if (operand.isEmpty()) {
      report.count("ops.empty", 1); // {} as an operand does nothing and does not look at the existing value
      return;
    }
    LuaValue existing = f.get();
    if (!(existing instanceof LuaNil || existing instanceof LuaTable)) {
      f.skip("existing value is not a table");
      return;
    }
    Optional<LuaValue> result;
    try {
      result = op == Op.ADD ? TableOps.add(shape.get(), existing, operand) : TableOps.remove(shape.get(), existing, operand);
    } catch (IllegalArgumentException e) {
      f.skip("malformed operand: " + e.getMessage());
      return;
    }
    if (result.isEmpty()) {
      f.skip("unsupported op on shape " + shape.get());
      return;
    }
    f.set(result.get());
    report.count("ops", 1);
  }

  private static Optional<LuaUnresolved> firstUnresolved(LuaValue v) {
    if (v instanceof LuaUnresolved u) {
      return Optional.of(u);
    }
    if (v instanceof LuaTable t) {
      for (Map.Entry<LuaValue, LuaValue> e : t.entries()) {
        Optional<LuaUnresolved> inner = firstUnresolved(e.getValue());
        if (inner.isPresent()) {
          return inner;
        }
      }
    }
    return Optional.empty();
  }

  private void layerSkip(CorrectionLayer layer, String counter, String reason) {
    report.count(counter, 1);
    report.skip(new Skip(layer.name(), layer.kind(), 0, Skip.LAYER, reason));
  }

  /** One field of one entity, addressed by a correction in one layer. */
  private final class Field {
    private final EntityTable table;
    private final long id;
    private final int index;
    private final String layer;

    Field(EntityTable table, long id, int index, String layer) {
      this.table = table;
      this.id = id;
      this.index = index;
      this.layer = layer;
    }

    String name() {
      return table.schema().name(index);
    }

    FieldType type() {
      return table.schema().type(index);
    }

    Optional<Shape> shape() {
      return table.schema().shape(index);
    }

    LuaValue get() {
      return table.get(id, name());
    }

    void set(LuaValue v) {
      table.set(id, name(), v);
    }

    void skip(String reason) {
      report.skip(new Skip(layer, table.kind(), id, name(), reason));
    }
  }
}
