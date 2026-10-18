# Exact-build humanoid material planning

This isolated extension advances portrait dependencies without relaxing the production renderer's humanoid guard. It does not produce a portrait yet. A baked body texture atlas is not a ready-made NPC picture.

`decode-native-table.cjs` adapts the reviewed wow.export WDC reader entirely offline. It requires the pinned upstream commit, a supplied DB2 binary, and a supplied DBD definition. It records both input hashes. The caller must first validate the binary against the official exact-build CASC root using `NativeAssets`; the decoder alone is not an attestation mechanism. The DBD used for the recorded run is from WoWDBDefs commit `b9a63096cdd99b26967f17f9515377ae62cadfd7`.

`plan-materials.py` joins native CreatureDisplayInfoExtra baked-material resource IDs against TextureFileData UsageType 0, preserving ambiguous or missing relationships. It requires the same exact Forever build and the verified TextureFileData input hash, pinned build-config/root identity, and file ID. It emits dependencies, not render requests. Every output remains `DEPENDENCY_PLAN_ONLY` until customization/geosets and equipment are assembled.

The actual run decoded 58,693 rows from root-verified TextureFileData (982459). All 33 mapped humanoid variants have plans: 61 material references resolve uniquely and 5 remain missing. Both standard and HD candidates remain explicit; neither is automatically selected based merely on availability. These plans provide no NPC-to-display mapping for Auntie Bernice Stonefield (246), whose Creature record is absent from the acquired table.

Evidence, decoder hashes, exact native input proof and plans are in `docs/npc-render-research-2026-10-17/evidence/humanoid-70450/`. The input was transported through Wago, then independently matched against the official Forever root; Wago's version parameter alone is not proof. Two other necessary table transports currently fail verification, which is recorded rather than substituted.

```sh
node decode-native-table.cjs WOW_EXPORT TextureFileData VERIFIED_DB2 PINNED_DBD decoded.json
python plan-materials.py --catalog CATALOG --textures decoded.json --proof NATIVE_ASSET_PROOF --output plan.json
python -m unittest discover -s tools/npcmodels/humanoid -v
```

Next portrait slice: acquire verified CreatureDisplayInfoOption and NPCModelItemSlotDisplayInfo, then resolve their customization/geoset and equipment dependencies. Apply those selections and skin texture to a real mapped humanoid M2; validate a single portrait before enabling it in the app. Shared appearance cache identity must include the full new composition recipe.

`acquire-inputs.py` reproduces native acquisition + pinned-definition offline decoding and records a manifest after every table. Pass `--native-tools` pointing to the existing `tools/npcmodels` directory, `--metadata` pointing to the verified attestation seed, and explicit cache/upstream/Node/output paths. It returns failure when a table is unavailable and preserves successful inputs for the archive transport follow-up. The decoder now records skipped encrypted sections so a partial table cannot be mistaken for complete customization coverage.
