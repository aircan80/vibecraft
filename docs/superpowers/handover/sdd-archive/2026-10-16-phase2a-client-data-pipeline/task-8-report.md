# Task 8 report: drop-rate provenance and conflict report

Implemented:
- DropRates keeps every source per pair (pair entries keyed by precedence) and allSources(npc,item) returns them plus the item-level override, ordered by precedence. get is the first of that list, same winner as before.
- New sqlite/DropProvenance: one stat per (npc,item) shared by item_npc_drop and boss_loot writers (boss rows only with a boss npc_id); writes drop_rate_source once per pair/source (TreeMap, deterministic); source_count/disagrees on both tables; disagrees = any two values differ by more than 2.0 points.
- ImportReport: conflict(Conflict), conflicts(), compared(a,b,disagreeing); render adds "== Conflicts ==" (top 50 by |delta| desc then npc, item; then "a vs b: n disagreeing of m shared", sorted) only when something was recorded, so older report tests are untouched. Uses \n, not %n, for determinism.
- Counters: provenance.pairs, provenance.disagreeing, provenance.sources.<label>.

Fixture conflicts section:
```
== Conflicts ==
639 2874 questie-correction=60.0 atlasloot-override=57.0 questie-wowhead=56.07 atlasloot=56.07 |Δ|=3.93
atlasloot-override vs atlasloot: 0 disagreeing of 1 shared
atlasloot-override vs questie-wowhead: 0 disagreeing of 1 shared
questie-correction vs atlasloot: 1 disagreeing of 1 shared
questie-correction vs atlasloot-override: 1 disagreeing of 1 shared
questie-correction vs questie-wowhead: 1 disagreeing of 1 shared
questie-wowhead vs atlasloot: 0 disagreeing of 1 shared
```
Counters: pairs 8, disagreeing 1.

Concern: the ruling said pair (639, 2874) has 3 sources, but the fixture droprate.lua also holds atlasloot 56.07 for it, so the real count is 4 (source_count 4, disagrees 1). Tests assert 4. (11520, 14149) is 1 source, 0 disagrees, as ruled.

TDD: tests written alongside; first run failed on %n line endings, a wrong test threshold value and the 3 vs 4 count; fixed. Final: importer tests 308 green, full ./mvnw verify green.

Files: DropRates, DropProvenance (new), Conflict (new), ImportReport, NpcDrops, AtlasWriter, SqliteWriter; tests DropRatesTest (new), ImportReportTest, SqliteWriterTest, ImportPipelineTest.
