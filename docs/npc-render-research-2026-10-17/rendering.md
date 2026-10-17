# NPC renders: offline rendering feasibility

Research date: 2026-10-17. This is an implementation proposal, not a claim that the supplied NPC has already been rendered. No application code, database or client installation was changed.

## Recommendation

Generate our own transparent NPC portraits from build-pinned client assets, using a separate extraction/rendering toolchain. Show the resulting PNG in Swing. Add a drag-to-rotate sequence of still images after proving appearance correctness. Keep interactive 3D as a later optional viewer.

This is technically feasible with existing tools. The important unknown is not whether an M2 mesh can be drawn: it is whether we can prove the exact NPC-to-display/skin/equipment mapping for the selected Forever build. An attractive render of the wrong skin is still incorrect. Generic model previews must be labelled separately from verified NPC appearances.

## Existing project fit

The current pipeline already downloads build-pinned DB2 CSV exports and FileDataID assets, hashes inputs in build locks, caches failures, converts BLP icons through Pillow, and imports PNG blobs for offline display. Relevant files:

- `tools/clientdata/clientdata/fetch.py`: existing HTTP/cache/build validation and atomic writes. Its current `fetch_files` writes every FDID as `.blp`; model extraction needs a typed asset manifest rather than reusing that assumption.
- `tools/clientdata/clientdata/icons.py`: BLP-to-PNG, currently 56 px icons.
- `app/src/main/java/dev/wowforever/app/db/AssetRepository.java`: icon and named UI texture blob access.
- `importer/src/main/resources/dev/wowforever/importer/schema.sql`: `icon` and `ui_asset` tables; neither currently models NPC display variants or render provenance.

No `.m2`, `.skin`, `.skel`, `.anim`, `.glb` or `.gltf` files were found beneath this repository's `.local` cache in this inspection, and Blender is not installed on PATH. Therefore there is no honest ready-made local NPC render to attach to this report. No full client download was attempted.

## Tool choices verified against primary sources

| Tool | Useful capability | Fit and limitation |
| --- | --- | --- |
| [wow.export](https://github.com/Kruithne/wow.export) | Client/CDN extraction; Classic support; M2/WMO preview; OBJ/glTF export; BLP conversion | Best initial extraction and appearance investigation tool. MIT project. We have not proved Forever 1.60.1.70450 compatibility end to end. |
| [wow.export M2 exporter](https://github.com/Kruithne/wow.export/blob/c2fd7bde36a712be78a5da896c995b84fbfa2545/src/js/3D/exporters/M2Exporter.js) | Explicit creature variant textures, geoset masks, GLB embedding, equipment and skeleton dependencies | Concrete implementation to study or adapt in a separate worker. It depends on application state and CASC helpers; it is not a drop-in standalone Node CLI. |
| [Blender](https://github.com/blender/blender/blob/main/source/creator/creator_args.cc) | Background execution, Python scripts, frame rendering, factory startup and disabling automatic script execution | Suitable deterministic rendering worker after glTF conversion. Pin the released binary/version. Do not feed an arbitrary downloaded `.blend` file to an unrestricted worker. |
| [WoW Model Viewer](https://github.com/wowmodelviewer/wowmodelviewer/blob/8f4e791f626b67e453abb05b877249ec82fea4da/readme.md) | Current program reads Retail/Classic, exports PNG screenshots/textures and OBJ | Valuable visual reference and manual validation on Windows. Current documented viewport is a separate Unity player; Linux automation is not demonstrated. GPL-3.0-or-later plus third-party notices. |
| [CascLib](https://github.com/ladislav-zezula/CascLib) / [CASCExplorer](https://github.com/WoW-Tools/CASCExplorer) | Read/extract CASC storage | Alternatives when extracting assets directly. Extraction does not provide creature appearance assembly or rendering. Review each dependency's actual license before bundling; do not infer CASCExplorer permission from its public repository. |
| [old Blender WMO scripts](https://github.com/WowDevTools/Blender-WMO-import-export-scripts) | Old WMO editing | Explicitly deprecated, archived in 2023, and tied to Blender 2.79b; unsuitable foundation for a new NPC pipeline. WMO buildings and M2 creatures are different concerns. |

Source snapshot inspected: wow.export `c2fd7bde36a712be78a5da896c995b84fbfa2545`; WoW Model Viewer `8f4e791f626b67e453abb05b877249ec82fea4da`. Upstream feature descriptions establish feasibility, not our own build compatibility.

The exporter does not guarantee perfect conversion of every WoW effect. In particular its [glTF writer](https://github.com/Kruithne/wow.export/blob/c2fd7bde36a712be78a5da896c995b84fbfa2545/src/js/3D/writers/GLTFWriter.js) explicitly skips unsupported spline interpolation. Materials, transparency, additive glows, particles and attachment placement need visual acceptance; a geometrically valid GLB can still look different from the game. This is especially relevant to spectral NPCs such as the user's Seeking Remnant example.

## Proposed pipeline

```text
NPC identity + source/build evidence
  -> candidate display variants (never name-matched)
  -> display/model/texture/customization dependencies
  -> typed FileDataID extraction + dependency hashes
  -> appearance-aware M2 export to GLB
  -> fixed studio scene, pose, camera, lighting
  -> transparent PNG + optional turntable frames
  -> content-addressed cache + provenance manifest
  -> asynchronous Swing image load
```

1. Resolve NPC identity to one or more supported display IDs, preserving which source asserted each mapping. Do not treat a model named after an NPC as proof. Retain variants rather than silently picking the first. Keep uncertainty in this step visible.
2. Resolve client tables and M2 references in the same build. Expect model geometry, SKIN submesh data, BLP textures and possibly SKEL/ANIM files, geoset choices and attachments. Humanoid equipment/customization is a separate complexity tier. NPC ID, display ID and model FDID are different identifiers.
3. Extract only the dependency closure for a small approved sample. Build pinning must refer to the actual CASC build/encoding/root identity, not merely a request against whichever CDN product happens to be newest. Missing or encrypted dependencies become explicit statuses; there is no automatic Era substitution.
4. First export one static creature appearance with wow.export's UI to confirm all dependencies. Then build a maintained batch adapter around pinned source. The inspected repository has no obvious documented general headless NPC-export CLI; automation is engineering work, not an assumed command-line flag.
5. Import GLB into a clean Blender scene. Use orthographic or mild perspective framing from the combined visible geometry bounds; preserve appendages/weapons. Render a consistent three-quarter pose to 512 px RGBA PNG, then produce the display size thumbnail. Store camera/pose overrides for outliers. Prefer a diffuse studio look over dramatic lighting that hides identifying features.
6. Validate the output visually against the game or an independently checked reference; a Wowhead page is useful for comparison, while its image/viewer output is not an input to our generated asset pipeline.
7. Load images off the Swing EDT. Bound decoded-image memory, show a stable-size placeholder, and cancel obsolete loads when navigation changes. Existing entity navigation must remain usable if media is missing or rendering failed.

Suggested cache key:

```text
sha256(product + build/root identity + appearance recipe + sorted dependency hashes
       + exporter commit + renderer version + scene/camera/pose recipe)
```

A render manifest should retain `npcId`, candidate/selected `displayId`, relationship source and confidence, build/locale, model and dependency FDIDs/hashes, missing dependencies, geosets, textures, attachments, render settings, tool versions, output SHA/dimensions, validation status and timestamps. Store original evidence separately from the selected appearance, just as current source comparisons preserve competing item facts.

A separate optional media pack/cache is preferable to putting every full-size model and turntable into the already large application database/JAR. The canonical database can index render manifests and packaged thumbnails. Cache misses must not trigger an unexpected huge client download while browsing.

## Display options

| Approach | User experience | Engineering and operational cost |
| --- | --- | --- |
| Transparent PNG portrait | Immediate recognisable NPC in header; offline; no GPU dependency in app | Lowest risk. One viewpoint and pose. Recommended first milestone. |
| 24-angle still sequence | Drag to rotate, keyboard left/right, reset view | Moderate. Approximately 24 times the compressed image storage per resolution before deduplication; lazy frame decoding essential. No live pose or arbitrary camera. |
| Animated WebP/APNG/video | Idle animation | More encoders/decoders, motion accessibility and memory considerations. Java ImageIO support must be explicitly packaged/tested. PNG is the safe initial format for this app. |
| Local glTF viewer in JCEF | True rotation/zoom and possible animation | [JCEF](https://github.com/chromiumembedded/java-cef) embeds Chromium through native libraries; [jcefmaven](https://github.com/jcefmaven/jcefmaven) helps distribute artifacts. Adds per-platform binaries, GPU/driver testing, browser updates, focus/input integration and process lifecycle work. Load only our local viewer/assets and block external navigation. |
| JOGL native renderer | Direct Swing/OpenGL integration | [JOGL](https://github.com/JogAmp/jogl) provides Java OpenGL bindings, not a complete M2/glTF creature viewer. We would own scene loading, shaders, animation, cameras and context lifecycle. Highest implementation burden here. |

The cost ranking is an engineering assessment, not a benchmark or delivery-time promise. A still turntable and real interactive 3D should be described honestly in the interface.

## POC acceptance and sequence

The parallel datamining audit has now established a concrete build 70450 control: **NPC 2671 Mechanical Squirrel → CreatureDisplayInfo 7937 → CreatureModelData 134 → M2 FileDataID 126061**, with texture FDID 126062 and display scale 1.3. This is the recommended first extraction/render candidate; preserve that audit's original table rows and hashes in its render manifest. The audit obtained Creature, CreatureDisplayInfo, CreatureModelData and CreatureDisplayInfoExtra tables successfully. NPC 247003 was absent from the acquired Creature table, so the user's Seeking Remnant appearance remains unresolved rather than inferred from the reference page. Neither the bounded installation search nor this repository cache inspection found a usable CASC client/model closure. Consequently the control has a proven table relationship, **not yet an extracted/rendered model**.


**Gate A — mapping and extraction:** six small representative cases: simple animal, ordinary humanoid, equipment-bearing humanoid, transparent/glowing NPC, large boss and an NPC with multiple displays. Include NPC 247003 only after its binding is supported by evidence. Produce a manifest and precise missing-dependency report for every case.

**Gate B — rendering:** at least one genuine extracted appearance rendered from our pipeline with no missing dependencies; compare skin, silhouette, geosets and alpha to a reference. Then expand to the six-case sample. The spectral case must not be accepted simply because an opaque mesh renders.

**Gate C — app prototype:** add a portrait to an isolated NPC-page prototype. Confirm keyboard navigation, light/dark backgrounds, 420 px width, cancelled navigation loads and missing-image fallback. No network required to reopen the image.

**Gate D — scale:** measure dependency bytes, portrait size, render duration and decoded memory on the real sample before committing to all-NPC coverage. Batch deduplicate by appearance recipe; many NPC IDs can share one appearance. Capture failure reasons rather than substituting unrelated models.

These are research gates. The main uncertainty is data completeness and appearance fidelity, followed by automating the export tool. Basic PNG presentation is comparatively straightforward.

## Licensing and process boundaries

The [wow.export MIT license](https://github.com/Kruithne/wow.export/blob/c2fd7bde36a712be78a5da896c995b84fbfa2545/LICENSE) covers its tool code, not ownership or redistribution rights to Blizzard assets. Preserve notices for adapted code and record the game's asset provenance. Generated portraits still derive from game artwork; do not equate tool licensing with a blanket asset-distribution grant. This report makes no legal conclusion about shipping a public media bundle.

Run extraction/rendering as explicit build/import work, with a limited input directory, process timeout and output limits. Do not execute uploaded scripts, shell-interpolate model paths, enable Blender auto-run, expose a rendering HTTP service publicly or load remote JavaScript to show a local portrait. Keep render errors separate from NPC data correctness.
