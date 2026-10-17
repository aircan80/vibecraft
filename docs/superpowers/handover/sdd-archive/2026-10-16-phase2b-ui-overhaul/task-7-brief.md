### Task 7: Type-ahead, kind chips, recent searches, paste parsing

**Files:** Create `ui/widgets/SearchDropdown.java` (non-focusable `JWindow` with a `JList` under the field; top 8 hits grouped by kind; Up/Down/Enter/Esc forwarded from the field's `InputMap`; "Show all n results" row), `ui/widgets/KindChips.java` (`JToggleButton`s All/Items/NPCs/Objects/Quests/Zones/Dungeons & Raids with counts from `SearchRepository.countsByKind(text)`), `ui/PasteParser.java` (`Optional<ViewTarget> parse(String)`: `|Hitem:ID:…|h[Name]|h`, `item:ID`, `npc:ID`, Wowhead URLs `…/item=ID`, `/npc=ID`, `/quest=ID`, `/zone=ID`, `[Name]` → search, bare number → item id), `UiState.recentSearches` (last 10), `SearchBar` rewrite using these; `SearchRepository.countsByKind`.
**Tests:** `PasteParserTest` (the two Review Focus strings, URLs, bare numbers, garbage → empty), `SearchDropdownTest` (headless: model grouped by kind, Enter opens top hit, Esc closes), `KindChipsTest` (counts render, selection fires kind), `SearchRepositoryTest.countsByKind`.
- [ ] Commit `feat(ui): type-ahead search with kind chips, recents and paste parsing`.

