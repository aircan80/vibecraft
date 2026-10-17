package dev.wowforever.lua;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Reads the collector addon's sample SavedVariables file the way the importer will. */
class CollectorSampleTest {
  private static final Path SAMPLE = Path.of("tools", "collector", "fixtures", "WowForeverCollector.sample.lua");

  private static LuaTable db;
  private static ProblemCollector problems;

  /** Surefire runs in the module folder; the fixture lives at the repository root. */
  private static Path locate() {
    Path dir = Path.of("").toAbsolutePath();
    while (dir != null) {
      Path candidate = dir.resolve(SAMPLE);
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("fixture not found: " + SAMPLE);
  }

  @BeforeAll
  static void evaluate() {
    LuaChunk chunk = LuaChunk.parse(locate());
    problems = new ProblemCollector();
    Scope scope = new Scope();
    new ChunkEvaluator(chunk.fileName(), CallHandler.NONE, problems).run(chunk.statements(), scope);
    db = (LuaTable) scope.lookup("WowForeverCollectorDB").orElseThrow();
  }

  private static LuaTable table(LuaTable t, String key) {
    return LuaValues.asTable(t.get(key)).orElseThrow(() -> new AssertionError("no table at " + key));
  }

  private static LuaTable row(LuaTable list, int index) {
    return LuaValues.asTable(list.get(index)).orElseThrow(() -> new AssertionError("no row " + index));
  }

  @Test
  void evaluatesWithoutProblems() {
    assertThat(problems.problems()).isEmpty();
    assertThat(db.get("schema")).isEqualTo(LuaNumber.of(1));
  }

  @Test
  void readsTheSessionBuild() {
    LuaTable session = table(db, "session");
    assertThat(LuaValues.asString(session.get("build"))).hasValue("1.60.1");
    assertThat(LuaValues.asString(session.get("buildNumber"))).hasValue("70450");
    assertThat(LuaValues.asLong(session.get("interface"))).hasValue(16001);
  }

  @Test
  void readsALootRowWithItsSourceTypeAndId() {
    LuaTable loot = table(db, "loot");
    assertThat(loot.arrayLength()).isEqualTo(4);
    LuaTable corpse = row(loot, 2);
    assertThat(LuaValues.asString(corpse.get("lootKind"))).hasValue("corpse");
    LuaTable source = table(corpse, "source");
    assertThat(LuaValues.asString(source.get("type"))).hasValue("Creature");
    assertThat(LuaValues.asLong(source.get("id"))).hasValue(239512);
    assertThat(LuaValues.asLong(corpse.get("encounterID"))).hasValue(3303);
    LuaTable item = row(table(corpse, "items"), 1);
    assertThat(LuaValues.asLong(item.get("itemID"))).hasValue(251204);
    assertThat(LuaValues.asString(item.get("link")))
        .hasValue("|cnIQ3:|Hitem:251204::::::::28:::::::::|h[Archmage's Warded Mantle]|h|r");
    assertThat(LuaValues.asString(table(corpse, "instance").get("name"))).hasValue("City of Dalaran");

    LuaTable hidden = row(loot, 4);
    assertThat(hidden.get("sourceHidden")).isEqualTo(new LuaBoolean(true));
    assertThat(hidden.get("source")).isEqualTo(LuaNil.NIL);
  }

  @Test
  void keepsPickpocketLootApartFromTheCorpse() {
    LuaTable loot = table(db, "loot");
    LuaTable pickpocket = row(loot, 1);
    assertThat(LuaValues.asString(pickpocket.get("lootKind"))).hasValue("pickpocket");
    assertThat(LuaValues.asLong(pickpocket.get("lootSpellID"))).hasValue(921);
    // Same creature and spawn key as the corpse row: only the kind tells them apart.
    assertThat(table(pickpocket, "source").get("spawnKey")).isEqualTo(table(row(loot, 2), "source").get("spawnKey"));
  }

  @Test
  void readsEscapedQuestTextWithTheNamePlaceholder() {
    LuaTable accept = row(table(db, "quests"), 1);
    assertThat(LuaValues.asString(accept.get("objectivesText")))
        .hasValue("Bring 10 Ruined Pelts to Grimand Elmore in Stormwind, $N.\n\nHe says the tanners pay \"well\".");
  }

  @Test
  void readsNonAsciiNamesAndSparseReturns() {
    LuaTable trainer = row(table(db, "trainers"), 1);
    assertThat(LuaValues.asString(table(trainer, "npc").get("name"))).hasValue("Thérum Deepforge");
    LuaTable raw = table(row(table(trainer, "services"), 2), "raw");
    assertThat(raw.get(3)).isEqualTo(LuaNumber.of(132492));
    assertThat(raw.get(4)).isEqualTo(LuaNil.NIL);
    assertThat(LuaValues.asString(raw.get(5))).hasValue("Rank 1");
  }

  @Test
  void skipsArrayIndexComments() {
    // The 1.60.1 client writes none (checked against its own SavedVariables), but older clients do.
    LuaChunk chunk = LuaChunk.parse("\r\nDB = {\r\n{\r\n[\"a\"] = 1,\r\n}, -- [1]\r\n\"x\", -- [2]\r\n}\r\n", "c.lua");
    ProblemCollector found = new ProblemCollector();
    Scope scope = new Scope();
    new ChunkEvaluator("c.lua", CallHandler.NONE, found).run(chunk.statements(), scope);
    assertThat(found.problems()).isEmpty();
    LuaTable t = (LuaTable) scope.lookup("DB").orElseThrow();
    assertThat(t.arrayLength()).isEqualTo(2);
    assertThat(LuaValues.asString(t.get(2))).hasValue("x");
  }

  @Test
  void readsAnApiFailureRow() {
    LuaTable failure = row(table(db, "apiFailures"), 2);
    assertThat(LuaValues.asString(failure.get("call"))).hasValue("GetLootSourceInfo");
    assertThat(LuaValues.asString(failure.get("where"))).hasValue("LOOT_READY");
    assertThat(LuaValues.asString(failure.get("reason"))).hasValue("secret");
    assertThat(LuaValues.asLong(failure.get("count"))).hasValue(1);
  }

  @Test
  void holdsNoCharacterRealmOrGuildKey() {
    Set<String> keys = new HashSet<>();
    collectKeys(db, keys);
    assertThat(keys).doesNotContain("character", "characterName", "playerName", "player", "realm", "guild", "guid");
  }

  @Test
  void holdsNoGuidOrCharacterNameInAnyString() {
    Set<String> strings = new HashSet<>();
    collectStrings(db, strings);
    assertThat(strings).isNotEmpty();
    // Raw GUIDs start with their kind; the addon keeps only type, id and a hashed spawn key.
    assertThat(strings).noneMatch(s -> s.matches("(?s).*\\b(Player|Creature|GameObject|Pet|Vehicle|Item)-\\d+-.*"));
    // Quest text keeps the client's $N placeholder instead of the character name.
    assertThat(strings).noneMatch(s -> s.contains("Mockplayername"));
    assertThat(strings).anyMatch(s -> s.contains("$N"));
  }

  private static void collectKeys(LuaValue v, Set<String> into) {
    if (v instanceof LuaTable t) {
      for (Map.Entry<LuaValue, LuaValue> e : t.entries()) {
        LuaValues.asString(e.getKey()).ifPresent(into::add);
        collectKeys(e.getValue(), into);
      }
    }
  }

  private static void collectStrings(LuaValue v, Set<String> into) {
    LuaValues.asString(v).ifPresent(into::add);
    if (v instanceof LuaTable t) {
      for (Map.Entry<LuaValue, LuaValue> e : t.entries()) {
        collectStrings(e.getValue(), into);
      }
    }
  }
}
