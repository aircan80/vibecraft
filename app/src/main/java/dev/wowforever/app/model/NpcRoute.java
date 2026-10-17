package dev.wowforever.app.model;

/** A named NPC's patrol, loaded before the map is rendered. */
public record NpcRoute(EntityRef npc, String name, Integer rank, Route route) {}
