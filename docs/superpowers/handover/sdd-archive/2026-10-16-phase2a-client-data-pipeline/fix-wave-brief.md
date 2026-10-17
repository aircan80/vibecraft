# Phase 2A fix wave (after the final whole-branch review)

Branch `feature/phase2a-client-data`, main checkout `H:\claude_java` (ignore `.worktrees/`, never `git stash`).
Report file: `H:\claude_java\.superpowers\sdd\2026-10-16-phase2a-client-data-pipeline\fix-wave-report.md`.
Commit trailer: `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` (or your own attribution line if your instructions require one).

## Important (must fix)

### F1. Era-sourced items lose their Use/Equip/Chance-on-hit lines
`ItemEffectLoader` reads only Forever's `ItemXItemEffect`/`ItemEffect`. Items that fell back to the Era `ItemSparse` (ruling 17; `item.client_source = 'era'`, 4,813 items) usually have no Forever effect rows, so 472 of 546 Era epic weapons/armour ship with no effect lines (e.g. 19137 Onslaught Girdle, 16914 Netherwind Crown).

Ruling: extend the Era fallback to item effects.
- `tools/clientdata/clientdata/fetch.py`: generalise `FALLBACK_TABLE`/`fetch_fallback_sparse` to a fallback table list `("ItemSparse", "ItemXItemEffect", "ItemEffect")`, each cached as `csv/<Table>.era.csv`, hashed into the lock under `tables["<Table>.era"]`, same stale-build rule. Keep `--fallback-product/--fallback-build` flags. Tests in `tools/clientdata/tests`.
- `convert.py` copies every `*.era.csv`; `ClientDataSource.checkFallback` extends to all `.era` tables present (lock must list each); `make_fixture.py` + the importer fixture gain small `ItemXItemEffect.era.csv`/`ItemEffect.era.csv` cuts for at least two Era-sourced fixture items (pick from the fixture's existing Era rows, e.g. 19137 if present, else add one).
- `ItemEffectLoader`: for items whose sparse row is Era-sourced, take effects from the Era tables (Forever rows win when both exist); spell text still comes from Forever's spell tables; when the spell id is absent from Forever's `Spell`/`SpellName`, count `clientdata.era.effectUnresolved` and emit no line. Keep the muted `source_note`.
- Unit tests on the fixture: an Era-sourced item gets its Equip line; a Forever item is unchanged; an unresolved Era spell is counted.
- Then re-fetch the two Era tables into the cache (`fetch.py --product wow_classic_beta --build 1.60.1.70425 --base-url https://wago.tools --cache C:\Users\aircan80\.cache\wowforever-clientdata --fallback-product wow_classic_era --fallback-build 1.15.9.70003`; tables are cache-skipped, only the new era files download), re-run `convert.py` to `C:\Users\aircan80\.cache\wowforever-clientdata\out\current`, regenerate the shipped DB once with the full fix wave applied (`--fail-on-unsupported`, coverage gate default), update `import-report.txt`, `build-id.txt`, `tools/clientdata/build.lock`, and record the sanity numbers (how many Era items gained effect lines; `era.effectUnresolved`).

### F2. Icon-only items show "?" although their icon is shipped
`DetailPanels.load` takes the icon from `d.card().iconId()`, which is `ItemCard.none()` unless `has_client_data = 1`; 488 named items with only an `Item` row (e.g. 212347) have `icon_id` set and their icon embedded. Ruling: use `d.summary().iconId()` (fall back to the card only if the summary has none). Add a repository/panel test for an icon-only item from the fixture (one with an Item row but no ItemSparse/Era row).

## Minor (do in the same wave)

- M3. User-Agent (`fetch.py`): prefix `wow-forever-browser-pipeline/<version>` per the plan, with a contact part that is configurable (`--user-agent` flag or `WOWFOREVER_CONTACT` env var) and defaults to a neutral placeholder `(+contact: set WOWFOREVER_CONTACT)`; never embed personal data by default. Document in the tools README.
- M4. Default `--base-url` becomes `http://localhost:5000` (wow.tools.local's port); README and tools README updated.
- M5. Validate the build strings from `/api/builds` (main and fallback) against `^\d+(\.\d+){3}$` before they become paths; test with a hostile value.
- M7. `ShippedDataTest`: assert `import-report.txt` lists the `clientdata.unverified.*` counters and a `== Conflicts ==` section.
- M8. `StatText`: stat ids 84–89 are rendered but still counted in `clientdata.stats.unknownId`; fix the counter. Map any remaining "+N Stat #id" ids that have a known Classic name (e.g. from the wowdev ItemStatType list); leave genuinely unknown ones counted.
- M10. README "Damage of 114 caster weapons" → 114 unverified damage rows of which 99 are caster weapons (take the real numbers from the regenerated report).
- M11 (if cheap): `ItemTooltips.build` calls `ItemSparseLoader.load` a second time for set pieces; reuse the already-loaded map.

## Not in this wave (ledgered)
- Top-50 most-changed names in the conflict report (plan-level; add a line to docs/ROADMAP.md under "smaller").
- `\n` inside Use/Equip lines in the Swing panel (2B Part 2 replaces the panel).

## Order and verification
Code fixes with tests first (F2, M3–M8, M11), then F1 code + fixture + tests, then the data refresh (fetch era tables → convert → import → sanity numbers), then README/ROADMAP, then full `./mvnw -B -q verify` (all modules green) and `tools/clientdata/.venv/Scripts/python -m pytest tools/clientdata -q`. Commits: `fix(importer)`/`fix(app)`/`fix(tools)` commits as you see fit, one `data:` commit for the regenerated DB, one `docs:` commit. Do not launch the app (a running instance locks the jar). Report: what changed per finding, the sanity numbers, test commands and output, concerns.
