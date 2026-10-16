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
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * The zone page: name, kind and parent zone, the zone map when there is one, then the NPCs, objects, quests,
 * instances and sub-zones in it.
 */
public final class ZoneDetailPanel extends JPanel {
  private final HeaderPanel header;
  private final MapImagePanel map;
  private final JTabbedPane tabs = new JTabbedPane();
  private final LinkTable<InstanceSummary> instances;

  public ZoneDetailPanel(ZoneDetail d, Navigator nav) {
    super(new BorderLayout());
    ZoneSummary s = d.summary();
    header = new HeaderPanel(s.displayName(), HeaderPanel.join("Zone #" + s.id(), kindText(s.kind())));
    if (s.parentId() != null) {
      EntityRef parent = new EntityRef(EntityKind.ZONE, s.parentId());
      header.addLink(parent.displayName(s.parentName()), parent, nav);
    }
    LinkTable.addTab(tabs, "NPCs", Columns.npcs(), d.npcs(), nav);
    LinkTable.addTab(tabs, "Objects", Columns.objects(), d.objects(), nav);
    LinkTable.addTab(tabs, "Quests", Columns.quests(), d.quests(), nav);
    instances = LinkTable.addTab(tabs, "Dungeons & Raids", Columns.instances(), d.instances(), nav);
    LinkTable.addTab(tabs, "Sub-zones", Columns.zones(), d.subzones(), nav);
    LinkTable.selectFirstEnabled(tabs);
    map = d.map().map(MapImagePanel::new).orElse(null);
    JPanel top = new JPanel(new BorderLayout());
    top.add(header, BorderLayout.NORTH);
    if (map != null) {
      JPanel mapRow = new JPanel(new BorderLayout());
      mapRow.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 12));
      mapRow.add(map, BorderLayout.CENTER);
      top.add(mapRow, BorderLayout.CENTER);
    }
    add(top, BorderLayout.NORTH);
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

  /** The zone map, when the zone has one. */
  public Optional<MapImagePanel> map() {
    return Optional.ofNullable(map);
  }

  public LinkTable<InstanceSummary> instances() {
    return instances;
  }
}
