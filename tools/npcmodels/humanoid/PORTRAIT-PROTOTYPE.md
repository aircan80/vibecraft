# Isolated humanoid portrait prototype

This is a **laboratory reconstruction**, not an authorized production portrait.
It is fixed to NPC 15698 / display 15660 / extra 10766 and Forever 1.60.1.70450.
It never writes the application portrait cache or changes the Java application.

Inputs must first be obtained with `NativeAssets.get`, which checks decoded
content against the exact official build root. Copy those verified bytes to a
separate lab directory as `900914.bin` (M2), `900966.bin` (SKIN), `928687.bin`
(HD baked body atlas), and optionally `917856.bin` (actual selected hair texture).
Keep the corresponding asset proofs beside the experiment.

Use the pinned wow.export checkout documented by the parent NPC tooling:

```sh
INSPECT_ONLY=1 NODE portrait-prototype.cjs WOW_EXPORT ASSET_DIR OUTPUT_DIR
```

`NODE` above means the actual pinned Node executable. Inspection writes native
submesh IDs and texture mappings. Generate the customization-only mask with
`customization_recipe.py` using those IDs; this mask is not final equipment
visibility. Then:

```sh
PROTOTYPE_RECIPE=recipe.json NODE portrait-prototype.cjs WOW_EXPORT ASSET_DIR OUTPUT_DIR
BLENDER --background --factory-startup --disable-autoexec \
  --python render_prototype.py -- OUTPUT_DIR/partial-humanoid.glb OUTPUT_DIR/rendered --head
```

The adapter accepts only the matching explicit incomplete-prototype recipe.
Only meshes with known body/hair materials are exported; unresolved material
meshes are recorded and omitted. It uses the real HD bake, and never substitutes
that atlas for a hair texture. Source hashes and the full recipe are persisted.
Three fixed eye/effect textures outside the exported known-material meshes
are not acquired; upstream logs their omission while exporting all texture slots.

Remaining limitations are recorded in `prototype-limits.json`: encrypted option
records remain unavailable, equipment beyond the optional known helmet remains incomplete,
customization bone adjustments and remaining material conditions are not
applied. The image is a native bind pose. A correctly dressed baked body does not
prove that the full NPC appearance has been reconstructed. Do not put this image
in the app or mark its appearance ready without resolving those limitations.

## Known eye and helmet lab slices

A verified `3484614.bin` enables selected eye geoset3301. The adapter checks the
actual pinned `ShaderMapper`: shader16403 is `Combiners_Mod_Add` with vertex
shader `Diffuse_T1_T2`. The Blender material samples separate UV arrays despite
both slots referencing one texture. It adds lit tex1 RGB and additive tex2 RGB
without clamping scene-linear values, tests `tex1.a + tex2.a < 0.501960814`, and
uses opaque mesh opacity for surviving blend-mode1 fragments. Native flags0
backface culling is retained. Cycles studio lighting is an explicit approximation
of exterior lighting; no world fog or animation is claimed.

For the independently resolved helmet recipe, acquire verified `137812.bin`,
`482811.bin` and `137827.bin`, then add
`PROTOTYPE_EQUIPMENT_RECIPE=equipment-recipe.json` when exporting. The only
accepted head variant is display33918, race7/sex0 model137812 and texture137827.
It exports `native-helmet.glb` in the character bind-pose coordinate system:
attachment11 selects bone172 and native position `[x,y,z]` becomes `[x,z,-y]`.
No animation is applied, so the structural bone chain has identity deformation
matrices. Pivots are not added twice. The full attachment, parent chain and
transform are persisted in `helmet-proof.json`.

Pass `--helmet` to Blender together with `--head`. Helmet visibility249 hides
geosets1–99 and201–299, including hairstyle3, while retaining base0. The actual
CreatureDisplayInfo scale0.40000000596 and model scale1 apply equally to the body
and helmet before automatic orthographic framing. The head option also makes a
real 128×128 render, rather than merely resizing its HTML presentation.

Run numeric eye/alpha/UV/contract tests with Python unittest discovery for
`test_eye_shader.py`; run native attachment/hierarchy/visibility checks with the
pinned Node executable and `test-equipment-transform.cjs`. These tests do not
prove a complete appearance. Missing encrypted options, customization bone
adjustments, remaining material conditions and other equipment still prevent
production promotion.
