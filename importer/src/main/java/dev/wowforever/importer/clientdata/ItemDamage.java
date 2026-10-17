package dev.wowforever.importer.clientdata;

/**
 * An item's weapon damage.
 *
 * @param school  {@code ItemSparse.DamageType}: 0 physical, 1 holy, 2 fire, 3 nature, 4 frost, 5 shadow, 6 arcane
 * @param delayMs the swing time; 0 for ammunition, which has no speed of its own
 * @param dps     {@code (min + max) / 2} per second of delay, as the tooltip shows it; for ammunition the damage it
 *                adds per second, {@code (min + max) / 2}
 */
public record ItemDamage(int min, int max, int school, int delayMs, double dps) {}
