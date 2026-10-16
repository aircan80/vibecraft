package dev.wowforever.importer.atlasloot;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.source.SourceFetcher;
import dev.wowforever.lua.ProblemCollector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

class AtlasLootLoaderTest {
  private static final Path REAL =
      SourceFetcher.defaultCacheDir().resolve("atlasloot/8e99341e4e779328460bf7684c0d5b22ce50ddf1");

  static boolean realCacheExists() {
    return Files.isRegularFile(REAL.resolve("AtlasLootClassic_DungeonsAndRaids/data.lua"));
  }

  @Test
  void loadsInstancesBossesAndLoot() {
    ProblemCollector problems = new ProblemCollector();
    List<AtlasInstance> all = AtlasLootLoader.load(
        Fixtures.atlasLootRoot(), Map.of(2437L, "Ragefire Chasm", 1581L, "The Deadmines"), problems);
    assertThat(problems.problems()).isEmpty();
    assertThat(all).extracting(AtlasInstance::key).containsExactly("Ragefire", "TheDeadmines", "MoltenCore");
    AtlasInstance rfc = all.get(0);
    assertThat(rfc.name()).isEqualTo("Ragefire Chasm");
    assertThat(rfc.levelMin()).isEqualTo(10);
    assertThat(rfc.levelMax()).isEqualTo(13);
    assertThat(rfc.contentType()).isEqualTo("DUNGEON");
    AtlasBoss tara = rfc.bosses().get(0);
    assertThat(tara.name()).isEqualTo("Taragaman the Hungerer");
    assertThat(tara.npcId()).isEqualTo(11520L);
    assertThat(tara.loot()).containsExactly(
        new AtlasLoot("NORMAL", 1, 14149), new AtlasLoot("NORMAL", 2, 14148), new AtlasLoot("NORMAL", 3, 14145));
  }

  @Test
  void skipsHeaderRowsSetTablesAndKeepsExtraLists() {
    AtlasInstance dm = AtlasLootLoader.load(Fixtures.atlasLootRoot(), Map.of(), new ProblemCollector()).get(1);
    assertThat(dm.name()).isEqualTo("The Deadmines");
    assertThat(dm.bosses()).noneMatch(b -> b.name().equals("Keys")); // KEYS local has IgnoreAsSource
    assertThat(dm.bosses()).anyMatch(b -> b.extraList() && b.name().equals("Trash Mobs"));
    assertThat(dm.bosses().stream().flatMap(b -> b.loot().stream())).allMatch(l -> l.itemId() > 0);
  }

  @Test
  void moltenCoreIsRaid40() {
    assertThat(AtlasLootLoader.load(Fixtures.atlasLootRoot(), Map.of(), new ProblemCollector()).get(2).contentType())
        .isEqualTo("RAID40");
  }

  @Test
  void derivesNamesFromKeysAndCleansColourCodes() {
    assertThat(AtlasLootLoader.splitCamelCase("Zul'Farrak")).isEqualTo("Zul'Farrak");
    assertThat(AtlasLootLoader.splitCamelCase("TheTempleOfAtal'Hakkar")).isEqualTo("The Temple Of Atal'Hakkar");
    assertThat(AtlasLootLoader.splitCamelCase("ScarletMonasteryGraveyard")).isEqualTo("Scarlet Monastery Graveyard");
    assertThat(AtlasLootLoader.splitCamelCase("TheTempleofAhnQiraj")).isEqualTo("The Temple of Ahn Qiraj");
    assertThat(AtlasLootLoader.cleanName("|cffC0C0C0Ring of Law:|r Hurley")).isEqualTo("Ring of Law: Hurley");
    assertThat(AtlasLootLoader.cleanName("|cff6666ffKey|r:")).isEqualTo("Key");
  }

  @Test
  @EnabledIf("realCacheExists")
  void loadsTheRealFileWithoutProblems() {
    ProblemCollector problems = new ProblemCollector();
    List<AtlasInstance> all = AtlasLootLoader.load(REAL, Map.of(), problems);
    assertThat(problems.problems()).isEmpty();
    assertThat(all).hasSize(33);
    assertThat(all).allSatisfy(i -> assertThat(i.bosses()).as(i.key()).isNotEmpty());
    assertThat(all.stream().flatMap(i -> i.bosses().stream()).flatMap(b -> b.loot().stream()))
        .allMatch(l -> l.itemId() > 0);
    assertThat(all.stream().flatMap(i -> i.bosses().stream()).map(AtlasBoss::name))
        .noneMatch(n -> n.contains("|c") || n.contains("|r") || n.startsWith("Boss #"));
    AtlasInstance naxx = all.stream().filter(i -> i.key().equals("Naxxramas")).findFirst().orElseThrow();
    assertThat(naxx.instanceId()).isEqualTo(533L); // vanilla branch of the version-dependent id
    AtlasInstance world = all.stream().filter(i -> i.key().equals("WorldBosses")).findFirst().orElseThrow();
    assertThat(world.mapId()).isNull();
  }
}
