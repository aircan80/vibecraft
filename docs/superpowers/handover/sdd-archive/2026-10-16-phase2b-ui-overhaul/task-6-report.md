# Task 6 report: NPC, instance, zone, object and quest pages

Status: DONE_WITH_CONCERNS (all brief items done and green; concerns are judgement calls listed at the end)
Commit: `f8e9113 feat(ui): NPC, instance, zone, object and quest pages` on `feature/phase2b-ui`

## What I implemented

### Data layer (controller rulings)
- `LootRow` gains `Integer classId, String className, Integer subclassId` (plus a 6-argument constructor for rows
  without a class, so `LinkTableTest` and other callers stay unchanged).
- Every loot query joins `item_class`: `NpcRepository` drops and sells, `ObjectRepository` contains,
  `QuestRepository` rewards, `InstanceRepository.bossLoot`. The shared column list (`NpcRepository.ITEM_COLUMNS`),
  the join (`NpcRepository.itemJoin(alias)`) and the row mapper (`NpcRepository.loot(...)`) live next to the
  existing `plainLoot`.
- `InstanceRepository.difficulties(long instanceId)`: distinct `boss_loot.difficulty` over the instance's bosses,
  NORMAL first then by name. `bossLoot(long bossId, String difficulty)` added; `bossLoot(long)` kept (all
  difficulties).

### Pages (`ui/pages`)
- `SourcesPage` (new, abstract): the scrolling-page scaffolding extracted from `ItemPage` (content box, scroll pane,
  `addSection`, Ctrl+1..6 `bindJumpKeys`, `jumpTo`, `sections()`, `section(title)`, `scrollPane()`). `ItemPage` now
  extends it; its behaviour and tests are unchanged.
- `NpcPage(NpcDetail, Navigator, ClipboardPort)`: header "Name <Title>", rank chip after the title, subtitle
  "NPC #639 · Level 21 · <zone link>". Sections Drops (grouped by `ItemCategories` under heading divider rows
  "Gear (1)", each group by rate desc, unknown last, then name), Sells, Starts quests, Ends quests, Spawns.
- `InstancePage(InstanceDetail, Repositories, Navigator)`: header "Dungeon · Level 10–17 · <zone link> · NPCs in
  <zone link>" (both links open the zone; there is no zone-filtered NPC target). Left: numbered encounter `JList`
  "1. Rhahk'Zor  Lv 19  Boss" (AtlasLoot special type Rare/Elite replaces Boss; skull level 999 reads "Lv ??";
  unknown level omits "Lv"), then a gold "Other loot lists" heading and a second list for extra lists (both hidden
  when there are none; selecting in one clears the other). Right: boss header with "NPC page" link, the difficulty
  switch (toggle buttons, only when `difficulties` has more than one value), and "Loot (n)" grouped by `SlotGroups`
  in AtlasLoot slot order. Boss-loot and difficulty loading stay on the EDT, documented in `showLoot` as the one
  exception, as before.
- `ZonePage(ZoneDetail, Navigator)`: subtitle "Zone #1581 · Dungeon · Level 21 · <parent link>" with the level range
  from the zone's NPC levels (nulls and levels of 0 or below ignored; one known level counts as both). Compact link
  rows "Dungeons & raids: …" and "Sub-zones: …" at the top, a reserved empty map panel (preferred height 200,
  scaled, never stretched), then NPCs (boss, rare elite, rare, elite, rest; then name), Objects, Quests (level,
  unknown last; then name).
- `ObjectPage(ObjectDetail, Navigator, ClipboardPort)` and `QuestPage(QuestDetail, Navigator)`: ported to the section
  style. Quest objectives are a read-only wrapping text area under the header, hidden when empty.
- Helpers: `ItemCategories` (class name to Gear/Recipes/Quest items/Trade goods/Consumables/Junk, null to Other),
  `SlotGroups` (Weapon to Weapons, Armor subclass 0 to Jewellery, other Armor to Armour, Recipe to Recipes, else
  Other), `GroupedRows` (heading/entry rows plus a column adapter), `VisibleColumns` (hidden-column rules),
  `LootTables` (loot tables with rate bars, origin chips and, for grouped ones, `DividerRows`), `SpawnSection`
  (Spawns table "42.3, 67.4" plus "Copy /way" for the selected point, first when none), `ClipboardPort`
  (`copy(String)`, `system()` looks the clipboard up per copy so headless construction works).
- Hidden columns: a column is left out when every row is empty (null, blank or "—"), Origin when uniform, Zone when
  every row names the page's zone. If that would hide every column, the first column stays (VanCleef's fixture spawn
  is at -1,-1 in his own zone, so the Spawns table shows just "The Deadmines").

### Widgets and shared UI
- `ui/widgets/RankChip`: JLabel with a painted rounded border. Elite gold outline; Rare and Rare Elite silver
  (`textMuted`) outline; Boss gold fill with `bgBase` text; Normal/unknown hidden. Colours read from `Theme.current()`
  per paint.
- `ui/widgets/LinkButton`: the borderless link button, extracted from `HeaderPanel.addLink` so the zone page's link
  rows reuse it.
- `HeaderPanel`: `addBadge(JComponent)` puts a badge on the title line (title now sits in a line-axis row). The
  now-unused `join` and `levelText` helpers were removed.
- `Columns`: `RATE` constant, `lootRated()` (Item, Item level, Rate as Double, Origin, Slot) for the pages,
  `spawns()` now Zone + Coordinates, plus `hasCoordinates(Spawn)` and `coordinatesText(Spawn)`. `bossLoot()` removed
  (its only user was deleted). `lootRows()` kept because `LinkTableTest` covers it.
- `DetailPanels.render` dispatches to the new pages (`ClipboardPort.system()` for NPC and object pages).
- Deleted: `NpcDetailPanel`, `InstanceDetailPanel`, `ZoneDetailPanel`, `ObjectDetailPanel`, `QuestDetailPanel`,
  `BossLootPanel` and the three old panel tests. `AppControllerTest` and `DetailPanelsTest` now assert the new page
  classes.

## Tests

New: `ItemCategoriesTest` (3), `SlotGroupsTest` (2), `RankChipTest` (4), `NpcPageTest` (11), `InstancePageTest` (9),
`ZonePageTest` (7), `ObjectPageTest` (2), `QuestPageTest` (2), test helper `Tables`. Repository tests:
`NpcRepositoryTest.lootRowsCarryTheItemClass`, `InstanceRepositoryTest.difficultiesAreTheDistinctOnesOfTheInstanceBosses`
and `bossLootOfOneDifficultyAndItsItemClass`.

Brief's named behaviours and where they are asserted:
- NPC 639 loot grouped and sorted: `vanCleefsLootIsGroupedByItemClassAndRankedByRate`, `unknownRatesComeLastWithinTheirGroup`.
- Hidden columns: `columnsEmptyOrUniformForEveryRowAreHidden`, `spawnsInThePageZoneHideTheZoneAndCopyTheSelectedPoint`,
  `sellsStartsAndEndsHaveSectionsAndEmptyOnesAreLeftOut`, zone and object tests.
- `/way Westfall 42.3 67.4` via the port: `spawnsReadAsCoordinatesAndCopyAWayCommand` (VanCleef from the fixture with
  his spawn list replaced by one Westfall point, since the fixture spawn is -1,-1 in The Deadmines).
- Rank chip text: `rankChipNamesTheRank` ("Rare Elite", silver outline).
- Instance numbering, extra lists, difficulty absent with one value: `encountersAreNumberedWithLevelAndKind`,
  `extraListsSitUnderOtherLootLists`, `difficultyControlIsAbsentWithOneDifficulty`.
- Zone level range, bosses first, instance links: `levelRangeSpansTheZoneNpcsIgnoringUnknownLevels`,
  `bossesAndRaresComeFirstThenByName`, `instancesAndSubZonesAreLinksAtTheTop`.
- Empty lists, null levels and missing spawns: `anNpcWithNothingKnownStillRenders`, `anInstanceWithoutZoneOrBossesStillRenders`,
  `aSkullLevelAndAnUnknownLevelReadAsSuch`, `anEmptyZoneHasNoSectionsOrLinks`, `aSpawnWithoutCoordinatesNamesItsZoneAndCannotBeCopied`.

### TDD evidence
Commands (bash, from the worktree root, with the JDK 21 PATH prefix):

RED, repository tests (`./mvnw -B -q -pl app -am test -Dtest='NpcRepositoryTest,InstanceRepositoryTest,QuestRepositoryTest' -Dsurefire.failIfNoSpecifiedTests=false`):
```
[ERROR] InstanceRepositoryTest.java:[22,33] cannot find symbol  symbol: method difficulties(int)
[ERROR] InstanceRepositoryTest.java:[30,33] method bossLoot in class dev.wowforever.app.db.InstanceRepository cannot be applied to given types;
[ERROR] NpcRepositoryTest.java:[24,19] cannot find symbol  symbol: method classId()
```
GREEN after the data-layer change (`-Dtest='*RepositoryTest'`): InstanceRepositoryTest 5/5, NpcRepositoryTest 3/3,
QuestRepositoryTest 3/3, ObjectRepositoryTest 2/2, all others passing. (A quest-reward class test was dropped: no
fixture quest has a reward row; the quest and object detail tests still execute the changed SQL.)

RED, UI tests (`-Dtest='ItemCategoriesTest,SlotGroupsTest,RankChipTest,NpcPageTest,InstancePageTest,ZonePageTest,ObjectPageTest,QuestPageTest'`):
compilation errors in every new test class (missing `ItemCategories`, `SlotGroups`, `RankChip`, `NpcPage`,
`InstancePage`, `ZonePage`, `ObjectPage`, `QuestPage`).

First GREEN run: 87 of 88 passed; `NpcPageTest.aSpawnWithoutCoordinatesCannotBeCopied` failed with
`ArrayIndexOutOfBounds 0 >= 0` because both spawn columns were hidden. Fixed by keeping the first column when every
column would be hidden; the test now asserts the zone-only table. Re-run: all passing (NpcPageTest 11, InstancePageTest 9,
ZonePageTest 7, ObjectPageTest 2, QuestPageTest 2, ItemPageTest 13, DetailPanelsTest 4, AppControllerTest 31).

Full build: `./mvnw -B -q clean verify` exited 0; app module 269 tests, 0 failures, 0 errors, 0 skipped. Output
otherwise only the importer's usual `RealFilesTest: parsed 41 files, 7 embedded chunks` line.

## Files changed
Main: `db/{Instance,Npc,Object,Quest}Repository.java`, `model/LootRow.java`, `ui/Columns.java`,
`ui/detail/{DetailPanels,HeaderPanel}.java`, `ui/pages/ItemPage.java`; new `ui/pages/{SourcesPage,NpcPage,InstancePage,ZonePage,ObjectPage,QuestPage,ItemCategories,SlotGroups,GroupedRows,VisibleColumns,LootTables,SpawnSection,ClipboardPort}.java`,
`ui/widgets/{RankChip,LinkButton}.java`; deleted the six old panels.
Tests: `db/{Instance,Npc}RepositoryTest.java`, `ui/AppControllerTest.java`, `ui/detail/DetailPanelsTest.java`; new
page, helper and widget tests listed above; deleted the three old panel tests.

## Self-review
- Completeness: every page and every test named in the brief exists; empty lists leave sections out; null and
  non-positive levels are skipped; spawns without coordinates read "—" (or only the zone) and cannot be copied.
- Discipline: `SourcesPage` extraction and `LinkButton` extraction are the only changes to earlier code beyond the
  rulings; both remove duplication the new pages would otherwise copy. `ItemPageTest` passes unchanged.
- Quality: no unused imports, LF endings, no linters configured. Pages are JPanels; tests stay headless.

## Concerns (judgement calls for the controller)
1. Rare Elite uses the silver outline (rank 2 groups with Rare). The ruling named Elite, Rare and Boss only.
2. AtlasLoot boss level 999 reads "Lv ??" (Lucifron is 999 in the fixture; the brief's example says "Lv 63").
3. Instance boss loot shows the Slot column, because AtlasLoot slots are filled and differ per row. The values are
   AtlasLoot positions, not equipment slots, so they may confuse readers.
4. The Origin rule hides the column whenever origins are uniform, including a single-row table, so a lone
   AtlasLoot drop shows no chip.
5. The instance header follows the brief verbatim and has no "Instance #id", unlike the other headers ("NPC #639").
   "NPCs in <zone>" opens the zone page, as no zone-filtered NPC view target exists.
6. `NpcPage.drops()` and `InstancePage.lootTable()` are public but return the package-private `GroupedRows.Row` type.
7. Zone link rows use a FlowLayout and do not wrap onto a second line in a narrow window.
8. `LinkTable.addTab/selectFirstEnabled` and `Columns.lootRows()` are now used only by `LinkTableTest`; candidates for
   removal when `LinkTable` moves in Task 8.
9. Attribution: the commit ends with `Co-Authored-By: Claude Opus 5.5`, per the harness attribution rule that only the
   user's own instructions override; the dispatch asked for Claude Fable 5.1. The branch already mixes both.

## Fix round 1 (commit 9069554 fix(ui): instance difficulties load off the EDT; shared page helpers)

What changed:
1. InstanceDetail gains a difficulties list, with a 2-argument constructor that leaves it empty.
   InstanceRepository.detail fills it in the same db.read call through a private
   difficulties(Connection, long). The public difficulties(long) stays and shares that helper.
   InstancePage reads d.difficulties() and no longer queries them on the EDT. The showLoot javadoc
   again names boss-loot selection as the only EDT read.
2. New ui/pages/BossLoot functional interface, lookup(long bossId, String difficulty).
   InstancePage(InstanceDetail, BossLoot, Navigator) is the main constructor.
   InstancePage(InstanceDetail, Repositories, Navigator) delegates with repos.instances()::bossLoot.
   The page always looks loot up by the selected difficulty. An instance without difficulties shows no loot.
   New test InstancePageTest.twoDifficultiesShowASwitchThatReloadsTheLoot uses a recording fake.
   It asserts the Normal and Heroic toggles, the initial NORMAL lookup, and the HEROIC lookup and table after the click.
3. VisibleColumns hides Role as well as Origin when every row has the same value. QuestPage passes the
   Given by and Turned in to tables through it, and QuestPageTest asserts the columns are Name and Type.
4. SourcesPage gains addLootSection(title, loot) and zoneHeader(title, facts, zoneId, zoneName).
   NpcPage, ObjectPage and QuestPage use them in place of their own copies.
   The instance page keeps its own header because it links the zone twice.
Commit trailer is Co-Authored-By: Claude Fable 5.1, as instructed.

TDD: RED with -Dtest='InstanceRepositoryTest,InstancePageTest,QuestPageTest'. The build failed to compile because
difficulties() was missing on InstanceDetail, the 3-argument constructor was missing, and class BossLoot was missing.
GREEN with -Dtest='InstanceRepositoryTest,InstancePageTest,QuestPageTest,NpcPageTest,ObjectPageTest,ZonePageTest,DetailPanelsTest,AppControllerTest'.
All passed. Counts: InstanceRepositoryTest 6, InstancePageTest 10, QuestPageTest 2, NpcPageTest 11, ObjectPageTest 2,
ZonePageTest 7, DetailPanelsTest 4, AppControllerTest 31.

Full build:
    export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; export PATH="$JAVA_HOME/bin:$PATH"; ./mvnw -B -q clean verify
    exit=0
    RealFilesTest: parsed 41 files, 7 embedded chunks
    app tests=271 failures=0 errors=0 skipped=0
