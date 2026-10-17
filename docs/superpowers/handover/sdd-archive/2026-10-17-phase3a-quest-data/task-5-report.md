# Task 5 report: regenerate the shipped database and shipped-data tests

Status: DONE_WITH_CONCERNS
Branch: `feature/phase3a-quest-data` (checkout H:\claude_java, base 7ac1847)

Commits:
- `b34e6cc` data: quest objectives, relations and UI icons (schema v3). Contains the sqlite, build-id.txt, import-report.txt and ShippedDataTest.java.
- `9751be5` docs: README rebuild notes and NOTICE for schema v3. Contains README.md only. NOTICE was already complete.

## Commands run

```
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -B -q -pl importer -am package -DskipTests
java -jar importer/target/importer.jar --out app/src/main/resources/data/wowforever.sqlite --fail-on-unsupported \
    --client-data-dir ~/.cache/wowforever-clientdata/out/current        # exit 0
./mvnw -B -q verify                                                    # exit 0
```

There was no fetch and no convert, per the ruling. Out/current was used as is: the 2A tables plus Task 4's ui_icons. The WoW installation was not touched and no network was used.

`tools/clientdata/build.lock` is unchanged. Its content is identical to `out/current/build.lock` ignoring CR/LF, because Task 4 already committed it. Nothing was staged for it.

## Report counters (new or changed vs the previous import-report.txt)

```
clientdata.uiAssets = 6
quest.objective.kinds.creature = 1029
quest.objective.kinds.explore = 243
quest.objective.kinds.item = 3991
quest.objective.kinds.object = 119
quest.objective.kinds.reputation = 144
quest.objective.kinds.spell = 3
quest.objective.unknownKind = 0
quest.relation.missing = 1
quest.sourceItems = 1391
table.meta = 14            (was 13)
table.quest_objective = 5529
table.quest_objective_point = 170
table.quest_relation = 7666
table.quest_source_item = 1391
table.ui_asset = 12
clientdata.coverage.pct = 96   (unchanged; the gate of 85 % passes)
```

Every other counter is unchanged. The diff of import-report.txt is 17 lines: 16 added and 1 changed.

meta: `schema_version = 3`, `build_id = 900b74572d67f8c8b70474eb6df3576586581f294f23f91e34e62cde7712b2b2`.

## Sanity numbers (queried from the regenerated DB)

| What | Value |
|---|---|
| quest | 5,232 |
| quest_objective rows | 5,529, over 3,355 quests |
| quest_objective_point rows | 170, over 144 explore objectives |
| explore objectives without points | 99. These are Questie extraObjectives stored as explore, such as 667 "Use the cannon", a known Task 2 minor. None has neither text nor points. |
| objectives with a credit_group | 24 |
| objectives naming an absent item, NPC or object | 0 / 0 / 0 |
| quest_source_item provided / required | 1,184 / 207 |
| quest_relation pre_single | 3,266 |
| quest_relation next | 2,028 |
| quest_relation exclusive | 1,467 |
| quest_relation breadcrumb / breadcrumb_for | 217 / 216 |
| quest_relation group | 202 |
| quest_relation pre_group | 183 |
| quest_relation child / parent | 45 / 42 |
| relation rows with a missing target | 1: 4604 to 2955, pre_single |
| relation rows with related_id <= 0 | 0 |
| quests with sort_label | 1,255, with 30 distinct labels |
| repeatable quests | 730. repeatable equals special_flags & 1 on every row. |
| quests with required skill / min reputation | 225 / 280 |
| ui_asset rows | 12, the six rail icons at native size and @24, 26,919 bytes in total |

The 30 sort labels, with counts:

Warlock 102, Camping 101, Paladin 95, Priest 85, Shaman 83, Ahn'Qiraj War 76, Warrior 75, Lunar Festival 72, Seasonal 69, Rogue 64, Druid 58, Mage 52, Hunter 52, Darkmoon Faire 44, Blacksmithing 42, Reputation 32, Invasion 31, Special 21, Leatherworking 21, Engineering 20, Midsummer 16, Fishing 12, Cooking 11, Legendary 9, First Aid 4, Tailoring 3, Epic 2, Night Elf 1, Herbalism 1, Alchemy 1.

## Brief facts against the real data

- **Quest 176.** It has exactly one objective: ordinal 0, kind item, Huge Gnoll Claw 1931. There is no creature objective. The givers are object 68 start, object 156561 start (both "Wanted Poster") and npc 240 end (Marshal Dughan). This matches the brief.
- **Quest 7787.** The brief says it has pre relations to 7785 and 7786. The real data has only `7787 -> 7786 pre_single`, plus `7786 -> 7785 pre_single`. 7785 is therefore a prerequisite one step further back, not a direct row. The test asserts both rows and the transitive closure {7786, 7785}.
- **Quest 6502 to 6602.** This is NOT reachable. See the next section.
- **Repeatable.** Quest 16, Give Gerard a Drink, has repeatable 1. Quest 176 has repeatable 0.
- **quest.relation.missing.** It is 1 and is reported.
- **ui_asset.** It holds the six icons plus their @24 variants. All twelve decode as PNG, and every @24 is 24x24.
- **Negative preQuestGroup (Task 3 ruling).** 4964's corrected preQuestGroup is {4976, -4962}. It is stored as pre_group 4976 and pre_group 4962, and no stored related_id is <= 0.

## 6502 to 6602 chain path

Questie's raw record for 6502, Drakefire Amulet, holds only `preQuestSingle {6501}`. It has no exclusiveTo, no nextQuestInChain and no correction touching relations. So 6502 has exactly one outgoing relation row and three rows touching it: `6501 -> 6502 next` and `7495 -> 6502 pre_single` come in, and `6502 -> 6501 pre_single` goes out. The brief's "6502 has ≥ 10 relation rows" is false for 6502's own rows.

A breadth-first search over quest_relation found no path from 6502 to 6602, with the edges taken either directed or undirected. The two quests sit in separate components:

- **6502's component.** Quests 4182, 4183, 4184, 4185, 4186, 4223, 4224, 4241, 4242, 4264, 4282, 4322, 6402, 6403, 6501, 6502, 7495, 7496 and 7497. This is the Alliance Onyxia attunement.
- **6602's component.** Quests 4903, 4941, 4974, 6566, 6567, 6568, 6569, 6570, 6582, 6583, 6584, 6585, 6601, 6602, 7490, 7491 and 7493. This is the Horde attunement.

Their real link is the shared single objective, item 16663, Blood of the Black Dragon Champion. It is not a relation row.

6502's prerequisite chain, following pre_single and pre_group, is 15 quests deep:

```
6502 Drakefire Amulet <- 6501 The Dragon's Eye <- 6403 The Great Masquerade <- 6402 Stormwind Rendezvous
<- 4322 Jail Break! <- 4282 A Shred of Hope <- 4264 A Crumpled Up Note <- 4242 Abandoned Hope
<- 4241 Marshal Windsor <- 4224 <- 4223 <- 4186 <- 4185 <- 4184 <- 4183 (The True Masters)
<- 4182 Dragonkin Menace
```

The chain, 6502 included, has 30 relation rows. The whole component has 35.

The test `drakefireAmuletChain` asserts four things:
- 6502's only row is 6501 pre_single.
- The prerequisite closure has 15 quests and contains 6501, 6403, 4322 and 4182.
- The chain has at least 10 relation rows.
- 6502 and 6602 share the item objective 16663.

It does not assert that 6602 is unreachable. A comment records it.

## DB size

| | Bytes |
|---|---|
| Before (7ac1847) | 65,966,080 |
| After (b34e6cc) | 67,129,344 |
| Delta | +1,163,264 (+1.8 %) |

ui_asset accounts for about 27 KB of the delta. The rest is the four quest tables, their indexes and the new quest columns. The README now says 67 MB.

## App compatibility

The app module has no diff against `main` on this branch (`git diff main --stat -- app/src/main/java` is empty). The app reads `schema_version` nowhere. The shipped-data tests run the app's own `DatabaseInstaller`, `Database`, `Repositories` and `AppController` on the regenerated DB, and they are green, so main's app code opens it. The GUI was not launched.

## Tests

Full `./mvnw -B -q verify` exited 0. Totals from the surefire reports:

| Module | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| lua-table | 61 | 0 | 0 | 0 |
| importer | 364 | 0 | 0 | 0 |
| app | 215 | 0 | 0 | 0 |

ShippedDataTest has 30 tests, all green. 11 are new:
- schemaVersionIsThree
- questTableMinimumCounts
- questObjectivesNameKnownEntities
- wantedHogger
- riseThunderfuryNeedsTheVesselChain
- drakefireAmuletChain
- negativePreQuestGroupIdsAreStoredAbsolute
- repeatableFollowsTheSpecialFlag
- questSortLabels
- committedImportReportShowsTheQuestCounters
- railIconsAreEmbedded

The last one checks the 12 names, that every image decodes, and that each @24 is 24x24.

No importer `KnownQuestsTest` was added. The importer has no quest oracle: `KnownItemsTest` is an item-tooltip oracle against Wowhead CSV. The quest assertions therefore live in ShippedDataTest only, as instructed.

## README / NOTICE

The README changes:
- "Rebuild the data" now has a paragraph on the schema v3 quest tables.
- It notes that the rail icons need the fetch step's `--ui-icons`, and that without them ui_asset stays empty. UiAssetLoader returns nothing when ui_icons/ is absent, which confirms this.
- The headline counts table gains rows for quest, quest_objective, quest_objective_point, quest_source_item, quest_relation and ui_asset.
- A sentence names the quest report counters.
- A known-gap bullet covers the missing reference 4604 to 2955.
- The size is now 67 MB, and the tests bullet mentions quest and UI-icon checks.
- The data-sources line now reads "Icons, UI textures and map art".

NOTICE.md and app/src/main/resources/NOTICE.md are in sync. Both already credit the rail's UI textures (Task 4) and QuestieDB, so they are unchanged.

## Concerns

1. **The brief's 6502 facts do not hold.** 6502 has 1 own relation row, not ≥ 10, and 6602 is not reachable in the relation graph. The lead's fallback, "assert the ≥ 10 relation rows only", cannot be met literally for 6502's own rows. Instead the test asserts at least 10 rows across 6502's prerequisite chain, which has 30, plus the shared objective item 16663. Please rule whether that substitute is acceptable.
2. **7787 has no direct row to 7785.** The brief says "pre relations to 7785/7786". The data has 7787 to 7786 and 7786 to 7785. The test asserts the real rows and the closure.
3. **Explore rows without points.** 99 of the 243 explore rows have none. They are the extraObjectives rows already noted as a Task 2 minor for the 3B brief.
4. **Working directory notice.** Mid-task the harness reported the primary working directory as `.worktrees/phase2b-ui`. I ignored it, per your instruction never to touch `.worktrees/`. All commands used H:\claude_java.
5. **Amended docs commit.** The docs commit was amended once, before reporting, to fix an ambiguous "Its" in the README. Nothing was pushed.
