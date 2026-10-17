### Task 5: Regenerate the shipped database and shipped-data tests

**Files:**
- Modify: `app/src/main/resources/data/*` (regenerated with the real client cache: fetch the six icons, convert, import with `--fail-on-unsupported`), `tools/clientdata/build.lock`, `app/src/test/java/dev/wowforever/app/ShippedDataTest.java` (+ `importer` `KnownQuestsTest`): quest 176 has exactly one objective, the item objective Huge Gnoll Claw 1931 (Questie records no creature objective for Hogger), two object starts (Wanted Posters 68 and 156561) and the NPC end 240; 7787 has pre relations to 7785/7786; 6502 has ≥ 10 relation rows and a chain reachable to 6602 via `exclusive` or a shared ancestor (verify and record); a repeatable quest has `repeatable = 1`; `quest.relation.missing` is reported; `ui_asset` has the six icons (+@24). Record the sanity numbers in the report (objective rows, relation rows by kind, missing targets, sort labels).
- Docs: README "Rebuild the data" and NOTICE updated.

- [ ] Run, test, commit `data: quest objectives, relations and UI icons (schema v3)` + `docs: …`.

