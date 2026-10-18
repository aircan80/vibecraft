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
Three fixed eye/effect textures unused by these selected body/hair meshes are
not acquired; upstream logs their omission while exporting all texture slots.

Remaining limitations are recorded in `prototype-limits.json`: encrypted option
records remain unavailable, equipment attachments/visibility are not assembled,
customization bone adjustments and remaining material conditions are not
applied. The image is a native bind pose. A correctly dressed baked body does not
prove that the full NPC appearance has been reconstructed. Do not put this image
in the app or mark its appearance ready without resolving those limitations.
