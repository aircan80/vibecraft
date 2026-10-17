package dev.wowforever.app.ui.pages;

import com.formdev.flatlaf.FlatLaf;
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
import dev.wowforever.app.ui.theme.QualityColors;
import dev.wowforever.app.ui.widgets.DividerRows;
import dev.wowforever.app.ui.widgets.IconCache;
import dev.wowforever.app.ui.widgets.ItemCardPanel;
import dev.wowforever.app.ui.widgets.OriginChip;
import dev.wowforever.app.ui.widgets.RateBarRenderer;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

/**
 * The item page, one scrolling page: an item with client data opens with its tooltip card ({@link ItemCardPanel})
 * over a facts line ("Item #id", class and levels, a starts-quest link), an item without it with the header (name
 * and that line); then a one-line summary of the sources ("3 NPCs · 2 loot lists · no
 * vendor · 1 quest"), then a section per kind of source the item has, in the order Dropped by, In instance loot,
 * Contained in, Sold by, Quest reward, Related quests (kinds without rows are left out). Drops are ranked by rate,
 * the unknown rates last under a divider. Ctrl+1 to Ctrl+6 jump to the first six sections ({@link SourcesPage}).
 */
public final class ItemPage extends SourcesPage {
  private final ItemCardPanel card;
  private final HeaderPanel header;
  private final JLabel summary;
  private final LinkTable<DropRow> droppedBy;

  /** The page of {@code d} without its icon (the card shows an empty one). */
  public ItemPage(ItemDetail d, Navigator nav) {
    this(d, null, nav);
  }

  /**
   * The page of {@code d}; {@code icon} is the item's icon (or the question mark) as
   * {@link dev.wowforever.app.ui.detail.DetailPanels#load} decoded it off the EDT, null for none.
   */
  public ItemPage(ItemDetail d, BufferedImage icon, Navigator nav) {
    super(nav);
    header = ItemHeader.of(d, nav);
    summary = PageContent.mutedLabel(ItemSummaryLine.of(d));
    summary.setBorder(BorderFactory.createEmptyBorder(0, 12, 4, 12));
    if (d.card().lines().isEmpty()) {
      card = null;
    } else {
      card = new ItemCardPanel(d.card(), d.summary().displayName(), icon == null ? IconCache.empty(IconCache.CARD)
          : new ImageIcon(IconCache.scale(icon, IconCache.CARD)));
      content.add(new CardHolder(card));
      header.setTitleShown(false); // the card has the name
      header.setBorder(BorderFactory.createEmptyBorder(2, 12, 6, 12));
    }
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

  /** The tooltip card; null for an item without client data. */
  public ItemCardPanel card() {
    return card;
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
   * Holds the card inside the page's margins: as wide as the card wants, at most the page width, and as tall as the
   * card is at that width.
   */
  private static final class CardHolder extends JPanel {
    private final ItemCardPanel card;

    CardHolder(ItemCardPanel card) {
      super(null);
      this.card = card;
      setOpaque(false);
      setBorder(BorderFactory.createEmptyBorder(10, 12, 2, 12));
      setAlignmentX(LEFT_ALIGNMENT);
      add(card);
    }

    @Override
    public Dimension getPreferredSize() {
      return withInsets(card.getPreferredSize());
    }

    @Override
    public Dimension getMinimumSize() {
      return withInsets(card.getMinimumSize());
    }

    @Override
    public void doLayout() {
      Insets in = getInsets();
      int width = Math.max(0, Math.min(card.getPreferredSize().width, getWidth() - in.left - in.right));
      card.setSize(width, card.getHeight());
      card.setBounds(in.left, in.top, width, card.getPreferredSize().height); // the height at that width
    }

    private Dimension withInsets(Dimension d) {
      Insets in = getInsets();
      return new Dimension(d.width + in.left + in.right, d.height + in.top + in.bottom);
    }
  }

  /**
   * The header: name, then "Item #id" and {@link Labels#itemSubtitle} ("Cloth · iLvl 18 · Req 13", no item or
   * required level of 1 or below), then a starts-quest link. The name is in the item's quality colour when the
   * item has client data.
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
      Integer quality = d.card().quality();
      if (quality != null) {
        header.setTitleColor(() -> QualityColors.text(quality, FlatLaf.isLafDark()));
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
