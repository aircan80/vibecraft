package dev.wowforever.app.ui.detail;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.ZoneDetail;
import dev.wowforever.app.model.ZoneSummary;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import java.awt.BorderLayout;
import java.util.Locale;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/** The zone page: name, kind and parent zone, then the NPCs, objects, quests, instances and sub-zones in it. */
public final class ZoneDetailPanel extends JPanel {
  private final HeaderPanel header;
  private final JTabbedPane tabs = new JTabbedPane();
  private final LinkTable<InstanceSummary> instances;

  public ZoneDetailPanel(ZoneDetail d, Navigator nav) {
    super(new BorderLayout());
    ZoneSummary s = d.summary();
    header = new HeaderPanel(s.displayName(), HeaderPanel.join("Zone #" + s.id(), kindText(s.kind())));
    if (s.parentId() != null) {
      header.addLink("Parent zone #" + s.parentId(), new EntityRef(EntityKind.ZONE, s.parentId()), nav);
    }
    LinkTable.addTab(tabs, "NPCs", Columns.npcs(), d.npcs(), nav);
    LinkTable.addTab(tabs, "Objects", Columns.objects(), d.objects(), nav);
    LinkTable.addTab(tabs, "Quests", Columns.quests(), d.quests(), nav);
    instances = LinkTable.addTab(tabs, "Dungeons & Raids", Columns.instances(), d.instances(), nav);
    LinkTable.addTab(tabs, "Sub-zones", Columns.zones(), d.subzones(), nav);
    LinkTable.selectFirstEnabled(tabs);
    add(header, BorderLayout.NORTH);
    add(tabs, BorderLayout.CENTER);
  }

  /** {@code "subzone"} → {@code "Subzone"}; null stays null. */
  private static String kindText(String kind) {
    if (kind == null || kind.isEmpty()) {
      return null;
    }
    return kind.substring(0, 1).toUpperCase(Locale.ROOT) + kind.substring(1);
  }

  public HeaderPanel header() {
    return header;
  }

  public JTabbedPane tabs() {
    return tabs;
  }

  public LinkTable<InstanceSummary> instances() {
    return instances;
  }
}
