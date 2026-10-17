### Task 2: Quest objectives, points and source items

**Files:**
- Create: `importer/src/main/java/dev/wowforever/importer/sqlite/QuestObjectiveWriter.java` (reads `objectives` [1..6], `triggerEnd`, `sourceItemId`, `requiredSourceItems`, `extraObjectives` text-only; writes `quest_objective`, `quest_objective_point`, `quest_source_item`).
- Modify: `EntityWriter` (calls it per quest), the fixture `foreverQuestDB.lua` (add 176 Wanted: Hogger with creature + item objectives, a quest with a killCredit group, one with triggerEnd in two zones, one with sourceItemId and requiredSourceItems).
- Test: `QuestObjectiveWriterTest` (`creatureAndItemObjectivesInOrder`, `killCreditGroupSharesOneGroup`, `explorePointsKeepTheirZone`, `providedAndRequiredItems`, `reputationObjectiveKeepsFactionAndValue`), `ImportPipelineTest` counts `table.quest_objective > 0`.

**Interfaces:**
- Produces: rows as the spec's tables; report counters `quest.objective.kinds.<kind>`, `quest.objective.unknownKind`.

- [ ] Tests red → green; commit `feat(importer): quest objectives, exploration points and source items`.

