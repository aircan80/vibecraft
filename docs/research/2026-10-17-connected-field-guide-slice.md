# Connected field guide: quest reward research

The first slice connects a recorded item reward to the existing quest graph. On an Explorer Journal item page with quest reward records, **Research quest trail →** opens a **Research trail** section. Select an actual reward quest to inspect its prerequisites, alternatives, follow-ups and source links without leaving the item. **Open quest** navigates through ordinary history.

This is reference research, not character progress or a suggested leveling route. No quest names, prerequisite edges, coordinates or source assertions are hardcoded. The implementation reuses `QuestRepository.chain` and `ChainPanel`, including AND/OR, unavailable steps, bounded traversal, cycle/truncation and authored-evidence qualifications. A recorded reward without an observed flag is explicitly not described as an in-game observation. Missing quest detail and no recorded prerequisite are distinct states; neither means the content does not exist.

Normal item construction adds no SQL. The graph loads only after section selection, on the supplied worker. Selecting another quest invalidates queued work and stale delivery. Failures remain visible with Retry and an ordinary quest link. The current reward selection, tab and scroll restore through JournalPage state; source URLs retain the existing explicit browser action.

Integration is limited to `JournalDetails` and a small action in `JournalItemFacts`; the panel is `JournalResearchTrail`. No database, importer, route, LoadedItem or Original-interface changes are required. Other tabs implementing PageState now participate in the existing JournalPage snapshot.

## Validation

- Isolated worktree Maven command: `./mvnw -B -q -pl app -am -Dtest=JournalResearchTrailTest,JournalDetailsTest,ChainPanelTest,QuestRelationEvidenceTest -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.resources.skip=true test`.
- **16 tests passed**, zero failures/errors/skips: four new panel tests; six JournalDetails tests including the new lazy action/state integration; three existing ChainPanel and three relationship-attribution tests.
- New tests cover deferred work, actual off-EDT loading/EDT delivery, stale queued selection, source-qualified reused graph, ordinary quest navigation, failed query/retry/missing-detail distinction and selected reward restoration.
- Read-only shipped-data probe: **Cozy Sleeping Bag 211527 → reward quest 79976**; chain has **seven quest nodes and six attributed edges**, including both faction starts 79007/79008. Two cold diagnostic runs measured approximately 51 ms and 372 ms while other builds were active. These are local diagnostics, not p95 or a full-page performance guarantee; do not present the faster sample as typical.
- Four settled page-only headless captures at 1280×900 and 420×900, both actual Journal palettes, are in the main checkout's ignored `.local/connected-field-guide/` together with `ConnectedTrailProbe.java` and `trail-real.log`. Compact copy was visually checked after correcting wrap measurement with the shared JournalSurface layout. These are development page captures, not final packaged-app/native acceptance.

Root integration still needs the normal complete build and final artifact acceptance. Generic craft/NPC relation trails, pinned notebooks and a graph visualization are intentionally outside this slice.
