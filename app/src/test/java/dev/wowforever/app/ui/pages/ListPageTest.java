package dev.wowforever.app.ui.pages;

import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.QUEST;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static dev.wowforever.app.ui.pages.Tables.column;
import static dev.wowforever.app.ui.pages.Tables.columnNames;
import static dev.wowforever.app.ui.pages.Tables.layOut;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.ItemFilter;
import dev.wowforever.app.model.ListFilter;
import dev.wowforever.app.model.NpcFilter;
import dev.wowforever.app.model.QuestFilter;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.widgets.ManualDelay;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class ListPageTest {
  private final Repositories repos = FixtureDb.repositories();
  private final ListOptions options = ListOptions.load(repos);
  private final List<ListFilter> filters = new ArrayList<>();
  private final List<ViewTarget> opened = new ArrayList<>();
  /** The debounce of the level spinners, elapsed by hand. */
  private final ManualDelay levels = new ManualDelay();

  private ListPage page(ViewTarget.List target) {
    return new ListPage(target, options, ListRows.load(target, repos), opened::add, filters::add, levels);
  }

  /** A page whose filter changes reload it at once, as the controller does (minus the worker thread). */
  private ListPage reloading(ViewTarget.List target) {
    ListPage[] page = new ListPage[1];
    page[0] = new ListPage(target, options, ListRows.load(target, repos), opened::add, f -> {
      filters.add(f);
      ViewTarget.List next = new ViewTarget.List(target.kind(), f);
      page[0].showRows(next, ListRows.load(next, repos));
    }, levels);
    return page[0];
  }

  private static <V> void select(JComboBox<FilterBar.Choice<V>> combo, V value) {
    for (int i = 0; i < combo.getItemCount(); i++) {
      if (Objects.equals(combo.getItemAt(i).value(), value)) {
        combo.setSelectedIndex(i);
        return;
      }
    }
    throw new AssertionError("No choice " + value + " in " + combo);
  }

  private static <V> V selected(JComboBox<FilterBar.Choice<V>> combo) {
    return combo.getItemAt(combo.getSelectedIndex()).value();
  }

  private static List<String> labels(JComboBox<? extends FilterBar.Choice<?>> combo) {
    List<String> out = new ArrayList<>();
    for (int i = 0; i < combo.getItemCount(); i++) {
      out.add(combo.getItemAt(i).label());
    }
    return out;
  }

  private static String text(JSpinner s) {
    return ((JSpinner.DefaultEditor) s.getEditor()).getTextField().getText();
  }

  @Test
  void filtersProduceTheExpectedQuery() {
    ItemFilter armor = new ItemFilter(4, null, 10, 20, null);
    assertThat(ListRows.load(new ViewTarget.List(ITEM, armor), repos)).isEqualTo(repos.items().list(armor));
    NpcFilter bosses = new NpcFilter(null, 3, null, null);
    assertThat(ListRows.load(new ViewTarget.List(NPC, bosses), repos)).isEqualTo(repos.npcs().list(bosses));
    QuestFilter westfall = new QuestFilter(40L, null, null);
    assertThat(ListRows.load(new ViewTarget.List(QUEST, westfall), repos)).isEqualTo(repos.quests().list(westfall));
    assertThat(ListRows.load(new ViewTarget.List(INSTANCE), repos)).isEqualTo(repos.instances().all());
  }

  @Test
  void theFilterBarShowsTheFilterItWasOpenedWith() {
    ListPage p = page(new ViewTarget.List(ITEM, new ItemFilter(4, 1, 10, 20, null)));
    FilterBar bar = p.filterBar();
    assertThat(selected(bar.classes())).isEqualTo(4);
    assertThat(selected(bar.subclasses())).isEqualTo(1);
    assertThat(bar.subclasses().isEnabled()).isTrue();
    assertThat(bar.minLevel().getValue()).isEqualTo(10);
    assertThat(bar.maxLevel().getValue()).isEqualTo(20);
    assertThat(bar.filter()).isEqualTo(new ItemFilter(4, 1, 10, 20, null));
    assertThat(filters).as("showing a filter is not a change").isEmpty();
    assertThat(column(p.results().table(), 0)).containsExactly("Crystalline Cuffs", "Subterranean Cape");
    assertThat(p.results().table().getColumnCount()).isEqualTo(6);
  }

  @Test
  void itemClassAndSubclassCombos() {
    ListPage p = page(new ViewTarget.List(ITEM));
    FilterBar bar = p.filterBar();
    assertThat(labels(bar.classes())).startsWith("All classes").contains("Armor", "Weapon");
    assertThat(labels(bar.subclasses())).containsExactly("All subclasses");
    assertThat(bar.subclasses().isEnabled()).isFalse();
    select(bar.classes(), 4);
    assertThat(filters).containsExactly(new ItemFilter(4, null, null, null, null));
    assertThat(labels(bar.subclasses())).startsWith("All subclasses").contains("Cloth");
    assertThat(bar.subclasses().isEnabled()).isTrue();
    select(bar.subclasses(), 1);
    assertThat(filters).last().isEqualTo(new ItemFilter(4, 1, null, null, null));
    select(bar.classes(), (Integer) null);
    assertThat(filters).last().isEqualTo(ItemFilter.ALL);
    assertThat(bar.subclasses().isEnabled()).isFalse();
    assertThat(filters).hasSize(3);
  }

  @Test
  void theQualityComboFiltersItemsByQuality() {
    ListPage p = reloading(new ViewTarget.List(ITEM));
    FilterBar bar = p.filterBar();
    assertThat(labels(bar.qualities()))
        .containsExactly("Any", "Poor", "Common", "Uncommon", "Rare", "Epic", "Legendary", "Artifact");
    assertThat(selected(bar.qualities())).isNull();
    select(bar.qualities(), 3);
    assertThat(filters).containsExactly(new ItemFilter(null, null, null, null, 3));
    assertThat(column(p.results().table(), 0)).isNotEmpty()
        .isEqualTo(repos.items().list(new ItemFilter(null, null, null, null, 3)).stream()
            .map(i -> i.displayName()).toList());
    select(bar.classes(), 4);
    assertThat(filters).last().as("the quality survives a class change")
        .isEqualTo(new ItemFilter(4, null, null, null, 3));
    assertThat(selected(page(new ViewTarget.List(ITEM, new ItemFilter(null, null, null, null, 4))).filterBar()
        .qualities())).isEqualTo(4);
  }

  @Test
  void levelSpinnersBoundTheLevelsAndShowAnyWhenUnbounded() {
    ListPage p = page(new ViewTarget.List(ITEM));
    FilterBar bar = p.filterBar();
    assertThat(text(bar.minLevel())).isEqualTo("Any");
    assertThat(text(bar.maxLevel())).isEqualTo("Any");
    bar.minLevel().setValue(10);
    levels.fire();
    assertThat(filters).last().isEqualTo(new ItemFilter(null, null, 10, null, null));
    assertThat(text(bar.minLevel())).isEqualTo("10");
    bar.maxLevel().setValue(20);
    levels.fire();
    assertThat(filters).last().isEqualTo(new ItemFilter(null, null, 10, 20, null));
    bar.minLevel().setValue(0);
    levels.fire();
    assertThat(filters).last().isEqualTo(new ItemFilter(null, null, null, 20, null));
  }

  @Test
  void levelTicksAreDebouncedButCombosAreNot() {
    FilterBar bar = page(new ViewTarget.List(NPC)).filterBar();
    for (int level = 1; level <= 5; level++) {
      bar.minLevel().setValue(level); // an arrow held down
    }
    assertThat(filters).isEmpty();
    assertThat(levels.pending()).as("each tick replaces the pending reload").isEqualTo(1);
    levels.fire();
    assertThat(filters).containsExactly(new NpcFilter(null, null, 5, null));
    select(bar.ranks(), 3);
    assertThat(filters).last().isEqualTo(new NpcFilter(null, 3, 5, null));
    bar.maxLevel().setValue(60);
    select(bar.ranks(), 1); // a combo change takes the pending level along at once
    assertThat(filters).last().isEqualTo(new NpcFilter(null, 1, 5, 60));
    levels.fire();
    assertThat(filters).hasSize(3);
  }

  @Test
  void aMinimumAboveTheMaximumShowsAHint() {
    FilterBar bar = page(new ViewTarget.List(ITEM)).filterBar();
    assertThat(bar.rangeHint().isVisible()).isFalse();
    assertThat(bar.rangeHint().getText()).isEqualTo("Minimum is above maximum");
    bar.maxLevel().setValue(20);
    bar.minLevel().setValue(30);
    assertThat(bar.rangeHint().isVisible()).as("shown at once, before the debounce").isTrue();
    bar.maxLevel().setValue(0);
    assertThat(bar.rangeHint().isVisible()).as("no maximum").isFalse();
    assertThat(page(new ViewTarget.List(QUEST, new QuestFilter(null, 30, 20))).filterBar().rangeHint().isVisible())
        .isTrue();
  }

  @Test
  void theEmptyNoteSpeaksOfFiltersOnlyWhereThereAreSome() {
    ListPage zones = new ListPage(new ViewTarget.List(ZONE), ListOptions.EMPTY, List.of(), opened::add,
        filters::add, levels);
    assertThat(zones.emptyNote().isVisible()).isTrue();
    assertThat(zones.emptyNote().getText()).isEqualTo("No entries.");
    ListPage instances = new ListPage(new ViewTarget.List(INSTANCE), ListOptions.EMPTY, List.of(), opened::add,
        filters::add, levels);
    assertThat(instances.emptyNote().getText()).isEqualTo("No entries.");
    assertThat(page(new ViewTarget.List(ITEM)).emptyNote().getText()).isEqualTo("Nothing matches these filters.");
  }

  @Test
  void typedLevelTextParses() throws Exception {
    JFormattedTextField field = ((JSpinner.DefaultEditor) page(new ViewTarget.List(ITEM)).filterBar().minLevel()
        .getEditor()).getTextField();
    assertThat(field.getFormatter().stringToValue("12")).isEqualTo(12);
    assertThat(field.getFormatter().stringToValue("any")).isEqualTo(0);
    assertThat(field.getFormatter().stringToValue(" ")).isEqualTo(0);
  }

  @Test
  void theFilterBarWrapsOnANarrowPage() {
    FilterBar bar = page(new ViewTarget.List(ITEM)).filterBar();
    int oneRow = bar.getPreferredSize().height;
    bar.getParent().setSize(220, 1_000);
    assertThat(bar.getPreferredSize().height).isGreaterThan(oneRow);
    assertThat(bar.getPreferredSize().width).isLessThanOrEqualTo(220);
    bar.getParent().setSize(5_000, 1_000);
    assertThat(bar.getPreferredSize().height).isEqualTo(oneRow);
  }

  @Test
  void theLaidOutFilterBarShrinksToANarrowedPageAndWraps() {
    ListPage p = page(new ViewTarget.List(ITEM));
    FilterBar bar = p.filterBar();
    p.setSize(900, 700);
    layOut(p);
    int wideHeight = bar.getHeight();
    assertThat(bar.getWidth()).isLessThanOrEqualTo(900).isGreaterThan(420);
    assertThat(p.header().getWidth()).isLessThanOrEqualTo(900).isGreaterThan(420);
    p.setSize(420, 700); // narrowed after being wide
    layOut(p);
    assertThat(bar.getWidth()).as("as wide as the page, not its one-row width").isLessThanOrEqualTo(420);
    assertThat(bar.getHeight()).as("wrapped onto more rows").isGreaterThan(wideHeight);
    assertThat(p.header().getWidth()).isLessThanOrEqualTo(420);
    assertThat(p.section().getWidth()).isLessThanOrEqualTo(420);
  }

  @Test
  void changingAFilterReloads() {
    ListPage p = reloading(new ViewTarget.List(ITEM));
    int all = p.results().rowCount();
    assertThat(all).isEqualTo(repos.items().list(ItemFilter.ALL).size());
    assertThat(p.section().heading().getText()).isEqualTo("Results (" + all + ")");
    select(p.filterBar().classes(), 4);
    assertThat(p.results().rowCount()).isLessThan(all).isPositive();
    assertThat(p.section().heading().getText()).isEqualTo("Results (" + p.results().rowCount() + ")");
    assertThat(p.target()).isEqualTo(new ViewTarget.List(ITEM, new ItemFilter(4, null, null, null, null)));
    assertThat(p.emptyNote().isVisible()).isFalse();
    p.filterBar().minLevel().setValue(90);
    levels.fire();
    assertThat(p.results().rowCount()).isZero();
    assertThat(p.section().heading().getText()).isEqualTo("Results (0)");
    assertThat(p.emptyNote().isVisible()).isTrue();
  }

  @Test
  void resultsAreSortable() {
    ListPage p = page(new ViewTarget.List(ITEM, new ItemFilter(null, null, 1, null, null)));
    var table = p.results().table();
    assertThat(table.getRowSorter()).isNotNull();
    int level = columnNames(table).indexOf("Item level");
    table.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(level, SortOrder.DESCENDING)));
    assertThat(column(table, level)).startsWith(80, 66, 65, 60, 24);
    assertThat(column(table, 0)).first().isEqualTo("Thunderfury, Blessed Blade of the Windseeker");
  }

  @Test
  void aSingleClickOnANameOpensTheEntity() {
    ListPage p = page(new ViewTarget.List(NPC, new NpcFilter(null, 3, null, null)));
    p.results().activateRow(0);
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(NPC, 639)));
  }

  @Test
  void npcFilterBarHasZoneRankAndLevels() {
    ListPage p = page(new ViewTarget.List(NPC));
    FilterBar bar = p.filterBar();
    assertThat(bar.classes()).isNull();
    assertThat(labels(bar.zones())).first().isEqualTo("All zones");
    List<String> zones = labels(bar.zones()).subList(1, bar.zones().getItemCount());
    assertThat(zones).contains("Elwynn Forest", "Ragefire Chasm").isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    assertThat(labels(bar.ranks())).containsExactly("All ranks", "Normal", "Elite", "Rare", "Rare Elite", "Boss");
    select(bar.ranks(), 3);
    assertThat(filters).last().isEqualTo(new NpcFilter(null, 3, null, null));
    select(bar.zones(), 2717L);
    assertThat(filters).last().isEqualTo(new NpcFilter(2717L, 3, null, null));
    bar.maxLevel().setValue(63);
    levels.fire();
    assertThat(filters).last().isEqualTo(new NpcFilter(2717L, 3, null, 63));
    assertThat(column(p.results().table(), 0)).hasSize(15);
    assertThat(columnNames(p.results().table())).containsExactly("Name", "Title", "Level", "Rank", "Friendly to", "Zone");
  }

  @Test
  void questFilterBarHasZoneAndLevels() {
    ListPage p = page(new ViewTarget.List(QUEST, new QuestFilter(40L, null, null)));
    FilterBar bar = p.filterBar();
    assertThat(bar.ranks()).isNull();
    assertThat(selected(bar.zones())).isEqualTo(40L);
    assertThat(column(p.results().table(), 0)).containsExactly("Captain Sander's Hidden Treasure");
    bar.minLevel().setValue(5);
    levels.fire();
    assertThat(filters).containsExactly(new QuestFilter(40L, 5, null));
  }

  @Test
  void zonesAreListedByNameWithKindAndParent() {
    ListPage p = page(new ViewTarget.List(ZONE));
    assertThat(p.filterBar()).isNull();
    var table = p.results().table();
    assertThat(columnNames(table)).containsExactly("Name", "Kind", "Parent");
    assertThat(column(table, 0)).hasSize(repos.zones().all().size())
        .isSortedAccordingTo((a, b) -> String.CASE_INSENSITIVE_ORDER.compare((String) a, (String) b));
    int row = column(table, 0).indexOf("The Deadmines");
    assertThat(table.getValueAt(row, 1)).isEqualTo("dungeon");
    assertThat(table.getValueAt(row, 2)).isEqualTo("Westfall");
    assertThat(p.results().model().refAt(table.convertRowIndexToModel(row), 2))
        .contains(new EntityRef(ZONE, 40));
    assertThat(p.header().title()).isEqualTo("Zones");
  }

  @Test
  void instancesAreGroupedByLevelBand() {
    ListPage p = page(new ViewTarget.List(INSTANCE));
    assertThat(p.filterBar()).isNull();
    assertThat(p.results()).isNull();
    assertThat(p.sections()).extracting(s -> s.heading().getText())
        .containsExactly("Levels 1–20 (2)", "Level unknown (1)");
    SourceSection low = p.sections().get(0);
    assertThat(column(low.table().table(), 0)).containsExactly("Ragefire Chasm", "The Deadmines");
    assertThat(p.header().title()).isEqualTo("Dungeons & Raids");
  }

  @Test
  void levelBandsByLowestLevel() {
    List<InstanceSummary> instances = List.of(inst(1, 60), inst(2, 41), inst(3, 59), inst(4, 20), inst(5, 1),
        inst(6, 21), inst(7, 40), inst(8, null), inst(9, 0), inst(10, 62));
    assertThat(LevelBands.of(instances)).extracting(LevelBands.Band::title)
        .containsExactly("Levels 1–20", "Levels 21–40", "Levels 41–59", "Level 60", "Level unknown");
    assertThat(LevelBands.of(instances)).extracting(b -> b.instances().stream().map(InstanceSummary::id).toList())
        .containsExactly(List.of(5L, 4L), List.of(6L, 7L), List.of(2L, 3L), List.of(1L, 10L), List.of(8L, 9L));
    assertThat(LevelBands.of(List.of(inst(1, 30)))).extracting(LevelBands.Band::title)
        .containsExactly("Levels 21–40");
  }

  private static InstanceSummary inst(long id, Integer levelMin) {
    return new InstanceSummary(id, "Instance " + id, "DUNGEON", levelMin, null, null, null);
  }
}
