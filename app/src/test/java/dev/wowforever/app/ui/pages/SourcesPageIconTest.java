package dev.wowforever.app.ui.pages;

import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.OBJECT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.NpcDetail;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.model.QuestGiver;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.model.Spawn;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.widgets.RailIcons;
import dev.wowforever.app.ui.widgets.RailTarget;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** The 16 px rail icons on the section headings of the entity pages and list pages. */
class SourcesPageIconTest {
  private static final NpcSummary HOGGER = new NpcSummary(448, "Hogger", null, 11, 11, 1, "Elwynn Forest", 12L);

  @Test
  void questSectionsCarryTheQuestIcon() {
    withArt(() -> {
      QuestSummary quest = new QuestSummary(176, "Wanted:  \"Hogger\"", 11, 5, "Elwynn Forest");
      NpcPage page = new NpcPage(new NpcDetail(HOGGER, 0, null, List.of(), List.of(), List.of(quest), List.of(quest),
          List.of()), t -> {}, text -> {});
      // the NPC page's "Quests" section arrives in Task 14; until then its "Starts quests" and "Ends quests"
      assertThat(icon(page.section("Starts quests"))).isEqualTo(RailIcons.shared().small(RailTarget.QUESTS, 16));
      assertThat(icon(page.section("Ends quests"))).isEqualTo(RailIcons.shared().small(RailTarget.QUESTS, 16));
      return null;
    });
  }

  @Test
  void itemSectionsCarryTheItemIconAndSpawnsNone() {
    withArt(() -> {
      LootRow claw = new LootRow(new EntityRef(ITEM, 1931), "Huge Gnoll Claw", 10, 84.0, "both", null, null, "Quest",
          null);
      NpcPage page = new NpcPage(new NpcDetail(HOGGER, 0, null, List.of(claw), List.of(claw), List.of(), List.of(),
          List.of(new Spawn(40, "Westfall", 24.6, 74.7))), t -> {}, text -> {});
      assertThat(icon(page.section("Drops"))).isEqualTo(RailIcons.shared().small(RailTarget.ITEMS, 16));
      assertThat(icon(page.section("Sells"))).isEqualTo(RailIcons.shared().small(RailTarget.ITEMS, 16));
      // spawn points are places, not entities: their Zone column comes and goes with the page's zone
      assertThat(icon(page.section("Location"))).isNull();
      return null;
    });
  }

  @Test
  void sectionsMixingKindsOrWithoutRowsHaveNoIcon() {
    LinkTable<QuestGiver> givers = LinkTable.inline(Columns.questGivers(), t -> {});
    givers.setRows(List.of(new QuestGiver(new EntityRef(NPC, 240), "Marshal Dughan", "end"),
        new QuestGiver(new EntityRef(OBJECT, 68), "Wanted Poster", "start")));
    assertThat(SourcesPage.railTarget(givers)).isEmpty();
    givers.setRows(List.of(new QuestGiver(new EntityRef(NPC, 240), "Marshal Dughan", "end")));
    assertThat(SourcesPage.railTarget(givers)).contains(RailTarget.NPCS);
    givers.setRows(List.of(new QuestGiver(new EntityRef(OBJECT, 68), "Wanted Poster", "start")));
    assertThat(SourcesPage.railTarget(givers)).as("objects have no rail icon").isEmpty();
    givers.setRows(List.of());
    assertThat(SourcesPage.railTarget(givers)).isEmpty();
  }

  @Test
  void listPagesCarryTheirKindsIconEvenWhenEmpty() {
    withArt(() -> {
      for (EntityKind kind : List.of(EntityKind.ZONE, EntityKind.QUEST)) {
        ListPage page = new ListPage(new ViewTarget.List(kind), ListOptions.EMPTY, List.of(), t -> {}, f -> {});
        assertThat(icon(page.section(ListPage.RESULTS))).as(kind.label())
            .isEqualTo(RailIcons.shared().small(RailTarget.of(kind).orElseThrow(), 16));
      }
      return null;
    });
  }

  @Test
  void withoutClientArtTheHeadingsShowTheSvgIcons() {
    RailIcons before = RailIcons.install(RailIcons.none());
    try {
      QuestSummary quest = new QuestSummary(176, "Wanted:  \"Hogger\"", 11, 5, "Elwynn Forest");
      NpcPage page = new NpcPage(new NpcDetail(HOGGER, 0, null, List.of(), List.of(), List.of(quest), List.of(),
          List.of()), t -> {}, text -> {});
      assertThat(icon(page.section("Starts quests"))).isNotNull();
      assertThat(icon(page.section("Starts quests")).getIconWidth()).isEqualTo(16);
    } finally {
      RailIcons.install(before);
    }
  }

  private static javax.swing.Icon icon(SourceSection section) {
    return section.heading().label().getIcon();
  }

  /** Runs {@code test} with client art installed for every rail target, then restores the shared icons. */
  private static void withArt(Supplier<Void> test) {
    Map<RailTarget, BufferedImage> art = new EnumMap<>(RailTarget.class);
    for (RailTarget t : RailTarget.values()) {
      art.put(t, new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB));
    }
    RailIcons before = RailIcons.install(new RailIcons(art, art));
    try {
      test.get();
    } finally {
      RailIcons.install(before);
    }
  }
}
