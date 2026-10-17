# NPC rendering: Wowhead reference and independent pipeline

Research date: 2026-10-17. Scope: feasibility and recommendation; no production code changed, no Wowhead models/images downloaded.

## What was and was not observed

The user’s [Seeking Remnant, NPC 247003](https://www.wowhead.com/forever/npc-247003/seeking-remnant) page could not be opened by the browsing tool. Its exact current UI, portrait, display ID, animation list, and network requests were **not verified**. Do not present a guessed creature render as Seeking Remnant or infer its appearance from its name. The URL establishes an NPC entry ID, not a model or display ID.

Wowhead-style model viewing is a useful interaction reference: a stable portrait on the NPC page, with an optional larger interactive model view. This is a proposed experience, not an assertion that this specific inaccessible page currently exposes both.

## What existing tools actually support

The MIT-licensed [wow.export repository](https://github.com/Kruithne/wow.export) documents Classic and Retail support, client/local or CDN data access, native M2/WMO previews, and model export including glTF. Its [changelog](https://github.com/Kruithne/wow.export/blob/main/CHANGELOG.md) records a Creatures tab supporting custom and character-model NPCs and animated glTF export. This makes native extraction and our own rendering technically credible, but does not prove every Forever beta model works.

The project’s [DBCreatures source](https://github.com/Kruithne/wow.export/blob/main/src/js/db/caches/DBCreatures.js) explicitly resolves CreatureDisplayInfo.ModelID through CreatureModelData.FileDataID and retains display texture variants and display-specific geosets. Several display IDs can share geometry. An NPC entry, display ID, model-data ID, and file-data ID are distinct identifiers.

The [creature UI implementation](https://github.com/Kruithne/wow.export/blob/main/src/js/modules/tab_creatures.js) handles character-style NPCs separately using extra display/customization data, armor display IDs, weapon items, composited textures, attachments, and geosets. Therefore a naked race model is not an adequate fallback for a dressed NPC. Missing appearance dependencies should produce an explicit unavailable state.

The [public wow-model-viewer wrapper](https://github.com/vincentnderitu/model-viewer) requires the Wowhead/ZAM JavaScript viewer and its model-content service. It is a wrapper around their assets, not a standalone native WoW decoder. Its documented setup uses remote content and a proxy. It would introduce the wrong dependency for our offline, independently generated media. No attempt was made to mirror that content.

[WoW Model Viewer](https://github.com/wowmodelviewer/wowmodelviewer) is another native-client reference; its current documentation describes Windows, an embedded Unity renderer, local game data, and PNG capture. This is useful as a visual comparison tool, but is not a drop-in Linux Swing widget.

## Recommended first deliverable

Generate a small **build-pinned portrait pack** from actual local client assets, outside the Java UI. The application should asynchronously load cached PNG/WebP portraits and display them in an NPC summary card. This is simpler to distribute, works offline, and keeps the Swing event thread free of model decoding or GPU initialization. An optional full viewer can follow after the identity chain is validated.

Proposed pipeline (architecture recommendation):

1. Resolve NPC entry to one or more display IDs from same-build native records or explicitly sourced creature-query data. Preserve alternatives; do not name-match or substitute a different creature.
2. Resolve display to model geometry, texture substitutions, geosets, scale and optional character customization/equipment. Verify the exact build definitions, rather than assuming current Retail layouts: [CreatureDisplayInfo definitions](https://github.com/wowdev/WoWDBDefs/blob/master/definitions/CreatureDisplayInfo.dbd), [CreatureModelData definitions](https://github.com/wowdev/WoWDBDefs/blob/master/definitions/CreatureModelData.dbd).
3. Extract the exact referenced assets from local CASC, including geometry sidecars, textures and applicable skeleton/animation dependencies. Native asset availability and encryption must be measured on the installed Forever build.
4. Render a neutral idle frame using a pinned exporter/renderer, fixed camera and lighting. Keep full-body framing so size, silhouette and equipment remain useful to players. Never synthesize missing details with generated artwork.
5. Write a media manifest with product/build, NPC and display IDs, referenced file IDs and hashes, renderer version, pose, camera, output hash and unresolved dependencies. Cache by appearance plus build and renderer settings, not NPC ID alone.
6. Smoke-test a simple creature, transparent/particle creature, multi-skin creature, equipped humanoid, and an unavailable/encrypted model before batch generation. Include NPC 247003 only when its identity and assets are actually resolved.

A subsequent interactive viewer should support rotate, zoom, reset camera, animation selection and pause. Evaluate an independent glTF renderer or a separate local viewer process. Embedding a browser/WebGL or native GPU context adds packaging, focus, accessibility and GPU-driver work to Swing; it should not block useful portraits.

## Honest states and open questions

The UI should distinguish “model not yet generated”, “display identity unknown”, “client asset unavailable”, “unsupported rendering effect”, and “multiple appearances”. A source/build caption can live behind a small details action. A static render is a model preview, not an in-game screenshot; it does not establish the NPC’s location, live behavior, loot, or encounter mechanics.

Still unverified: Seeking Remnant’s exact display/model IDs, native Forever table coverage for NPC-to-display identity, complete local geometry/texture availability, an automated headless render of this build, particle fidelity, and render-pack storage cost. These are proof-of-concept gates, not reasons to scrape Wowhead.

The [wow.export MIT license](https://github.com/Kruithne/wow.export/blob/main/LICENSE) covers its software. It is not a grant of rights over Blizzard’s game assets; record asset provenance separately from tool licensing. No redistribution policy is decided by this research.
