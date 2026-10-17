package dev.wowforever.importer.clientdata;

/** Item {@code itemId} teaches the crafting spell {@code spellId} (a recipe, pattern, plans or formula). */
public record CraftRecipeItem(long spellId, long itemId) {}
