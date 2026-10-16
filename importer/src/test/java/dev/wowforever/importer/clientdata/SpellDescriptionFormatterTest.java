package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.report.ImportReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The spell description formatter on real descriptions from the fixture's {@code Spell} table (spec 4.5). Expected
 * texts are derived by hand from the fixture rows ({@code SpellEffect.EffectBasePointsF}, {@code Variance},
 * {@code EffectAuraPeriod}, {@code EffectChainTargets}, {@code SpellMisc.DurationIndex -> SpellDuration.Duration});
 * the working is in the comments. {@code $sN} is effect index N-1. Where Wowhead's Forever tooltip shows the same
 * spell (known-items.csv), the expected text equals it up to whitespace.
 */
class SpellDescriptionFormatterTest {
  private static SpellDescriptionFormatter formatter;

  @BeforeAll
  static void load() {
    formatter = new SpellDescriptionFormatter(SpellTables.load(ClientItems.fixture()));
  }

  static Stream<Arguments> descriptions() {
    return Stream.of(
        // 21992 Thunderfury: s2 = 300, s1 = -25 -> 25 (no sign before it), x1 = EffectChainTargets of effect 0 = 5,
        // 27648 s1 = -20 -> 20, d = DurationIndex 29 = 12000 ms -> "12 sec". Wowhead's proc line, up to spacing.
        desc(21992, "Blasts your enemy with lightning, dealing 300 Nature damage and then jumping to additional nearby "
            + "enemies.  Each jump reduces that victim's Nature resistance by 25. Affects 5 targets. Your primary "
            + "target is also consumed by a cyclone, slowing its attack speed by 20% for 12 sec."),
        // 1139 Cold Eye: s2 = -20 (effect 1), s1 = -40 (effect 0), d = index 8 = 15000 ms. Wowhead: Cold Basilisk Eye.
        desc(1139, "Increases time between target's attacks by 20% and reduces their movement speed by 40% for 15 sec."),
        desc(18112, "Blasts a target for 700 Fire damage."),
        desc(21991, "When struck in combat inflicts 4 Nature damage to the attacker."),
        // 18381 Cripple: s1 = -24 -> 24, d = index 9 = 30000 ms. Wowhead: Cursed Felblade.
        desc(18381, "Reduces target enemy's attack power by 24 for 30 sec."),
        desc(1293654, "Silences an enemy, preventing it from casting spells for 6 sec."),
        // 18382: ${$s1}.1 = 2 with one decimal. Wowhead: the Bloodvine (3) set bonus.
        desc(18382, "Improves your chance to get a critical strike with spells by 2.0%."),
        desc(7597, "Improves your chance to get a critical strike with melee attacks by 1.0%."),
        desc(430952, "Improves your chance to get a critical strike with spells by 1.0%."),
        // 430 Drink: ${($s1/5)*$d} = (42 / 5) * 18 sec = 151.2 -> 151 (no precision: whole number); $d = index 85.
        desc(430, "Restores 151 mana over 18 sec.  Must remain seated while drinking."),
        // 433 Food: aura 84 (regeneration) has EffectAuraPeriod 0; the game ticks it every 5 s, so
        // $o1 = 17 * 18000 / 5000 = 61.2 -> 61, the classic "61 health over 18 sec".
        desc(433, "Restores 61 health over 18 sec.  Must remain seated while eating."),
        // 439 Healing Potion: s1 = 80 with Variance 0.25 -> 80 * (1 -/+ 0.125) = 70 to 90.
        desc(439, "Restores 70 to 90 health."),
        desc(17534, "Restores 1050 to 1750 health."),
        desc(2023, "Restores 455 to 585 mana."),
        // 13439 Frostbolt: s2 = 50 with Variance 0.40000000596 -> 39.99999985 to 60.0000001 -> 40 to 60 (rounded,
        // not floored); s1 = -50 -> 50; d = index 28 = 5000 ms.
        desc(13439, "Launches a bolt of frost at the enemy causing 40 to 60 Frost damage and slowing movement speed by "
            + "50% for 5 sec."),
        // 5024 Flee: s2 = s3 = 300 with Variance 1.33333337307 -> 300 * (1 -/+ 0.666666686535) = 100 to 500.
        desc(5024, "Increase your run speed by 60% for 10 sec, but deals 100 to 500 damage and drains 100 to 500 mana "
            + "every 2 seconds."),
        // 9796 Blight: s1 = 100; o2 = 30 * 60000 / 5000 (EffectAuraPeriod) = 360; d = index 3 = 60000 ms = "1 min".
        desc(9796, "Diseases a target for 100 Nature damage and an additional 360 damage over 1 min."),
        // 16403 Rend: o1 = 17 * 30000 / 6000 = 85.
        desc(16403, "Wounds the target causing them to bleed for 85 damage over 30 sec."),
        // 18956 Restore Life: t1 = EffectAuraPeriod 3000 ms = 3.
        desc(18956, "Restores 48 health every 3 sec for 30 sec."),
        // 18364 Cloak of Fire: $18374s1 = 25 (another spell), t1 = 5000 ms = 5, d = index 8 = 15 sec.
        desc(18364, "Deals 25 Fire damage every 5 sec to all nearby enemies for 15 sec."),
        // 9160: $9159d = index 1 of spell 9159 = 10000 ms.
        desc(9160, "When struck by a melee attacker, that attacker has a 5% chance of being put to sleep for 10 sec.  "
            + "Only affects enemies level 50 and below."),
        // 9159: trailing spaces are trimmed.
        desc(9159, "Puts the enemy target to sleep for up to 10 sec.  Any damage caused will awaken the target."),
        // 21741: $21740s1 = 15, $21740s2 = 100.
        desc(21741, "Your pet gains 15 stamina and 100 spell resistance against all schools of magic."),
        desc(23553, "Mana cost of Shadow spells reduced by 15%."),
        desc(23554, "Health or Mana gained from Drain Life and Drain Mana increased by 15%."),
        // 14134: $m1 = 1, $leffect:effects; after "1" -> singular; likewise $m2.
        desc(14134, "Purifies the friendly target, removing 1 disease effect and 1 poison effect."),
        // 15279: $ghim:her; takes the first form; d = index 6 = 600000 ms = "10 min".
        desc(15279, "A crystal shield surrounds the friendly target, doing 12 damage to anyone who hits him.  Lasts 10 min."),
        // 17538: d = index 30 = 1800000 ms = "30 min".
        desc(17538, "Increases Agility by 25 and chance to get a critical hit by 2%. Lasts for 30 min."),
        // 17626: d = index 367 = 7200000 ms = "2 hrs".
        desc(17626, "Increases the player's maximum health by 1200 for 2 hrs. You can only have the effect of one flask "
            + "at a time. This effect persists through death."),
        // 17746: ${$s1/5} = 6 / 5 = 1.2 -> 1 (the classic +1 stealth level).
        desc(17746, "Increases your effective stealth level by 1."),
        // 17768: ${$m1/10} = 50 / 10 = 5; $m2 = 5.
        desc(17768, "You gain an additional 5 Rage from activating Enrage and an additional 5 Energy from activating "
            + "Shifting Power."),
        // 18388: $/1000;s1 = -2000 / 1000 = -2 -> 2 (magnitude, no sign before it).
        desc(18388, "Reduces the cooldown of your Fade ability by 2 sec."),
        // 435481: $439164s1 = 75 with Variance 0.40000000596 -> 60 to 90.
        desc(435481, "Permanently enchant a Weapon to cause all spells and attacks to sometimes deal 60 to 90 additional "
            + "damage to mechanical creatures."),
        // 1314143: $@spelldesc16939 is 16939's description, formatted for 16939 (s1 = 4).
        desc(1314143, "Chance on hit to grant nearby party members 4% increased critical strike chance."),
        // 1226814: $25695o1 = 358 * 30000 / 5000 = 2148 (aura 84, 5 s ticks); $25696o2 = effect index 1 of 25696,
        // 735 * 30000 / 5000 = 4410 (aura 85); $25695d = 30 sec. $?s446847[...][] has no player: the else branch
        // (empty) is taken, leaving the two spaces around it.
        desc(1226814, "Restores 2148 health and 4410 mana over 30 sec.  Must remain seated while eating."),
        // 1247426: $@spelldesc1308117, the |cnIQ1: ... |r colour stripped, real line breaks kept, $M3..$M7 =
        // effect 2..6 maxima (no variance): 20, 20, 10, 10, 10.
        desc(1247426, "Fill the crate with any bundle from the following list:\n- 20 Peacebloom\n- 20 Silverleaf\n"
            + "- 10 Earthroot\n- 10 Mageroyal\n- 10 Briarthorn"),
        desc(21160, "The Eye of Sulfuras can be combined with the Sulfuron Hammer to create Sulfuras, legendary hammer "
            + "of Ragnaros."),
        desc(4975, "Invokes Winterhoof spirits of cleansing."),
        desc(17559, "Transmutes Essence of Air into Essence of Fire."));
  }

  private static Arguments desc(long spellId, String expected) {
    return Arguments.of(spellId, expected);
  }

  @ParameterizedTest(name = "spell {0}")
  @MethodSource("descriptions")
  void formatsRealDescriptions(long spellId, String expected) {
    SpellDescriptionFormatter.Formatted f = formatter.description(spellId);
    assertThat(f.text()).isEqualTo(expected);
    assertThat(f.complete()).as("complete").isTrue();
    assertThat(f.unresolved()).isEmpty();
  }

  @Test
  void theTableHasAtLeastThirtyRealDescriptions() {
    assertThat(descriptions().count()).isGreaterThanOrEqualTo(30);
  }

  @Test
  void auraDescriptionsFormatToo() {
    // 433: $/5;s1 = 17 / 5 = 3.4
    assertThat(formatter.format(433, "Restores $/5;s1 health per second.").text())
        .isEqualTo("Restores 3.4 health per second.");
    // 1139's aura text holds a CRLF: it becomes one line break
    assertThat(formatter.format(1139, "Attack speed slowed by $s2%.\r\nMovement speed slowed by $s1%.").text())
        .isEqualTo("Attack speed slowed by 20%.\nMovement speed slowed by 40%.");
    assertThat(formatter.format(9796, "Diseased.  $s2 nature damage every $t2 seconds.").text())
        .isEqualTo("Diseased.  30 nature damage every 5 seconds.");
  }

  @Test
  void procChanceAndRadiusComeFromTheOptionalTables() {
    // 7617 (Totem of Infliction): $h = SpellAuraOptions.ProcChance 1; $16783s1 = 100 +/- 25 %
    SpellDescriptionFormatter.Formatted f = formatter.description(7617);
    assertThat(f.text()).isEqualTo("When struck in combat has a 1% chance of inflicting 75 to 125 Shadow damage to the "
        + "attacker.");
    assertThat(f.complete()).isTrue();
    // 1293674: ProcChance 15
    assertThat(formatter.description(1293674).text()).startsWith("Taking damage has a 15% chance to grant ");
    // 12022: $M1 = 10, $a1 = SpellRadius 10 (EffectRadiusIndex_0 of effect 0) = 30 yards
    SpellDescriptionFormatter.Formatted hat = formatter.description(12022);
    assertThat(hat.text()).isEqualTo("Gives 10 additional stamina to party members within 30 yards.");
    assertThat(hat.complete()).isTrue();
    // ProcChance 101 (12022's row) marks a proc that rolls no percentage: $h stays raw
    SpellDescriptionFormatter.Formatted always = formatter.format(12022, "a $h% chance, ${$h*2}");
    assertThat(always.text()).isEqualTo("a $h% chance, ${$h*2}");
    assertThat(always.unresolved()).containsExactly("$h", "${$h*2}");
    assertThat(formatter.format(7617, "${$h*2}% and $H%").text()).isEqualTo("2% and 1%");
  }

  @Test
  void withoutTheOptionalTablesProcChanceAndRadiusStayRaw(@TempDir Path dir) throws IOException {
    Path csv = Files.createDirectories(dir.resolve("csv"));
    for (String table : List.of("Spell", "SpellName", "SpellEffect", "SpellMisc", "SpellDuration")) {
      Files.copy(Fixtures.clientDataRoot().resolve("csv").resolve(table + ".csv"), csv.resolve(table + ".csv"));
    }
    SpellDescriptionFormatter bare = new SpellDescriptionFormatter(SpellTables.load(new ClientDataSource(dir)));
    SpellDescriptionFormatter.Formatted f = bare.description(7617);
    assertThat(f.text()).isEqualTo("When struck in combat has a $h% chance of inflicting 75 to 125 Shadow damage to the "
        + "attacker.");
    assertThat(f.unresolved()).containsExactly("$h");
    assertThat(bare.description(12022).unresolved()).containsExactly("$a1");
  }

  @Test
  void unknownPlaceholderKeepsRawText() {
    // 8690 Hearthstone: $z (the bound home) has no client table
    SpellDescriptionFormatter.Formatted home = formatter.description(8690);
    assertThat(home.text()).startsWith("Returns you to $z.");
    assertThat(home.complete()).isFalse();
    assertThat(home.unresolved()).containsExactly("$z");
    // a missing effect index, a token nobody knows and an unbalanced expression stay as written
    SpellDescriptionFormatter.Formatted odd = formatter.format(18112, "A $s3 B $proccooldown C ${$s1+ D");
    assertThat(odd.text()).isEqualTo("A $s3 B $proccooldown C ${$s1+ D");
    assertThat(odd.complete()).isFalse();
    assertThat(odd.unresolved()).containsExactly("$s3", "$proccooldown", "${$s1+ D");
  }

  @Test
  void levelConditionsAssumeLevelSixtyAndOtherConditionsAreFalse() {
    // 440212 Cryoblast: $?$pl>=25[$s1][...] at level 60 takes $s1 = 50 +/- 14.88 = 42.56 to 57.44 -> 43 to 57;
    // $a1 = SpellRadius 8 = 5 yards
    SpellDescriptionFormatter.Formatted f = formatter.description(440212);
    assertThat(f.text()).isEqualTo("Inflicts 43 to 57 Frost damage in a 5 yard radius.");
    assertThat(f.complete()).isTrue();
    assertThat(formatter.format(18112, "$?$pl<10[low]?s123[known][else $s1]").text()).isEqualTo("else 700");
    assertThat(formatter.format(18112, "$?!s123[not known][else]").text()).isEqualTo("not known");
    assertThat(formatter.format(18112, "a$?s123[known] b").text()).isEqualTo("a b");
  }

  @Test
  void signRuleAndArithmetic() {
    // 18381 s1 = -24: magnitude without a sign before it; with one, the value's own sign wins when negative
    assertThat(formatter.format(18381, "by $s1, +$s1, -$s1").text()).isEqualTo("by 24, -24, -24");
    assertThat(formatter.format(18112, "+$s1 and -$s1").text()).isEqualTo("+700 and -700");
    assertThat(formatter.format(18112, "${($s1+100)/3}.2 ${$s1*2-(4/2)} ${-$s1}").text())
        .isEqualTo("266.67 1398 -700");
    assertThat(formatter.format(18112, "${$max($s1,800)} ${$min($s1,800)} ${$floor(7/2)}").text())
        .isEqualTo("800 700 3");
    assertThat(formatter.format(18112, "|cffff0000red|r |Tinterface\\icon:0|tplain").text()).isEqualTo("red plain");
    assertThat(formatter.format(18112, "a\\nb").text()).isEqualTo("a\nb");
    assertThat(formatter.format(14134, "$m1 $Lcharge:charges; $s1 $lloaf:loaves; 2 $lcinnamon roll:cinnamon rolls;")
        .text()).isEqualTo("1 charge 1 loaf 2 cinnamon rolls");
    assertThat(formatter.format(18112, "$@spellname18381 / $@spellicon18381.").text()).isEqualTo("Cripple / .");
  }

  @Test
  void durations() {
    assertThat(SpellDescriptionFormatter.duration(1500)).isEqualTo("1.5 sec");
    assertThat(SpellDescriptionFormatter.duration(59000)).isEqualTo("59 sec");
    assertThat(SpellDescriptionFormatter.duration(60000)).isEqualTo("1 min");
    assertThat(SpellDescriptionFormatter.duration(90000)).isEqualTo("1.5 min");
    assertThat(SpellDescriptionFormatter.duration(3600000)).isEqualTo("1 hour");
    assertThat(SpellDescriptionFormatter.duration(7200000)).isEqualTo("2 hrs");
    assertThat(SpellDescriptionFormatter.duration(86400000)).isEqualTo("1 day");
    assertThat(SpellDescriptionFormatter.duration(259200000)).isEqualTo("3 days");
  }

  @Test
  void reportsUnformattedSpellsOnce() {
    SpellDescriptionFormatter f = new SpellDescriptionFormatter(SpellTables.load(ClientItems.fixture()));
    f.description(8690);
    f.description(8690);
    f.format(12022, "$h%");
    f.description(18112);
    f.description(7617);
    ImportReport report = new ImportReport();
    f.report(report);
    assertThat(report.get(SpellDescriptionFormatter.UNFORMATTED)).isEqualTo(2);
    assertThat(report.notes()).anyMatch(n -> n.startsWith("clientdata.unformatted:") && n.contains("8690 ($z)")
        && n.contains("12022 ($h)"));
    assertThat(List.of(f.unformattedSpells().keySet().toArray())).containsExactly(8690L, 12022L);
  }
}
