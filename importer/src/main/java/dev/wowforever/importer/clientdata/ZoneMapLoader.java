package dev.wowforever.importer.clientdata;

import dev.wowforever.importer.clientdata.ClientDataSource.MapInfo;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.zones.Zone;
import dev.wowforever.lua.CallHandler;
import dev.wowforever.lua.ChunkEvaluator;
import dev.wowforever.lua.LuaChunk;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValue;
import dev.wowforever.lua.LuaValues;
import dev.wowforever.lua.ProblemCollector;
import dev.wowforever.lua.Scope;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.LongPredicate;
import javax.imageio.ImageIO;

/**
 * Resolves each zone's UiMap and reads the maps' images. A zone's map comes from, in order: Questie's
 * {@code areaIdToUiMapId.lua} override (the maps its converted spawn positions refer to; 0 means none), the
 * client's {@code UiMapAssignment} table, and finally the nearest ancestor (at most {@value #MAX_DEPTH} levels up)
 * that has one of its own. A resolved UiMap without an image leaves the zone unmapped.
 */
public final class ZoneMapLoader {
  static final int MAX_DEPTH = 5;
  private static final String OVERRIDE_FILE = "support/Forever/Zones/areaIdToUiMapId.lua";
  private static final String OVERRIDE_TARGET = "ZoneDB.private.areaIdToUiMapIdOverride";

  private ZoneMapLoader() {}

  public static ZoneMaps load(ClientDataSource source, Path questieDbRoot, Map<Long, Zone> zones, ImportReport report) {
    Map<Long, Long> override = readOverride(questieDbRoot, report);
    Map<Long, List<Assignment>> assignments = readAssignments(source);
    Map<Long, MapInfo> infos = new HashMap<>();
    for (MapInfo m : source.maps()) {
      infos.putIfAbsent(m.uiMapId(), m);
    }
    Map<Long, Boolean> artCache = new HashMap<>();
    LongPredicate art = id -> artCache.computeIfAbsent(id, k -> source.map(k).isPresent());

    Map<Long, ZoneMaps.MapImage> images = new LinkedHashMap<>();
    Map<Long, Long> zoneToMap = new LinkedHashMap<>();
    long byOverride = 0;
    long byAssignment = 0;
    long byParent = 0;
    long unmapped = 0;
    long missingArt = 0;
    for (Zone zone : zones.values()) {
      Long uiMap = null;
      int kind = 0;
      Resolved own = own(zone.id(), override, assignments, art);
      if (own != null) {
        uiMap = own.uiMapId;
        kind = own.fromOverride ? 1 : 2;
      } else {
        Long parent = zone.parentId();
        for (int depth = 0; depth < MAX_DEPTH && parent != null; depth++) {
          Resolved r = own(parent, override, assignments, art);
          if (r != null) {
            uiMap = r.uiMapId;
            kind = 3;
            break;
          }
          Zone p = zones.get(parent);
          parent = p == null ? null : p.parentId();
        }
      }
      if (uiMap == null) {
        unmapped++;
        continue;
      }
      if (!art.test(uiMap)) {
        missingArt++;
        continue;
      }
      zoneToMap.put(zone.id(), uiMap);
      switch (kind) {
        case 1 -> byOverride++;
        case 2 -> byAssignment++;
        default -> byParent++;
      }
      if (!images.containsKey(uiMap)) {
        images.put(uiMap, image(source, uiMap, infos.get(uiMap)));
      }
    }
    long missingTiles = 0;
    for (long id : images.keySet()) {
      MapInfo info = infos.get(id);
      missingTiles += info == null ? 0 : info.missingTiles();
    }
    report.count("clientdata.zones.mapped.override", byOverride);
    report.count("clientdata.zones.mapped.assignment", byAssignment);
    report.count("clientdata.zones.mapped.parent", byParent);
    report.count("clientdata.zones.unmapped", unmapped);
    report.count("clientdata.zones.missingArt", missingArt);
    report.count("clientdata.maps", images.size());
    report.count("clientdata.maps.missingTiles", missingTiles);
    return new ZoneMaps(zoneToMap, images);
  }

  private record Resolved(long uiMapId, boolean fromOverride) {}

  private record Assignment(long uiMapId, long orderIndex) {}

  /** The zone's own UiMap (override, then assignment), ignoring ancestors; null when it has none. */
  private static Resolved own(long areaId, Map<Long, Long> override, Map<Long, List<Assignment>> assignments,
      LongPredicate art) {
    Long o = override.get(areaId);
    if (o != null && o != 0) {
      return new Resolved(o, true);
    }
    List<Assignment> rows = assignments.get(areaId);
    if (rows == null || rows.isEmpty()) {
      return null;
    }
    Assignment best = null;
    for (Assignment a : rows) {
      if (best == null) {
        best = a;
        continue;
      }
      boolean aArt = art.test(a.uiMapId);
      boolean bArt = art.test(best.uiMapId);
      if (aArt != bArt ? aArt : a.orderIndex < best.orderIndex) {
        best = a;
      }
    }
    return new Resolved(best.uiMapId, false);
  }

  private static Map<Long, Long> readOverride(Path root, ImportReport report) {
    Map<Long, Long> out = new HashMap<>();
    Path file = root.resolve(OVERRIDE_FILE);
    if (!Files.isRegularFile(file)) {
      report.note("no " + OVERRIDE_FILE + " in the QuestieDB checkout; zone maps use the client assignments only");
      return out;
    }
    ProblemCollector problems = new ProblemCollector();
    LuaChunk chunk = LuaChunk.parse(file);
    Optional<String> text = chunk.longStringAssignedTo(OVERRIDE_TARGET);
    if (text.isEmpty()) {
      report.note(OVERRIDE_FILE + ": areaIdToUiMapIdOverride long string not found");
      return out;
    }
    LuaChunk inner = LuaChunk.parse(text.get(), chunk.fileName() + ":areaIdToUiMapIdOverride");
    LuaValue table = new ChunkEvaluator(inner.fileName(), CallHandler.NONE, problems)
        .run(inner.statements(), new Scope()).orElse(null);
    if (table instanceof LuaTable t) {
      for (Map.Entry<LuaValue, LuaValue> e : t.entries()) {
        var area = LuaValues.asLong(e.getKey());
        var map = LuaValues.asLong(e.getValue());
        if (area.isPresent() && map.isPresent()) {
          out.put(area.getAsLong(), map.getAsLong());
        }
      }
    }
    return out;
  }

  private static Map<Long, List<Assignment>> readAssignments(ClientDataSource source) {
    Map<Long, List<Assignment>> out = new HashMap<>();
    Optional<CsvTable> table = source.optionalTable("UiMapAssignment");
    if (table.isEmpty()) {
      return out;
    }
    for (CsvTable.Row row : table.get().rows()) {
      OptionalLong area = row.getLong("AreaID");
      OptionalLong map = row.getLong("UiMapID");
      if (area.isPresent() && area.getAsLong() != 0 && map.isPresent() && map.getAsLong() != 0) {
        out.computeIfAbsent(area.getAsLong(), k -> new ArrayList<>())
            .add(new Assignment(map.getAsLong(), row.longOr("OrderIndex", 0)));
      }
    }
    return out;
  }

  private static ZoneMaps.MapImage image(ClientDataSource source, long uiMapId, MapInfo info) {
    Path file = source.map(uiMapId).orElseThrow();
    try {
      byte[] jpeg = Files.readAllBytes(file);
      if (info != null) {
        return new ZoneMaps.MapImage(info.width(), info.height(), jpeg);
      }
      BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(jpeg));
      if (decoded == null) {
        throw new IllegalStateException(file + " is not a decodable image");
      }
      return new ZoneMaps.MapImage(decoded.getWidth(), decoded.getHeight(), jpeg);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + file + ": " + e.getMessage(), e);
    }
  }
}
