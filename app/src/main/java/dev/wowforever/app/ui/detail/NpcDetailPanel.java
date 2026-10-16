package dev.wowforever.app.ui.detail;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.NpcDetail;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.model.Spawn;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/** The NPC page: name, title, levels, rank and zone, then its loot, wares, quests and spawn points. */
public final class NpcDetailPanel extends JPanel {
  private final HeaderPanel header;
  private final JTabbedPane tabs = new JTabbedPane();
  private final LinkTable<Spawn> spawns;

  public NpcDetailPanel(NpcDetail d, Navigator nav) {
    super(new BorderLayout());
    NpcSummary s = d.summary();
    String title = s.subName() == null || s.subName().isBlank()
        ? s.displayName() : s.displayName() + " <" + s.subName() + ">";
    boolean zoneLink = s.zoneId() != null && s.zoneName() != null;
    header = new HeaderPanel(title, HeaderPanel.join("NPC #" + s.id(), HeaderPanel.levelText(s.minLevel(),
        s.maxLevel()), Columns.rankText(s.rank()), zoneLink ? null : s.zoneName()));
    if (zoneLink) {
      header.addLink(s.zoneName(), new EntityRef(EntityKind.ZONE, s.zoneId()), nav);
    }
    LinkTable.addTab(tabs, "Drops", Columns.lootRows(), d.drops(), nav);
    LinkTable.addTab(tabs, "Sells", Columns.lootRows(), d.sells(), nav);
    LinkTable.addTab(tabs, "Starts quests", Columns.quests(), d.starts(), nav);
    LinkTable.addTab(tabs, "Ends quests", Columns.quests(), d.ends(), nav);
    spawns = LinkTable.addTab(tabs, "Spawns", Columns.spawns(), d.spawns(), nav);
    LinkTable.selectFirstEnabled(tabs);
    add(header, BorderLayout.NORTH);
    add(tabs, BorderLayout.CENTER);
  }

  public HeaderPanel header() {
    return header;
  }

  public JTabbedPane tabs() {
    return tabs;
  }

  public LinkTable<Spawn> spawns() {
    return spawns;
  }
}
