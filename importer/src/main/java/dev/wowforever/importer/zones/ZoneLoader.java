package dev.wowforever.importer.zones;

import dev.wowforever.lua.CallHandler;
import dev.wowforever.lua.Evaluator;
import dev.wowforever.lua.LuaChunk;
import dev.wowforever.lua.LuaBoolean;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValue;
import dev.wowforever.lua.LuaValues;
import dev.wowforever.lua.ChunkEvaluator;
import dev.wowforever.lua.ProblemCollector;
import dev.wowforever.lua.Scope;
import dev.wowforever.lua.ast.Expr;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Builds the zone table from QuestieDB's zone enum plus its dungeon and sub-zone support files. */
public final class ZoneLoader {
  private static final Set<Long> CONTINENTS = Set.of(10073L, 10074L, 10075L, 10089L);
  private static final Set<Long> CITIES = Set.of(1497L, 1519L, 1537L, 1637L, 1638L, 1657L);

  private ZoneLoader() {}

  public static Map<Long, Zone> load(
      Path questieDbRoot, LuaTable constants, Set<Long> raidAreaIds, ProblemCollector problems) {
    Map<Long, Zone> zones = new LinkedHashMap<>();
    if (constants.get("zoneIDs") instanceof LuaTable ids) {
      for (Map.Entry<LuaValue, LuaValue> e : ids.entries()) {
        if (e.getKey() instanceof LuaString name && LuaValues.asLong(e.getValue()).isPresent()) {
          long id = LuaValues.asLong(e.getValue()).getAsLong();
          String kind = CONTINENTS.contains(id) ? "continent" : CITIES.contains(id) ? "city" : "zone";
          // Several symbols can share an id; the first one in source order names it.
          zones.putIfAbsent(id, new Zone(id, ZoneNames.fromSymbol(name.value()), null, kind));
        }
      }
    }
    Set<Long> dungeonIds = applyDungeons(questieDbRoot, zones, raidAreaIds, problems);
    applySubZones(questieDbRoot, zones, dungeonIds, problems);
    return zones;
  }

  /** Entries are {@code [areaId] = {name, altAreaIds|nil, parentZoneId, locations}}. Returns the ids seen. */
  private static Set<Long> applyDungeons(
      Path root, Map<Long, Zone> zones, Set<Long> raidAreaIds, ProblemCollector problems) {
    Set<Long> seen = new java.util.HashSet<>();
    LuaChunk chunk = LuaChunk.parse(root.resolve("support/Forever/Zones/dungeons.lua"));
    Optional<Expr> value = chunk.localValue("dungeons");
    if (value.isEmpty()) {
      problems.add(chunk.fileName(), 0, "local dungeons not found");
      return seen;
    }
    Scope scope = new Scope();
    scope.define("isHorde", new LuaBoolean(false));
    scope.define("ZoneDB", new LuaTable());
    scope.define("Expansions", new LuaTable());
    CallHandler calls = (name, args, line) -> switch (name) {
      case "UnitFactionGroup" -> Optional.of(new LuaString("Alliance"));
      case "QuestieLoader:ImportModule" -> Optional.of(new LuaTable());
      default -> Optional.empty();
    };
    LuaValue table = new Evaluator(chunk.fileName(), scope, calls, problems).eval(value.get());
    if (!(table instanceof LuaTable t)) {
      return seen;
    }
    for (Map.Entry<LuaValue, LuaValue> e : t.entries()) {
      var id = LuaValues.asLong(e.getKey());
      if (id.isEmpty() || !(e.getValue() instanceof LuaTable entry)) {
        continue;
      }
      long areaId = id.getAsLong();
      Optional<String> name = LuaValues.asString(entry.get(1));
      Long parent = LuaValues.asLong(entry.get(3)).isPresent() ? LuaValues.asLong(entry.get(3)).getAsLong() : null;
      Zone existing = zones.get(areaId);
      String displayName = name.orElse(existing != null ? existing.name() : "Zone #" + areaId);
      zones.put(areaId, new Zone(areaId, displayName, parent, raidAreaIds.contains(areaId) ? "raid" : "dungeon"));
      seen.add(areaId);
    }
    return seen;
  }

  /** {@code [subAreaId] = parentAreaId}; applies to ids already present as well as new ones. */
  private static void applySubZones(
      Path root, Map<Long, Zone> zones, Set<Long> dungeonIds, ProblemCollector problems) {
    LuaChunk chunk = LuaChunk.parse(root.resolve("support/Forever/Zones/subZoneToParentZone.lua"));
    Optional<String> text = chunk.longStringAssignedTo("ZoneDB.private.subZoneToParentZoneOverride");
    if (text.isEmpty()) {
      problems.add(chunk.fileName(), 0, "subZoneToParentZoneOverride long string not found");
      return;
    }
    LuaChunk inner = LuaChunk.parse(text.get(), chunk.fileName() + ":subZoneToParentZoneOverride");
    LuaValue table = new ChunkEvaluator(inner.fileName(), CallHandler.NONE, problems)
        .run(inner.statements(), new Scope()).orElse(null);
    if (!(table instanceof LuaTable t)) {
      return;
    }
    Map<Long, Long> parents = new HashMap<>();
    for (Map.Entry<LuaValue, LuaValue> e : t.entries()) {
      var sub = LuaValues.asLong(e.getKey());
      var parent = LuaValues.asLong(e.getValue());
      if (sub.isPresent() && parent.isPresent()) {
        parents.put(sub.getAsLong(), parent.getAsLong());
      }
    }
    parents.forEach((subId, parentId) -> {
      Zone existing = zones.get(subId);
      if (existing == null) {
        zones.put(subId, new Zone(subId, "Zone #" + subId, parentId, "subzone"));
      } else if (dungeonIds.contains(subId)) {
        zones.put(subId, new Zone(subId, existing.name(), parentId, existing.kind()));
      } else {
        zones.put(subId, new Zone(subId, existing.name(), parentId, "subzone"));
      }
    });
  }
}
