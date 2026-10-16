package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.model.ObjectSummary;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.model.ZoneSummary;
import org.junit.jupiter.api.Test;

class SubtitlesTest {

  @Test
  void itemShowsClassSubclassAndLevels() {
    assertThat(Subtitles.item(new ItemSummary(1, "Cape", 19, 14, "Armor", "Cloth", 2)))
        .isEqualTo("Armor · Cloth · iLvl 19 · Req 14");
  }

  @Test
  void itemSkipsWhatIsUnknown() {
    assertThat(Subtitles.item(new ItemSummary(1, "Cloth", null, 0, "Trade Goods", null, 0)))
        .isEqualTo("Trade Goods");
    assertThat(Subtitles.item(new ItemSummary(1, null, null, null, null, null, 0))).isEqualTo("Item");
  }

  @Test
  void npcShowsLevelsRankAndZone() {
    assertThat(Subtitles.npc(new NpcSummary(448, "Hogger", null, 11, 11, 1, "Elwynn Forest", 12L)))
        .isEqualTo("NPC · Level 11 · Elite · Elwynn Forest");
    assertThat(Subtitles.npc(new NpcSummary(1, "Rat", null, 1, 3, 0, null, null))).isEqualTo("NPC · Level 1–3");
  }

  @Test
  void otherKinds() {
    assertThat(Subtitles.object(new ObjectSummary(1, "Chest", "Elwynn Forest", 12L)))
        .isEqualTo("Object · Elwynn Forest");
    assertThat(Subtitles.quest(new QuestSummary(1, "Q", 12, 10, "Westfall"))).isEqualTo("Quest · Level 12 · Westfall");
    assertThat(Subtitles.zone(new ZoneSummary(1, "Goldshire", "subzone", 12L, "Elwynn Forest")))
        .isEqualTo("Subzone · Elwynn Forest");
    assertThat(Subtitles.instance(new InstanceSummary(1, "Blackrock Spire", "RAID40", 55, 60, 46L,
        "Burning Steppes"))).isEqualTo("Raid (40) · Level 55–60 · Burning Steppes");
  }
}
