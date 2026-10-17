package dev.wowforever.app.ui.widgets;

import java.util.List;
import java.util.Set;
import dev.wowforever.app.ui.widgets.MapView.PinKind;

/** Registered map controls; future layers declare their legend and preset membership here. */
public record MapLayer(String id, String label, Set<PinKind> kinds, boolean defaultVisible, Set<String> presets,
    java.util.function.Supplier<javax.swing.Icon> icon) {
  public MapLayer(String id, String label, Set<PinKind> kinds, boolean defaultVisible, Set<String> presets) {
    this(id, label, kinds, defaultVisible, presets, null);
  }

  /** Every registered layer supplies its legend presentation; the legend does not name individual layers. */
  public javax.swing.Icon legendIcon() {
    return icon != null ? icon.get() : kinds.isEmpty() ? new PinLegend.RouteIcon()
        : new PinLegend.PinIcon(kinds.iterator().next());
  }

  public static List<String> presetIds() {
    return REGISTERED.stream().flatMap(layer -> layer.presets().stream()).distinct().sorted().toList();
  }

  public static String presetLabel(String id) {
    return Character.toUpperCase(id.charAt(0)) + id.substring(1).replace('-', ' ');
  }

  public MapLayer {
    kinds = Set.copyOf(kinds);
    presets = Set.copyOf(presets);
  }

  public static final String TRAVEL = "travel";
  public static final String ENTRANCES = "entrances";
  public static final String FLIGHTS = "flights";
  public static final String FLIGHT_ROUTES = "flight-routes";
  public static final String BOSSES_AND_RARES = "bosses-and-rares";
  public static final List<MapLayer> REGISTERED = List.of(
      new MapLayer("bosses", "Boss", Set.of(PinKind.BOSS), true, Set.of(BOSSES_AND_RARES)),
      new MapLayer("rares", "Rare", Set.of(PinKind.RARE), true, Set.of(BOSSES_AND_RARES)),
      new MapLayer("npcs", "NPC", Set.of(PinKind.NPC), true, Set.of()),
      new MapLayer("objects", "Object", Set.of(PinKind.OBJECT), true, Set.of()),
      new MapLayer("starts", "Starts a quest", Set.of(PinKind.QUEST_START), true, Set.of()),
      new MapLayer("ends", "Ends a quest", Set.of(PinKind.QUEST_END), true, Set.of()),
      new MapLayer("kill", "Kill", Set.of(PinKind.KILL), true, Set.of()),
      new MapLayer("collect", "Collect", Set.of(PinKind.COLLECT), true, Set.of()),
      new MapLayer("interact", "Interact", Set.of(PinKind.INTERACT), true, Set.of()),
      new MapLayer("explore", "Explore", Set.of(PinKind.EXPLORE), true, Set.of()),
      new MapLayer("routes", "Routes", Set.of(), false, Set.of()),
      new MapLayer(ENTRANCES, "Entrances", Set.of(), true, Set.of(TRAVEL), () -> TravelMapPainter.legendIcon(ENTRANCES)),
      new MapLayer(FLIGHTS, "Flight paths", Set.of(), true, Set.of(TRAVEL), () -> TravelMapPainter.legendIcon(FLIGHTS)),
      new MapLayer(FLIGHT_ROUTES, "Flight routes", Set.of(), false, Set.of()));
}
