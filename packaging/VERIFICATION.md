# Portable packaging verification — 2026-10-17

Linux x86_64 only. Windows and macOS configurations have not been executed.

A source-only shaded JAR (20,417,442 bytes, SHA-256
`621675bf1c4c065f51a1685e88014b4bb5e4de2a888f3f176e49d33eed6ef608`) passed
`ArchiveAudit.java` and produced a jpackage application image with Java 21.0.12.1.
The extracted folder was approximately 184 MiB and the gzip archive approximately 76 MiB.
Archive SHA-256: `0f00705cbc97180068f8d37b88184752fc3d5cccecaba43a72ad19b799606710`.
These are local QA artifacts, not a published release.

The native launcher ran with `PATH=/nonexistent`, so no system `java`, Python, Node or Blender could
be found through PATH. Its `--self-test` passed against an explicitly selected existing local dataset:
`items=32136 npcs=13392 instances=37`. The dataset was supplied by the intermediate developer JVM
property, through `JAVA_TOOL_OPTIONS`; it was not embedded in the source-only archive.
The native GUI also remained open for a five-second X11 smoke check with this empty PATH. The only
logged warnings were missing Marcellus font resources and their documented font fallbacks.
This is launcher/runtime evidence, not acceptance of the final first-run selector or every feature.

The audit fixture test passes four cases: clean archive accepted, bulk assets rejected by default,
explicit private-QA assets accepted, and external Python worker rejected even in private-QA mode.
Shell syntax checks passed. A separate asset-containing private-QA package also passed native launcher
self-test; that output is explicitly labeled PRIVATE-QA and is not suitable for upload.

Remaining integrated acceptance: fresh first-run dataset selection, static portrait loading, Java-only
audio acquisition/playback, relocated extracted folder, and Windows/macOS native platform launches.

## Reviewed-data packaging mechanism check (preliminary application build)

A second local Linux x86_64 archive included the compact runtime-v1 database plus four exact reviewed
runtime catalog/portrait files. It is **not the final integrated application candidate**: its source-only
JAR predates the final Java audio and external portrait resource changes.

- Database: 1,050,083,328 bytes; SHA-256
  `ee10d3ec2680551c4aff232cad1cd7a5a0abc9d0549badbbce9979e5c65b0066`.
- Five staged data files: 1,051,321,927 bytes total; generated hash/size inventory included.
- Complete gzip archive: 163,430,240 bytes; SHA-256
  `58d217beedac6e9026e1248be523f3716b1eba40837d58e6e499678afc388aa3`.
- Product and native Linux metadata: `0.4.1-preview.1`; no publication performed.

After moving the application folder to a different parent directory, its native launcher passed
`--self-test` with `PATH=/nonexistent`, fresh XDG data/cache/config directories, and **no manually supplied
JVM properties**. Counts remained `items=32136 npcs=13392 instances=37`. The generated launcher expands
`$APPDIR` for both its database path and external data directory. This demonstrates relocatable bundled
data, not final end-to-end audio/portrait acceptance. Rebuild and repeat acceptance using the final
integrated JAR before producing the candidate release inventory.
