package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import org.junit.jupiter.api.Test;

class LabelsTest {

  @Test
  void tunedLevel() {
    assertThat(Labels.tunedLevel(28)).isEqualTo("Level 28+");
    assertThat(Labels.tunedLevel(null)).isNull();
  }

  @Test
  void idTextNamesTheKindAndId() {
    assertThat(Labels.idText(new EntityRef(EntityKind.ITEM, 19019))).contains("item:19019");
    assertThat(Labels.idText(new EntityRef(EntityKind.NPC, 448))).contains("npc:448");
    assertThat(Labels.idText(new EntityRef(EntityKind.QUEST, 7))).contains("quest:7");
    assertThat(Labels.idText(new EntityRef(EntityKind.OBJECT, 9))).contains("object:9");
    assertThat(Labels.idText(new EntityRef(EntityKind.ZONE, 40))).contains("zone:40");
    assertThat(Labels.idText(new EntityRef(EntityKind.INSTANCE, 36))).contains("instance:36");
    assertThat(Labels.idText(new EntityRef(EntityKind.BOSS, 3))).isEmpty();
  }

  @Test
  void contentType() {
    assertThat(Labels.contentType("RAID40")).isEqualTo("Raid (40)");
    assertThat(Labels.contentType("RAID20")).isEqualTo("Raid (20)");
    assertThat(Labels.contentType("RAID10")).isEqualTo("Raid (10)");
    assertThat(Labels.contentType("RAID")).isEqualTo("Raid");
    assertThat(Labels.contentType("DUNGEON")).isEqualTo("Dungeon");
    assertThat(Labels.contentType("WORLD_BOSS")).isEqualTo("World Boss");
    assertThat(Labels.contentType("battleground")).isEqualTo("Battleground");
    assertThat(Labels.contentType(null)).isNull();
    assertThat(Labels.contentType(" ")).isNull();
  }

  @Test
  void zoneKind() {
    assertThat(Labels.zoneKind("raid")).isEqualTo("Raid zone");
    assertThat(Labels.zoneKind("dungeon")).isEqualTo("Dungeon");
    assertThat(Labels.zoneKind("city")).isEqualTo("City");
    assertThat(Labels.zoneKind("continent")).isEqualTo("Continent");
    assertThat(Labels.zoneKind("subzone")).isEqualTo("Sub-zone");
    assertThat(Labels.zoneKind("zone")).isEqualTo("Zone");
    assertThat(Labels.zoneKind("unknown")).isEqualTo("Area");
    assertThat(Labels.zoneKind("battleground")).isEqualTo("Battleground");
    assertThat(Labels.zoneKind(null)).isNull();
  }

  @Test
  void rank() {
    assertThat(Labels.rank(0)).isEmpty();
    assertThat(Labels.rank(1)).isEqualTo("Elite");
    assertThat(Labels.rank(2)).isEqualTo("Rare Elite");
    assertThat(Labels.rank(3)).isEqualTo("Boss");
    assertThat(Labels.rank(4)).isEqualTo("Rare");
    assertThat(Labels.rank(9)).isEmpty();
    assertThat(Labels.rank(null)).isEmpty();
  }

  @Test
  void levels() {
    assertThat(Labels.levels(10, 20)).isEqualTo("Level 10–20");
    assertThat(Labels.levels(60, 60)).isEqualTo("Level 60");
    assertThat(Labels.levels(60, null)).isEqualTo("Level 60");
    assertThat(Labels.levels(null, 63)).isEqualTo("Level 63");
    assertThat(Labels.levels(null, null)).isNull();
    assertThat(Labels.levels(0, 0)).isNull();
    assertThat(Labels.levels(-1, null)).isNull();
  }

  @Test
  void itemSubtitle() {
    assertThat(Labels.itemSubtitle("Armor", "Mail", 37, 32)).isEqualTo("Mail · iLvl 37 · Req 32");
    assertThat(Labels.itemSubtitle("Quest", "Quest", 16, 0)).as("not read as a quest")
        .isEqualTo("Quest item · iLvl 16");
    assertThat(Labels.itemSubtitle("Trade Goods", null, 1, 1)).isEqualTo("Trade Goods");
    assertThat(Labels.itemSubtitle("Miscellaneous", "Junk", 1, null)).isEqualTo("Junk");
    assertThat(Labels.itemSubtitle(null, null, null, null)).isEmpty();
    assertThat(Labels.itemSubtitle(null, "Cloth", 19, 14)).isEqualTo("Cloth · iLvl 19 · Req 14");
  }

  @Test
  void questItemSubtitle() {
    // Westfall Deed (1972): a quest item, rather than a quest search result.
    assertThat(Labels.itemSubtitle("Quest", "Quest", 8, 8)).isEqualTo("Quest item · iLvl 8 · Req 8");
  }

  @Test
  void requiredSkillFromTheTooltipLine() {
    assertThat(Labels.skill("Requires Leatherworking (65)")).isEqualTo("Leatherworking 65");
    assertThat(Labels.skill("Requires Engineering")).isEqualTo("Engineering");
    assertThat(Labels.skill("  ")).isNull();
    assertThat(Labels.skill(null)).isNull();
  }

  @Test
  void classesOfAClassMask() {
    assertThat(Labels.classes(256)).isEqualTo("Warlock");
    assertThat(Labels.classes(1 | 2)).isEqualTo("Warrior, Paladin");
    assertThat(Labels.classes(1024 | 128)).isEqualTo("Mage, Druid");
    assertThat(Labels.classes(null)).isNull();
    assertThat(Labels.classes(-1)).as("every class").isNull();
    assertThat(Labels.classes(0)).isNull();
    assertThat(Labels.classes(32767)).isNull();
    assertThat(Labels.classes(1535)).as("every class of the game").isNull();
    assertThat(Labels.classes(1535 | 32)).as("plus classes the game lacks").isNull();
    assertThat(Labels.classes(32)).as("only classes the game lacks").isNull();
  }

  @Test
  void npcSubtitle() {
    assertThat(Labels.npcSubtitle(1, 34, 35, "Scarlet Monastery")).isEqualTo("Elite · Level 34–35 · Scarlet Monastery");
    assertThat(Labels.npcSubtitle(0, 11, 11, "Elwynn Forest")).isEqualTo("Level 11 · Elwynn Forest");
    assertThat(Labels.npcSubtitle(null, null, null, null)).isEmpty();
  }

  @Test
  void instanceSubtitle() {
    assertThat(Labels.instanceSubtitle("RAID40", 60, 63, "Burning Steppes"))
        .isEqualTo("Raid (40) · Level 60–63 · Burning Steppes");
    assertThat(Labels.instanceSubtitle("DUNGEON", null, null, null)).isEqualTo("Dungeon");
    assertThat(Labels.instanceSubtitle(null, 10, 13, "Orgrimmar")).isEqualTo("Instance · Level 10–13 · Orgrimmar");
  }

  @Test
  void zoneSubtitle() {
    assertThat(Labels.zoneSubtitle("dungeon", "Westfall")).isEqualTo("Dungeon · Westfall");
    assertThat(Labels.zoneSubtitle("raid", null)).isEqualTo("Raid zone");
    assertThat(Labels.zoneSubtitle(null, "Westfall")).isEqualTo("Zone · Westfall");
  }

  @Test
  void questAndObjectSubtitles() {
    assertThat(Labels.questSubtitle(10, "Elwynn Forest")).isEqualTo("Level 10 · Elwynn Forest");
    assertThat(Labels.questSubtitle(null, null)).isEmpty();
    assertThat(Labels.objectSubtitle("Elwynn Forest")).isEqualTo("Elwynn Forest");
    assertThat(Labels.objectSubtitle(null)).isEmpty();
  }

  @Test
  void withKindNamesKindsTheFactsDoNotAlreadyName() {
    assertThat(Labels.withKind(EntityKind.NPC, "Elite · Level 16")).isEqualTo("NPC · Elite · Level 16");
    assertThat(Labels.withKind(EntityKind.QUEST, "Level 10 · Elwynn Forest")).isEqualTo("Quest · Level 10 · Elwynn Forest");
    assertThat(Labels.withKind(EntityKind.OBJECT, "")).isEqualTo("Object");
    assertThat(Labels.withKind(EntityKind.ITEM, "Mail · iLvl 37")).isEqualTo("Mail · iLvl 37");
    assertThat(Labels.withKind(EntityKind.ITEM, "")).isEqualTo("Item");
    assertThat(Labels.withKind(EntityKind.INSTANCE, "Raid (40)")).isEqualTo("Raid (40)");
  }

  @Test
  void instanceWingFromKey() {
    assertThat(Labels.instanceWing("Scarlet Monastery", "ScarletMonasteryLibrary")).isEqualTo("Library");
    assertThat(Labels.instanceWing("Blackrock Spire", "LowerBlackrockSpire")).isEqualTo("Lower");
    assertThat(Labels.instanceWing("Blackrock Spire", "UpperBlackrockSpire")).isEqualTo("Upper");
    assertThat(Labels.instanceWing("Dire Maul", "DireMaulNorth")).isEqualTo("North");
    // a client dungeon's key is its map id, no wing (3B Task 17)
    assertThat(Labels.instanceWing("The Hall of Thanes", "client:3065")).isNull();
    assertThat(Labels.instanceTitle("The Hall of Thanes", "client:3065")).isEqualTo("The Hall of Thanes");
    assertThat(Labels.instanceWing("Molten Core", "MoltenCore")).isNull();
    assertThat(Labels.instanceWing("Zul'Farrak", "Zul'Farrak")).isNull();
    assertThat(Labels.instanceWing("Onyxia's Lair", "Onyxia")).isNull();
    assertThat(Labels.instanceWing("Ruins of Ahn'Qiraj", "TheRuinsofAhnQiraj")).isNull();
    assertThat(Labels.instanceWing("Scarlet Monastery", null)).isNull();
    assertThat(Labels.instanceWing(null, "ScarletMonasteryLibrary")).isEqualTo("Scarlet Monastery Library");
  }

  @Test
  void instanceTitle() {
    assertThat(Labels.instanceTitle("Scarlet Monastery", "ScarletMonasteryArmory")).isEqualTo("Scarlet Monastery › Armory");
    assertThat(Labels.instanceTitle("Molten Core", "MoltenCore")).isEqualTo("Molten Core");
  }

  @Test
  void countSuffix() {
    assertThat(Labels.withCount("Object · Westfall", 4)).isEqualTo("Object · Westfall · ×4");
    assertThat(Labels.withCount("Object · Westfall", 1)).isEqualTo("Object · Westfall");
    assertThat(Labels.withCount("", 3)).isEqualTo("×3");
  }

  @Test
  void neverPrintsNullOrRawValues() {
    String all = String.join("|", Labels.itemSubtitle(null, null, 1, 1), Labels.npcSubtitle(null, null, null, null),
        Labels.instanceSubtitle("RAID40", null, null, null), Labels.zoneSubtitle("raid", null),
        Labels.questSubtitle(null, null), Labels.objectSubtitle(null));
    assertThat(all).doesNotContain("null", "iLvl 1", "RAID40", "raid|");
  }
  @org.junit.jupiter.api.Test
  void questLevelsAndFactionMasks() {
    assertThat(Labels.questLevels(7, 4)).isEqualTo("Lv 7 (req. 4)");
    assertThat(Labels.questLevels(null, 4)).isEqualTo("req. 4");
    assertThat(Labels.questLevels(7, null)).isEqualTo("Lv 7");
    assertThat(Labels.questLevels(null, null)).isNull();
    assertThat(Labels.factions(77)).isEqualTo("Alliance");
    assertThat(Labels.factions(178)).isEqualTo("Horde");
    assertThat(Labels.factions(255)).isNull();
    assertThat(Labels.factions(null)).isNull();
  }
}
