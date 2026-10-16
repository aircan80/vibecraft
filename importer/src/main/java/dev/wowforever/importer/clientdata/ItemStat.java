package dev.wowforever.importer.clientdata;

/** One computed item stat: an ItemModType id (see {@link StatNames}) and its value, e.g. (7, 8), "+8 Stamina". */
public record ItemStat(int statId, int value) {}
