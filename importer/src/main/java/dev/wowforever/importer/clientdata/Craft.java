package dev.wowforever.importer.clientdata;

/**
 * A crafting spell: it creates {@code createdItemId}, between {@code quantityMin} and {@code quantityMax} of it, see
 * {@link CraftLoader}. The skill fields come from the spell's {@code SkillLineAbility} row and are all null when it
 * has none; {@code recipeItemId} is the item that teaches it, null when no item does.
 *
 * @param spellName    {@code SpellName.Name_lang}, null when the spell has no SpellName row
 * @param skillLineId  {@code SkillLineAbility.SkillLine}
 * @param skillName    {@code SkillLine.DisplayName_lang}, null when the skill line has no row
 * @param minSkill     {@code SkillLineAbility.MinSkillLineRank}
 * @param trivialLow   {@code SkillLineAbility.TrivialSkillLineRankLow}
 * @param trivialHigh  {@code SkillLineAbility.TrivialSkillLineRankHigh}
 */
public record Craft(long spellId, String spellName, long createdItemId, int quantityMin, int quantityMax,
    Long skillLineId, String skillName, Integer minSkill, Integer trivialLow, Integer trivialHigh,
    Long recipeItemId) {}
