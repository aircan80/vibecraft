package dev.wowforever.app.ui.pages;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.ui.Columns;
import org.junit.jupiter.api.Test;
import java.util.List;

class ObservedColumnsTest {
  @Test
  void addonReportedLootKeepsItsSourcesAndQualificationVisible() {
    var detail=new dev.wowforever.app.model.InstanceDetail(
        new dev.wowforever.app.model.InstanceSummary(1,"Synthetic dungeon","DUNGEON",1,10,null,null),
        List.of(new dev.wowforever.app.model.BossSummary(1,"Synthetic boss",null,10,null,false)),List.of("NORMAL"));
    var row=new LootRow(new EntityRef(EntityKind.ITEM,1),"Synthetic loot",1,null,
        "AtlasLoot Continued + AtlasLoot Forever (reported)",null);
    var page=new InstancePage(detail,(id,difficulty)->List.of(row),target->{});
    assertThat(page.lootSection().note().getText()).contains("AtlasLoot Continued","AtlasLoot Forever","Not verified in game");
    assertThat(VisibleColumns.of(Columns.lootRows(),List.of(row),null)).anySatisfy(c -> assertThat(c.title()).isEqualTo("Origin"));
    assertThat(dev.wowforever.app.ui.widgets.OriginChip.label(row.origin())).isEqualTo("Addon reported");
  }

  @Test
  void loneObservedDifficultyExplainsItsScopeEvenWithoutADifficultySwitch() {
    var detail = new dev.wowforever.app.model.InstanceDetail(
        new dev.wowforever.app.model.InstanceSummary(1, "Synthetic dungeon", "DUNGEON", 1, 10, null, null),
        List.of(new dev.wowforever.app.model.BossSummary(1, "Synthetic boss", 2L, 10, null, false)),
        List.of(dev.wowforever.app.db.InstanceRepository.OBSERVED_DIFFICULTY));
    var row = new LootRow(new EntityRef(EntityKind.ITEM, 1), "Synthetic loot", 1, null, null, null)
        .withObservation(true);
    var page = new InstancePage(detail, (id, difficulty) -> List.of(row), target -> {});
    assertThat(page.lootSection().note().getText()).isEqualTo("Observed in game; difficulty unknown.");
  }

  @Test
  void singleObservedDropKeepsItsEvidenceVisible() {
    var row = new LootRow(new EntityRef(EntityKind.ITEM, 1), "Synthetic loot", 1, null, "questie", null)
        .withObservation(true);
    assertThat(VisibleColumns.of(Columns.lootRows(), List.of(row), null))
        .anySatisfy(c -> {
          assertThat(c.title()).isEqualTo("Origin");
          assertThat(c.value().apply(row)).isEqualTo("collector");
        });
    var object = new ItemSource(ItemSource.OBJECT, new EntityRef(EntityKind.OBJECT, 2), "Synthetic object",
        null, null, true);
    assertThat(VisibleColumns.of(Columns.itemSources(), List.of(object), null))
        .anySatisfy(c -> {
          assertThat(c.title()).isEqualTo("Origin");
          assertThat(c.value().apply(object)).isEqualTo("Observed in game");
        });
  }
}
