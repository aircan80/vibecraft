# Vibecraft 0.4.1-preview.1

**Status: local Linux preview candidate. Not published or signed.**

This preview targets a portable application that includes Java and the gameplay
database: extract the archive and run its launcher. No separate Java, database
server, Python, Node or Blender installation is required. The Linux artifact is frozen for acceptance; its evidence is recorded below.
Windows and Mac candidates are still pending.

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

The complete Linux archive is **163,412,349 bytes** (155.84 MiB); its extracted
application is **1,242,483,170 bytes** (approximately 1.24 GB). These measurements
include Java and the compact database. Earlier source-only measurements and
superseded packaging proofs are not release download measurements.

## Platform status

| Platform | Complete candidate archive | Candidate native launch | Signing |
| --- | --- | --- | --- |
| Linux x64 | Built: 163,412,349 bytes | Relocated launcher self-test and native Journal startup passed with empty tool PATH | Not signed |
| Windows x64 | Cross-built ZIP: 154,081,254 bytes (146.94 MiB) | Not launch-tested on Windows | Not signed |
| macOS Apple Silicon | Not built | Not run | Not signed or notarized |
| macOS Intel | Not built | Not run | Not signed or notarized |

A cross-built Windows archive and macOS scripts do not establish native launch acceptance. Each target
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
- Windows is cross-built but not launch-tested; both Mac architectures remain unbuilt and untested. macOS signing/notarization
  has not been performed.

## Evidence and publication boundary

The final integrated build passed **2,011 tests, with six skipped and no failures**.
The portable Linux archive bundles Java **21.0.12.1** and product version
`0.4.1-preview.1` (numeric native-package version `0.4.1`). Its relocated launcher
self-test passed with a fresh user-data directory and no system Java/tool PATH,
reporting 32,136 items, 13,392 NPCs and 37 instances.

Native isolated GUI acceptance passed **149 component assertions across five suites**,
with 65 component captures. Those component checks used the frozen private QA JAR
with the same compact dataset. Separately, the exact packaged source JAR launched
through its relocated native executable, with the bundled Java runtime, fresh user
data/cache/settings and no system tool PATH or manual database property. Its branded
Journal Home and gameplay data were inspected in a 66th capture. This distinction
keeps component evidence separate from the actual distributable launch proof. The
compact database hash remained unchanged after browsing.

Source build commit: `447020f2f53c38a7b56769e0b0159b8ad9f2a558`.

Known minor UI issues: a long item title may clip at 420 pixels while its full name
remains in the breadcrumb/search; two optional Marcellus font warnings use working
fallback headings. GUI sound checks establish settings behavior, not human-listening
acceptance.

Linux archive: `vibecraft-0.4.1-preview.1-Linux-x86_64-with-data.tar.gz`

SHA256: `54e5bd64bf75df5d207a035dfdb36539b7d6541aec72cdec828dd20338c4f219`

Application JAR SHA256:
`0dbc143a89c2452bb50f1aa9cd82c17f3501a4362b7d4ab059bf0cc250c08d35`.

Compact DB SHA256:
`ee10d3ec2680551c4aff232cad1cd7a5a0abc9d0549badbbce9979e5c65b0066`.

Dataset build:
`40ca42c8e8c6442c0e2e633f76bf4f4638ebf23faf4029b568ee1ee038c207b7`.

The local candidate inventory includes SHA256SUMS, packaging evidence, full-build
output and native acceptance reports. Windows/Mac unrun checks remain visible; the
inventory does not approve publication or third-party redistribution rights.

The GitHub source snapshot is a separate reviewed, data-free artifact. Existing
development Git history and its populated JAR must not be uploaded as that snapshot.
Portable end-user downloads include reviewed data and have their own content and
attribution review. Code licensing does not automatically license artwork or source
datasets. No GitHub push, release tag, upload or public release has been performed.

## Windows cross-build candidate

The Windows ZIP contains a real `Vibecraft.exe`, a bundled verified Windows Java
runtime, the same compact database and reviewed support files, plus a CMD fallback.
It was assembled on Linux. ZIP integrity and pinned component hashes passed;
Windows launch acceptance remains outstanding. No public download has been uploaded.

ZIP SHA-256: `732e185782a3db373445c68d9f058135bc262a540af6d92bd02154618395924b`.
