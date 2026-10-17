### Task 8: Crafting data from the client (owner request)

**Files:** tools/clientdata `REQUIRED_TABLES` + `SpellReagents`, `SkillLineAbility` (and `SkillRaceClassInfo` if present); importer `clientdata/CraftLoader` (SpellEffect Effect = 24 "Create Item" → `EffectItemType`, `EffectBasePoints`+1 = quantity; SkillLineAbility → skill id, min/trivial ranks; SpellReagents → reagents; recipe items: `ItemEffect` with a "learn spell" trigger whose taught spell creates an item → `source recipe_item_id`); schema `craft`, `craft_reagent` (+ indexes on item_id); `ClientDataStage` wiring; report counters `clientdata.crafts`, `clientdata.crafts.noSkill`; fixture CSVs cut for a few crafts (e.g. Heavy Leather, a Blacksmithing weapon, a cooking recipe); shipped-data checks (3+ crafts for a known item, reagent lists non-empty).
**Tests:** `CraftLoaderTest` (created item + quantity, reagents, skill ranks, recipe item link, a spell that creates nothing is skipped), `ImportPipelineTest` counts.
- [ ] Commit `feat(clientdata): crafting recipes and reagents`.

