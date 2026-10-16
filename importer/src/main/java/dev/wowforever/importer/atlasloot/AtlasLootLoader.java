package dev.wowforever.importer.atlasloot;

import dev.wowforever.lua.ChunkEvaluator;
import dev.wowforever.lua.LuaBoolean;
import dev.wowforever.lua.LuaChunk;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValue;
import dev.wowforever.lua.LuaValues;
import dev.wowforever.lua.ProblemCollector;
import dev.wowforever.lua.Scope;
import dev.wowforever.lua.ast.Expr;
import dev.wowforever.lua.ast.Stmt;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reads AtlasLootClassic's dungeon and raid tables into instance, boss and loot records. */
public final class AtlasLootLoader {
  private static final String DATA_FILE = "AtlasLootClassic_DungeonsAndRaids/data.lua";
  private static final Set<String> DIFFICULTIES = Set.of("NORMAL", "RAID20", "RAID40", "ALLIANCE", "HORDE");

  private AtlasLootLoader() {}

  public static List<AtlasInstance> load(Path atlasLootRoot, Map<Long, String> areaNames, ProblemCollector problems) {
    LuaChunk chunk = LuaChunk.parse(atlasLootRoot.resolve(DATA_FILE));
    String file = chunk.fileName();
    Scope scope = AtlasLootScope.scope();
    LuaTable data = scope.lookup("data").flatMap(LuaValues::asTable).orElseThrow();
    scope.define("addonname", new LuaString("AtlasLootClassic_DungeonsAndRaids"));
    new ChunkEvaluator(file, AtlasLootScope.calls(data), problems).run(executable(chunk.statements()), scope);

    List<AtlasInstance> out = new ArrayList<>();
    for (Stmt.Assign a : chunk.assignments()) {
      Optional<String> key = instanceKey(a.target());
      if (key.isEmpty()) {
        continue;
      }
      if (data.get(key.get()) instanceof LuaTable table) {
        out.add(instance(key.get(), table, areaNames, file, a.line(), problems));
      } else {
        problems.add(file, a.line(), "instance " + key.get() + " is not a table");
      }
    }
    return out;
  }

  /**
   * Drops the locals that would clobber the pre-defined scope: {@code local x = ...} (the addon name is
   * defined directly) and bare redeclarations such as {@code local A, B} that would rebind to nil (their
   * values come from an if/else block the parser skips).
   */
  private static List<Stmt> executable(List<Stmt> statements) {
    return statements.stream()
        .filter(s -> !(s instanceof Stmt.Local l
            && (l.values().isEmpty() || l.values().stream().anyMatch(v -> v instanceof Expr.Vararg))))
        .toList();
  }

  /** The key of a {@code data["Key"]} assignment target, if that is what it is. */
  private static Optional<String> instanceKey(Expr target) {
    if (target instanceof Expr.Index i
        && i.target() instanceof Expr.Name n
        && n.name().equals("data")
        && i.key() instanceof Expr.Literal l
        && l.value() instanceof LuaString s) {
      return Optional.of(s.value());
    }
    return Optional.empty();
  }

  private static AtlasInstance instance(
      String key, LuaTable t, Map<Long, String> areaNames, String file, int line, ProblemCollector problems) {
    Long mapId = number(t.get("MapID"));
    Long instanceId = number(t.get("InstanceID"));
    String name = mapId != null && areaNames.containsKey(mapId) ? areaNames.get(mapId) : splitCamelCase(key);
    String contentType = LuaValues.asString(t.get("ContentType")).orElse(null);
    Integer levelMin = null;
    Integer levelMax = null;
    if (t.get("LevelRange") instanceof LuaTable range) {
      Long min = number(range.get(1));
      Long max = number(range.get(2));
      levelMin = min == null ? null : min.intValue();
      levelMax = max == null ? null : max.intValue();
    }
    List<AtlasBoss> bosses = new ArrayList<>();
    if (t.get("items") instanceof LuaTable items) {
      List<LuaValue> elements = items.array();
      for (int i = 0; i < elements.size(); i++) {
        if (elements.get(i) instanceof LuaTable bossTable && !isSkipped(bossTable)) {
          int sortOrder = bosses.size() + 1;
          bosses.add(boss(key, bossTable, sortOrder, file, line, problems));
        }
      }
    } else {
      problems.add(file, line, "instance " + key + ": items is not a table");
    }
    return new AtlasInstance(key, name, mapId, instanceId, contentType, levelMin, levelMax, List.copyOf(bosses));
  }

  private static boolean isSkipped(LuaTable boss) {
    return "Set".equals(LuaValues.asString(boss.get("TableType")).orElse(null))
        || boss.get("IgnoreAsSource") instanceof LuaBoolean b && b.value();
  }

  private static AtlasBoss boss(
      String instanceKey, LuaTable t, int sortOrder, String file, int line, ProblemCollector problems) {
    String name = LuaValues.asString(t.get("name")).map(AtlasLootLoader::cleanName).orElse(null);
    if (name == null || name.isEmpty()) {
      problems.add(file, line, "instance " + instanceKey + ": boss #" + sortOrder + " has no resolvable name");
      name = "Boss #" + sortOrder;
    }
    Long level = number(t.get("Level"));
    String specialType = LuaValues.asString(t.get("specialType")).orElse(null);
    boolean extraList = t.get("ExtraList") instanceof LuaBoolean b && b.value();
    return new AtlasBoss(
        name,
        firstNumber(t.get("npcID")),
        firstNumber(t.get("ObjectID")),
        level == null ? null : level.intValue(),
        specialType,
        extraList,
        sortOrder,
        loot(t));
  }

  private static List<AtlasLoot> loot(LuaTable boss) {
    List<AtlasLoot> loot = new ArrayList<>();
    for (Map.Entry<LuaValue, LuaValue> e : boss.entries()) {
      if (!(e.getKey() instanceof LuaString difficulty)
          || !DIFFICULTIES.contains(difficulty.value())
          || !(e.getValue() instanceof LuaTable rows)) {
        continue;
      }
      for (LuaValue row : rows.array()) {
        if (row instanceof LuaTable r) {
          Long slot = number(r.get(1));
          Long itemId = number(r.get(2));
          if (slot != null && itemId != null) {
            loot.add(new AtlasLoot(difficulty.value(), slot.intValue(), itemId));
          }
        }
      }
    }
    return List.copyOf(loot);
  }

  private static Long number(LuaValue v) {
    var n = LuaValues.asLong(v);
    return n.isPresent() ? n.getAsLong() : null;
  }

  /** A number, or the first element when the value is a list of them. */
  private static Long firstNumber(LuaValue v) {
    return v instanceof LuaTable t ? number(t.get(1)) : number(v);
  }

  /** Strips WoW colour codes and the trailing colon left by templates such as {@code "|cffC0C0C0Name:|r %s"}. */
  static String cleanName(String raw) {
    String s = raw.replaceAll("\\|c[0-9a-fA-F]{8}", "").replace("|r", "").trim();
    if (s.endsWith(":")) {
      s = s.substring(0, s.length() - 1).trim();
    }
    return s;
  }

  /** {@code TheDeadmines} to {@code The Deadmines}; apostrophes are kept and never split on, a joined lowercase "of" is spaced. */
  static String splitCamelCase(String key) {
    key = key.replaceAll("([a-z])of([A-Z])", "$1 of $2"); // TheTempleofAhnQiraj: a lowercase "of" joiner
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < key.length(); i++) {
      char c = key.charAt(i);
      if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(key.charAt(i - 1))) {
        sb.append(' ');
      }
      sb.append(c);
    }
    return sb.toString();
  }
}
