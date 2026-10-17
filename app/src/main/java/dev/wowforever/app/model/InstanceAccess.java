package dev.wowforever.app.model;

import java.util.List;
import java.util.Optional;

/** Recorded access geography; absence is unknown, never an invented entrance. */
public record InstanceAccess(List<Location> locations, List<Parent> parents) {
  public static final InstanceAccess EMPTY = new InstanceAccess(List.of(), List.of());
  public InstanceAccess { locations = List.copyOf(locations); parents = List.copyOf(parents); }
  public record Location(long zoneId, String zoneName, TravelMapData.Entrance pin, Optional<ZoneMap> map) {}
  public record Parent(long instanceId, String name) {}
}
