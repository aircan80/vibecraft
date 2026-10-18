# Production library activation and startup resolver acceptance

On 2026-10-18, the genuine fresh Java preparation run from `374f88e` was accepted through the compact-library activation and next-startup resolution APIs from `a9732f8`. This was a direct Java 21 harness against the current compiled application/importer dependencies, with the exact `AssetDownloadPermissions`, `LibraryRegistry`, and `LibraryActivation` sources compiled into an external acceptance overlay. No Maven build or desktop window was launched for this check. A final packaged application/UI check remains separate.

The original preparation process had exited, so the harness read its actual persisted `selected-plan.properties` and complete `preparation-receipt.properties`, including all 53 table digests. It did not invent receipt values or reuse developer datasets. Candidate validation was re-derived from the actual full candidate and its recorded ingestion. `activateExportedForNextStartup` independently revalidated the full candidate and compact export, published a separate immutable managed copy into the previously absent registry under that workflow's own app storage, and retained the actual validation warnings.

| Verified artifact | Result |
| --- | --- |
| Full candidate | 642,269,184 bytes; SHA-256 `957b87550d92012a37ae1de02180581935e97fcbc712ffc0a7660028adb611d9` |
| Staged and managed runtime | 45,441,024 bytes; SHA-256 `01045bed76b333638d29189a899e7ee7a074a4d05601229cefd69968db22534c` |
| Build identity | `6e327d7bd77d730fc51e1de3f0a52dee5b00fd04b13cca322576f5f02048862b`; Forever beta `1.60.1.70450`, enUS |
| Persisted explicit permissions | `MANAGED`, Wago export allowed, Blizzard CDN denied, zero local installation roots |
| Registry | Format 2, permissions bound to the exact runtime SHA; fresh registry instance resolved the identical managed selection |
| Source coverage warnings | All four candidate warnings retained, including 91% strict client coverage and the conservative selected ItemSparse mirror policy |
| Timed activation/check phase | 14.18 seconds, excluding initial receipt hydration and independent candidate prevalidation |

The managed database opened through `Database.open` in read-only mode. Actual counts matched the independently validated full source: 21,543 items, 13,392 NPCs, 5,232 quests, 596 zones, 7,033 objects and 19 instances. There were 3,875 catalog items from the explicitly selected hotfix policy. Both the staged full candidate and staged compact file retained their original hashes.

The previously used application database was held open read-only during management and retained SHA-256 `ee10d3ec2680551c4aff232cad1cd7a5a0abc9d0549badbbce9979e5c65b0066` before and after. Activation left the existing process permission/database context unchanged. A separate in-process startup simulation reset that context, resolved the new registry, and installed exactly the persisted managed permissions. No production preferences or production registry were changed. No network acquisition was performed during activation, and no WoW installation was written.

External private evidence is under `vibecraft-work/review/production-activation-a9732f8/`: `ProductionActivationAcceptance.java`, `run.log`, and `activation-result.properties`. The actual managed version and permission-aware selection are under the original fresh workflow's `libraries/` directory. These generated databases and source payloads remain outside Git. The sanitized [result properties](production-library-activation-2026-10-18.properties) retain the checks without workstation paths or game data payloads.

This establishes genuine prepared-data activation, permission persistence and startup resolver/read-only query acceptance. It does not claim complete beta content or media, local/CDN-only catalogue construction, live desktop replacement, or a final packaged GUI acceptance run.
