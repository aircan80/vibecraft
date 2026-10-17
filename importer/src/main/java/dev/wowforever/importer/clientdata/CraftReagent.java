package dev.wowforever.importer.clientdata;

/** {@code count} of item {@code itemId} consumed by the crafting spell {@code spellId} ({@code SpellReagents}). */
public record CraftReagent(long spellId, long itemId, int count) {}
