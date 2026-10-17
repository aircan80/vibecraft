# Integrate phase 2A (main) into the 2B branch

Worktree `H:\claude_java\.worktrees\phase2b-ui`, branch `feature/phase2b-ui` (22 commits since it left main). Main now
carries phase 2A (client data: stats, tooltips, icons, maps, provenance; merge 3da3ab9). Report file:
`H:\claude_java\.worktrees\phase2b-ui\.superpowers\sdd\2026-10-16-phase2b-ui-overhaul\integration-report.md`.

## Ruling: merge, not rebase
`git merge main` into `feature/phase2b-ui` (one conflict pass; never `git stash`; never rewrite history; do not touch
`H:\claude_java` itself). A dry run (`git merge-tree --write-tree main HEAD`) shows conflicts in:
InstanceRepository, NpcRepository, SearchRepository, LootRow, SearchHit, Columns, DetailPanels, HeaderPanel,
ItemDetailPanel (modify/delete), ZoneDetailPanel (modify/delete), QualityColors, and the tests
ItemRepositoryTest, NpcRepositoryTest, SearchRepositoryTest, DetailPanelsTest, ItemDetailPanelTest (modify/delete),
LinkTableTest, ZoneDetailPanelTest (modify/delete), QualityColorsTest. Both sides also touched ItemRepository,
LinkRenderer, ShippedDataTest, InstanceRepositoryTest (auto-merged — re-read them anyway).

## Resolution rules (binding)
- **Structure wins for 2B, data wins for 2A.** Keep 2B's `ui/pages`, `ui/widgets`, `ui/theme`, `SourcesPage`, list pages,
  search, compact mode; keep 2A's model fields and repository queries (`ItemSummary.quality/iconId`, `ItemCard` +
  `TooltipLine`, `ItemDetail.card()`, `ZoneDetail.map()` + `ZoneMap`, `DropSource/LootRow/InstanceLoot.disagrees/sourceCount`,
  `AssetRepository` in `Repositories`, `ZoneRepository.map`, `ItemRepository.card`, `Columns.rateText` with "≠",
  `Labels`/`Columns` additions from both sides).
- **Records**: union the fields of both sides (`LootRow` = 2B's classId/className/subclassId + 2A's disagrees/sourceCount;
  `SearchHit` = 2B's count + 2A's quality/iconId); keep every old-arity constructor both sides added so all tests compile.
- **`QualityColors`**: one class in `ui/theme` with `text(int, boolean)` and `canonical(int)`; keep 2B's `rateBand`; merge
  the two test classes into one.
- **Deleted panels**: `ui/detail/ItemDetailPanel`, `ZoneDetailPanel` stay deleted (2B replaced them with pages). Their 2A
  behaviour moves to Part 2: do NOT port the icon/tooltip-card/map rendering now; instead keep 2A's reusable pieces
  (`ui/detail/TooltipLinesPanel`, `MapImagePanel`, `Images`, and the `ZonePage(ZoneDetail, BufferedImage)` loader record
  idea in `DetailPanels.load`) compiling and tested, relocating `TooltipLinesPanel`/`MapImagePanel`/`Images` to
  `ui/widgets`. `DetailPanels.load` keeps 2A's off-EDT icon and map decoding (the loaded image/icon travels with the
  page; pages may ignore it until Tasks 11–12). Delete `ItemDetailPanelTest`/`ZoneDetailPanelTest`; move their
  non-panel assertions (icon fallback pixels, map decode, card lines) into widget tests (`TooltipLinesPanelTest`,
  `MapImagePanelTest`, `ImagesTest` already exist on main — keep them) and note in the report which assertions wait
  for Tasks 11/12.
- **`HeaderPanel`**: 2B's version (LinkButton extracted, `zoneHeader` helpers) plus 2A's quality-coloured title supplier.
- **`DetailPanels`**: 2B's page dispatch + 2A's `load` records (icon/map decode) — keep both.
- **`Columns`**: 2B's catalogues (`dropSourcesRanked`, `lootRows` with class columns, `questGivers`, …) + 2A's `rateText`
  "≠" marker in every rate column and `Column` record overloads.
- **`LinkRenderer`**: 2B's hover/link behaviour + 2A's quality colour via `FlatLaf.isLafDark()`.
- **Repositories/tests**: 2B's filtered list queries and 2A's client-data columns both stay; `FixtureDb` from main (it now
  builds the fixture WITH client data: items 17204, 19682, 212347 etc.; coverage 75 %). Adjust 2B test expectations that
  the richer fixture changes (counts, subtitles with quality), never by weakening assertions.
- **`ShippedDataTest`**: union of both sides' checks against the shipped DB on main (66 MB, client data present).
- The shipped DB and `data/*` files come from main unchanged.

## Verification and commit
- `./mvnw -B -q verify` from the worktree root must be green for every module (expect app ≈ 2B's 485 + 2A's extra
  repository/widget tests). Fix compile and test fallout from the union of records.
- Commit the merge with `git commit` (merge commit message: `Merge main (phase 2A client data) into feature/phase2b-ui`,
  trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` or your own attribution line). Any follow-up fix
  commits are fine. Do not launch the real window (a screenshot pass follows).
- Report: the conflict list and how each was resolved, the test counts, and the assertions deferred to Tasks 11/12.
