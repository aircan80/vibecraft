package dev.wowforever.app.model;

/** Immutable quest-page data assembled off the EDT. */
public record QuestFacts(Long zoneId, String zoneName, String sortLabel, Integer requiredRaces, Integer requiredClasses, boolean repeatable) {}
