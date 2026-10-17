# Roadmap and ideas backlog

Collected 2026-10-16 after v0.1 (the first working browser). Not committed to any order yet;
the suggested sequence is at the bottom.

## A. Richer item data (client datamining)

1. **Item stats, quality and a WoW-style tooltip.** Source: client DB2 tables for the Forever
   build (`ItemSparse`, `Item`, `ItemEffect`, `Spell`/`SpellName`, `ItemSet`), via wago.tools CSV
   exports or a local dump. New importer stage and tables (`item_stats`, `item_effect`,
   `item_set`); tooltip panel with quality colours; quality colouring and slot/stat filters in
   item lists.
2. **Icons.** `ItemSparse`/`ItemAppearance` → icon FileDataID → BLP from CASC → PNG. ~6k unique
   icons for 21k items; ship as a resource folder (a few MB).
3. **Zone maps with spawn pins.** Client map tiles per zone; Questie spawn coordinates are map
   percentages, so pins map directly. Click a pin to open the NPC/object.
4. **3D item models.** Tier 1: pre-rendered static or 36-frame turntable images (wow.export →
   glTF → batch render). Tier 2: live viewer with LWJGL/JOGL. Armour on a body needs the
   dressing-room machinery and is out of scope until Tier 1 exists.
5. **Better drop percentages from emulator databases.** VMaNGOS loot templates (GPL-2.0) give real
   per-NPC rates for vanilla content, replacing "rate unknown" on ~153k of 164k drop rows.
6. **Smaller additions.** Crafting recipes and "crafted by" (AtlasLoot crafting module), faction
   and PvP vendors, quest chains, copyable `item:ID` links, "usable by my class/level" filter,
   two-item compare view, the 50 most-changed item names (client vs Questie, `item_alt_value`) in
   the import report's Conflicts section (planned in phase 2A, deferred by its final review).

**Build-time extraction vs. reading the user's client at runtime (decided 2026-10-16).** Do the
extraction in the build pipeline and ship the results. Reasons: modern clients store assets in
CASC (indexed, chunked archives), so reading them needs CASC + BLTE + BLP + M2 + DB2 readers,
none of which exist as mature Java libraries (the good tools are C#: TACTSharp, CASCExplorer; and
JS: wow.export); Blizzard's CDN serves the same CASC data publicly, so the pipeline needs no local
install and can use those tools or wago.tools' per-build CSV/file exports; FileDataIDs are stable
across builds but DB2 schemas change per build, so a once-per-build pipeline step is the right
place to absorb breakage; users then need no WoW install to browse. Runtime extraction buys only
legal cleanliness at the cost of the whole reader stack in Java and a first-run extraction step.
Proportion: ship stats (data) and icons (a few MB, widely redistributed); keep model renders as a
separate asset pack, which is also where an opt-in "render from my local install" would go.
Finding an install, if ever needed: Windows registry `HKLM\SOFTWARE\WOW6432Node\Blizzard
Entertainment\World of Warcraft` or Battle.net's `product.db`; macOS `/Applications/World of
Warcraft`; Linux via a folder picker (Wine).

**Caveat, stated once:** client extraction is a grey area under Blizzard's EULA and shipping icons
or models means redistributing Blizzard art. Fan sites do this and Blizzard has tolerated it; the
call is the project owner's. The cleaner alternative is an opt-in "import from my WoW install"
that reads assets from the user's own client at first run (needs a CASC reader in Java), so
nothing copyrighted ships in the repo. Do that second.

## B. Multiple sources and conflicting data (provenance)

Measured on the v0.1 data: "who drops it" never conflicts (drop lists are unioned; 162,225 rows
Questie-only, 969 AtlasLoot-only, 1,253 both). "How often" does: 242 pairs have a percentage from
both Questie's Wowhead table and AtlasLoot, 137 disagree by >1 point, 85 by >5 (mostly quest items,
where Wowhead's sample is players on the quest). AtlasLoot also omits 1,958 Questie drop rows on its
bosses by design (quest items, grey junk, reagents).

7. **Keep every competing value, not just the winner.** Replace the single `drop_pct`/`pct_source`
   with a `drop_rate_source(npc_id, item_id, source, pct)` table (keep a denormalised "best"
   column for list views). The UI shows the chosen value with its source and a disagreement marker
   when sources differ by more than a few points; hover/expand shows the others.
8. **Provenance on every fact.** Already on drop rows (`origin`). Extend to names and levels when
   a second source for them (client data) arrives; prefer the client's value and report mismatches.
9. **Conflict section in the import report.** List the top disagreements between sources and the
   rows one source has that another omits, so a data refresh shows what moved.
10. **Source filter in the UI.** Settings toggle to hide AtlasLoot-only or Questie-only rows for
    users who trust one source more.

## C. Verification

11. **Byte-level equivalence check against QuestieDB's own generated artifact.** QuestieDB ships a
    Lua 5.1 binary and `generate.lua Forever`; a one-off script could diff names and drop lists
    entity by entity against our SQLite to prove the importer matches their Source-mode view.

## Suggested order

1. A1 + A2 + B7–B10 together (stats, quality, tooltip, icons, provenance), since they all touch
   the item page and the importer schema.
2. A3 (maps).
3. A4 Tier 1 (pre-rendered models).
4. A5 (emulator drop rates), then A6 as small follow-ups.
5. C11 whenever a data refresh makes it worth proving.
