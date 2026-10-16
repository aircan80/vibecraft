package dev.wowforever.app.ui.detail;

import dev.wowforever.app.model.DropSource;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ItemDetail;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/** The item page: name, class and levels, then where the item comes from, one tab per kind of source. */
public final class ItemDetailPanel extends JPanel {
  private final HeaderPanel header;
  private final JTabbedPane tabs = new JTabbedPane();
  private final LinkTable<DropSource> droppedBy;

  public ItemDetailPanel(ItemDetail d, Navigator nav) {
    super(new BorderLayout());
    ItemSummary s = d.summary();
    header = new HeaderPanel(s.displayName(), HeaderPanel.join("Item #" + s.id(), s.className(), s.subclassName(),
        s.itemLevel() == null ? null : "iLvl " + s.itemLevel(),
        s.requiredLevel() == null || s.requiredLevel() <= 0 ? null : "Requires " + s.requiredLevel()));
    if (d.startQuestId() != null) {
      EntityRef quest = new EntityRef(EntityKind.QUEST, d.startQuestId());
      header.addLink("Starts quest: " + questName(d, quest), quest, nav);
    }
    droppedBy = LinkTable.addTab(tabs, "Dropped by", Columns.dropSources(), d.droppedBy(), nav);
    List<ItemSource> containedIn = new ArrayList<>(d.containedInObjects());
    containedIn.addAll(d.containedInItems());
    LinkTable.addTab(tabs, "Contained in", Columns.itemSources(), containedIn, nav);
    LinkTable.addTab(tabs, "Sold by", Columns.itemSources(), d.soldBy(), nav);
    LinkTable.addTab(tabs, "Reward from", Columns.itemSources(), d.rewardFrom(), nav);
    LinkTable.addTab(tabs, "Related quests", Columns.itemSources(), d.relatedQuests(), nav);
    LinkTable.selectFirstEnabled(tabs);
    add(header, BorderLayout.NORTH);
    add(tabs, BorderLayout.CENTER);
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

  public JTabbedPane tabs() {
    return tabs;
  }

  public LinkTable<DropSource> droppedBy() {
    return droppedBy;
  }
}
