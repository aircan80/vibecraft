package dev.wowforever.importer.sqlite;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.corrections.CorrectionApplier;
import dev.wowforever.importer.corrections.CorrectionLayer;
import dev.wowforever.importer.corrections.CorrectionLayers;
import dev.wowforever.importer.corrections.CorrectionScope;
import dev.wowforever.importer.corrections.EnumLoader;
import dev.wowforever.importer.drops.DropRates;
import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.entity.RawEntityLoader;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.schema.EntitySchema;
import dev.wowforever.importer.schema.MetaLoader;
import dev.wowforever.lua.LuaNil;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValue;
import dev.wowforever.lua.ProblemCollector;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The fixture entity tables after every Forever correction layer, written to SQLite, for the quest writer tests. */
final class QuestFixture {
  private QuestFixture() {}

  static Map<EntityKind, EntitySchema> schemas() {
    Map<EntityKind, EntitySchema> m = new EnumMap<>(EntityKind.class);
    for (EntityKind k : EntityKind.values()) {
      m.put(k, MetaLoader.load(
          Fixtures.questieDbRoot().resolve("src/meta/" + k.metaEntity().toLowerCase(Locale.ROOT) + "Meta.lua")));
    }
    return m;
  }

  static CorrectionScope.Bound bound() {
    return CorrectionScope.build(EnumLoader.load(Fixtures.questieDbRoot(), new ProblemCollector()), schemas());
  }

  /** The fixture entity tables after every Forever correction layer, as {@code ImportPipeline} builds them. */
  static Map<EntityKind, EntityTable> corrected() {
    Map<EntityKind, EntitySchema> schemas = schemas();
    Map<EntityKind, EntityTable> tables = new EnumMap<>(EntityKind.class);
    for (EntityKind k : EntityKind.values()) {
      tables.put(k, RawEntityLoader.load(Fixtures.questieDbRoot().resolve("data/Forever/forever" + k.metaEntity()
          + "DB.lua"), k, schemas.get(k), new ProblemCollector()));
    }
    CorrectionScope.Bound bound = CorrectionScope.build(
        EnumLoader.load(Fixtures.questieDbRoot(), new ProblemCollector()), schemas);
    CorrectionApplier applier = new CorrectionApplier(new ImportReport());
    for (CorrectionLayer layer : CorrectionLayers.forever()) {
      applier.applyLayer(layer, Fixtures.questieDbRoot(), tables, bound, new ProblemCollector());
    }
    return tables;
  }

  /** Writes {@code tables} to {@code dir/t.sqlite} and opens it. */
  static Connection write(Path dir, Map<EntityKind, EntityTable> tables, ImportReport report) throws Exception {
    Path out = dir.resolve("t.sqlite");
    SqliteWriter.write(out, new ImportModel(tables, new DropRates(), Map.of(), List.of(), Map.of()), report);
    return DriverManager.getConnection("jdbc:sqlite:" + out);
  }

  static List<String> column(Connection c, String sql) throws SQLException {
    List<String> out = new ArrayList<>();
    try (ResultSet rs = c.createStatement().executeQuery(sql)) {
      while (rs.next()) {
        out.add(rs.getString(1));
      }
    }
    return out;
  }

  static long scalar(Connection c, String sql) throws SQLException {
    try (ResultSet rs = c.createStatement().executeQuery(sql)) {
      rs.next();
      return rs.getLong(1);
    }
  }

  /** An array table; nil values leave holes. */
  static LuaTable table(LuaValue... values) {
    LuaTable t = new LuaTable();
    for (int i = 0; i < values.length; i++) {
      if (!(values[i] instanceof LuaNil)) {
        t.put(LuaNumber.of(i + 1), values[i]);
      }
    }
    return t;
  }
}
