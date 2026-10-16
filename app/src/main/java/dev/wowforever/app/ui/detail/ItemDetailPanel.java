package dev.wowforever.app.ui.detail;

import com.formdev.flatlaf.FlatLaf;
import dev.wowforever.app.model.DropSource;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceLoot;
import dev.wowforever.app.model.ItemDetail;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.theme.QualityColors;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * The item page: the icon beside the name (in its quality colour), class and levels, the tooltip lines, then
 * where the item comes from, one tab per kind of source (the AtlasLoot loot tables it is in among them).
 */
public final class ItemDetailPanel extends JPanel {
  /** Width and height of the item icon in pixels. */
  public static final int ICON_SIZE = 56;

  private final HeaderPanel header;
  private final JLabel iconLabel = new JLabel();
  private final TooltipLinesPanel tooltip;
  private final JTabbedPane tabs = new JTabbedPane();
  private final LinkTable<DropSource> droppedBy;
  private final LinkTable<InstanceLoot> inInstanceLoot;

  /** The item page without an icon. */
  public ItemDetailPanel(ItemDetail d, Navigator nav) {
    this(d, null, nav);
  }

  /** The item page with {@code icon} (null: no icon and no icon space). */
  public ItemDetailPanel(ItemDetail d, BufferedImage icon, Navigator nav) {
    super(new BorderLayout());
    ItemSummary s = d.summary();
    header = new HeaderPanel(s.displayName(), HeaderPanel.join("Item #" + s.id(), s.className(),
        subclassText(s.className(), s.subclassName()),
        s.itemLevel() == null ? null : "iLvl " + s.itemLevel(),
        s.requiredLevel() == null || s.requiredLevel() <= 0 ? null : "Requires " + s.requiredLevel()));
    if (d.startQuestId() != null) {
      EntityRef quest = new EntityRef(EntityKind.QUEST, d.startQuestId());
      header.addLink("Starts quest: " + questName(d, quest), quest, nav);
    }
    Integer quality = d.card().quality();
    if (quality != null) {
      header.setTitleColor(() -> QualityColors.text(quality, FlatLaf.isLafDark()));
    }
    if (icon != null) {
      Image scaled = icon.getWidth() == ICON_SIZE && icon.getHeight() == ICON_SIZE ? icon
          : icon.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH);
      iconLabel.setIcon(new ImageIcon(scaled));
    }
    iconLabel.setVerticalAlignment(JLabel.TOP);
    iconLabel.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 0));
    iconLabel.setPreferredSize(new Dimension(ICON_SIZE + 12, ICON_SIZE + 10));
    iconLabel.setVisible(icon != null); // no icon at all: the title stays at the left edge
    tooltip = new TooltipLinesPanel(d.card().lines());
    droppedBy = LinkTable.addTab(tabs, "Dropped by", Columns.dropSources(), d.droppedBy(), nav);
    inInstanceLoot = LinkTable.addTab(tabs, "In instance loot", Columns.instanceLoot(), d.inInstanceLoot(), nav);
    List<ItemSource> containedIn = new ArrayList<>(d.containedInObjects());
    containedIn.addAll(d.containedInItems());
    LinkTable.addTab(tabs, "Contained in", Columns.itemSources(), containedIn, nav);
    LinkTable.addTab(tabs, "Sold by", Columns.itemSources(), d.soldBy(), nav);
    LinkTable.addTab(tabs, "Reward from", Columns.itemSources(), d.rewardFrom(), nav);
    LinkTable.addTab(tabs, "Related quests", Columns.itemSources(), d.relatedQuests(), nav);
    LinkTable.selectFirstEnabled(tabs);
    JPanel top = new JPanel(new BorderLayout());
    top.add(iconLabel, BorderLayout.WEST);
    top.add(header, BorderLayout.CENTER);
    JPanel tooltipRow = new JPanel(new BorderLayout());
    tooltipRow.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 12));
    tooltipRow.add(tooltip, BorderLayout.CENTER);
    tooltipRow.setVisible(tooltip.isVisible());
    top.add(tooltipRow, BorderLayout.SOUTH);
    add(top, BorderLayout.NORTH);
    add(tabs, BorderLayout.CENTER);
  }

  /** The subclass name, or null when it only repeats the class name ("Trade Goods · Trade Goods"). */
  private static String subclassText(String className, String subclassName) {
    return subclassName != null && subclassName.equals(className) ? null : subclassName;
  }

  /** The name of the quest the item starts, taken from the item's quest lists; "Unknown quest #id" otherwise. */
  private static String questName(ItemDetail d, EntityRef quest) {
    List<ItemSource> quests = new ArrayList<>(d.relatedQuests());
    quests.addAll(d.rewardFrom());
    return quests.stream().filter(q -> q.ref().equals(quest) && q.name() != null).findFirst()
        .map(ItemSource::name).orElse(quest.displayName(null));
  }

  public HeaderPanel header() {
    return header;
  }

  /** The label holding the item icon; hidden, with a null icon, when there is none. */
  public JLabel iconLabel() {
    return iconLabel;
  }

  public TooltipLinesPanel tooltip() {
    return tooltip;
  }

  public JTabbedPane tabs() {
    return tabs;
  }

  /** The AtlasLoot loot tables (instance and boss) the item is in. */
  public LinkTable<InstanceLoot> inInstanceLoot() {
    return inInstanceLoot;
  }

  public LinkTable<DropSource> droppedBy() {
    return droppedBy;
  }
}
