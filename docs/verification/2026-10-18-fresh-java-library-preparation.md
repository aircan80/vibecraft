# Fresh Java library preparation acceptance

The default production `LibraryPreparationWorkflow` passed from a nonexistent owned storage directory and an empty acquisition cache. No developer data directory, existing database, pre-downloaded input, alternate provider or external converter was supplied. No activation was performed by this preparation run.

Source tested: `374f88eebcc723a55e1003076f92dff65cfb0472`. Selected sources were Questie, native data and explicit Raidbots hotfix; acquisition permission was Wago export only. Exact identity: `wow_classic_beta`, `1.60.1.70450`, requested locale `enUS`. AtlasLoot and AllTheThings were not selected.

## Acquired inputs

| Input | Fresh response bytes | Evidence |
| --- | ---: | --- |
| Questie | 14,352,135 across 42 files | commit `e9a67d1a2753a0e64c4c96bf7864788fac7b765f` |
| Wago native tables | 28,194,442 | 51 pinned tables; 49 downloads and two identical-hash reuses within this initially empty job |
| Raidbots raw snapshot | 3,827,112 | SHA `f6fae4dfd32a6368efcdbe896704a6240db2ee11f5c50a90e1ef850cffc1582b` |
| Reviewed WoWDBDefs schema | 75,858 | SHA `8664c3b5862dfe4c3a4322941b48b032c57b90412c070deb2028f3e448cf674a` |

Total completed response bytes: 46,449,547. Community, raw snapshot and schema cache flags were false. The conservative overlay contained 4,864 valid rows and 38 excluded IDs, SHA `ea75aafb5b54b7ef4945203a72be712b38470d4965e171032d0410d350fba2cb`. Product/locale evidence remains provider-based rather than CASC authentication.

## Actual result

Core/client projection passed the unchanged 85% gate at 91% named item detail coverage, zero UI skips and zero unsupported fields. The independently checked archive evidence matched 3,875 applied hotfix catalogue items.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Full validated candidate | 642,269,184 | `957b87550d92012a37ae1de02180581935e97fcbc712ffc0a7660028adb611d9` |
| Verified compact runtime | 45,441,024 | `01045bed76b333638d29189a899e7ee7a074a4d05601229cefd69968db22534c` |

The export and independent verifier checked 53 retained table digests. Full and compact databases both contain 21,543 items, 13,392 NPCs, 5,232 quests, 596 zones and 19 instances, with zero foreign-key violations. The compact export removes 436,048 raw structured records and 48,717,573 archived payload bytes while retaining provenance identities. This is a 92.9% reduction relative to this run's full candidate.

Total preparation: 138.90 seconds. Observed phase times were community 11.85s, native tables 24.84s, raw snapshot 0.84s, schema 0.31s, conservative materialization 1.70s, catalogue capture/projection/validation 86.78s, runtime export 7.01s and independent verification 5.55s. These are one machine/run measurements, not performance guarantees.

## Scope and retained evidence

This is a first core/client library, **not feature parity with the older approximately 1.05GB library**. It lacks map pictures, flight routes, instance floors, collector imports and expanded source-comparison projections. Boss loot is empty because AtlasLoot was unselected. Icons and map/UI artwork were not acquired. Those limitations remain in the preparation warnings; a follow-up preflight warning now explains them before Build. Expanded inventory and integration work is tracked in #14.

The durable external evidence root is `review/workflow-production-zero-input-374f88e` under the separate Vibecraft work directory. It contains `production-smoke-result.properties`, `production-sql-audit.json`, source proofs and the fresh `jobs/production-zero-input` acquisition cache. The catalogue job is `catalogue-76c62d06-f97b-47ce-99b2-7e4d5b663583`, holding `selected-plan.properties`, `acquisition-report.properties`, `preparation-receipt.properties`, original source archive, full candidate, compact runtime and build log. Raw inputs and databases remain outside Git.

The standalone Java harness is retained externally at `review/workflow-production-evidence/ProductionPreparationSmoke.java`; its only runtime arguments were the fresh owned storage path and source revision label. `build-verification/workflow-production-compile.log` records successful isolated importer/Lua compilation. `build-verification/workflow-production-live.log` records actual production phase progress. Subsequent activation/startup acceptance is separate from this preparation evidence.
