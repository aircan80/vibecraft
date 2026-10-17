package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.NpcDetail;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.model.RateSource;
import dev.wowforever.app.model.SourceFilter;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.Labels;
import dev.wowforever.app.ui.detail.HeaderPanel;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.widgets.ClipboardPort;
import dev.wowforever.app.ui.widgets.MapView;
import dev.wowforever.app.ui.widgets.RankChip;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/**
 * The NPC page, one scrolling page: the header (name and title, a rank chip, levels and a zone link), then the
 * sections Drops, Sells, Starts quests, Ends quests and Spawns (those without rows are left out). Drops are
 * grouped by {@link ItemCategories} under heading rows, each group ranked by rate with unknown rates last.
 * Columns that say nothing for any row are left out ({@link VisibleColumns}). Given the decoded map of a spawn
 * zone, the Spawns section shows it above its table with the NPC's spawn points there.
 *
 * <p>The drop source filter ({@link SourceFilter}) applies to the drops: the heading counts the drops shown and a
 * note under the section says how many it hid. A rate whose sources were loaded with the page lists them in its
 * tooltip.
 */
public final class NpcPage extends SourcesPage {
  private final HeaderPanel header;
  private final RankChip rankChip;
  private final LinkTable<GroupedRows.Row<LootRow>> drops;
  private final SpawnSection spawns;

  public NpcPage(NpcDetail d, Navigator nav, ClipboardPort clipboard) {
    this(d, null, null, nav, clipboard);
  }

  /** The page with {@code map}, the decoded map of zone {@code mapZoneId} (both null: none), in its Spawns section. */
  public NpcPage(NpcDetail d, Long mapZoneId, BufferedImage map, Navigator nav, ClipboardPort clipboard) {
    this(d, mapZoneId, map, Map.of(), SourceFilter.ALL, nav, clipboard);
  }

  /**
   * The page with a spawn map (as above) whose drops {@code filter} filters; {@code rateSources} are the drop rate
   * sources of its drops, loaded off the EDT ({@link dev.wowforever.app.ui.detail.DetailPanels#load}), which the
   * rate tooltips list.
   */
  public NpcPage(NpcDetail d, Long mapZoneId, BufferedImage map, Map<RateSource.Key, List<RateSource>> rateSources,
      SourceFilter filter, Navigator nav, ClipboardPort clipboard) {
    super(nav);
    NpcSummary s = d.summary();
    header = zoneHeader(title(s), Labels.join("NPC #" + s.id(), Labels.levels(s.minLevel(), s.maxLevel())),
        s.zoneId(), s.zoneName());
    rankChip = new RankChip(s.rank());
    header.addBadge(rankChip);
    content.add(header);
    SourceFilters.Filtered<LootRow> shown = SourceFilters.apply(d.drops(), LootRow::origin, filter);
    if (d.drops().isEmpty()) {
      drops = null;
    } else {
      drops = LootTables.grouped(shown.rows(), l -> ItemCategories.of(l.className()), ItemCategories.ORDER,
          LootTables.BY_RATE, LootTables.sourcesOf(s.id(), rateSources), nav);
      addFilteredSection("Drops", shown, drops);
    }
    addLootSection("Sells", d.sells());
    addSection("Starts quests", d.starts().size(), VisibleColumns.of(Columns.quests(), d.starts(), s.zoneName()),
        d.starts());
    addSection("Ends quests", d.ends().size(), VisibleColumns.of(Columns.quests(), d.ends(), s.zoneName()),
        d.ends());
    if (d.spawns().isEmpty()) {
      spawns = null;
    } else {
      spawns = new SpawnSection(d.spawns(), s.zoneName(), s.ref(), s.name(), mapZoneId, map, nav, clipboard);
      addSection(spawns.section());
    }
    bindJumpKeys();
  }

  /** {@code "Name <Title>"}, or the name alone. */
  private static String title(NpcSummary s) {
    return s.subName() == null || s.subName().isBlank()
        ? s.displayName() : s.displayName() + " <" + s.subName() + ">";
  }

  public HeaderPanel header() {
    return header;
  }

  public RankChip rankChip() {
    return rankChip;
  }

  /** The grouped drop table; null when the NPC drops nothing. */
  public LinkTable<GroupedRows.Row<LootRow>> drops() {
    return drops;
  }

  /** The map of the Spawns section; null without one. */
  public MapView spawnMap() {
    return spawns == null ? null : spawns.map();
  }

  /** The spawns section; null when the NPC has no spawn points. */
  SpawnSection spawns() {
    return spawns;
  }
}
