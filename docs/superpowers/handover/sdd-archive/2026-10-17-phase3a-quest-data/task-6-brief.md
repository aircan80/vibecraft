### Task 6: Minimal app readers (no UI yet)

**Files:**
- Create: `app/.../model/QuestObjective`, `QuestRelation`, `QuestSourceItem`, `QuestChain` (records); `app/.../db/QuestRepository` gains `objectives(questId)`, `relations(questId)`, `sourceItems(questId)`, `chain(questId)` (walks pre/next/child/exclusive/breadcrumb edges both ways with the depth cap 50 and cycle stop, returns an ordered step list with labels per the spec §4), `uiIcon(name) -> Optional<byte[]>` on `AssetRepository`.
- Test: repository tests on the fixture DB (`FixtureDb` builds with the quest fixture), `QuestChainTest` (linear chain, all-of/one-of, exclusive branch, breadcrumb, cycle, missing target, depth cap) on an in-memory DB.

- [ ] Tests red → green; commit `feat(app): quest objective, relation and chain readers`.

