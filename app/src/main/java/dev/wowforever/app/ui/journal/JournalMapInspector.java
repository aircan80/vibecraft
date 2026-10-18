package dev.wowforever.app.ui.journal;

import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.WayCommand;
import dev.wowforever.app.ui.pages.ZoneMapPanel;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.widgets.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.*;

/** Journal-only inspector of the same loaded markers and layer models used by its map. */
public final class JournalMapInspector extends JPanel {
  public record Location(String key, String name, String facts, EntityRef target, Double x, Double y) {}
  private final JPanel contents = JournalSurface.stack();
  private final JPanel selection = JournalSurface.stack();
  private final JPanel locations = JournalSurface.stack();
  private final List<Location> recorded = new ArrayList<>();
  private final String mapName;
  private final Consumer<Location> open;
  private final ClipboardPort clipboard;
  private Location selected;

  private JournalMapInspector(String mapName, Consumer<Location> open, ClipboardPort clipboard) {
    this.mapName = mapName; this.open = open; this.clipboard = clipboard;
    setLayout(new JournalSurface.Stack());
    setBackground(Theme.current().bgSurface());
    setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.current().hairline()),
        BorderFactory.createEmptyBorder(16, 16, 16, 16)));
    getAccessibleContext().setAccessibleName("On this map");
    add(JournalSurface.heading("On this map", 16));
    add(Box.createVerticalStrut(12)); add(contents);
    JournalSurface.add(contents, selection);
    showSelection(null);
  }

  public static JournalMapInspector zone(String name, ZoneMapPanel panel, TravelMapData data,
      Navigator navigator, ClipboardPort clipboard) {
    var inspector = new JournalMapInspector(name,
        location -> { if (location.target() != null) navigator.open(new ViewTarget.Entity(location.target())); }, clipboard);
    JPanel layers = JournalSurface.stack();
    for (String id : List.of(MapLayer.ENTRANCES, MapLayer.FLIGHTS)) {
      var layer = MapLayer.REGISTERED.stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
      JCheckBox toggle = new JCheckBox(id.equals(MapLayer.ENTRANCES) ? "Dungeon entrances" : "Flight points",
          panel.mapView().isLayerVisible(layer));
      toggle.setOpaque(false);
      toggle.addActionListener(e -> panel.mapView().setLayerVisible(layer, toggle.isSelected()));
      panel.mapView().addPropertyChangeListener("layerVisibility", e ->
          toggle.setSelected(panel.mapView().isLayerVisible(layer)));
      JournalSurface.add(layers, toggle);
    }
    inspector.contents.add(layers, 0);
    JPanel more = JournalSurface.stack();
    JCheckBox quests = new JCheckBox("Quest givers"); quests.setModel(panel.questsToggle().getModel());
    JCheckBox patrols = new JCheckBox("Patrol routes"); patrols.setModel(panel.routesToggle().getModel());
    JournalSurface.add(more, quests); JournalSurface.add(more, patrols);
    JRadioButton bosses = new JRadioButton("Bosses & rares"); bosses.setModel(panel.bossesToggle().getModel());
    JRadioButton npcs = new JRadioButton("All NPCs"); npcs.setModel(panel.allNpcsToggle().getModel());
    JCheckBox objects = new JCheckBox("Objects"); objects.setModel(panel.objectsToggle().getModel());
    JournalSurface.add(more, bosses); JournalSurface.add(more, npcs); JournalSurface.add(more, objects);
    JButton travelOnly = new JButton("Travel layers only");
    travelOnly.addActionListener(e -> panel.mapView().applyPreset(MapLayer.TRAVEL));
    JournalSurface.add(more, travelOnly);
    inspector.disclosure("More map layers", more);
    inspector.disclosure("Recorded locations", inspector.locations);
    for (var entrance : data.entrances()) inspector.recorded.add(new Location("entrance:" + entrance.instance().id()
        + ":" + entrance.x() + ":" + entrance.y(), entrance.name(), entrance.tooltip()
        + (entrance.exitHere() ? " · Exit lands here" : ""), entrance.instance(), entrance.x(), entrance.y()));
    for (var node : data.flights()) inspector.recorded.add(new Location("flight:" + node.id(), node.name(),
        switch (node.faction()) { case ALLIANCE -> "Alliance"; case HORDE -> "Horde"; case NEUTRAL -> "Neutral"; }
        + " flight point" + (node.destinations().isEmpty() ? "" : " · Flies to: " + String.join(", ", node.destinations())),
        node.flightMaster(), node.x(), node.y()));
    inspector.rebuildLocations();
    if (!data.sourceLabel().isBlank()) {
      JToggleButton sources = new JToggleButton("Map & travel sources");
      var evidence = JournalSurface.copy(data.sourceLabel()); evidence.setVisible(false);
      sources.addActionListener(e -> { evidence.setVisible(sources.isSelected()); inspector.revalidate(); });
      JournalSurface.add(inspector.contents, sources); JournalSurface.add(inspector.contents, evidence);
    }
    panel.mapView().addMouseListener(new MouseAdapter() {
      @Override public void mouseClicked(MouseEvent event) {
        if (event.getButton() != MouseEvent.BUTTON1) return;
        var point = panel.mapView().coordinatesAt(event.getX(), event.getY());
        if (point.isEmpty()) return;
        Location closest = null; double distance = Double.MAX_VALUE;
        for (Location location : inspector.recorded) {
          String layerId = location.key().startsWith("entrance:") ? MapLayer.ENTRANCES : MapLayer.FLIGHTS;
          var layer = MapLayer.REGISTERED.stream().filter(item -> item.id().equals(layerId)).findFirst().orElseThrow();
          if (!panel.mapView().isLayerVisible(layer)) continue;
          var at = panel.mapView().projection().toComponent(location.x(), location.y());
          double candidate = at.distance(event.getPoint());
          if (candidate < distance && candidate <= 14) { closest = location; distance = candidate; }
        }
        if (panel.mapView().travelAt(event.getX(), event.getY()) != null && closest != null) {
          inspector.showSelection(closest); return;
        }
        var cluster = panel.mapView().clusterAt(event.getX(), event.getY());
        if (cluster != null) {
          var pin = cluster.pins().getFirst();
          inspector.showSelection(new Location("pin:" + pin.ref(), pin.name(),
              cluster.count() + (cluster.count() == 1 ? " recorded map point" : " recorded map points"),
              pin.ref(), pin.x(), pin.y()));
        } else {
          var route = panel.mapView().routeAt(event.getX(), event.getY());
          inspector.showSelection(new Location("point:" + point.get().x + ":" + point.get().y,
              route == null ? "Map location" : route.name(), route == null ? "Coordinates on " + name : "Recorded patrol route",
              route == null ? null : route.npc(), point.get().x, point.get().y));
        }
      }
    });
    return inspector;
  }

  public static JournalMapInspector world(String name, WorldMapCatalog catalog, List<WorldMapRegion> children,
      Consumer<WorldMapRegion> open, Consumer<Long> focus) {
    var inspector = new JournalMapInspector(name, location -> children.stream()
        .filter(region -> location.key().equals("region:" + region.uiMapId())).findFirst().ifPresent(region -> {
          focus.accept(region.uiMapId()); open.accept(region);
        }), text -> {});
    JournalSurface.add(inspector.contents, JournalSurface.heading("Destinations", 14));
    JournalSurface.add(inspector.contents, inspector.locations);
    for (var region : children) {
      String facts = region.levelBand().map(b -> "Levels " + b.min() + "–" + b.max() + " · ").orElse("")
          + (region.zoneId() == null && catalog.children(region.uiMapId()).isEmpty() ? "Not in the database yet"
              : region.questCount() + " quests · " + region.instanceCount() + " instances")
          + (!region.placed() ? " · Position unavailable" : "");
      inspector.recorded.add(new Location("region:" + region.uiMapId(), region.name(), facts,
          region.zoneId() == null ? null : new EntityRef(EntityKind.ZONE, region.zoneId()), null, null));
    }
    inspector.rebuildLocations();
    return inspector;
  }

  private void disclosure(String title, JComponent body) {
    JToggleButton toggle = new JToggleButton(title + " ▾");
    toggle.setHorizontalAlignment(SwingConstants.LEADING);
    toggle.putClientProperty("JButton.buttonType", "borderless");
    body.setVisible(false);
    toggle.addActionListener(e -> { body.setVisible(toggle.isSelected()); revalidate(); repaint(); });
    JournalSurface.add(contents, toggle); JournalSurface.add(contents, body);
  }

  private void rebuildLocations() {
    locations.removeAll();
    for (Location location : recorded) {
      JButton button = new JButton(location.name());
      button.setToolTipText(location.facts());
      button.getAccessibleContext().setAccessibleName("Select " + location.name());
      button.addActionListener(e -> showSelection(location));
      JournalSurface.add(locations, button);
    }
    if (recorded.isEmpty()) JournalSurface.add(locations, JournalSurface.copy("No travel locations recorded on this map."));
  }

  public void selectRegion(Long id) {
    showSelection(id == null ? null : recorded.stream().filter(location -> location.key().equals("region:" + id)).findFirst().orElse(null));
  }

  public void showSelection(Location location) {
    selected = location; selection.removeAll();
    JournalSurface.add(selection, JournalSurface.title(location == null ? "Explore " + mapName : location.name(), 18));
    JournalSurface.add(selection, JournalSurface.copy(location == null
        ? "Select a pin for its destination and a clear next action."
        : location.facts()));
    if (location != null) {
      JButton openButton = new JButton("Open record →");
      boolean worldRegion = location.key().startsWith("region:");
      openButton.setEnabled(worldRegion || location.target() != null);
      if (worldRegion && location.facts().contains("Not in the database yet")) openButton.setEnabled(false);
      openButton.addActionListener(e -> open.accept(location));
      JournalSurface.add(selection, openButton);
      if (location.x() != null && location.y() != null) {
        JournalSurface.add(selection, JournalSurface.copy(String.format(java.util.Locale.ROOT, "%.1f, %.1f", location.x(), location.y())));
        JButton copy = new JButton("Copy /way");
        copy.addActionListener(e -> clipboard.copy(WayCommand.of(mapName, location.x(), location.y())));
        JournalSurface.add(selection, copy);
      }
    }
    selection.revalidate(); revalidate(); repaint();
  }

  public void selectEntity(EntityRef target) {
    recorded.stream().filter(location -> Objects.equals(location.target(), target)).findFirst().ifPresent(this::showSelection);
  }

  public Location selected() { return selected; }
}
