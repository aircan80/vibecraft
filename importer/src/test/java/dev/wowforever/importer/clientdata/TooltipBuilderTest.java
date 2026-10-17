package dev.wowforever.importer.clientdata;

import static dev.wowforever.importer.clientdata.TooltipLine.EQUIP_GREEN;
import static dev.wowforever.importer.clientdata.TooltipLine.FLAVOUR_GOLD;
import static dev.wowforever.importer.clientdata.TooltipLine.MUTED;
import static dev.wowforever.importer.clientdata.TooltipLine.SET_GREY;
import static dev.wowforever.importer.clientdata.TooltipLine.WHITE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.report.ImportReport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Tooltip lines: kinds in spec 4.3 order, the game's texts and colours. */
class TooltipBuilderTest {
  private static ItemTooltips fixture;
  private static ImportReport report;
  private static TooltipNames names;

  /** The kinds in spec 4.3 order, with damage_extra after damage and source_note last (controller rulings). */
  static final List<String> ORDER = List.of("name", "binding", "unique", "begins_quest", "slot", "damage",
      "damage_extra", "speed", "dps", "armor", "block", "stat", "resist", "durability", "req_class", "req_race",
      "req_level", "req_skill", "req_rep", "use", "equip", "proc", "set_name", "set_item", "set_bonus", "charges",
      "duration", "flavour", "item_level", "sell_price", "source_note");

  @BeforeAll
  static void build() {
    long[] ids = {19019, 10329, 5079, 13482, 4500, 5768, 5613, 1307, 5411, 7953, 13262, 19316, 19682, 12640, 6948,
        118, 159, 25, 17204, 6900, 215129, 2916, 5248};
    Map<Long, ClientItem> items = ClientItems.load(ids);
    report = new ImportReport();
    Map<Long, ItemValues> values = ItemValues.compute(items, ClientItems.fixtureTables(), report);
    fixture = ItemTooltips.build(ClientItems.fixture(), items, values, Map.of(), report);
    names = TooltipNames.load(ClientItems.fixture(), Map.of(424242L, "Questie Name"));
  }

  private static List<String> render(long id) {
    return fixture.lines().get(id).stream()
        .map(l -> l.ordinal() + " " + l.kind() + " " + l.colour() + " " + l.text())
        .toList();
  }

  @Test
  void thunderfuryForeverRow() {
    assertThat(render(19019)).containsExactly(
        "0 name #ff8000 Thunderfury, Blessed Blade of the Windseeker",
        "1 binding #ffffff Binds when picked up",
        "2 unique #ffffff Unique",
        "3 slot #ffffff One-Hand\tSword",
        "4 damage #ffffff 82 - 153 Damage",
        "5 speed #ffffff Speed 1.90",
        "6 dps #ffffff (61.84 damage per second)",
        "7 stat #ffffff +5 Agility",
        "8 stat #ffffff +8 Stamina",
        "9 resist #ffffff +8 Fire Resistance",
        "10 resist #ffffff +9 Nature Resistance",
        "11 req_level #ffffff Requires Level 60",
        "12 proc #1eff00 Chance on hit: Blasts your enemy with lightning, dealing 300 Nature damage and then jumping "
            + "to additional nearby enemies.  Each jump reduces that victim's Nature resistance by 25. Affects 5 "
            + "targets. Your primary target is also consumed by a cyclone, slowing its attack speed by 20% for 12 "
            + "sec.",
        "13 item_level #9d9d9d Item Level 80",
        "14 sell_price #9d9d9d Sell Price: 25g 53s 55c");
  }

  @Test
  void scarletBeltEraRowWithItsSetAndTheSourceNote() {
    List<String> lines = render(10329);
    assertThat(lines.subList(0, 9)).containsExactly(
        "0 name #1eff00 Scarlet Belt",
        "1 binding #ffffff Binds when equipped",
        "2 slot #ffffff Waist\tMail",
        "3 armor #ffffff 123 Armor",
        "4 stat #ffffff +8 Strength",
        "5 stat #ffffff +7 Stamina",
        "6 durability #ffffff Durability 35 / 35",
        "7 req_level #ffffff Requires Level 32",
        "8 set_name #ffd100 Chain of the Scarlet Crusade (0/6)");
    assertThat(lines.subList(9, 15)).containsExactly(
        "9 set_item #808080 Scarlet Belt",
        "10 set_item #808080 Scarlet Boots",
        "11 set_item #808080 Scarlet Chestpiece",
        "12 set_item #808080 Scarlet Gauntlets",
        "13 set_item #808080 Scarlet Leggings",
        "14 set_item #808080 Scarlet Wristguards");
    assertThat(lines.get(15)).isEqualTo("15 set_bonus #808080 (2) Set: +10 Shadow Resistance.");
    assertThat(lines.get(16)).isEqualTo("16 set_bonus #808080 (3) Set: +30 Attack Power against Undead.");
    assertThat(lines.get(18)).isEqualTo("18 set_bonus #808080 (5) Set: Improves your chance to hit by 1.0%.");
    assertThat(lines.get(19)).startsWith("19 set_bonus #808080 (6) Set: Taking damage while at or below 20% health");
    assertThat(lines.subList(20, lines.size())).containsExactly(
        "20 item_level #9d9d9d Item Level 37",
        "21 sell_price #9d9d9d Sell Price: 25s 7c",
        "22 source_note #9d9d9d Stats from Classic Era data; Forever values may differ");
  }

  @Test
  void theBuilderPublishesTheSameKindOrder() {
    assertThat(TooltipBuilder.KINDS).isEqualTo(ORDER);
  }

  @Test
  void everyTooltipFollowsTheKindOrderWithConsecutiveOrdinals() {
    for (Map.Entry<Long, List<TooltipLine>> e : fixture.lines().entrySet()) {
      List<TooltipLine> lines = e.getValue();
      assertThat(lines.get(0).kind()).as("item %d", e.getKey()).isEqualTo("name");
      int last = -1;
      for (int i = 0; i < lines.size(); i++) {
        TooltipLine l = lines.get(i);
        assertThat(l.ordinal()).isEqualTo(i);
        int pos = ORDER.indexOf(l.kind());
        assertThat(pos).as("item %d kind %s", e.getKey(), l.kind()).isGreaterThanOrEqualTo(0);
        if (!(l.kind().equals("req_skill") && last == ORDER.indexOf("set_name"))) { // a set's own skill requirement
          assertThat(pos).as("item %d line %d (%s) after %s", e.getKey(), i, l.kind(), ORDER.get(Math.max(last, 0)))
              .isGreaterThanOrEqualTo(last);
          last = pos;
        }
        assertThat(l.text()).as("item %d line %d", e.getKey(), i).isNotBlank();
      }
    }
  }

  @Test
  void coloursByKind() {
    Map<String, Set<String>> byKind = fixture.lines().values().stream().flatMap(List::stream)
        .collect(Collectors.groupingBy(TooltipLine::kind, Collectors.mapping(TooltipLine::colour, Collectors.toSet())));
    assertThat(byKind.get("use")).containsExactly(EQUIP_GREEN);
    assertThat(byKind.get("equip")).containsExactly(EQUIP_GREEN);
    assertThat(byKind.get("proc")).containsExactly(EQUIP_GREEN);
    assertThat(byKind.get("flavour")).containsExactly(FLAVOUR_GOLD);
    assertThat(byKind.get("set_name")).containsExactly(FLAVOUR_GOLD);
    assertThat(byKind.get("set_item")).containsExactly(SET_GREY);
    assertThat(byKind.get("set_bonus")).containsExactly(SET_GREY);
    for (String k : List.of("item_level", "sell_price", "source_note")) {
      assertThat(byKind.get(k)).as(k).containsExactly(MUTED);
    }
    for (String k : List.of("binding", "unique", "slot", "damage", "speed", "dps", "armor", "stat", "resist",
        "durability", "req_class", "req_race", "req_level", "req_skill", "req_rep", "charges", "duration")) {
      assertThat(byKind.get(k)).as(k).containsExactly(WHITE);
    }
    assertThat(TooltipLine.qualityColour(0)).isEqualTo("#9d9d9d");
    assertThat(Arrays.stream(new int[] {1, 2, 3, 4, 5, 6, 7}).mapToObj(TooltipLine::qualityColour).toList())
        .containsExactly("#ffffff", "#1eff00", "#0070dd", "#a335ee", "#ff8000", "#e6cc80", "#00ccff");
    assertThat(TooltipLine.qualityColour(null)).isEqualTo(WHITE);
  }

  @Test
  void useLinesWithCooldownsUniqueEquippedAndFlavour() {
    assertThat(render(5079)).contains("2 unique #ffffff Unique-Equipped", "3 slot #ffffff Trinket",
        "4 resist #ffffff +9 Frost Resistance", "5 req_level #ffffff Requires Level 35",
        "6 use #1eff00 Use: Increases time between target's attacks by 20% and reduces their movement speed by 40% "
            + "for 15 sec. (7 Min Cooldown)");
    assertThat(render(118)).contains("1 use #1eff00 Use: Restores 70 to 90 health. (2 Min Cooldown)");
    assertThat(texts(159, "use")).as("a 1 s shared category cooldown is not shown")
        .containsExactly("Use: Restores 151 mana over 18 sec.  Must remain seated while drinking.");
    assertThat(texts(13262, "flavour")).containsExactly("\"Blade of the Scarlet Highlord\"");
    assertThat(texts(17204, "use")).containsExactly("Use: The Eye of Sulfuras can be combined with the Sulfuron "
        + "Hammer to create Sulfuras, legendary hammer of Ragnaros.");
    assertThat(texts(6948, "unique")).containsExactly("Unique");
    assertThat(texts(6948, "use")).singleElement().asString().startsWith("Use: Returns you to $z.");
  }

  @Test
  void recipeRequirementsReputationAndNoFlavour() {
    assertThat(render(13482)).containsExactly(
        "0 name #1eff00 Recipe: Transmute Air to Fire",
        "1 binding #ffffff Binds when picked up",
        "2 req_skill #ffffff Requires Alchemy (275)",
        "3 req_rep #ffffff Requires Argent Dawn - Honored",
        "4 use #1eff00 Use: Teaches you how to transmute Essence of Air into Essence of Fire.",
        "5 item_level #9d9d9d Item Level 55");
    assertThat(texts(215129, "flavour")).isEmpty();
    assertThat(texts(215129, "use")).containsExactly("Use: Permanently enchant a Weapon to cause all spells and "
        + "attacks to sometimes deal 60 to 90 additional damage to mechanical creatures.");
  }

  @Test
  void bagsTimedItemsChargesQuestItemsAndRestrictions() {
    assertThat(texts(4500, "slot")).containsExactly("16 Slot Bag");
    assertThat(texts(5768, "duration")).containsExactly("Duration: 10 min");
    assertThat(texts(5613, "charges")).containsExactly("3 Charges");
    assertThat(texts(1307, "begins_quest")).containsExactly("This Item Begins a Quest");
    assertThat(texts(1307, "req_level")).containsExactly("Requires Level 7");
    assertThat(texts(5411, "binding")).containsExactly("Quest Item");
    assertThat(texts(5411, "req_race")).containsExactly("Races: Tauren");
    assertThat(texts(6900, "req_class")).containsExactly("Classes: Warlock");
    assertThat(texts(6900, "slot")).containsExactly("Chest\tCloth");
    assertThat(texts(7953, "req_class")).containsExactly("Classes: Rogue");
    assertThat(texts(7953, "set_name")).containsExactly("Garb of Thero-shan (0/6)");
    assertThat(texts(7953, "set_bonus"))
        .containsExactly("(6) Set: Improves your chance to get a critical strike with melee attacks by 1.0%.");
    assertThat(texts(25, "req_level")).as("level 1 is not shown").isEmpty();
  }

  @Test
  void ammunitionShieldsWandsAndRatings() {
    assertThat(texts(19316, "slot")).containsExactly("Ammo\tArrow");
    assertThat(texts(19316, "dps")).containsExactly("Adds 16.5 damage per second");
    assertThat(kinds(19316)).doesNotContain("damage", "speed");
    assertThat(texts(2916, "slot")).containsExactly("Off Hand\tShield");
    assertThat(texts(2916, "armor")).containsExactly("645 Armor");
    assertThat(kinds(2916)).as("no block value in the client tables").doesNotContain("block");
    assertThat(texts(5248, "damage")).containsExactly("27 - 52 Nature Damage");
    assertThat(texts(5248, "slot")).containsExactly("Ranged\tWand");
    // Lionheart Helm: crit 28 and hit 20 rating become Wowhead's Equip lines (14 and 10 rating per 1 %)
    assertThat(texts(12640, "equip")).containsExactly("Equip: Improves your chance to hit by 2.0%.",
        "Equip: Improves your chance to get a critical strike by 2.0%.");
    assertThat(texts(12640, "stat")).containsExactly("+18 Strength");
    // Bloodvine Vest: spell power 45; set block with the set's skill requirement under its name
    assertThat(texts(19682, "equip")).containsExactly("Equip: Improves your chance to hit by 2.0%.",
        "Equip: Increases damage and healing done by magical spells and effects by up to 27.");
    assertThat(render(19682)).contains("8 set_name #ffd100 Bloodvine Garb (0/3)",
        "9 req_skill #ffffff Requires Tailoring (300)", "10 set_item #808080 Bloodvine Vest",
        "13 set_bonus #808080 (3) Set: Improves your chance to get a critical strike with spells by 2.0%.");
  }

  @Test
  void syntheticItemExercisesTheRemainingKinds() {
    ClientItem.Absolute era = new ClientItem.Absolute(List.of(), 82, 153, 2, 0, List.of(), 16, 30);
    ClientItem item = new ClientItem(424242, true, 5, 80, 60, 2, 7, 13, 1, 10203L, 0L, 1, null, null, null,
        "Flavour", 1 | 4, -1L, 197, 300, List.of(0, 0, 0, 0, 0), null, List.of(), 1900, 0.6, 0, ClientItem.ERA, era,
        new ClientItem.Details(3, 125, null, 529, 7, 3600, -1L));
    List<ItemStat> stats = List.of(new ItemStat(7, 8), new ItemStat(55, 9), new ItemStat(43, 5),
        new ItemStat(46, 4), new ItemStat(38, 30), new ItemStat(41, 66), new ItemStat(42, 22), new ItemStat(85, 12),
        new ItemStat(50, 40), new ItemStat(48, 9), new ItemStat(39, 24), new ItemStat(92, 5), new ItemStat(0, 3));
    ItemDamage damage = new ItemDamage(82, 153, 0, 1900, 61.8421);
    List<ItemSpellEffect> effects = List.of(
        new ItemSpellEffect(0, ItemSpellEffect.CHANCE_ON_HIT, 1, "Proc.", true, 0, 0, false),
        new ItemSpellEffect(1, ItemSpellEffect.EQUIP, 2, "Equip text.", true, 0, 0, false),
        new ItemSpellEffect(2, ItemSpellEffect.USE, 3, "Use text.\nSecond line.", true, -2, 90000, false),
        new ItemSpellEffect(3, ItemSpellEffect.USE, 4, null, true, 0, 0, false));
    ItemSetInfo set = new ItemSetInfo(1, "Odd Set", List.of(424242L, 77L), null, null,
        List.of(new ItemSetInfo.Bonus(2, 5, "Bonus.", true), new ItemSetInfo.Bonus(3, 6, null, true)));
    List<String> lines = TooltipBuilder.build(item, stats, damage, null, effects, set, names).stream()
        .map(l -> l.kind() + " " + l.text()).toList();
    assertThat(lines).containsExactly(
        "name Questie Name",
        "binding Binds when picked up",
        "unique Unique (3)",
        "slot One-Hand\tSword",
        "damage 82 - 153 Damage",
        "damage_extra +16 - 30 Damage",
        "speed Speed 1.90",
        "dps (61.84 damage per second)",
        "stat +8 Stamina",
        "stat Restores +5 mana per 5 sec.",
        "stat Restores +4 health per 5 sec.",
        "stat +40 Armor",
        "stat +9 Block Value",
        "stat +24 Ranged Attack Power",
        "stat +5 Stat #92",
        "stat +3 Mana",
        "resist +9 Nature Resistance",
        "durability Durability 125 / 125",
        "req_class Classes: Warrior, Hunter",
        "req_level Requires Level 60",
        "req_skill Requires Tailoring (300)",
        "req_rep Requires Argent Dawn - Exalted",
        "use Use: Use text.\nSecond line. (1 Min 30 Sec Cooldown)",
        "equip Equip: +30 Attack Power.",
        "equip Equip: Increases healing done by up to 66 and damage done by up to 22 for all magical spells and "
            + "effects.",
        "equip Equip: Increases damage done by Fire spells and effects by up to 12.",
        "equip Equip: Equip text.",
        "proc Chance on hit: Proc.",
        "set_name Odd Set (0/2)",
        "set_item Questie Name",
        "set_item Item #77",
        "set_bonus (2) Set: Bonus.",
        "charges 2 Charges",
        "duration Duration: 1 hour",
        "flavour \"Flavour\"",
        "item_level Item Level 80",
        "sell_price Sell Price: 1g 2s 3c",
        "source_note Stats from Classic Era data; Forever values may differ");
  }

  @Test
  void countersAndMissingKinds() {
    assertThat(report.get(ItemTooltips.LINES)).isEqualTo(
        fixture.lines().values().stream().mapToLong(List::size).sum());
    assertThat(report.get(ItemTooltips.ITEMS)).isEqualTo(fixture.lines().size());
    assertThat(report.get(ItemTooltips.MISSING_KIND + "durability"))
        .as("Forever's ItemSparse has no MaxDurability column").isEqualTo(1);
    assertThat(report.counters()).containsKeys(ItemEffectLoader.EFFECTS, ItemSetLoader.SETS,
        SpellDescriptionFormatter.UNFORMATTED);
    assertThat(fixture.sets().keySet()).as("only sets of the given items").containsExactlyInAnyOrder(163L, 221L, 421L);
    assertThat(TooltipBuilder.money(10203)).isEqualTo("1g 2s 3c");
    assertThat(TooltipBuilder.money(52)).isEqualTo("52c");
    assertThat(TooltipBuilder.money(10000)).isEqualTo("1g");
  }

  private static List<String> texts(long id, String kind) {
    List<String> out = new ArrayList<>();
    for (TooltipLine l : fixture.lines().get(id)) {
      if (l.kind().equals(kind)) {
        out.add(l.text());
      }
    }
    return out;
  }

  private static List<String> kinds(long id) {
    return fixture.lines().get(id).stream().map(TooltipLine::kind).toList();
  }
}
