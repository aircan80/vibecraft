package dev.wowforever.app.ui.detail;

import static dev.wowforever.app.model.EntityKind.QUEST;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ItemDetail;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.nav.ViewTarget;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class ItemDetailPanelTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void tabsCarryCounts() {
    var p = new ItemDetailPanel(repos.items().detail(14149).get(), t -> {});
    assertThat(p.tabs().getTitleAt(0)).startsWith("Dropped by (").doesNotStartWith("Dropped by (0");
  }

  @Test
  void unknownNpcRendersPlaceholder() {
    var p = new ItemDetailPanel(repos.items().detail(190179).get(), t -> {});
    assertThat(p.droppedBy().table().getValueAt(0, 0)).isEqualTo("Unknown NPC #185333");
  }

  @Test
  void dropPercentFormatted() {
    var p = new ItemDetailPanel(repos.items().detail(2874).get(), t -> {});
    assertThat(p.droppedBy().table().getValueAt(0, 3)).isEqualTo("60.00 %");
  }

  @Test
  void everyTabIsPresentAndEmptyOnesAreDisabled() {
    var p = new ItemDetailPanel(repos.items().detail(14149).get(), t -> {});
    assertThat(p.tabs().getTabCount()).isEqualTo(5);
    List<String> prefixes = List.of("Dropped by (", "Contained in (", "Sold by (", "Reward from (",
        "Related quests (");
    for (int i = 0; i < 5; i++) {
      String title = p.tabs().getTitleAt(i);
      assertThat(title).startsWith(prefixes.get(i));
      assertThat(p.tabs().isEnabledAt(i)).as(title).isEqualTo(!title.endsWith("(0)"));
    }
  }

  @Test
  void headerNamesClassSubclassAndLevels() {
    ItemDetail d = repos.items().detail(14149).get();
    var p = new ItemDetailPanel(d, t -> {});
    assertThat(p.header().title()).isEqualTo("Subterranean Cape");
    assertThat(p.header().subtitle()).isEqualTo("Item #14149 · Armor · Cloth · iLvl 18 · Requires 13");
  }

  @Test
  void startsQuestLinkOpensTheQuest() {
    EntityRef quest = new EntityRef(QUEST, 373);
    ItemDetail d = new ItemDetail(new ItemSummary(2874, "An Unsent Letter", 1, null, "Quest", "Quest", 1), 12, 0,
        373L, List.of(), List.of(), List.of(), List.of(), List.of(),
        List.of(new ItemSource(ItemSource.RELATED, quest, "The Unsent Letter")));
    List<ViewTarget> opened = new ArrayList<>();
    var p = new ItemDetailPanel(d, opened::add);
    assertThat(p.header().subtitle()).isEqualTo("Item #2874 · Quest · Quest · iLvl 1 · Starts quest: The Unsent Letter");
    p.header().links().get(0).doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(quest));
  }
}
