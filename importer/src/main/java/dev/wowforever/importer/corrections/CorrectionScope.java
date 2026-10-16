package dev.wowforever.importer.corrections;

import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.schema.EntitySchema;
import dev.wowforever.lua.CallHandler;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValue;
import dev.wowforever.lua.Scope;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Builds the global symbols the Questie correction files expect when their Load functions run. */
public final class CorrectionScope {
  private CorrectionScope() {}

  /** The scope to evaluate correction files in, and the call handler answering their loader calls. */
  public record Bound(Scope scope, CallHandler calls) {}

  public static Bound build(LuaTable constants, Map<EntityKind, EntitySchema> schemas) {
    Scope scope = new Scope();
    LuaTable questieDb = new LuaTable();
    for (EntityKind kind : EntityKind.values()) {
      questieDb.put(str(kind.keysField()), keyTable(schemas.get(kind)));
    }
    LuaTable forever = expansion(constants, "Forever");
    LuaTable classic = expansion(constants, "Classic");
    for (String name : List.of("raceKeys", "classKeys", "npcFlags")) {
      LuaValue v = forever.get(name);
      questieDb.put(str(name), v instanceof LuaTable ? v : classic.get(name));
    }
    for (String name : List.of(
        "sortKeys", "specialFlags", "questFlags", "factionIDs", "itemClasses", "waypointPresets", "iconTypes")) {
      questieDb.put(str(name), constants.get(name));
    }
    scope.define("QuestieDB", questieDb);
    scope.define("ZoneDB", module("zoneIDs", constants.get("zoneIDs")));
    LuaTable professions = new LuaTable();
    for (String name : List.of("professionKeys", "specializationKeys", "rankNames")) {
      professions.put(str(name), constants.get(name));
    }
    scope.define("QuestieProfessions", professions);
    scope.define("Phasing", module("phases", constants.get("phases")));
    LuaTable corrections = new LuaTable();
    corrections.put(str("itemObjectiveFirst"), new LuaTable());
    corrections.put(str("killCreditObjectiveFirst"), new LuaTable());
    scope.define("QuestieCorrections", corrections);
    scope.define("DropDB", module("correctionKeys", constants.get("dropCorrectionKeys")));
    scope.define("l10n", new LuaTable());
    // Objective icons: Questie.ICON_TYPE_* mirrors constants.iconTypes under the same names.
    LuaTable questie = new LuaTable();
    if (constants.get("iconTypes") instanceof LuaTable icons) {
      icons.entries().forEach(e -> questie.put(e.getKey(), e.getValue()));
    }
    scope.define("Questie", questie);
    scope.define("Expansions", new LuaTable());

    CallHandler calls = (name, args, line) -> switch (name) {
      case "QuestieLoader:ImportModule" -> Optional.of(
          args.isEmpty() || !(args.get(0) instanceof LuaString s)
              ? new LuaTable()
              : scope.lookup(s.value()).filter(LuaTable.class::isInstance).orElseGet(LuaTable::new));
      case "QuestieLoader:CreateModule" -> Optional.of(new LuaTable());
      case "UnitClassBase" -> Optional.of(str("WARRIOR"));
      case "UnitFactionGroup" -> Optional.of(str("Alliance"));
      // l10n(text) translates for the client locale; the database keeps the English source text.
      case "l10n" -> args.isEmpty() ? Optional.empty() : Optional.of(args.get(0));
      default -> Optional.empty();
    };
    return new Bound(scope, calls);
  }

  private static LuaTable keyTable(EntitySchema schema) {
    LuaTable t = new LuaTable();
    for (String field : schema.names()) {
      int index = schema.index(field);
      t.put(str(field), LuaNumber.of(index));
      t.put(str(field + "_add"), str(CorrectionKey.ADD_PREFIX + index));
      t.put(str(field + "_remove"), str(CorrectionKey.REMOVE_PREFIX + index));
    }
    return t;
  }

  private static LuaTable expansion(LuaTable constants, String name) {
    return constants.get("byExpansion") instanceof LuaTable by && by.get(name) instanceof LuaTable t ? t : new LuaTable();
  }

  private static LuaTable module(String field, LuaValue value) {
    LuaTable t = new LuaTable();
    t.put(str(field), value);
    return t;
  }

  private static LuaString str(String s) {
    return new LuaString(s);
  }
}
