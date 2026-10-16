package dev.wowforever.app.ui.detail;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ObjectDetail;
import dev.wowforever.app.model.ObjectSummary;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/** The game object page: name and zone, then what it contains, the quests it starts and ends, and its spawns. */
public final class ObjectDetailPanel extends JPanel {
  private final HeaderPanel header;
  private final JTabbedPane tabs = new JTabbedPane();

  public ObjectDetailPanel(ObjectDetail d, Navigator nav) {
    super(new BorderLayout());
    ObjectSummary s = d.summary();
    boolean zoneLink = s.zoneId() != null && s.zoneName() != null;
    header = new HeaderPanel(s.displayName(), HeaderPanel.join("Object #" + s.id(), zoneLink ? null : s.zoneName()));
    if (zoneLink) {
      header.addLink(s.zoneName(), new EntityRef(EntityKind.ZONE, s.zoneId()), nav);
    }
    LinkTable.addTab(tabs, "Contains", Columns.lootRows(), d.contains(), nav);
    LinkTable.addTab(tabs, "Starts quests", Columns.quests(), d.starts(), nav);
    LinkTable.addTab(tabs, "Ends quests", Columns.quests(), d.ends(), nav);
    LinkTable.addTab(tabs, "Spawns", Columns.spawns(), d.spawns(), nav);
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
}
