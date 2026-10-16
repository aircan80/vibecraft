package dev.wowforever.app.ui.pages;

import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.ui.pages.Tables.column;
import static dev.wowforever.app.ui.pages.Tables.columnNames;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.QuestDetail;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class QuestPageTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void headerObjectivesAndGiversSplitByRole() {
    List<ViewTarget> opened = new ArrayList<>();
    var p = new QuestPage(repos.quests().detail(2).get(), opened::add);
    assertThat(p.header().title()).isEqualTo("Sharptalon's Claw");
    assertThat(p.header().subtitle()).isEqualTo("Quest #2 · Level 30 · Requires 20 · Ashenvale");
    assertThat(p.objectives().getText()).startsWith("Bring Sharptalon's Claw to Senani Thunderheart");
    assertThat(p.objectives().isEditable()).isFalse();
    assertThat(p.objectives().getLineWrap()).isTrue();
    assertThat(p.sections()).extracting(SourceSection::title).containsExactly("Given by", "Turned in to");
    assertThat(p.section("Given by").table().model().refAt(0, 0)).contains(new EntityRef(ITEM, 16305));
    // each table holds one role: the Role column says nothing
    assertThat(columnNames(p.section("Given by").table().table())).containsExactly("Name", "Type");
    assertThat(columnNames(p.section("Turned in to").table().table())).containsExactly("Name", "Type");
    p.section("Turned in to").table().activateRow(0);
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(NPC, 12696)));
  }

  @Test
  void rewardsAndRelatedItemsAndNoObjectives() {
    EntityRef claw = new EntityRef(ITEM, 16305);
    var d = new QuestDetail(new QuestSummary(9, "Q", null, null, null), null, List.of(),
        List.of(new LootRow(new EntityRef(ITEM, 1), "Sword", 20, null, null, null, 2, "Weapon", 7)),
        List.of(new ItemSource(ItemSource.RELATED, claw, "Sharptalon's Claw")));
    var p = new QuestPage(d, t -> {});
    assertThat(p.header().subtitle()).isEqualTo("Quest #9");
    assertThat(p.objectives().isVisible()).isFalse();
    assertThat(p.sections()).extracting(SourceSection::title).containsExactly("Rewards", "Related items");
    assertThat(columnNames(p.section("Rewards").table().table())).containsExactly("Item", "Item level");
    assertThat(column(p.section("Related items").table().table(), 0)).containsExactly("Sharptalon's Claw");
  }
}
