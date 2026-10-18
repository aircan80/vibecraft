# Expanding exact-Forever NPC appearance coverage

2026-10-17. Isolated follow-up; no production catalog or Java pipeline changes.

The reviewed native `Creature` table supplies 184 NPC records, whereas the app's NPC corpus is much larger. `CreatureDisplayInfo` and `CreatureModelData` describe appearances but do not independently establish the missing NPC-to-display relationships. Matching names, using Era mappings, or choosing nearby IDs would fabricate that relationship.

A read-only search of the owner's Downloads and current repository `.local` files found no `CreatureCache.wdb` or installed client cache to validate. Historical WDB documentation does not establish the current Forever creature-cache payload layout. No WDB parser is supplied: accepting an older packet layout here could silently assign incorrect display IDs. An actual exact-build cache and matching verified format are needed before implementing that path.

## Concrete independent acquisition path

The isolated experimental addon `tools/collector/WowForeverAppearanceCollector` records the selected NPC's actual observed display through `PlayerModel:SetUnit("target")` and `GetDisplayInfo()`. Nine synthetic tests pass: genuine-format capture, wrong-product/version rejection, target-change rejection, secret-value rejection, overlapping requests, duplicate pairs, player exclusion, rejected model assignment, and secret identity rejection. It writes separate SavedVariables, avoiding changes to the established loot collector schema or current app build.

Primary generated API definitions confirm `GetDisplayInfo`, `SetUnit`, and `SetCreature(creatureID, displayID=0)` exist in the classic_beta UI source. Immutable source reviewed:

- [FrameAPICharacterModelBaseDocumentation.lua](https://github.com/Gethe/wow-ui-source/blob/4d9399a664eb65f2c8d5e96d7a6bf5e234414239/Interface/AddOns/Blizzard_APIDocumentationGenerated/FrameAPICharacterModelBaseDocumentation.lua)

This is declaration evidence, not proof that Forever permits all those calls. Runtime secret restrictions and model-loading behavior remain acceptance gates. `SetCreature` could support a bounded, explicitly initiated query pass eventually, but an API declaration alone does not prove it resolves server-only NPC records reliably; the experimental addon does not scan IDs or claim this coverage.

## Integration sequence

1. Accept one real Forever 70450 observation, including one restricted API failure, with no player identity in saved output.
2. Add a strict SavedVariables importer using the repository's existing Lua parser. Preserve input file hash and row path; reject absent/mixed/wrong-build stamps. Keep observation and native-default sources separate.
3. Validate display IDs against the exact-build native tables and reuse the existing content-attested model/texture/render cache pipeline. Do not turn humanoid/equipment unsupported states into guessed simple creatures.
4. Show alternate observed appearances and disagreements as explicit provenance in the NPC page. Keep existing native mappings; an observed transformation must not overwrite a default.
5. If a real exact-build WDB cache becomes available, verify its header and payload format before a second read-only importer.

This path can grow verified coverage while the owner explores the game. It cannot honestly promise all roughly 13,000 NPCs immediately or complete automatic coverage without an additional authoritative query/cache source.
