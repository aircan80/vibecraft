"""Client tables required by the Forever build (spec section 4.1)."""

REQUIRED_TABLES = (
    "Item", "ItemSparse", "ItemClass", "ItemSubClass", "ItemNameDescription",
    "ItemXItemEffect", "ItemEffect",
    "Spell", "SpellName", "SpellEffect", "SpellMisc", "SpellDuration", "SpellAuraOptions", "SpellRadius",
    "SpellItemEnchantment", "ItemSet", "ItemSetSpell",
    "RandPropPoints",
    "ItemDamageOneHand", "ItemDamageOneHandCaster", "ItemDamageTwoHand",
    "ItemDamageTwoHandCaster", "ItemDamageRanged", "ItemDamageThrown",
    "ItemDamageWand", "ItemDamageAmmo",
    "ItemArmorTotal", "ItemArmorQuality", "ItemArmorShield", "ArmorLocation",
    "UiMap", "UiMapArt", "UiMapArtTile", "UiMapArtStyleLayer", "UiMapXMapArt",
    "UiMapAssignment", "WorldMapOverlay", "WorldMapOverlayTile",
    "AreaTable", "Map", "ChrRaces", "ChrClasses", "SkillLine", "Faction",
    # crafting (3A Task 8): reagents by craft spell, skill line and ranks by spell; SkillRaceClassInfo is fetched for
    # completeness (which races and classes may learn a skill) and not read by the importer yet
    "SpellReagents", "SkillLineAbility", "SkillRaceClassInfo",
    # the client's interface file listing, read by fetch.py --ui-icons to resolve the rail icons' FileDataIDs
    "ManifestInterfaceData",
)

# The tables fetched from the fallback product (ruling 17 and the 2A fix wave): its ItemSparse, read only for items
# absent from the main ItemSparse, and its ItemEffect, read only for those Era-sourced items. Classic Era (1.15.9)
# has no ItemXItemEffect table: its ItemEffect names each effect's item in ParentItemID. Each is cached and converted
# as csv/{Table}.era.csv and locked under "{Table}.era".
FALLBACK_TABLES = ("ItemSparse", "ItemEffect")
FALLBACK_SUFFIX = ".era"


def fallback_name(table: str) -> str:
    """The cache, output and lock name of a fallback table: ``ItemSparse`` -> ``ItemSparse.era``."""
    return table + FALLBACK_SUFFIX


FALLBACK_NAMES = tuple(fallback_name(t) for t in FALLBACK_TABLES)
FALLBACK_NAME = fallback_name("ItemSparse")
