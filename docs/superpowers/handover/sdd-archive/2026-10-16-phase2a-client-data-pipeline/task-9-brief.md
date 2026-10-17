### Task 9: Pipeline integration, CLI flags and coverage gate

**Files:**
- Create: `clientdata/ClientDataStage.java` (orchestrates Tasks 4–7 in order; no-op that creates empty tables when skipped); modify `ImportPipeline`, `ImportOptions` (`--client-data-dir`, `--skip-client-data`), `Main` (exit 2 when `clientdata.items.matched` < 85 % of named items unless skipped), README importer section, `import-data` Maven profile (adds `--client-data-dir ${clientdata.dir}` property with the default `~/.cache/wowforever-clientdata/out/current`).
- Test: `ImportPipelineTest.endToEndWithClientDataFixture` (every new table populated; `item_tooltip_line` for 17204 contains "Use:"-kind line; `zone.ui_map_id` for 40 = 1436; icon BLOB for 134124; `drop_rate_source` rows), `skipClientDataStillProducesOpenableDb`, `MainTest.coverageGateExits2`.

- [ ] Steps 1–5; commit `feat(importer): client-data stage wired into the pipeline with coverage gate`

---

