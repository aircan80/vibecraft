package dev.wowforever.importer.clientdata;

import java.util.List;

/**
 * An item set, written to {@code item_set}, {@code item_set_item} and {@code item_set_bonus}.
 *
 * @param itemIds           {@code ItemSet.ItemID_0..16}, non-zero, in column order
 * @param requiredSkill     {@code ItemSet.RequiredSkill} (a {@code SkillLine} id), null for none
 * @param requiredSkillRank null without a skill
 * @param bonuses           ascending by threshold
 */
public record ItemSetInfo(long id, String name, List<Long> itemIds, Integer requiredSkill, Integer requiredSkillRank,
    List<Bonus> bonuses) {

  public ItemSetInfo {
    itemIds = List.copyOf(itemIds);
    bonuses = List.copyOf(bonuses);
  }

  /**
   * A set bonus: {@code ItemSetSpell(Threshold, SpellID)}.
   *
   * @param text the spell's formatted description, or null when it has none
   */
  public record Bonus(int threshold, long spellId, String text, boolean complete, SpellTextEvidence evidence) {
    public Bonus(int threshold, long spellId, String text, boolean complete) {
      this(threshold, spellId, text, complete, SpellTextEvidence.unknown(spellId));
    }
  }
}
