package dev.wowforever.importer.drops;

import dev.wowforever.importer.source.SourceRoots;
import dev.wowforever.lua.CallHandler;
import dev.wowforever.lua.ChunkEvaluator;
import dev.wowforever.lua.Evaluator;
import dev.wowforever.lua.LuaChunk;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValue;
import dev.wowforever.lua.LuaValues;
import dev.wowforever.lua.ProblemCollector;
import dev.wowforever.lua.Scope;
import dev.wowforever.lua.ast.Expr;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Loads drop percentages from four sources and defines the precedence of all of them (lower wins): 1 Questie manual
 * corrections, 2 AtlasLoot item overrides, 3 Questie Wowhead data, 4 VMaNGOS server rates, 5 AtlasLoot drop table,
 * 6 VMaNGOS quest-only rates. The VMaNGOS rates are loaded separately by
 * {@link dev.wowforever.importer.serverdb.ServerDropRates}: they are a vanilla emulator's configured chances, while
 * Forever's loot is reworked, so observed and curated data outranks them, and a quest-only chance (the chance while
 * on the quest) is a different quantity that headlines only when no other source rates the pair.
 */
public final class DropRateLoader {
  public static final int QUESTIE_CORRECTION = 1;
  public static final int ATLASLOOT_OVERRIDE = 2;
  public static final int QUESTIE_WOWHEAD = 3;
  /** VMaNGOS rates of ordinary rows: below Questie's observed data, above the AtlasLoot drop table. */
  public static final int VMANGOS = 4;
  public static final int ATLASLOOT = 5;
  /** VMaNGOS quest-only rates (negative {@code ChanceOrQuestChance}): below every other source. */
  public static final int VMANGOS_QUEST_ONLY = 6;

  private static final Path QUESTIE_DROPS = Path.of("support/Forever/DropTables/classicItemDrops.lua");
  private static final Path QUESTIE_CORRECTIONS = Path.of("support/Forever/DropTables/itemDropCorrections.lua");
  private static final Path ATLAS_DIR = Path.of("AtlasLootClassic_DungeonsAndRaids");

  private DropRateLoader() {}

  public static DropRates load(SourceRoots roots, ProblemCollector problems) {
    DropRates rates = new DropRates();
    loadWowhead(roots.questieDb().resolve(QUESTIE_DROPS), rates, problems);
    loadCorrections(roots.questieDb().resolve(QUESTIE_CORRECTIONS), rates, problems);
    loadAtlasLoot(roots.atlasLoot().resolve(ATLAS_DIR).resolve("droprate.lua"), rates, problems);
    loadOverrides(roots.atlasLoot().resolve(ATLAS_DIR).resolve("droprate_override.lua"), rates, problems);
    return rates;
  }

  /** {@code [itemId] = {[npcId] = pct}} inside the {@code wowheadData} long string. */
  private static void loadWowhead(Path file, DropRates rates, ProblemCollector problems) {
    LuaChunk outer = LuaChunk.parse(file);
    Optional<String> text = outer.longStringAssignedTo("QuestieClassicItemDrops.wowheadData");
    if (text.isEmpty()) {
      problems.add(outer.fileName(), 0, "wowheadData long string not found");
      return;
    }
    LuaChunk inner = LuaChunk.parse(text.get(), outer.fileName() + ":wowheadData");
    LuaValue table = new ChunkEvaluator(inner.fileName(), CallHandler.NONE, problems)
        .run(inner.statements(), new Scope()).orElse(null);
    forEachPair(table, (itemId, npcId, pct) ->
        rates.put(npcId, itemId, new DropRate(pct, "questie-wowhead"), QUESTIE_WOWHEAD));
  }

  /**
   * {@code QuestieItemDropCorrections.Era = {[itemId] = {[npcId] = pct | DropKeys.MARKER}}}. The Era
   * expression is evaluated directly with {@code DropKeys} bound to the marker constants (-1, -2), so
   * the outer chunk is never run; marker values are non-positive and therefore skipped.
   */
  private static void loadCorrections(Path file, DropRates rates, ProblemCollector problems) {
    LuaChunk chunk = LuaChunk.parse(file);
    Optional<Expr> era = chunk.assignedValue("QuestieItemDropCorrections.Era");
    if (era.isEmpty()) {
      problems.add(chunk.fileName(), 0, "QuestieItemDropCorrections.Era not found");
      return;
    }
    LuaTable keys = new LuaTable();
    keys.put(new LuaString("WOWHEAD"), LuaNumber.of(-1));
    keys.put(new LuaString("PSERVER"), LuaNumber.of(-2));
    Scope scope = new Scope();
    scope.define("DropKeys", keys);
    LuaValue table = new Evaluator(chunk.fileName(), scope, CallHandler.NONE, problems).eval(era.get());
    forEachPair(table, (itemId, npcId, pct) ->
        rates.put(npcId, itemId, new DropRate(pct, "questie-correction"), QUESTIE_CORRECTION));
  }

  /** {@code local DropRateData = {[npcId] = {[itemId] = pct}}}. */
  private static void loadAtlasLoot(Path file, DropRates rates, ProblemCollector problems) {
    LuaChunk chunk = LuaChunk.parse(file);
    Optional<Expr> data = chunk.localValue("DropRateData");
    if (data.isEmpty()) {
      problems.add(chunk.fileName(), 0, "DropRateData not found");
      return;
    }
    LuaValue table = new Evaluator(chunk.fileName(), new Scope(), CallHandler.NONE, problems).eval(data.get());
    forEachPair(table, (npcId, itemId, pct) ->
        rates.put(npcId, itemId, new DropRate(pct, "atlasloot"), ATLASLOOT));
  }

  /** {@code DROPRATE_OV = {[itemId] = pct | false}}; a global in the real file, a local is tolerated too. */
  private static void loadOverrides(Path file, DropRates rates, ProblemCollector problems) {
    LuaChunk chunk = LuaChunk.parse(file);
    Optional<Expr> data = chunk.assignedValue("DROPRATE_OV").or(() -> chunk.localValue("DROPRATE_OV"));
    if (data.isEmpty()) {
      problems.add(chunk.fileName(), 0, "DROPRATE_OV not found");
      return;
    }
    LuaValue table = new Evaluator(chunk.fileName(), new Scope(), CallHandler.NONE, problems).eval(data.get());
    if (!(table instanceof LuaTable t)) {
      return;
    }
    for (Map.Entry<LuaValue, LuaValue> e : t.entries()) {
      var itemId = LuaValues.asLong(e.getKey());
      // A value of false means "ignore this item".
      if (itemId.isPresent() && e.getValue() instanceof LuaNumber pct) {
        rates.putItem(itemId.getAsLong(), new DropRate(pct.value(), "atlasloot-override"), ATLASLOOT_OVERRIDE);
      }
    }
  }

  private interface PairSink {
    void accept(long outerKey, long innerKey, double percent);
  }

  /** Visits {@code outer -> inner -> number}; non-positive numbers are reference markers and skipped. */
  private static void forEachPair(LuaValue table, PairSink sink) {
    if (!(table instanceof LuaTable outer)) {
      return;
    }
    for (Map.Entry<LuaValue, LuaValue> o : outer.entries()) {
      var outerKey = LuaValues.asLong(o.getKey());
      if (outerKey.isEmpty() || !(o.getValue() instanceof LuaTable inner)) {
        continue;
      }
      for (Map.Entry<LuaValue, LuaValue> i : inner.entries()) {
        var innerKey = LuaValues.asLong(i.getKey());
        if (innerKey.isPresent() && i.getValue() instanceof LuaNumber n && n.value() > 0) {
          sink.accept(outerKey.getAsLong(), innerKey.getAsLong(), n.value());
        }
      }
    }
  }
}
