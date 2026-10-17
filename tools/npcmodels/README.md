# Native NPC portrait proof of concept

This isolated worker converts real WoW M2/SKIN/BLP inputs to a GLB, then renders
our own PNG in Blender. It does not use a Wowhead screenshot or AI image. It does
not modify the application database or require a browser/3D library in Swing.

The first control is **NPC 2671 Mechanical Squirrel → display 7937 → model 134 →
M2 126061**, display texture **126062**, SKIN **479933**, scale **1.3**. The
relationship was read from archived Forever 1.60.1.70450 tables. This simple
opaque creature has one submesh, 157 vertices and 138 triangles.

**Build identity verified:** all three extracted inputs match content keys in the
actual Forever **1.60.1.70450** Blizzard CASC root. The proof resolves the official
build configuration `84bea4233331e390fda8724c6f91ffa5`, verifies decoded encoding
content key `c441a5427cb86b23bbfb6ed9c6cf0114` and root content key
`8a0ca94be2f6ace93991c04ca6e19b57`, then matches the model, SKIN and texture
bytes to that root's FDID entries. This is a genuine Forever-build control render.
The exact proof is `.local/npc-render-poc/attestation/content-proof.json`.

An important acquisition pitfall remains documented: the Wago API accepted an
invalid version and silently supplied fallback bytes. Its URL alone did not prove
build identity. Independent Blizzard encoding/root verification resolved that
uncertainty; no Era assets were substituted. Original request/negative-control
records are preserved in the asset manifest.
Seeking Remnant (247003) still has no supported display mapping in acquired data.

## Reproduce locally

Requirements: Python with the project's clientdata dependencies, Node 22.15.0,
a checkout of [wow.export](https://github.com/Kruithne/wow.export) at
`c2fd7bde36a712be78a5da896c995b84fbfa2545`, Blender 4.5.3 LTS.
Tool downloads live in ignored `.local/npc-render-poc/toolchain`.

```bash
python3 tools/npcmodels/fetch_assets.py --build 1.60.1.70450 \
  --product wow_classic_beta --model 126061 --texture 126062 \
  --output .local/npc-render-poc
python3 tools/npcmodels/attest.py
node tools/npcmodels/export-m2.cjs .local/npc-render-poc/wow-export \
  .local/npc-render-poc .local/npc-render-poc/converted
blender --background --factory-startup --disable-autoexec \
  --python tools/npcmodels/render_portrait.py -- \
  .local/npc-render-poc/converted/mechanical-squirrel.glb \
  .local/npc-render-poc/rendered
```

`export-m2.cjs` is deliberately a single-control adapter, not a generic NPC
renderer. It invokes the real upstream M2 loader, texture variant resolution,
SKIN geometry assembly, BLP decoder/PNG encoder and GLB writer. The adapters
replace the GUI settings, logging and online CASC access with a strict local
FDID resolver. Optional WebP conversion is unused. No remote fallback exists.
External skeletons, animations, equipment, particles and unsupported material
modes are not accepted as proven by this control. Native bind pose is rendered;
this is not an animated in-game pose.

The worker emits an embedded-texture GLB and conversion.json. Blender imports
the GLB into a clean scene, frames the actual bounds, applies native display
scale, uses an orthographic three-quarter camera and CPU Cycles lighting, and
writes a 768×768 transparent PNG with a render-report.json containing settings,
input/output SHA256 and elapsed time. No downloaded blend file is executed.

The upstream MIT license is retained in `WOW-EXPORT-LICENSE.txt`. It covers
upstream tool code; it does not grant redistribution rights to game artwork.
Original game assets and generated POC output remain in ignored local storage.

## First actual render result

Completed locally with Blender **4.5.3 LTS**, build `67807e1800cc`:

- Input GLB: 322,936 bytes; SHA256
  `c09c3311392f79747e12a648fbc57e90078ef9c36d4881d7291471a8f63c16b6`.
- Output: `.local/npc-render-poc/rendered/mechanical-squirrel.png`, 768×768 RGBA;
  SHA256 `71982d7beff2d6452c37bd3c92ca784b08ca74bed7b482fea550da6a6308b10d`.
- CPU render: 48 samples, 8.343 seconds including scene import and PNG write.
- Visual inspection: complete mechanical squirrel with native red/gold/metal skin,
  green eye, tail and feet, fully inside the frame. Transparent margins confirmed.
  No independent in-game comparison has been performed.
- Blender's glTF importer adds an armature custom-shape Icosphere. The worker
  excludes custom-shape objects from framing and measures actual mesh vertices;
  otherwise this invisible helper makes the creature look much too small.

Node archive (official v22.15.0 Linux x64) SHA256:
`dafe2e8f82cb97de1bd10db9e2ec4c07bbf53389b0799b1e095a918951e78fd4`.
Blender official 4.5.3 Linux x64 archive SHA256:
`975c58fcb244273838534bba771e64ad87739216b0f9b39a888531a49a72d845`.
Both were compared with their upstream checksum listings. Blender download and
build details are in the ignored `toolchain/blender-bootstrap-report.json`.


## Optional turntable review

Append `--turntable` after the renderer output directory to create twelve genuine
30-degree views plus an opaque studio portrait. These are still views of the
native bind pose, not an idle animation or live 3D. Copy `turntable-viewer.html`
into the output directory as `index.html`, then open it locally or serve the
parent directory on loopback. It supports drag, arrow keys and a labelled slider;
no network JavaScript or external viewer dependency is used. The report retains
all frame hashes and the exact official build proof reference.

The completed review is `.local/npc-render-poc/review/index.html`: twelve 768 px
views, transparent three-quarter portrait and opaque studio portrait, about
6.1 MB combined PNGs. The complete batch took **86.118 seconds** on this CPU.
Every turntable alpha bounding box stays inside the image. This validates offline
still rotation for this one control; it does not establish animation, arbitrary
NPC appearance assembly or spectral/material correctness for other NPCs.
