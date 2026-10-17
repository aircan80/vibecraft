package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.NpcDetail;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.Labels;
import dev.wowforever.app.ui.detail.HeaderPanel;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.widgets.ClipboardPort;
import dev.wowforever.app.ui.widgets.RankChip;
import dev.wowforever.app.ui.widgets.SourceSection;

/**
 * The NPC page, one scrolling page: the header (name and title, a rank chip, levels and a zone link), then the
 * sections Drops, Sells, Starts quests, Ends quests and Spawns (those without rows are left out). Drops are
 * grouped by {@link ItemCategories} under heading rows, each group ranked by rate with unknown rates last.
 * Columns that say nothing for any row are left out ({@link VisibleColumns}).
 */
public final class NpcPage extends SourcesPage {
  private final HeaderPanel header;
  private final RankChip rankChip;
  private final LinkTable<GroupedRows.Row<LootRow>> drops;
  private final SpawnSection spawns;

  public NpcPage(NpcDetail d, Navigator nav, ClipboardPort clipboard) {
    super(nav);
    NpcSummary s = d.summary();
    header = zoneHeader(title(s), Labels.join("NPC #" + s.id(), Labels.levels(s.minLevel(), s.maxLevel())),
        s.zoneId(), s.zoneName());
    rankChip = new RankChip(s.rank());
    header.addBadge(rankChip);
    content.add(header);
    if (d.drops().isEmpty()) {
      drops = null;
    } else {
      drops = LootTables.grouped(d.drops(), l -> ItemCategories.of(l.className()), ItemCategories.ORDER,
          LootTables.BY_RATE, nav);
      addSection(new SourceSection("Drops", d.drops().size(), drops));
    }
    addLootSection("Sells", d.sells());
    addSection("Starts quests", d.starts().size(), VisibleColumns.of(Columns.quests(), d.starts(), s.zoneName()),
        d.starts());
    addSection("Ends quests", d.ends().size(), VisibleColumns.of(Columns.quests(), d.ends(), s.zoneName()),
        d.ends());
    if (d.spawns().isEmpty()) {
      spawns = null;
    } else {
      spawns = new SpawnSection(d.spawns(), s.zoneName(), nav, clipboard);
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

  /** The spawns section; null when the NPC has no spawn points. */
  SpawnSection spawns() {
    return spawns;
  }
}
