package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.MapCoordinates;
import dev.wowforever.app.model.Spawn;
import dev.wowforever.app.model.Route;
import dev.wowforever.app.ui.widgets.MapLayer;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.widgets.ClipboardPort;
import dev.wowforever.app.ui.widgets.MapView;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.util.List;
import dev.wowforever.app.ui.WayCommand;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;

/**
 * The {@value #TITLE} section of the NPC and object pages: each spawn point's zone and coordinates ({@code "42.3, 67.4"};
 * the Zone column is left out when every point is in the page's zone), and a "Copy /way" button that copies a
 * TomTom waypoint command for the selected point (the first when none is selected). Ctrl+Shift+C on a point
 * copies the same command. With the decoded map of a spawn zone, the map shows above the table with the
 * entity's spawn points in that zone pinned as its own ({@link MapView.PinKind#SELF}).
 */
final class SpawnSection {
  /** The section's title (it was "Spawns" before the shared page skeleton). */
  static final String TITLE = "Location";
  private final SourceSection section;
  private final LinkTable<Spawn> table;
  private final JButton copy = new JButton("Copy /way");
  private final MapView map;

  SpawnSection(List<Spawn> spawns, String pageZone, Navigator nav, ClipboardPort clipboard) {
    this(spawns, pageZone, null, null, null, null, nav, clipboard);
  }

  /**
   * The section of the spawn points of {@code self} (named {@code selfName}), with {@code map}, the map of zone
   * {@code mapZoneId}, above the table, pinning the points in that zone {@linkplain MapCoordinates#onMap on the map};
   * no map when {@code map} is null or none of the points is.
   */
  SpawnSection(List<Spawn> spawns, String pageZone, EntityRef self, String selfName, Long mapZoneId,
      BufferedImage map, Navigator nav, ClipboardPort clipboard) {
    this(spawns, pageZone, self, selfName, mapZoneId, map, nav, clipboard, List.of());
  }

  SpawnSection(List<Spawn> spawns, String pageZone, EntityRef self, String selfName, Long mapZoneId,
      BufferedImage map, Navigator nav, ClipboardPort clipboard, List<Route> routes) {
    table = LinkTable.inline(VisibleColumns.of(Columns.spawns(), spawns, pageZone), nav);
    table.setRows(spawns);
    table.setClipboard(clipboard);
    table.setIdText(s -> Columns.hasCoordinates(s) ? Optional.of(wayCommand(s)) : Optional.empty());
    section = new SourceSection(TITLE, spawns.size(), table);
    copy.setToolTipText("Copy a /way command for the selected spawn point");
    copy.addActionListener(e -> {
      Spawn target = target();
      if (target != null && Columns.hasCoordinates(target)) {
        clipboard.copy(wayCommand(target));
      }
    });
    table.table().getSelectionModel().addListSelectionListener(e -> updateCopy());
    updateCopy();
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
    buttons.setOpaque(false);
    buttons.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
    buttons.add(copy);
    section.addFooter(buttons);
    List<MapView.Pin> pins = map == null || mapZoneId == null ? List.of() : spawns.stream()
        .filter(s -> s.zoneId() == mapZoneId && MapCoordinates.onMap(s.x(), s.y()))
        .map(s -> new MapView.Pin(self, self.displayName(selfName), s.x(), s.y(), MapView.PinKind.SELF))
        .toList();
    if (pins.isEmpty()) {
      this.map = null;
    } else {
      this.map = new MapView(map, nav);
      this.map.setPins(pins);
      this.map.setRoutes(routes.stream().filter(r -> mapZoneId != null && r.zoneId() == mapZoneId)
          .map(r -> new MapView.RouteLine(self, self.displayName(selfName), MapView.PinKind.SELF,
              r.points(), r.loop())).toList());
      this.map.setLayerVisible(MapLayer.REGISTERED.stream().filter(l -> l.id().equals("routes"))
          .findFirst().orElseThrow(), true);
      this.map.setClipboard(clipboard);
      this.map.setZoneName(spawns.stream().filter(s -> s.zoneId() == mapZoneId)
          .map(Spawn::zoneName).filter(java.util.Objects::nonNull).findFirst().orElse(null));
      section.addAboveTable(this.map);
    }
  }

  /** {@code "/way Westfall 42.3 67.4"}; without the zone when its name is unknown. */
  static String wayCommand(Spawn s) {
    return WayCommand.of(s.zoneName(), s.x(), s.y());
  }

  /** The selected spawn point, else the first; null without spawns. */
  private Spawn target() {
    int view = table.table().getSelectedRow();
    if (view >= 0) {
      return table.model().rowAt(table.table().convertRowIndexToModel(view));
    }
    return table.rowCount() == 0 ? null : table.model().rowAt(0);
  }

  private void updateCopy() {
    Spawn target = target();
    copy.setEnabled(target != null && Columns.hasCoordinates(target));
  }

  SourceSection section() {
    return section;
  }

  LinkTable<Spawn> table() {
    return table;
  }

  JButton copyButton() {
    return copy;
  }

  /** The map above the table; null without one. */
  MapView map() {
    return map;
  }
}
