### Task 8: Drop-rate provenance and conflict report

**Files:**
- Modify: `drops/DropRates.java` (keep every source: `allSources(npcId, itemId) -> List<DropRate>`), `drops/DropRateLoader.java`, `sqlite/NpcDrops.java`/`AtlasWriter.java` (write `drop_rate_source`, `disagrees`, `source_count` on `item_npc_drop` and `boss_loot`), `report/ImportReport.java` (`== Conflicts ==` section: top 50 disagreements by absolute difference and counts per source pair), `ImportPipeline`.
- Test: `DropRatesTest` (all sources kept; disagreement rule > 2 points), writer tests (fixture pair (639, 2874): sources questie-correction 60.0, atlasloot-override 57.0 (item-level), questie-wowhead 56.07 → `source_count 3`, `disagrees 1`), `ImportReportTest` conflicts section.

- [ ] Steps 1–5; commit `feat(importer): drop-rate provenance and conflict report`

---

