package dev.wowforever.importer.clientdata;

/**
 * One spell effect of an item, written to {@code item_effect} and rendered as a "Use:", "Equip:" or "Chance on hit:"
 * tooltip line.
 *
 * @param ordinal             the effect's position among the item's effects ({@code LegacySlotIndex}, then id)
 * @param trigger             {@link #USE}, {@link #EQUIP} or {@link #CHANCE_ON_HIT}
 * @param text                the formatted description, or null when the spell has none (no tooltip line)
 * @param complete            every placeholder in the text was formatted
 * @param charges             {@code ItemEffect.Charges}: negative means the item is used up on the last charge
 * @param cooldownMs          {@code CoolDownMSec} when set, else {@code CategoryCoolDownMSec}; 0 for none
 * @param fromItemDescription a recipe-like item (a "learn spell" effect): the text is the item's own description
 *                            (formatted for the taught spell), which then is not shown as flavour text
 */
public record ItemSpellEffect(int ordinal, int trigger, long spellId, String text, boolean complete, int charges,
    int cooldownMs, boolean fromItemDescription, SpellTextEvidence evidence) {
  public ItemSpellEffect(int ordinal, int trigger, long spellId, String text, boolean complete, int charges,
      int cooldownMs, boolean fromItemDescription) {
    this(ordinal, trigger, spellId, text, complete, charges, cooldownMs, fromItemDescription, SpellTextEvidence.unknown(spellId));
  }
  public static final int USE = 0;
  public static final int EQUIP = 1;
  public static final int CHANCE_ON_HIT = 2;
  /** {@code ItemEffect.TriggerType} of an item that teaches its spell (recipes, patterns, formulae). */
  public static final int LEARN = 6;
}
