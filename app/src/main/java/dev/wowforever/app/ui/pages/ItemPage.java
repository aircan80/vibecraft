package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ItemDetail;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.DropRow;
import dev.wowforever.app.ui.Labels;
import dev.wowforever.app.ui.LootListRow;
import dev.wowforever.app.ui.detail.HeaderPanel;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.widgets.DividerRows;
import dev.wowforever.app.ui.widgets.OriginChip;
import dev.wowforever.app.ui.widgets.RateBarRenderer;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

/**
 * The item page, one scrolling page: the header, a one-line summary of the sources ("3 NPCs · 2 loot lists · no
 * vendor · 1 quest"), then a section per kind of source the item has, in the order Dropped by, In instance loot,
 * Contained in, Sold by, Quest reward, Related quests (kinds without rows are left out). Drops are ranked by rate,
 * the unknown rates last under a divider. Ctrl+1 to Ctrl+6 jump to the first six sections ({@link SourcesPage}).
 */
public final class ItemPage extends SourcesPage {
  private final HeaderPanel header;
  private final JLabel summary;
  private final LinkTable<DropRow> droppedBy;

  public ItemPage(ItemDetail d, Navigator nav) {
    super(nav);
    header = ItemHeader.of(d, nav);
    summary = PageContent.mutedLabel(ItemSummaryLine.of(d));
    summary.setBorder(BorderFactory.createEmptyBorder(0, 12, 4, 12));
    content.add(header);
    content.add(summary);
    droppedBy = addSection("Dropped by", d.droppedBy().size(), Columns.dropSourcesRanked(),
        DropRow.ranked(d.droppedBy()));
    if (droppedBy != null) {
      RateBarRenderer.install(column(droppedBy, "Rate"));
      OriginChip.install(column(droppedBy, "Origin"));
      DividerRows.install(droppedBy, DropRow.Divider.class::isInstance);
    }
    LinkTable<LootListRow> loot = addSection("In instance loot", d.inInstanceLoot().size(), Columns.lootLists(),
        LootListRow.of(d.inInstanceLoot()));
    if (loot != null) {
      RateBarRenderer.install(column(loot, "Rate"));
    }
    List<ItemSource> containedIn = Stream.concat(d.containedInObjects().stream(), d.containedInItems().stream())
        .toList();
    addSection("Contained in", containedIn.size(), Columns.itemSources(), containedIn);
    addSection("Sold by", d.soldBy().size(), Columns.sourceNames(), d.soldBy());
    addSection("Quest reward", d.rewardFrom().size(), Columns.sourceNames(), d.rewardFrom());
    addSection("Related quests", d.relatedQuests().size(), Columns.sourceNames(), d.relatedQuests());
    bindJumpKeys();
  }

  private static TableColumn column(LinkTable<?> table, String title) {
    TableColumnModel columns = table.table().getColumnModel();
    return columns.getColumn(columns.getColumnIndex(title));
  }

  public HeaderPanel header() {
    return header;
  }

  /** The summary line as read. */
  public String summary() {
    return summary.getText();
  }

  JLabel summaryLabel() {
    return summary;
  }

  /** The ranked drop table; null when no NPC drops the item. */
  public LinkTable<DropRow> droppedBy() {
    return droppedBy;
  }

  /**
   * The header: name, then "Item #id" and {@link Labels#itemSubtitle} ("Cloth · iLvl 18 · Req 13", no item or
   * required level of 1 or below), then a starts-quest link.
   */
  private static final class ItemHeader {
    static HeaderPanel of(ItemDetail d, Navigator nav) {
      ItemSummary s = d.summary();
      HeaderPanel header = new HeaderPanel(s.displayName(), Labels.join("Item #" + s.id(),
          Labels.itemSubtitle(s.className(), s.subclassName(), s.itemLevel(), s.requiredLevel())));
      header.setAlignmentX(LEFT_ALIGNMENT);
      if (d.startQuestId() != null) {
        EntityRef quest = new EntityRef(EntityKind.QUEST, d.startQuestId());
        header.addLink("Starts quest: " + questName(d, quest), quest, nav);
      }
      return header;
    }

    /** The name of the quest the item starts, from the item's quest lists; "Unknown quest #id" otherwise. */
    private static String questName(ItemDetail d, EntityRef quest) {
      return Stream.concat(d.relatedQuests().stream(), d.rewardFrom().stream())
          .filter(q -> q.ref().equals(quest) && q.name() != null).findFirst()
          .map(ItemSource::name).orElse(quest.displayName(null));
    }
  }
}
