package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.NpcDetail;
import dev.wowforever.app.model.Route;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.model.QuestInvolvement;
import dev.wowforever.app.model.RateSource;
import dev.wowforever.app.model.SourceFilter;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.Labels;
import dev.wowforever.app.ui.detail.HeaderPanel;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.widgets.Chip;
import dev.wowforever.app.ui.widgets.ClipboardPort;
import dev.wowforever.app.ui.widgets.MapView;
import dev.wowforever.app.ui.widgets.RailTarget;
import dev.wowforever.app.ui.widgets.SourceFilterControl;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The NPC page, one scrolling page: the header (an NPCs plate, name and title, a rank chip, levels and a zone link),
 * then Drops, Sells, Quests and Location in {@link SectionOrder#NPC} order
 * (those without rows are left out). Quest roles share one row per quest. Drops are
 * grouped by {@link ItemCategories} under heading rows, each group ranked by rate with unknown rates last: Notable
 * first (epics and better, and quest items: what the NPC is looked up for; each keeps its quality colour and says what
 * it is, "Quest item" or "Gear", in a Type column), then by item class; the world drops (the
 * generic pool many NPCs drop, at an unknown or low rate) come last in a collapsed group, and recipes show their
 * skill.
 * Columns that say nothing for any row are left out ({@link VisibleColumns}). Given the decoded map of a spawn
 * zone, the Location section shows it above its table with the NPC's spawn points there.
 *
 * <p>The drop source filter ({@link SourceFilter}), chosen with the control beside the "Drops" heading
 * ({@link SourceFilterControl}, whose tooltip is the rate marks' legend), applies to the drops: the heading counts the
 * drops shown and a note under the section says how many it hid. A rate whose sources were loaded with the page lists
 * them in its tooltip.
 */
public final class NpcPage extends SourcesPage {
  /** The title of the drops section. */
  public static final String DROPS = "Drops";
  private final HeaderPanel header;
  private final Chip rankChip;
  private final LinkTable<GroupedRows.Row<LootRow>> drops;
  private final SpawnSection spawns;

  public NpcPage(NpcDetail d, Navigator nav, ClipboardPort clipboard) {
    this(d, null, null, nav, clipboard);
  }

  /** The page with {@code map}, the decoded map of zone {@code mapZoneId} (both null: none), in its Location section. */
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
    this(d, mapZoneId, map, rateSources, filter, f -> { }, nav, clipboard);
  }

  /**
   * As above, the "Drops" heading's filter control ({@link SourceFilterControl}) handing a new filter to
   * {@code onFilterChange}, which saves it and shows the page again.
   */
  public NpcPage(NpcDetail d, Long mapZoneId, BufferedImage map, Map<RateSource.Key, List<RateSource>> rateSources,
      SourceFilter filter, Consumer<SourceFilter> onFilterChange, Navigator nav, ClipboardPort clipboard) {
    this(d, mapZoneId, map, rateSources, Set.of(), filter, onFilterChange, nav, clipboard);
  }

  /**
   * As above; the NPC is a dungeon or raid boss when {@code bossNpcs} holds its id (loaded off the EDT with the rate
   * sources), so its VMaNGOS rates' tooltips add {@value RateSource#BOSS_NOTE}.
   */
  public NpcPage(NpcDetail d, Long mapZoneId, BufferedImage map, Map<RateSource.Key, List<RateSource>> rateSources,
      Set<Long> bossNpcs, SourceFilter filter, Consumer<SourceFilter> onFilterChange, Navigator nav,
      ClipboardPort clipboard) {
    this(d, mapZoneId, map, rateSources, bossNpcs, filter, onFilterChange,
        QuestInvolvement.fromGivers(d.starts(), d.ends()), nav, clipboard, List.of());
  }

  /** The page including its NPC's own patrol routes, loaded off the EDT. */
  public NpcPage(NpcDetail d, Long mapZoneId, BufferedImage map, Map<RateSource.Key, List<RateSource>> rateSources,
      Set<Long> bossNpcs, SourceFilter filter, Consumer<SourceFilter> onFilterChange, Navigator nav,
      ClipboardPort clipboard, List<Route> routes) {
    this(d, mapZoneId, map, rateSources, bossNpcs, filter, onFilterChange,
        QuestInvolvement.fromGivers(d.starts(), d.ends()), nav, clipboard, routes);
  }

  /** All quest roles loaded off the EDT with this NPC. */
  public NpcPage(NpcDetail d, Long mapZoneId, BufferedImage map, Map<RateSource.Key, List<RateSource>> rateSources,
      Set<Long> bossNpcs, SourceFilter filter, Consumer<SourceFilter> onFilterChange,
      QuestInvolvement.Result involvement, Navigator nav, ClipboardPort clipboard) {
    this(d, mapZoneId, map, rateSources, bossNpcs, filter, onFilterChange, involvement, nav, clipboard, List.of());
  }

  /** Fully preloaded NPC page, including both quest roles and patrol routes. */
  public NpcPage(NpcDetail d, Long mapZoneId, BufferedImage map, Map<RateSource.Key, List<RateSource>> rateSources,
      Set<Long> bossNpcs, SourceFilter filter, Consumer<SourceFilter> onFilterChange,
      QuestInvolvement.Result involvement, Navigator nav, ClipboardPort clipboard, List<Route> routes) {
    super(nav);
    NpcSummary s = d.summary();
    header = zoneHeader(title(s), Labels.join("NPC #" + s.id(), Labels.levels(s.minLevel(), s.maxLevel())),
        s.zoneId(), s.zoneName());
    header.setPlate(plateIcon(RailTarget.NPCS));
    header.setKindWord(EntityKind.NPC.label());
    rankChip = Chip.rank(s.rank()).orElse(null);
    if (rankChip != null) {
      header.addBadge(rankChip);
    }
    content.add(header);
    SourceFilters.Filtered<LootRow> shown = SourceFilters.apply(d.drops(), LootRow::displayOrigin, filter);
    if (d.drops().isEmpty()) {
      drops = null;
    } else {
      drops = LootTables.grouped(shown.rows(), new LootTables.Grouping(ItemCategories::ofNpcDrop,
          ItemCategories.DROP_ORDER, LootTables.BY_RATE, Set.of(ItemCategories.WORLD_DROPS), Set.of(),
          ItemCategories::notableType), LootTables.sourcesOf(s.id(), rateSources), bossNpcs.contains(s.id()), nav);
      addFilteredSection(DROPS, shown, drops);
      section(DROPS).heading().setTrailing(new SourceFilterControl(filter, onFilterChange));
    }
    addLootSection("Sells", d.sells());
    addQuestInvolvements(involvement, EntityKind.NPC, s.zoneName());
    if (d.spawns().isEmpty()) {
      spawns = null;
    } else {
      spawns = new SpawnSection(d.spawns(), s.zoneName(), s.ref(), s.name(), mapZoneId, map, nav, clipboard, routes);
      addSection(spawns.section());
    }
    addSectionsInOrder(SectionOrder.NPC);
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

  /** The rank chip beside the name; null for a normal NPC or an unknown rank. */
  public Chip rankChip() {
    return rankChip;
  }

  /** The grouped drop table; null when the NPC drops nothing. */
  public LinkTable<GroupedRows.Row<LootRow>> drops() {
    return drops;
  }

  /** The map of the Location section; null without one. */
  public MapView spawnMap() {
    return spawns == null ? null : spawns.map();
  }

  /** The spawns section; null when the NPC has no spawn points. */
  SpawnSection spawns() {
    return spawns;
  }
}
