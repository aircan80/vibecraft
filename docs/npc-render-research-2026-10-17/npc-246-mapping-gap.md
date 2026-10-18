# Forever NPC identity → appearance mapping audit

## NPC 246 remains unresolved

The exact Forever 1.60.1.70450 `Creature` export contains 184 NPC identities and 193 display slots; NPC 246 ("Auntie" Bernice Stonefield) is absent. The separately acquired `CreatureXDisplayInfo` export contains exactly the same 193 identity/display pairs, with no additions or differences. `CreatureDifficulty` contains 184 records and no NPC 246. Native model and customization tables can render a specified display but cannot establish which display belongs to this NPC.

No Era identity/display link or name-matched model was promoted into Forever data. No application source, bundled database, or running application was changed during this audit.

## Public source checks

- The public [Wowhead Forever NPC page](https://www.wowhead.com/forever/npc=246/auntie-bernice-stonefield) is indexed with a 3D-view link, but direct access returned HTTP 403 / a browser-verification challenge. The challenge was not bypassed.
- The ordinary, publicly reachable [Wowhead Forever tooltip response](https://nether.wowhead.com/forever/tooltip/npc/246) returned HTTP 200 and name, type/faction and map coordinates. It contains **no display or model identifier**, and no exact client build. This does not establish an appearance mapping.
- [The Forever Era's first-party NPC page](https://theforeverera.com/en/npcs/246/) explicitly identifies its NPC information as Classic 1.12 / CMaNGOS data. [MetaForge's page](https://metaforge.app/wow-forever/database/npcs/auntie-bernice-stonefield) explicitly includes VMaNGOS data. A Forever URL or a plausible portrait does not turn those baseline mappings into a Forever observation.
- An image labelled with the NPC name is insufficient to reverse-infer a numeric display ID. The task requires an explicit identity link from a versioned source.

Raw response bytes and acquired CSVs are retained in `.local/npc-mapping-246/`. The source manifest records requested exact builds, URLs, lengths and SHA256 hashes. These observations support the mapping gap; they do not invalidate unrelated quest, loot, or location evidence.

## A genuine additional hotfix link

The independently reacquired Raidbots-distributed cache remains build **70450**, SHA256 **f6fae4dfd32a6368efcdbe896704a6240db2ee11f5c50a90e1ef850cffc1582b**, Last-Modified **17 October 2026 19:58:35 GMT**. It contains one valid `CreatureXDisplayInfo` record:

| Field | Value |
| --- | --- |
| Record ID | 690799 |
| NPC ID | 278644 |
| Display ID | 8289 |
| Probability | 0 — retained, not treated as a missing value |
| Scale | 1 |
| Push / region | 112821 / 70 |
| Payload | `61200000000000000000803f00000074400400` (19 bytes) |

This link is absent from the 193 base CSV pairs. NPC 278644 is also absent from the current application NPC table: this finding **does not increase portrait coverage for existing browseable NPCs**, establish a name/world spawn, or prove which display is selected in the world. It is a real additional mapping input for future acquisition.

Display 8289 resolves through native `CreatureDisplayInfo` to model 84 / model FileDataID 124248 and texture FileDataID 124251. Asset proof is retained separately after exact-build CASC verification; no unrelated NPC is assigned this model.

The other cache records examined comprise 77 invalidated `Creature` entries and 14 invalidated `CreatureDifficulty` entries, not valid appearance mappings. None provides NPC 246.

The pinned [CreatureXDisplayInfo definition](https://github.com/wowdev/WoWDBDefs/blob/b9a63096cdd99b26967f17f9515377ae62cadfd7/definitions/CreatureXDisplayInfo.dbd) has SHA256 **5f84e7190816c677099a8879d31bf8e647d76b72c4be81eaba780ae2f9ac5951**. The audit explicitly handles this layout's trailing `CreatureID` relation in the flattened hotfix payload, with exact field-order and payload-length checks. It leaves the production generic decoder unchanged. As a format cross-check, [DBCD's hotfix reader](https://github.com/wowdev/DBCD/blob/master/DBCD.IO/HotfixReader.cs) excludes the non-inline ID while decoding other reflected fields; this source review was not a separate execution of that decoder.

Reproduce the mapping audit without modifying application data:

```bash
python3 tools/npcmodels/audit_creature_hotfix.py \
  --cache .local/npc-mapping-246/DBCache-current.bin \
  --cache-sha256 f6fae4dfd32a6368efcdbe896704a6240db2ee11f5c50a90e1ef850cffc1582b \
  --definition .local/npc-mapping-246/CreatureXDisplayInfo.dbd \
  --definition-sha256 5f84e7190816c677099a8879d31bf8e647d76b72c4be81eaba780ae2f9ac5951 \
  --build 1.60.1.70450 --baseline .local/npc-mapping-246/CreatureXDisplayInfo.csv \
  --npc 246 --npc 278644 --output .local/npc-mapping-246/hotfix-mappings.json
```

Three focused tests cover explicit NPC/display identity, zero probability, invalidation rollback, tombstones, and rejection of a wrong build or relation layout. The complete native asset/catalog test directory has 19 passing tests.

## Practical next acquisition paths

1. **Exact-client cache/capture:** a genuine Forever creature query/cache or unit observation containing NPC ID and selected display ID, with product, client build, capture time and original bytes. This can establish the missing identity relationship; asset validation then independently verifies every file against the same build.
2. **Experimental default-appearance client query:** the versioned `classic_beta` [generated CharacterModelBase API declaration](https://github.com/Gethe/wow-ui-source/blob/4d9399a664eb65f2c8d5e96d7a6bf5e234414239/Interface/AddOns/Blizzard_APIDocumentationGenerated/FrameAPICharacterModelBaseDocumentation.lua) exposes `SetCreature(creatureID, displayID=0)` and `GetDisplayInfo()`. A manual, throttled collector could request NPC 246 without travel. This declaration is not an exact 70450 runtime test: whether it resolves server-only IDs remains unverified. Results must be labelled `client-query-default`, distinct from a display observed on an actual world unit.
3. **Versioned public Forever observations:** a legitimately accessible source that explicitly links numeric NPC and display IDs, states its provenance/version and permits reuse. Preserve the raw response and attribution; keep community reports distinct from native client facts.

The existing strict missing-mapping state is correct until one of these paths supplies actual evidence. A rendering fallback cannot repair a missing identity relationship.
