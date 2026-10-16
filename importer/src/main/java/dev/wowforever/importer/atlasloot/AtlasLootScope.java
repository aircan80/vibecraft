package dev.wowforever.importer.atlasloot;

import dev.wowforever.lua.CallHandler;
import dev.wowforever.lua.LuaNil;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValue;
import dev.wowforever.lua.Scope;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The names and calls AtlasLoot's data files expect from the addon environment. */
public final class AtlasLootScope {
  private static final long VANILLA = 1;

  private AtlasLootScope() {}

  public static Scope scope() {
    Scope s = new Scope();
    s.define("AL", LuaTable.identity());
    s.define("ALIL", LuaTable.identity());
    str(s, "NORMAL_DIFF", "NORMAL");
    str(s, "RAID20_DIFF", "RAID20");
    str(s, "RAID40_DIFF", "RAID40");
    str(s, "ALLIANCE_DIFF", "ALLIANCE");
    str(s, "HORDE_DIFF", "HORDE");
    str(s, "LOAD_DIFF", "ALLIANCE");
    str(s, "NORMAL_ITTYPE", "Item");
    str(s, "SET_ITTYPE", "Set");
    str(s, "QUEST_EXTRA_ITTYPE", "Quest");
    str(s, "PRICE_EXTRA_ITTYPE", "Price");
    str(s, "DUNGEON_CONTENT", "DUNGEON");
    str(s, "RAID20_CONTENT", "RAID20");
    str(s, "RAID40_CONTENT", "RAID40");
    str(s, "ATLASLOOT_IT_ALLIANCE", "ALLIANCE");
    str(s, "ATLASLOOT_IT_HORDE", "HORDE");
    str(s, "ATLASLOOT_IT_AMOUNT1", "AMOUNT1");
    str(s, "ATLASLOOT_DUNGEON_COLOR", "");
    str(s, "ATLASLOOT_RAID20_COLOR", "");
    str(s, "ATLASLOOT_RAID40_COLOR", "");
    s.define("_G", new LuaTable());
    s.define("data", new LuaTable());
    return s;
  }

  public static CallHandler calls() {
    return calls(new LuaTable());
  }

  /** As {@link #calls()}, but the addon's item database answers the given table (the scope's data table). */
  public static CallHandler calls(LuaTable data) {
    return (name, args, line) -> switch (name) {
      case "format" -> Optional.of(format(args));
      case "GetForVersion" -> Optional.of(args.isEmpty() ? LuaNil.NIL : args.get(0));
      case "data:AddDifficulty" -> registered(args, Map.of("20RAID", "RAID20", "40RAID", "RAID40"));
      case "data:AddContentType" ->
          registered(args, Map.of("Dungeons", "DUNGEON", "20 Raids", "RAID20", "40 Raids", "RAID40"));
      case "data:AddItemTableType", "data:AddExtraItemTableType" -> registered(args, Map.of());
      case "AtlasLoot:GameVersion_GE" -> Optional.of(versionChoice(args, true));
      case "AtlasLoot:GameVersion_LT" -> Optional.of(versionChoice(args, false));
      case "C_Map.GetAreaInfo" ->
          !args.isEmpty() && text(args.get(0)).equals("796") ? Optional.of(new LuaString("Scarlet Monastery")) : Optional.empty();
      case "UnitFactionGroup" -> Optional.of(new LuaString("Alliance"));
      case "AtlasLoot.ItemDB:Add" -> Optional.of(data);
      case "getfenv" -> Optional.of(globals());
      default -> Optional.empty();
    };
  }

  /**
   * The id AtlasLoot would register under: the file's own string, except where the scope pre-defines a
   * different canonical id (the file registers display names such as "20RAID" and "Dungeons").
   */
  private static Optional<LuaValue> registered(List<LuaValue> args, Map<String, String> canonical) {
    if (args.isEmpty()) {
      return Optional.empty();
    }
    String given = text(args.get(0));
    return Optional.of(new LuaString(canonical.getOrDefault(given, given)));
  }

  /** {@code GameVersion_GE(n, a, b)} / {@code _LT}: the file is evaluated as the vanilla client. */
  private static LuaValue versionChoice(List<LuaValue> args, boolean greaterOrEqual) {
    if (args.size() < 2 || !(args.get(0) instanceof LuaNumber n)) {
      return LuaNil.NIL;
    }
    boolean matches = greaterOrEqual ? VANILLA >= n.value() : VANILLA < n.value();
    return matches ? args.get(1) : args.size() > 2 ? args.get(2) : LuaNil.NIL;
  }

  /** What the environment lookup yields: enough for the file's upvalue lines to rebind without problems. */
  private static LuaTable globals() {
    LuaTable g = new LuaTable();
    g.put(new LuaString("string"), new LuaTable());
    LuaTable atlasLoot = new LuaTable();
    atlasLoot.put(new LuaString("Locales"), LuaTable.identity());
    atlasLoot.put(new LuaString("IngameLocales"), LuaTable.identity());
    atlasLoot.put(new LuaString("CLASSIC_VERSION_NUM"), LuaNumber.of(VANILLA));
    atlasLoot.put(new LuaString("BC_VERSION_NUM"), LuaNumber.of(2));
    atlasLoot.put(new LuaString("WRATH_VERSION_NUM"), LuaNumber.of(3));
    g.put(new LuaString("AtlasLoot"), atlasLoot);
    return g;
  }

  private static LuaValue format(List<LuaValue> args) {
    if (args.isEmpty()) {
      return new LuaString("");
    }
    String pattern = text(args.get(0));
    StringBuilder out = new StringBuilder();
    int next = 1;
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      if (c == '%' && i + 1 < pattern.length() && (pattern.charAt(i + 1) == 's' || pattern.charAt(i + 1) == 'd')) {
        out.append(next < args.size() ? text(args.get(next++)) : "");
        i++;
      } else {
        out.append(c);
      }
    }
    return new LuaString(out.toString());
  }

  private static String text(LuaValue v) {
    if (v instanceof LuaString s) {
      return s.value();
    }
    if (v instanceof LuaNumber n) {
      return n.isInteger() ? Long.toString(n.asLong()) : Double.toString(n.value());
    }
    return String.valueOf(v);
  }

  private static void str(Scope s, String name, String value) {
    s.define(name, new LuaString(value));
  }
}
