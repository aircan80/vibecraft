### Task 8: Category rail list pages with filter bars (tree removed)

**Files:** Create `ui/pages/ListPage.java` (filter bar + `JTable` on `ResultsTableModel`; filter combos/spinners feed repository queries), `db/ItemRepository.list(ItemFilter)` (`record ItemFilter(Integer classId, Integer subclassId, Integer minLevel, Integer maxLevel, Integer quality /*null until 2A*/)`), `NpcRepository.list(NpcFilter(zoneId, rank, minLevel, maxLevel))`, `QuestRepository.list(QuestFilter(zoneId, minLevel, maxLevel))`, `ZoneRepository.all()` list page, `InstanceRepository.all()` list page grouped by level band; `BrowseTree` and its tests deleted; `Rail` targets → list pages; `ViewTarget.List` gains an optional filter.
**Tests:** `ListPageTest` (filters produce the expected query; changing a filter reloads; sortable), repository filter tests on the fixture DB (`listWithLevelRange`, `npcsByRank`), `AppControllerTest` for rail navigation.
- [ ] Commit `feat(ui): filterable list pages from the category rail`.

