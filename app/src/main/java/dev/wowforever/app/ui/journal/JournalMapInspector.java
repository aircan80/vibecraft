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
    setBackground(Theme.current().bgRaised());
    setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.current().hairline()),
        BorderFactory.createEmptyBorder(16, 16, 16, 16)));
    getAccessibleContext().setAccessibleName("On this map");
    JToggleButton drawer = new JToggleButton("On this map", true);
    drawer.setFont(drawer.getFont().deriveFont(Font.BOLD, 17f));
    drawer.addActionListener(e -> { contents.setVisible(drawer.isSelected()); revalidate(); });
    add(drawer); add(Box.createVerticalStrut(12)); add(contents);
    JournalSurface.add(contents, JournalSurface.copy(mapName));
    JournalSurface.add(contents, selection);
    showSelection(null);
  }

  public static JournalMapInspector zone(String name, ZoneMapPanel panel, TravelMapData data,
      Navigator navigator, ClipboardPort clipboard) {
    var inspector = new JournalMapInspector(name,
        location -> { if (location.target() != null) navigator.open(new ViewTarget.Entity(location.target())); }, clipboard);
    JPanel layers = JournalSurface.stack();
    JournalSurface.add(layers, JournalSurface.heading("Layers & legend", 14));
    var legend = PinLegend.interactive(panel.mapView());
    JournalSurface.add(layers, legend);
    JCheckBox quests = new JCheckBox("Quest givers"); quests.setModel(panel.questsToggle().getModel());
    JCheckBox patrols = new JCheckBox("Patrol routes"); patrols.setModel(panel.routesToggle().getModel());
    JournalSurface.add(layers, quests); JournalSurface.add(layers, patrols);
    JRadioButton bosses = new JRadioButton("Bosses & rares"); bosses.setModel(panel.bossesToggle().getModel());
    JRadioButton npcs = new JRadioButton("All NPCs"); npcs.setModel(panel.allNpcsToggle().getModel());
    JCheckBox objects = new JCheckBox("Objects"); objects.setModel(panel.objectsToggle().getModel());
    JournalSurface.add(layers, bosses); JournalSurface.add(layers, npcs); JournalSurface.add(layers, objects);
    JButton travelOnly = new JButton("Travel layers only");
    travelOnly.addActionListener(e -> panel.mapView().applyPreset(MapLayer.TRAVEL));
    JournalSurface.add(layers, travelOnly);
    inspector.contents.add(layers, 2);
    JournalSurface.add(inspector.contents, JournalSurface.heading("Recorded locations", 14));
    JournalSurface.add(inspector.contents, inspector.locations);
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
        ? "Select a marker or recorded destination to inspect it, then open its page or copy its location."
        : location.facts()));
    if (location != null) {
      JButton openButton = new JButton("Open destination →");
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
