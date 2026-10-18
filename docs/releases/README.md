# Versioned portable release preparation

The first proposed public-testing version is **0.4.1-preview.1**. This is a plan,
not an existing release or a claim that every 0.4.1 gate has passed. The current
Maven development version remains `0.4.1-SNAPSHOT`. Later-roadmap features already
present are experimental additions; the preview name does not close those releases.

Normal end-user downloads include the Java runtime **and compact gameplay database**.
Raw ingestion archives and development history belong outside the release database.
A source-only JAR is not a usable replacement for that populated portable download.
Keep source preparation and runnable distribution as separate artifacts:

| Artifact | Includes | Release boundary |
| --- | --- | --- |
| Reviewed source snapshot | Authored code/build files/notices | Exact-path source audit; no populated DB/media archives; code license checked |
| Portable end-user archive | OS launcher, bundled Java, compact DB, individually reviewed static portraits/assets | Database content/media audit; platform launch and relocation verification; notices |
| Developer labs/archives | Raw imports, Blender/Node/Python tools, original model/audio caches | Not a normal end-user download |

The code license does not automatically license artwork or source datasets.
Development CI still builds and verifies the populated JAR, but no longer uploads
it automatically. No release/upload workflow is enabled by this change.

## Candidate gates

- [ ] Freeze source commit and concrete preview version; record full commit ID.
- [ ] Build compact DB separately; preserve original ingestion DB; verify schema,
  product/build and expected entity counts, provenance/conflict UI, icons/maps and
  representative search/detail/loot/city queries against the development DB.
- [ ] Confirm runtime has no Python/Node/Blender or externally installed Java need;
  static NPC portraits only. Test offline browsing with shipped DB on first start.
- [ ] Audit included files and DB BLOBs, licensing/notices, source export boundaries.
- [ ] Build on each target OS/architecture with the bundled runtime. Move/extract
  archive to a new path, launch via native launcher with no developer tool PATH,
  browse data, verify writable user cache/settings and clean exit.
- [ ] Run application tests and native GUI acceptance against the exact frozen
  artifact; capture database hash, artifact hashes, runtime version and evidence.
- [ ] Record per-platform results truthfully. Linux testing does not establish
  Windows, Apple Silicon or Intel Mac behavior. Record signing/notarization state.
- [ ] Check collector-capture acceptance separately; an unavailable genuine capture
  remains an explicit preview limitation, not an accepted final-release gate.
- [ ] Complete notes and review manifest. Publishing/tagging/uploading remains a
  distinct explicit action after the concrete candidate is reviewable.

## Local inventory tool

`python3 tools/releases/inventory.py candidate.json --output candidate-inventory`

This is developer-side tooling; Python is not deployed to end users. Paths in the
input are relative to the JSON file. The output directory must not already exist.
The tool streams SHA256 hashes of local files, verifies the expected database hash,
rejects missing/empty files and placeholders, and writes `manifest.json` plus
`SHA256SUMS`. It never uploads files, approves rights, validates archive contents or
infers successful platform testing. Preserve the evidence files next to the inventory.

Minimal input shape (replace the descriptive values with actual measurements):

```json
{
  "version": "0.4.1-preview.1",
  "source_commit": "FULL_40_HEX_COMMIT",
  "database": {
    "path": "data/wowforever.sqlite",
    "sha256": "ACTUAL_64_HEX_SHA256",
    "build_id": "ACTUAL_DATASET_BUILD_ID",
    "product": "wow_classic_beta"
  },
  "evidence": [
    {"name": "Linux portable relocation launch", "result": "passed", "path": "evidence/linux-launch.txt"}
  ],
  "artifacts": [
    {
      "path": "vibecraft-0.4.1-preview.1-Linux-x86_64-with-data.tar.gz",
      "os": "linux", "arch": "x64",
      "includes_database": true, "bundled_java": true,
      "java_version": "ACTUAL_RUNTIME_VERSION",
      "launch_test": "passed", "launch_evidence_index": 0
    }
  ]
}
```

Use `launch_test: "not-run"` and an explicit `launch_note` for an existing artifact
that has not been launch-tested; do not present it as supported/verified. Evidence
records may likewise be `failed` with a report path, or `not-run` with a `reason`.
Only list artifacts that actually exist. Missing target builds belong in the notes,
not invented manifest entries. OS values: `linux`, `windows`, `macos`; architecture:
`x64`, `arm64`. A passed launch must reference a passed evidence record by index.

Run tooling tests with
`python3 -m unittest discover -s tools/releases -p 'test_*.py'`.
