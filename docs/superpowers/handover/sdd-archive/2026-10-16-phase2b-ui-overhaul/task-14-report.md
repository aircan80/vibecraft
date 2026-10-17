# Task 14 report — Part 2 polish, screenshots, docs

Status: DONE_WITH_CONCERNS (concerns at the end; none blocks the screenshots).
Branch `feature/phase2b-ui`, commits `4a77cf0..b211773` (9 commits). App tests 662 → 714, full `./mvnw -B -q verify`
green at the tip of every batch (see "Verification").

## Commits

| Commit | What |
|---|---|
| bc94537 | fix(ui): zone map pin loads, page status and one window menu builder |
| 216e4f0 | fix(ui): Part 2 polish: quality filter, card text and set links, source icons, search, accessibility, text gold |
| 6202c1c | fix(ui): NPC drops group world drops, recipes show their skill, encounters read Boss |
| 66eaf7b | fix(ui): recent searches keep only explicit searches and opened hits |
| 6095041 | fix(ui): zone page level band, friendly NPCs, sub-zones and a map pin legend |
| ec6ca42 | fix(ui): a rerender keeps the page's scroll and encounter and runs no query again |
| 140e31d | fix(ui): map menus and tooltips capped, shared HTML escape, icon loader newest first, list refilter history |
| 0ee93be | test(app): every page kind renders from the shipped database |
| b211773 | docs: screenshots and usage guide |

## Must do (addendum 1–10)

1. **Quality filter.** FilterBar gains a "Quality" combo (Any, Poor … Artifact); `ItemFilter.withQuality`;
   `ItemRepository.list(ItemFilter)` queries `item.quality` (no more throw); stale Javadoc gone in both files.
   Tests: `ListPageTest.theQualityComboFiltersItemsByQuality`, `ItemRepositoryTest.aQualityFilterKeepsItemsOfThatQualityOnly`.
2. **"NPCs in <zone>"** opens `ViewTarget.List(NPC, NpcFilter.ALL.withZone(zoneId))` (new `HeaderPanel.addLink(String,
   ViewTarget, Navigator)` and `LinkButton.create(…, ViewTarget, …)`). Test: `InstancePageTest.headerLinksTheZoneAndTheNpcListOfTheZone`.
3. **Unresolved spell tokens** render as "?" (`TooltipLinesPanel.withoutSpellTokens`, regex
   `\$\{[^}]*\}|\$[A-Za-z0-9]+` per the ruling), UI-side only. Test: `TooltipLinesPanelTest.unresolvedSpellVariablesReadAsQuestionMarks`.
   README "Known limitations" now says the placeholders show as `?`.
4. **ItemSource rows** carry `quality`/`iconId` (5-arg record, 3-arg constructor kept) through the container and
   quest related-item queries; `Columns.itemSources()` Name column is an item link with quality colour and 18 px icon;
   `DetailPanels.load` warms those icons. Tests: `ItemRepositoryTest.containerSourcesCarry…`,
   `QuestRepositoryTest.relatedItemsCarryTheItemQualityAndIcon`, `QuestPageTest.relatedItemsShowTheirIconAndQualityColour`.
5. **Dire Maul.** `SearchRepository` leaves out a zone row whose parent zone has the same name, in hits and in
   `countsByKind`. Tests: `SearchRepositoryTest.aSubZoneNamedLikeItsParentIsHiddenAndTheParentKept`, shipped
   `theShippedPagesReadAsReviewed` (one "Dire Maul" zone hit).
6. **Accessibility walk.** `AccessibilityTest.everyPageNamesItsButtonsInputsTablesListsAndMaps` builds 8 entity pages
   (item with card, set item, item, NPC, object, quest, zone with map, instance) and the 5 list pages from the fixture
   and asserts names on icon-only buttons, combos, spinners, tables, lists and the map. Fixes: tables named by their
   section title (list page: by kind), level spinners "Minimum/Maximum (item) level", instance lists "Encounters" /
   "Other loot lists", `MapView` has an accessible context ("Zone map", description = pin count), legend "Map legend".
   Also `theFilterBarNamesItsLevelSpinnersByWhatTheyBound`.
7. **WindowMenus** builds the View items (Dark theme, Drop sources) and Help items (Keyboard shortcuts, About) for
   both the menu bar and the compact context menu (`WindowMenus.Actions`); the Dark theme item reads the theme live
   (a model whose `isSelected` asks), so neither layout needs a refresh listener. Test:
   `WindowMenusTest.bothLayoutsGetTheSameViewAndHelpItemsFromOneBuilder`.
8. **Zone map loads.** (a) The controller gives each detail page a `StatusSink` tied to its detail generation; a late
   report from a page left is dropped, and a "Loading pins…" the left page wrote becomes "Ready" when another entity
   opens. (b) `ZoneMapPanel` keeps an in-flight `EnumSet<PinLoad>`: a toggle changed during a load asks only for what
   is neither loaded nor loading; every load's result is kept; the count shows once nothing loads; failures revert
   their own toggles. Tests: `AppControllerTest.aPinLoadThatFinishesAfterThePageWasLeftWritesNoStatus`,
   `ZonePageTest.togglesLoadOnTheWorkerAndNeverRunAQueryAlreadyInFlight`, the reworked combined-failure test.
9. **Light gold.** `Theme.goldText` (new token): light `#7A5E14` (5.6:1 on bg.base, per the controller's ruling over the
   addendum's `#7A5B10`), dark = accent gold. Used by section headings and "Other loot lists"; chips, rail and pins keep
   `accent.gold #9A7419`. Tests: `TokensTest` (WCAG ratio ≥ 4.5 on bg.base and bg.surface in both themes),
   `SourceSectionTest.headingNamesTitleAndCountInGold`.
10. **README + smoke.** README "Using the app" (search, rail/list pages, item card, NPC/dungeon/object/quest pages,
    zone maps and pins incl. quest givers, drop sources filter, compact mode, keyboard table) referencing
    `docs/screenshots/{home,search,item,npc,instance,zone,quest,compact}.png`; the home shot replaces "Screenshot coming
    soon". The table is generated by `KeyMap.markdownTable()` and `ReadmeKeyMapTest` asserts the README block between
    `<!-- keymap:start -->`/`<!-- keymap:end -->` equals it. `ShippedDataTest.everyPageKindRendersFromTheShippedDatabase`
    builds, lays out at 1100 and 420 px and paints item 19019, NPC 448, Molten Core, zone 40, object 94, quest 176.

## Appended review items

- **Compact "≠"**: `CompactColumns.Cell` carries `disagrees`; the row tooltip appends "≠" to a hidden disagreeing rate
  (not twice). The Rate column itself stays visible in compact, so this only matters for other rate-like columns.
  Tests: `CompactColumnsTest.aHiddenRateWhoseSourcesDisagreeKeepsItsMarker`, `LinkTableTest.aHiddenRateColumnTells…`.
- **Rerender** (filter change, layout switch): the controller keeps the last loaded entity record and rebuilds the page
  from it (no query); pages implement `PageState` (SourcesPage: scroll offset; InstancePage: selected encounter and
  difficulty), saved before and restored after; list pages reload and get their scroll back once shown; under a
  search the page behind returns and the master list keeps its results (no search re-run; the Home case does not clear
  the list). Theme toggling never went through rerender (updateComponentTreeUI in place). Tests:
  `AppControllerTest.rerenderingAnEntityPageBuildsItFromWhatItLoadedAndKeepsItsSelection`,
  `…UnderASearchShowsThePageBehindAgainAndLeavesTheResults`, `ItemPageTest.aPageBuiltAgainScrollsToWhereTheOldOneWas`,
  `InstancePageTest.aPageBuiltAgainSelectsTheSameEncounterInTheSameDifficulty`, LayoutSwitchTest unchanged and green.
- **World drops / Skill**: per-item NPC count = a `dropper_count` subquery on `item_npc_drop` in the NPC drops query
  (primary-key prefix count; no schema change). Rows with unknown rate and ≥ 20 droppers go to "World drops (n) ▸",
  last, collapsed; click the heading or Enter on it (the heading is selectable, other headings are not) toggles.
  Recipes group shows `Labels.skill(req_skill line)` ("Leatherworking 65") in the Item level column with "Skill" in
  its heading row (one table, so the label sits on the group heading; the column header stays "Item level"). Same on
  the instance page's Recipes group. Tests: `NpcPageTest.worldDropsComeLast…`, `…recipesShowTheirRequiredSkill…`,
  `NpcRepositoryTest.dropRowsCountTheNpcsThatDropTheirItem…`, `LabelsTest.requiredSkillFromTheTooltipLine`, shipped
  Hogger check.
- **Zone level band**: `LevelBand.of` = 10th–90th percentile (interpolated) of the zone's NPC mid-levels, low end
  floored to 5 (≥ 1), high end rounded to the nearest 5. To reach the ruling's Elwynn 1–10 / Westfall 10–20 on the
  shipped data I had to (a) weight each NPC by its spawn points on the zone map (an NPC met only inside instances
  weighs 0; one with no spawn at all counts once), (b) use hostile NPCs only (`friendly_to` blank; guards and critters
  are friendly and dragged the band to 1–30/1–35), falling back to all NPCs, and (c) round the high end to the nearest
  5, not up (Elwynn's p90 is 11–12). Bosses (`NpcRepository.RANK` 3) and skull levels are out. Measured on the shipped
  DB: Elwynn 1–10, Westfall 10–20, Dun Morogh 5–10, Durotar 5–10, Barrens 10–25, STV 30–45, Felwood 45–55, EPL 50–60.
  The header drops the duplicated "Zone" for plain zones; without a band the page falls back to the min–max.
  Tests: `LevelBandTest` (4), `ZoneRepositoryTest.theLevelBandWeighsHostileNpcs…` (fixture copy), `ZonePageTest`,
  shipped `theShippedPagesReadAsReviewed`.
- **Map legend**: `PinLegend` under the zone map, dots in `MapView.colour(kind)` and the quest glyphs painted by
  `PinGlyphs`. Tests: `PinLegendTest` (3, pixel checks in both themes), `ZonePageTest.mapShownWithPins`.
- **Recent searches**: recorded on Enter/Shift+Enter/"Show all" (and a recent re-run); an opened hit records the
  hit's name, not the typed fragment (the reviewer's "westfa" came from opening hits mid-typing, which the ruling
  allows; recording the name fixes it); chip re-runs and pastes record nothing. Tests: `SearchBarTest` (updated plus
  `typeAheadAloneRemembersNothing`).
- **VanCleef**: `NpcRepository.RANK` = Boss for an AtlasLoot main-list encounter **without a special type**, else
  Questie's rank — used in summaries, the rank filter, search subtitles and map pins. Refinement of the ruling:
  AtlasLoot itself marks 28 encounters "rare" and one "elite" (Miner Johnson, Deviate Faerie Dragon, …), and the
  instance page already labels them so; those keep Questie's rank. Tests updated (5 bosses in the fixture now) plus
  `NpcRepositoryTest.anAtlasLootEncounterIsABossWhateverQuestiesRank`, `SearchRepositoryTest.anAtlasLootEncounterReadsBoss…`.
- **Zone page lore**: sub-zones that are one of the zone's dungeons (instance zone id or same name) are not listed;
  NPC tables gain "Friendly to" (Alliance/Horde/Both); the zone page sorts friendlies last; outside dungeon/raid zones
  NPCs whose every spawn is an instance placeholder are left out (0 such rows in the shipped data, so a guard only).
- **Instance "#" / Quest item**: `Columns.LOOT_ORDER = "#"`; the instance page leaves the column out (order kept); compact
  tooltip reads "#7". Item subtitles read "Quest item · iLvl 8".
- **Set piece links**: `TooltipLine.itemId` (4-arg record, 3-arg kept) resolved by name within the item's set in the
  card query; with a navigator the set_item line is a grey link button (accessible name = piece). Tests:
  `TooltipLinesPanelTest.aSetPieceWithAKnownItemIsALink…`, `ItemPageTest.theCardsSetPiecesOpenTheirItems`,
  `ItemRepositoryTest.setItemCardListsTheSetLines`.
- **Class tag**: muted "Classes" column after Item in `Columns.lootRated()` from `item.allowable_class`
  (`Labels.classes`, hidden when no row is restricted); applies to every loot table, not only instance loot.
  Tests: `LabelsTest.classesOfAClassMask`, `InstancePageTest.lootKeepsAtlasLootsOrder…`.
- **Task 15 minors**: giver menu capped at 10 + "…" (`MapView.GIVER_CAP`); multi-giver tooltip capped at 12 lines +
  "+n more" (`QuestPinText.TOOLTIP_LINES`); one `QuestPinText.Roles` partition; `HtmlText.escape` shared by MapView,
  QuestPinText, CompactColumns; `spawnsOf` on NpcRepository/ObjectRepository with `Repositories.spawnsOf(ref)`
  dispatch; the on-map filter now lives once in `QuestPage.GiverMap`'s constructor; the Quests toggle is set before its
  listener (opening a zone no longer writes the preference; `ZonePageTest.openingAMapWithQuestsOn…`); the item-giver
  test has an item giver with Thor's NPC id (`SpawnFixtureDb`); QuestPageTest import order fixed. MapView Pin/Cluster
  records left in place for the final review, per the Task 15 ruling.
- **Plan file**: Task 15 section (from `task-15-brief.md`) added before Task 14 with a note on why.

## Nice to have

Done: IconCache newest-first loading with a 256-entry waiting queue (older misses dropped, asked again when painted)
and `finally` so even an Error never leaves a key stuck (3 tests); `InstancePage.firstBoss` shared with
`DetailPanels.firstBossLoot` (the choice, not the second query: the page still runs its own synchronous lookup); a
list filter change uses `NavigationHistory.replaceCurrentDroppingForward`; a failed refilter empties the table and
shows "Could not load these rows: …" (`ListPage.showFailure`); clustering moved to `PinClusters` (`MapView.cluster`
delegates); the PIN_CAP note under the map. Not done: the type-to-search zone combo (not a small change) → ROADMAP 15.

## Out of scope → ROADMAP (section D)

HiDPI icons (12), top-50 changed names (13), kind chips at > 150 % (14), plus type-to-search zone combo (15),
equipment slot column (16) and a pinned pages list (17: Ctrl+D saves pins but nothing lists them, found while writing
the README, which therefore does not claim pinned pages).

## Verification

- `./mvnw -B -q -pl app -am test -Dtest=…` per item while working; full `./mvnw -B -q verify` (exit 0) before each
  commit batch: verify1 (before bc94537/216e4f0), verify2 (6202c1c), verify3 (66eaf7b/6095041), verify4 (ec6ca42),
  verify5 (140e31d), verify6 (0ee93be/b211773). Final: 714 app tests, 0 failures, 0 errors, 0 skipped; no
  `*-output.txt` files (no stray test logging); the only console output is the importer's existing oracle report.
- Commits split from one verified state (bc94537 from 216e4f0's state; 66eaf7b from 6095041's; 0ee93be from
  b211773's) were then checked out and verified on their own: see the note appended below.
- Shipped data measurements: rank expression over all 13,392 NPCs 8.6 ms; Hogger's drops with dropper_count and skill
  subqueries 0.7 ms.

## Self-review

- Completeness: every Must do and every appended item shipped; 6 of 7 nice-to-haves; ROADMAP lines written.
- Quality: Javadoc on every new public type and method; lines ≤ 120; new SQL parameterised (constants only are
  concatenated: `NpcRepository.RANK`, `NOT_TWIN_SUBZONE`, `ON_MAP`).
- Discipline: worktree only, no stash, no window launched, no artifacts; tests written with or before each change.

## Concerns

1. **Commit granularity.** The first polish commit (216e4f0) bundles several areas because they share files
   (InstancePage, FilterBar, ItemRepository); splitting by hunk was not practical without interactive staging.
2. **Level band method** differs from the ruling's literal wording (spawn-weighted, hostile-only, nearest-5 high end)
   — needed to hit the ruling's own Elwynn/Westfall numbers on the shipped data; reviewer to confirm.
3. **Boss rule** keeps Questie's rank for encounters AtlasLoot marks rare/elite (consistent with the instance page).
4. **Opened hits record the hit's name** in recents, not the typed text (stricter reading of "no fragments").
5. **Classes column** shows on every loot table with restricted rows (NPC drops too), not only instance loot.
6. Visual checks for the controller: the legend row and cap note under the map, the selectable World drops heading,
   the Skill label on the Recipes heading, the grey set-piece links on the card, the light-theme heading gold.

## Note: split commits verified on their own

Checked out in this worktree and run with `./mvnw -B -q verify` after the last commit: bc94537 EXIT 0, 66eaf7b EXIT 0,
0ee93be EXIT 0. The worktree is back on `feature/phase2b-ui` at b211773, clean. Every one of the nine commits has
therefore had a green full verify.

## Fix round 1 (commit fe8c527)

Full `./mvnw -B -q verify` exit 0 (only a Javadoc rewrap after it); app tests 714 → 717, 0 failures, 0 errors,
0 skipped, no test output files.

Important:
1. **Chip contrast.** `RankChip` outline and fill use `Theme.goldText`: light Elite text #7A5E14 on bg.base 5.6:1
   (bg.surface 6.1:1), light Boss chip #F4F5F7 on #7A5E14 5.6:1; dark unchanged (goldText = accentGold, 8.1–8.9:1).
   `RankChipTest.everyChipsTextReadsAtFourAndAHalfToOneInBothThemes` computes the WCAG ratio for Elite, Rare Elite,
   Rare (on both backgrounds) and Boss (text on fill) in both themes; the ratio lives in the test helper
   `ui/theme/Contrast.java`, which TokensTest uses too. Theme Javadoc: accentGold is now the rail and map pins only.
2. **World-drop classes.** `ItemCategories.WORLD_POOL_CLASSES = Recipe, Trade Goods, Consumable, Miscellaneous`;
   Quest, Armor, Weapon and Container items never fold, whatever the dropper count. On the shipped data the excluded
   rows were Quest 6,838, Armor 3,458, Container 2,469, Weapon 861. Tests:
   `NpcPageTest.onlyTheGenericPoolFoldsIntoWorldDropsNeverQuestItemsGearOrBags` (25-dropper quest item, armour, bag,
   weapon stay; trade good, junk, consumable, recipe fold) and the shipped check: Deviate Horror (3642) keeps Deviate
   Hide (22 droppers, unknown rate) in its table. Hogger has no such row (his quest items have 1 and 4 droppers).
3. **Ctrl+D.** Removed from `KeyMap.BINDINGS` and its strokes, with `KeyMap.PIN`, the MainFrame handler and the now
   unused `MainFrame.togglePin`/`CANNOT_PIN` (and their test); `UiState` keeps the pinned storage. The README table
   lost the row (ReadmeKeyMapTest green); `KeyMapTest.ctrlDIsNotBoundUntilPinnedPagesHaveAView`. ROADMAP 17 kept,
   reworded so it stays true ("Ctrl+D is unbound and not advertised; bind it again with a pinned list").

Minor:
4. Smoke test renders quest 7787 ("Rise, Thunderfury!") as well as 176.
5. `AppController.pageTarget` records which target the page on screen shows (a list page's follows its filter);
   `stateOf(t)` saves the page's state only for that target. Test
   `aRerenderWhileAnotherPageLoadsGivesItNoneOfTheOldPagesState` (Deadmines encounter 1 selected, Molten Core
   loading, rerender): checked that it fails with the guard disabled and passes with it.
6. The on-map filter lives once, in `DetailPanels.giverMap` (it picks the zone by those points); `GiverMap` no longer
   filters and documents the contract; `QuestPage.giverPins` does not filter.
7. `LinkTableTest.compactKeepsEachColumnsRenderer` finds the Rate column by name and checks it in compact too.

Follow-ups left for the final review (per the lead): tooltip cap wording, LootTables size, repeated test setup
(fixture-copy boilerplate in several repository tests).

Note: the commit trailer is `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, the attribution line this
session's harness prescribes, as in the earlier Task 14 commits (the brief allowed "your own attribution line if
required"); amend if the Fable trailer is wanted.
