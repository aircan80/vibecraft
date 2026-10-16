"""Client tables required by the Forever build (spec section 4.1)."""

REQUIRED_TABLES = (
    "Item", "ItemSparse", "ItemClass", "ItemSubClass", "ItemNameDescription",
    "ItemXItemEffect", "ItemEffect",
    "Spell", "SpellName", "SpellEffect", "SpellMisc", "SpellDuration",
    "SpellItemEnchantment", "ItemSet", "ItemSetSpell",
    "RandPropPoints",
    "ItemDamageOneHand", "ItemDamageOneHandCaster", "ItemDamageTwoHand",
    "ItemDamageTwoHandCaster", "ItemDamageRanged", "ItemDamageThrown",
    "ItemDamageWand", "ItemDamageAmmo",
    "ItemArmorTotal", "ItemArmorQuality", "ItemArmorShield", "ArmorLocation",
    "UiMap", "UiMapArt", "UiMapArtTile", "UiMapArtStyleLayer", "UiMapXMapArt",
    "UiMapAssignment", "WorldMapOverlay", "WorldMapOverlayTile",
    "AreaTable", "Map", "ChrRaces", "ChrClasses", "SkillLine", "Faction",
)

# The fallback product's ItemSparse (ruling 17), cached and converted as csv/ItemSparse.era.csv
# and locked under this name; read only for items absent from the main ItemSparse.
FALLBACK_NAME = "ItemSparse.era"
