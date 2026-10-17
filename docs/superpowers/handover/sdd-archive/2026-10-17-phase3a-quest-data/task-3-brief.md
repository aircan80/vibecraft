### Task 3: Quest relations, flags and sort labels

**Files:**
- Create: `importer/.../sqlite/QuestRelationWriter.java` (preQuestGroup→pre_group, preQuestSingle→pre_single, nextQuestInChain→next, childQuests→child, parentQuest→parent, exclusiveTo→exclusive, inGroupWith→group, breadcrumbForQuestId→breadcrumb_for, breadcrumbs→breadcrumb; dedupe; keep missing targets; count `quest.relation.missing`), `importer/.../questie/QuestSort.java` (QuestSort id → label table for Classic: Epic, Class names, professions, Darkmoon Faire, Seasonal, Battlegrounds, …; unknown → "Sort #id").
- Modify: `EntityWriter` (quest columns: repeatable = specialFlags & 1, requiredSkill, requiredMinRep, sort_label), fixture quests (a chain of three with a missing fourth, an exclusive pair, a breadcrumb, a negative zoneOrSort).
- Test: `QuestRelationWriterTest` (`bothPrerequisiteKindsAreKept`, `missingRelationTargetsAreKeptAndCounted`, `duplicatesCollapse`, `breadcrumbsBothWays`), `QuestSortTest.negativeZoneOrSortBecomesASortLabel`, `EntityWriterTest.questFlagsAndRequirements`.

- [ ] Tests red → green; commit `feat(importer): quest relations, repeatable flag, requirements and sort labels`.

