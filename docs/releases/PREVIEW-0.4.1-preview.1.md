# WoW Forever Browser 0.4.1-preview.1

**Status: local candidate preparation. Not published or signed.**

This preview targets a portable application that includes Java and the gameplay
database: extract the archive and run its launcher. No separate Java, database
server, Python, Node or Blender installation is required. The exact candidate build
and its acceptance evidence must be frozen before this document becomes release notes.

## Included changes

- The Explorer Journal is the application interface, with direct startup and no
  Original GUI chooser. Maps, city pages, research trails and source provenance
  remain part of the Journal experience.
- NPC pages use static portraits loaded asynchronously through Java, with supported
  bundled or reviewed cached images. Interactive 3D models, rotation and runtime
  model generation are out of scope. Missing images remain explicit.
- Native city music and interface effects use Java acquisition and playback.
  Interface effects have separate volume and mute controls. Audio availability can
  require a network download; bundled gameplay browsing does not.
- The compact release database preserves gameplay facts, maps/icons, source identity,
  selected values and conflicting assertions while omitting raw ingestion payloads.
  The development database is preserved separately.
- Portable packaging includes the runtime, database and explicitly reviewed static
  support files. The launcher resolves packaged data after the application moves to
  another directory, without a developer checkout or manually supplied database path.

## Database size

The validated compact dataset is **1,050,083,328 bytes** (approximately 1.05 GB),
down from **2,283,749,376 bytes**: **54.02% smaller uncompressed**. This is the database
size, not the complete download size. Full-row digests matched across the 81 retained
tables; integrity and foreign-key checks passed in the database export verification.
See [database export details](../publishing/release-database.md).

Complete archive sizes, extracted sizes and checksums will come from the final
integrated candidate. Earlier source-only measurements and superseded packaging
proofs are not release download measurements.

## Platform status

| Platform | Complete candidate archive | Candidate native launch | Signing |
| --- | --- | --- | --- |
| Linux x64 | Pending final integrated build | Pending; earlier relocated packaging proof passed | Not signed |
| Windows x64 | Not built | Not run | Not signed |
| macOS Apple Silicon | Not built | Not run | Not signed or notarized |
| macOS Intel | Not built | Not run | Not signed or notarized |

Windows/macOS scripts do not establish working Windows/macOS releases. Each target
needs its own build, extraction/relocation and native-launch checks. Product version
`0.4.1-preview.1` is separate from any platform-required numeric bundle metadata.

## Known limitations

- Genuine Forever collector-capture acceptance is still outstanding. This preview
  does not claim final acceptance of the full v0.4.1 collector workflow.
- Portrait coverage is partial. Auntie Bernice Stonefield (NPC 246) lacks an exact
  verified Forever appearance mapping; Era data is not silently substituted.
- Static portrait support does not mean portraits exist for every NPC. A renderer
  lab or appearance-metadata mapping alone does not establish a shipped image.
- Later-roadmap research, professions, exploration and comparison work remains
  partial even where individual features are present.
- Windows and both Mac architectures remain untested. macOS signing/notarization
  has not been performed.

## Evidence and publication boundary

Final application test totals, GUI acceptance results, source commit, packaged Java
version and artifact hashes are deliberately not claimed here before the frozen
candidate completes verification. Attach the measured inventory and evidence when
available; failures or unrun checks remain visible.

The GitHub source snapshot is a separate reviewed, data-free artifact. Existing
development Git history and its populated JAR must not be uploaded as that snapshot.
Portable end-user downloads include reviewed data and have their own content and
attribution review. Code licensing does not automatically license artwork or source
datasets. No GitHub push, release tag, upload or public release has been performed.
