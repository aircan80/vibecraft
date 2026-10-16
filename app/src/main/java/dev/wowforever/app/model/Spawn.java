package dev.wowforever.app.model;

/** One spawn point; {@code zoneName} is null when the zone is unknown. */
public record Spawn(long zoneId, String zoneName, double x, double y) {}
