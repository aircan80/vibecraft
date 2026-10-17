# Task 14 addendum — polish items collected from the per-task reviews (controller rulings)

The brief (`task-14-brief.md`) covers screenshots, the README "Using the app" section with the keyboard map, and
shipped-data/UI smoke additions. The controller captures and commits the screenshots (`docs/screenshots/`) after your
commit and writes nothing else; you do everything below. Each item gets a headless test where behaviour changes.

## Must do
1. **Item quality filter on list pages**: `ItemFilter.quality` now has its column (2A merged). Wire a "Quality" combo
   (Any, Poor … Legendary, Artifact) into the items FilterBar, query by `item.quality`, stop throwing on a non-null
   quality, and fix the stale Javadoc in `ItemRepository` ("Item quality is not in the database yet").
2. **"NPCs in <zone>" link on InstancePage** opens the NPC list page filtered to that zone
   (`ViewTarget.List(NPC, NpcFilter(zoneId, …))`) instead of the zone page.
3. **Unresolved spell variables in tooltip text** (e.g. set bonus (6) of item 10329 shows `$1293733s2` and
   `${$proccooldown/60}`): render any `$…` token the importer left unresolved as `?` in the card/tooltip lines (UI-side,
   regex on `\$\{[^}]*\}|\$\w+`), with a test. Do not touch the importer.
4. **ItemSource rows** (quest "Related items", container contents) get quality colour + 18 px icon like every other
   item row (extend `ItemSource` through its queries; old-arity constructor kept).
5. **Dire Maul**: four sub-zones named "Dire Maul" appear as identical zone hits in search; hide a zone hit whose
   parent zone has the same name (app-side, in `SearchRepository`/ranking), keeping the parent.
6. **Accessibility walk** covers the pages and the FilterBar (icon-only buttons on pages get names; extend
   `AccessibilityTest` to build one of each page from the fixture).
7. **Help menu duplication**: one `WindowMenus` helper builds the View/Help items for both the menu bar and the
   compact context menu (dark theme, drop sources, shortcuts, about).
8. **ZonePage/ZoneMapPanel**: a pin load that finishes after the user left the page must not write the status bar
   (guard the status sink with the page's generation or "showing" state); superseded pin loads do not re-run a query
   whose result is already in flight (small in-flight set).
9. **Light-theme gold contrast**: `accent.gold` light `#9A7419` is ≈ 3.9:1 on `bg.base`; use it only for the rail
   highlight/chips with their own backgrounds, and darken the light-theme text usages (section headings, "Other loot
   lists", Boss chip text) to a ≥ 4.5:1 token (`#7A5B10`), documented in `Tokens`.
10. **Screenshots + README**: README gains "Using the app" (search, rail, list pages, item card, maps/pins, source
    filter, compact mode, the full keyboard map table from `KeyMap.BINDINGS`), referencing
    `docs/screenshots/{home,search,item,npc,instance,zone,compact}.png` which the controller adds after your commit.
    Add a `ShippedDataTest`/smoke check that every page type renders headlessly from the shipped DB for one real id
    (item 19019, NPC 448, instance Molten Core, zone 40, object 94, quest 7787) with no exception.

## Nice to have (do if cheap, say which you did)
- `IconCache`: LIFO/cancel for stale requests after a fast scroll; `Error` caught in `fetch` so the key never sticks.
- `DetailPanels.firstBossLoot` duplicates InstancePage's first-boss choice (double query): share one helper.
- `NavigationHistory.replaceCurrent` after a list filter change drops the forward stack (browser semantics).
- A failed list refilter marks the table stale / shows the error instead of leaving old rows under a new filter.
- `MapView` (519 lines): move the pure clustering (`cluster()`/`Cluster`/`key()`) into `PinClusters`.
- `PIN_CAP` truncation shows a muted "showing the first 20,000 points" note.
- Zone combo on the NPC/quest list pages: type-to-search (FlatLaf `JComboBox` with an editable filter) — only if it
  is a small change; otherwise leave it.

## Out of scope (ROADMAP notes only)
HiDPI multi-resolution icons (needs a 2× source in the pipeline); top-50 changed names in the import conflict report;
chip overflow at > 150 % scaling.

Commit as the brief says (`docs: screenshots and usage guide` for README/docs; `fix(ui): Part 2 polish …` for the code),
full `./mvnw -B -q verify` green, report to `task-14-report.md`.
- Compact mode: the row tooltip's rate text (hidden Rate column) lacks the "≠" marker — add it in CompactColumns.tooltip when the row disagrees.
- Filter/theme changes go through rerender(), which loses scroll position and the instance page's selected boss and re-runs a Search target: preserve scroll + selection on rerender (or apply the source filter in place) and skip the search re-run.
- (WoW review, professions) NPC drops: items whose only rate is unknown AND that belong to the generic world-drop pool (recipes, trade goods, food with "—" across many NPCs) bury the NPC's real loot — group them under a collapsed "World drops (n)" section after the rated groups; "Item level" column is meaningless for recipes: show "Skill" (required skill from the recipe's tooltip line) when the group is Recipes.
- (WoW review) Zone header shows "Level 1–60" (min/max of every NPC incl. guards) and repeats "Zone #12 · Zone": compute the band from the 10th–90th percentile of NPC levels rounded to 5 (Elwynn → 1–10, Westfall → 10–20) and drop the duplicated kind word.
- (WoW review) Map pins need a legend/hover hint per kind (the tooltip exists on hover; add a small legend row under the map: ● boss ● rare ● NPC ■ object ! quest).
- (WoW review) The Recent searches list stores half-typed fragments ("westfa", "west"): record a recent only on an explicit search (Enter/Show all) or an opened hit, never on debounced type-ahead.
- (WoW review) Edwin VanCleef shows rank "Rare Elite" in the Deadmines list: verify the rank mapping (Questie rank 2 = rare elite, 3 = boss, 4 = rare; AtlasLoot/Questie may classify dungeon bosses as rare elite) and label dungeon bosses "Boss" when they are AtlasLoot encounters.
- (WoW review, lore) Zone page: hide "Sub-zones" entries that are also listed under "Dungeons & raids"; the NPC table needs a "friendly/hostile" cue (friendlyToFaction is in `npc` — show a muted "Friendly" tag or group friendlies last) and must exclude NPCs whose spawns are only inside the zone's instances (or label them "in The Deadmines").
- (WoW review, lore) Instance loot "Slot" column shows AtlasLoot's entry order, not an equipment slot: rename the column "#" (or hide it) until inventory_type is wired; item subtitles for quest items read "Quest · iLvl 8" like a quest — use "Quest item · iLvl 8".
- (WoW review, loot) Tooltip card: set pieces are plain grey text — make each set piece a link (ItemCard lines of kind set_item carry the item id? if not, resolve by name within the set's item list from item_set_item) so the piece opens its page; keep the grey colour.
- (WoW review, loot) Instance loot: add a muted class tag for class-restricted items (ItemSparse AllowableClass → "Warlock") next to the name; the full class/slot filter is a 3B feature.
- (Task 15 review minors, do them) MapView.menuFor: add the "…" marker when givers are capped and cap the giver part at 10; QuestPinText: cap the multi-giver tooltip at 12 quest lines total ("+n more"), extract one starts/ends partition helper, move the generic HTML `escape` back into a shared text helper (MapView/QuestPinText both call it); move `spawnsOf` from ZoneRepository to the NPC/object repositories; drop the duplicate on-map filter in QuestPage (DetailPanels.giverMap already filters) or document it; ZoneMapPanel: set the Quests toggle before attaching its listener so opening a zone page does not rewrite the preference; ZoneRepositoryTest: make the item-giver exclusion test use an item id that also exists as a Westfall NPC/object spawn id so the kind filter is really exercised; fix the import order in QuestPageTest.
- Plan file: add the Task 15 section (quest-giver pins, owner request) to docs/superpowers/plans/2026-10-16-phase2b-ui-overhaul.md in your docs commit, copying .superpowers/sdd/2026-10-16-phase2b-ui-overhaul/task-15-brief.md.
