### Task 5: Shared page skeleton and section rules

**Files:**
- Modify `{main}ui/detail/HeaderPanel.java` (+ icon plate, kind word, chip row, facts strip), `{main}ui/widgets/SourceSection.java` (+ 8-row limit with "Show all n"; + collapsible for compact), `{main}ui/pages/SourcesPage.java` (+ `addSectionsInOrder`), `{main}ui/pages/ItemSummaryLine.java` (positive facts only), and `ItemPage`, `NpcPage`, `ObjectPage`, `ZonePage` and `InstancePage` in `{main}ui/pages/` (section orders).
- Modify `{main}ui/AppController.java` (+ the delayed loading page) and `{main}ui/CompactShell.java` (sections collapsed except the first).
- Create `{main}ui/pages/SectionOrder.java` and `{main}ui/pages/LoadingPage.java`.
- Tests: modify `ItemPageTest`, `NpcPageTest`, `ZonePageTest`, `InstancePageTest`, `SourceSectionTest` and `AppControllerTest`; create `{test}ui/pages/SectionOrderTest.java` and `{test}ui/detail/HeaderPanelTest.java`.

**Interfaces:**
- Consumes: Task 2 `SectionHeading`, `Chip`; Task 3 `RailIcons.small`.
- Produces:
  - Section orders in `SectionOrder`:
    - `ITEM = List.of("Dropped by", "Sold by", "Quest reward", "Contained in", "Quests")`. Task 6 merges "In instance loot" into "Dropped by"; Task 14 fills "Quests".
    - `NPC = List.of("Drops", "Sells", "Quests", "Location")`.
    - `ZONE = List.of("Map", "Dungeons & raids", "Quests", "NPCs", "Objects")`.
    - `INSTANCE = List.of("Encounters", "Loot", "Other loot lists", "Quests")`.
  - Header and section API:
    - `HeaderPanel.setPlate(Icon)`, `setKindWord(String)`, `addChip(Chip)`, `addChipLink(String text, EntityRef ref, Navigator nav)` and `setFacts(String)`.
    - `SourceSection.ROW_LIMIT = 8`, `showAllButton() -> JButton` (text "Show all 34"), `setCollapsible(boolean)` and `setCollapsed(boolean)`.
    - `ItemSummaryLine.of(ItemDetail)` without negative parts.
  - Loading page:
    - `AppController.LOADING_DELAY_MS = 150`, with a `LoadingDelay` seam (as `MasterList.Delay`, so tests use `ManualDelay`).
    - `LoadingPage(String title, String what)` reads e.g. "Hogger" / "Loading drops…"; its header renders at once from the list entry or hit title.

- [ ] **Step 1: failing tests.**
  - `ItemPageTest.factsStripIsPositiveOnly`: Huge Gnoll Claw 1931 reads "1 NPC · 1 quest" with no "no vendor".
  - `ItemPageTest.sectionOrder`: the sections' titles follow `SectionOrder.ITEM` order, empty ones absent.
  - `NpcPageTest.sectionOrder`: Edwin VanCleef 639 → "Drops" then "Quests"/"Location" as present.
  - `ZonePageTest.sectionOrder`: Westfall 40 → map panel before "Dungeons & raids", "Quests", "NPCs", "Objects".
  - `InstancePageTest.questsSection`: The Deadmines (instance 3) shows a "Quests (n)" section listing 166, filed under zone 1581 via `QuestRepository.byZone`.
  - `SourceSectionTest.eightRowsThenShowAll`: 34 rows show 8 and a button "Show all 34"; pressing it shows 34 and hides the button; the heading still reads "(34)".
  - `SourceSectionTest.compactCollapsesAllButFirst`: within `CompactColumns` every section except the first shows its heading only, with the count, and a click expands it.
  - `HeaderPanelTest.chipsAndPlate`: `addChip` adds the chips after the facts in order; the plate icon is painted on `#0C1424`.
  - `AppControllerTest.loadingPageOnlyAfterDelay`: with `ManualDelay`, no `LoadingPage` appears before the delay fires; after it fires (load still pending) the detail is a `LoadingPage` titled with the entry name; a load finishing first cancels it.
- [ ] **Step 2:** run the tests and expect FAIL.
- [ ] **Step 3:** implement. Section titles stay the lookup keys that `SourcesPage.section(title)` uses. The NPC page's "Starts quests" and "Ends quests" stay until Task 14 merges them into "Quests". The NPC "Spawns" section is renamed "Location".
- [ ] **Step 4:** run and expect PASS, then the full verify.
- [ ] **Step 5:** commit `feat(ui): shared page skeleton, fixed section orders and show-all rows`.

