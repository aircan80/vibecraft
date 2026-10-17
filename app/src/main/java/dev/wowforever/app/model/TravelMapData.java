package dev.wowforever.app.model;

import java.util.List;
import java.util.Objects;

/** Factual travel markers already projected into one map's percentages; loaded off the UI thread. */
public record TravelMapData(List<Entrance> entrances, List<FlightNode> flights, List<FlightRoute> routes,
    String sourceLabel) {
  public TravelMapData(List<Entrance> entrances, List<FlightNode> flights, List<FlightRoute> routes) {
    this(entrances, flights, routes, "");
  }
  public static final TravelMapData EMPTY = new TravelMapData(List.of(), List.of(), List.of());
  public TravelMapData {
    entrances = List.copyOf(entrances);
    flights = List.copyOf(flights);
    routes = List.copyOf(routes);
    sourceLabel = sourceLabel == null ? "" : sourceLabel;
  }

  /** A recorded entrance or a separate exit landing; names can identify individual wings. */
  public record Entrance(EntityRef instance, String name, double x, double y, boolean raid,
      boolean exit, boolean exitHere, String tooltip) {
    public Entrance {
      Objects.requireNonNull(instance); Objects.requireNonNull(name); Objects.requireNonNull(tooltip);
    }
  }

  public enum Faction { ALLIANCE, HORDE, NEUTRAL }

  /** An unmatched flight master is null: that node has a tooltip but opens no entity. */
  public record FlightNode(long id, String name, double x, double y, Faction faction,
      EntityRef flightMaster, List<String> destinations) {
    public FlightNode {
      Objects.requireNonNull(name); Objects.requireNonNull(faction);
      destinations = List.copyOf(destinations);
    }
  }

  /** Projected route points can lie beyond the map; the renderer clips each segment to its art. */
  public record FlightRoute(long id, String destination, List<Route.Point> points) {
    public FlightRoute { Objects.requireNonNull(destination); points = List.copyOf(points); }
  }
}
