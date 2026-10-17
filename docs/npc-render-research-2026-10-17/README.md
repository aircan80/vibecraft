# NPC portraits and models: feasibility review

17 October 2026. Three research agents examined the Wowhead reference, native appearance data and independent rendering tools. This is a grounded proposal with acquired data, not a completed NPC renderer. The app and bundled database are unchanged.

## Conclusion

**Proceed with a small native-data proof of concept, beginning with generated portraits.** Use the same identity and asset pipeline later for rotatable turntables and an optional animated model viewer. Render actual game assets rather than using AI illustrations or copying Wowhead images.

Recommended architecture:

```text
NPC + exact build → proven display variant → model + textures + customization
  → extracted native assets → appearance-aware export → fixed render scene
  → cached portrait/turntable + provenance manifest → asynchronous Swing display
```

The strongest initial tool candidate is [wow.export](https://github.com/Kruithne/wow.export), followed by a pinned Blender rendering worker. Its export code needs integration; a ready-to-run headless NPC batch command has not been demonstrated. Geometry alone is insufficient: skins, geosets, equipment, transparency and attachments determine whether the NPC actually looks right.

## What we proved

- Successfully acquired native **1.60.1.70450** exports for Creature (184 rows), CreatureDisplayInfo (14,218), CreatureModelData (1,193) and CreatureDisplayInfoExtra (8,994).
- Resolved a real control: **Mechanical Squirrel, NPC 2671 → display 7937 → model 134 → model file 126061**, with texture file **126062**.
- Archived request outcomes, raw exports and checksums outside the shipping database. Small evidence records are copied into [evidence](evidence/).
- The current app's NPC schema has no model/display relationship. Its existing icon and map infrastructure can support cached images, but a dedicated appearance/render manifest is needed.

## Seeking Remnant: unresolved identity

The canonical data contains **247003, Seeking Remnant**, as a named NPC. It is absent from the newly acquired Creature table, which is not a complete NPC catalog. Its exact display/model mapping remains unresolved. A creature-query/cache record or another same-build native source must establish that link; name matching is insufficient.

The supplied Wowhead page was inaccessible to the research browser. We did not inspect its current network/model requests or extract its images. No M2/SKIN dependency set or actual rendered portrait has been produced. Bounded searches found no game installation in the checked local paths; this is not a claim about every drive or location.

## Proposed first deliverable

1. Extract the proven Mechanical Squirrel model and dependencies from the matching build.
2. Generate a transparent portrait and a studio-background image with pinned camera, pose and lighting; record every input/output hash.
3. Validate a multi-skin creature, an equipped humanoid and a spectral/transparent creature. Missing dependencies produce an explicit unavailable state, not a misleading partial portrait.
4. Resolve Seeking Remnant's appearance independently, then render it through the same pipeline.
5. Add the portrait to the NPC header with an optional full-body preview. Support multiple appearances where the source actually records them. Load images off the UI thread.
6. Add cached turntables before considering an embedded real-time 3D engine. No storage or generation-time claims should be made until the sample is measured.

This keeps 3D decoding/GPU setup out of the normal desktop browse path. A render is a model preview, not evidence of an NPC's location, behavior or loot.

## Research and evidence

- [Wowhead reference and independent tooling](wowhead-reference.md)
- [Actual native table audit and NPC identity chain](data-pipeline.md)
- [Renderer options, limitations and proof-of-concept gates](rendering.md)
- [Verified control rows](evidence/mechanical-squirrel-chain.json)
- [Acquisition attempts and hashes](evidence/acquisition-attempts.json)

The individual reports link directly to inspected primary definitions and source code. Tool licensing and asset provenance are recorded separately; this review does not decide a redistribution policy.
