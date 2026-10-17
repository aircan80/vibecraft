package dev.wowforever.app.ui.pages.quest;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ObjectiveRow;
import dev.wowforever.app.model.ObjectiveSource;
import dev.wowforever.app.model.ObjectiveVerb;
import dev.wowforever.app.model.Spawn;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ObjectiveTextTest {
  static ObjectiveSource source(EntityKind kind, long id, String name, Double pct, String pctSource) {
    return new ObjectiveSource(new EntityRef(kind, id), name, pct, pctSource,
        List.of(new Spawn(12, "Elwynn Forest", 42, 65)));
  }

  static ObjectiveRow row(ObjectiveVerb verb, List<ObjectiveSource> sources) {
    return new ObjectiveRow(1, verb, List.of(new EntityRef(EntityKind.ITEM, 1931)), "Huge Gnoll Claw",
        null, sources, List.of(), null);
  }

  @Test
  void descriptionsRemoveRequiredCountsButPreserveVerifiedNumericNames() {
    assertThat(ObjectiveText.description("Save at least 6 Sprite Darters from capture"))
        .isEqualTo("Save Sprite Darters from capture");
    assertThat(ObjectiveText.description("15 Patients Saved!")).isEqualTo("Patients Saved!");
    assertThat(ObjectiveText.description("Protect A-Me 01 until you reach Karna Remtravel"))
        .isEqualTo("Protect A-Me 01 until you reach Karna Remtravel");
    assertThat(ObjectiveText.description("Use the Draco-Incarcinatrix 900 and defeat the dragonkin."))
        .isEqualTo("Use the Draco-Incarcinatrix 900 and defeat the dragonkin.");
    assertThat(ObjectiveText.description("Unknown requirement 17 remaining")).isEmpty();
  }

  @Test
  void collectShowsThreeSourcesAndRemainingCountWithoutInventingRates() {
    var sources = IntStream.range(0, 6).mapToObj(i -> source(EntityKind.NPC, i, "Source " + i,
        i == 1 ? null : 34.0 - i, "questie")).toList();
    assertThat(ObjectiveText.where(row(ObjectiveVerb.COLLECT, sources)))
        .isEqualTo("Drops from Source 0 34 %, Source 1, Source 2 32 %, +3 more");
  }

  @Test
  void objectSourcesAreFoundInAndUnknownLocationIsExplicit() {
    var sources = IntStream.range(0, 16).mapToObj(i -> source(EntityKind.OBJECT, i, "Chest " + i,
        null, null)).toList();
    assertThat(ObjectiveText.where(row(ObjectiveVerb.COLLECT, sources)))
        .isEqualTo("Found in Chest 0, Chest 1, +14 more");
    assertThat(ObjectiveText.where(row(ObjectiveVerb.COLLECT, List.of())))
        .isEqualTo("Source unknown — not in the quest database yet.");
  }

  @Test
  void killCreditsSumSpawnsAcrossMembersAndEscortNamesItsFallback() {
    var sources = List.of(source(EntityKind.NPC, 1, "One", null, null),
        source(EntityKind.NPC, 2, "Two", null, null));
    var row = new ObjectiveRow(1, ObjectiveVerb.KILL, sources.stream().map(ObjectiveSource::ref).toList(),
        "One", null, sources, List.of(), null);
    assertThat(ObjectiveText.where(row)).isEqualTo("Elwynn Forest · 2 spawns of 2 NPCs");
    assertThat(ObjectiveText.where(row(ObjectiveVerb.ESCORT, List.of())))
        .isEqualTo("Escort — from the quest giver's spot");
  }

  @Test
  void vanillaRatesKeepTheirProvenance() {
    assertThat(ObjectiveText.where(row(ObjectiveVerb.COLLECT,
        List.of(source(EntityKind.NPC, 1, "Source", 34.0, "vmangos")))))
        .contains("34 %", "Vanilla rate");
  }

  @Test
  void ratesUseRootLocaleAndPrecisionBelowOnePercent() {
    var old = java.util.Locale.getDefault();
    try {
      java.util.Locale.setDefault(java.util.Locale.GERMANY);
      assertThat(ObjectiveText.rate(84.0)).isEqualTo("84 %");
      assertThat(ObjectiveText.rate(0.54)).isEqualTo("0.5 %");
      assertThat(ObjectiveText.rate(null)).isEmpty();
    } finally {
      java.util.Locale.setDefault(old);
    }
  }
}
