# Experimental Forever NPC appearance capture

This isolated addon collects **observed target appearances**, with no dependency on the existing loot collector. It has not been accepted against a real Forever client. Do not promote its output into the shipped catalog until that acceptance and an importer review are complete.

Copy this directory to the Forever client's `Interface/AddOns`, target an NPC, and type `/wfappearance`. Wait a second, then `/reload` or log out to save `WowForeverAppearanceCollector.lua` under account SavedVariables. No arbitrary NPC scan, target change, combat input, client installation, or network query is performed. Opening the tiny transparent model frame can trigger the client's normal model loading for the already selected target.

The addon rejects builds other than **1.60.1.70450**, records no player/realm names or raw GUIDs, discards a capture if the target changes, and treats secret/restricted API values as unavailable. Observations are capped at 20,000 and deduplicated by NPC/display pair. The one-second model-loading window is conservative but not a runtime guarantee; slow models may need another manual capture.

Each observation preserves `product`, exact `build`, `npcId`, `displayId`, API source, server timestamp (when available), and `scope=observed-instance`. An observed display can reflect transformations, quests, or phases. It is **not** evidence of a canonical default, equipment, every alternate appearance, or the whole NPC roster. Future importer logic must retain those distinctions and conflict evidence. It must join only the exact-build `CreatureDisplayInfo`/`CreatureModelData` records, preserving unsupported humanoid/equipment states.

Run the synthetic behavior checks in an isolated Python environment with `lupa` installed:

```sh
python -m unittest discover -s tools/collector/tests -v
```

The tests are API-contract simulations, not client validation.
