package dev.wowforever.importer.clientdata;

/**
 * An item's base armour.
 *
 * @param armor the base armour value; bonus armour (stat 50) is a separate stat line
 * @param block the shield's own block value, or null: no client table this pipeline reads holds it
 */
public record ItemArmor(int armor, Integer block) {}
