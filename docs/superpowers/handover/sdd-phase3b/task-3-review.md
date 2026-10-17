**Approved** (spec ✅, quality ✅) — Critical 0, Important 0, Minor 4, Nit 4.

# Task 3 review: rail icons from client art (commit 8bbae34)

Reviewed `git show 8bbae34` against `task-3-brief.md`, plan Global Constraints (lines 30–147), spec §5 and the
implementer's report. Read-only; Maven not run (controller reports the verify green on 8bbae34: lua-table 61 /
importer 420 / app 841).

## Spec compliance ✅

| Requirement | Where | Status |
|---|---|---|
| `RailTarget.assetName()` "rail.home" … "rail.instances"; `gossip()` true for NPCS, QUESTS | `RailTarget.java:12-17,44-55` | ✅ |
| `record RailIcons(plates, small)`, `load(Function<String,Optional<byte[]>>)`, `none()`, `plate()`, `small(t, px)` with SVG fallback, `shared()`/`install()` | `RailIcons.java` | ✅ (`install` returns the previous value, a harmless superset) |
| `plateArt`: Interface/Icons crop central 86 % then bicubic to 24 px | `RailIcons.java:84-90` | ✅ 64 px → 55 px copy at offset 4 (exact pixel copy, no resampling), then `IconCache.scale` 55→27→24 bicubic |
| `plateArt`: gossip 16 px glyph 1.5× bicubic to 24 px | `RailIcons.java:76-82` | ✅ factor min(1.5, 24/16) = 1.5, fills the 24 px canvas; larger glyphs are fitted down |
| `Rail.setIcons`, 28 px plates, states | `Rail.java`, `RailButtonUI.java` | ✅ |
| `MainFrame.attach` loads on the worker via `Async.run`, then `rail.setIcons` | `MainFrame.java:235-238` | ✅ `load` (all DB reads, decodes) runs on `controller.worker()`; `install` + `setIcons` run in the EDT callback; failure is logged and the SVGs stay |
| `ListPage` 16 px icon in the heading | `ListPage.java:81-84` | ✅ on the Results heading and on each instance level band, also with no rows |
| `SourcesPage` one-kind sections get that kind's 16 px icon | `SourcesPage.java:105-139` | ✅ every row-built section path (`addSection(title…)`, both `addFilteredSection`, `addLootSection`) goes through `addRowSection` |
| Decode with `Images.decode`; once per start; off the EDT | `RailIcons.java:53-64` | ✅ |
| SVG until `setIcons`; swap without layout change | `RailButtonUI.getPreferred/Min/MaxSize` fixed at 48 × 40 | ✅ and tested |
| Missing asset → SVG fallback everywhere (plate and small) | `RailIcons.java:94-101`, `Rail.setIcons` only replaces targets that have art | ✅ (missing and corrupt both tested) |
| Test `cropsCentralEightySixPercent` (64 px, 1 px red frame, green centre, 24 px, no red) | `RailIconsTest.java:50` | ✅ exact |
| Test `gossipGlyphFillsTheIconBox` (24 × 24, opaque width ≥ 20) | `RailIconsTest.java` | ✅ plus a centring check |
| Test `missingAssetFallsBackToSvg` (`plate(QUESTS)` empty, `small(QUESTS,16)` is `FlatSVGIcon`) | `RailIconsTest.java` | ✅ exact |
| Test `loadsAllSixFromTheShippedDatabase` | `ShippedDataTest.java` | ✅ moved to `ShippedDataTest` at the controller's request (needs the shipped DB); checks a 24 × 24 plate and a non-SVG small icon for all six |
| `RailTest.plateStates`: idle `#0C1424` both themes, icon at 0.85; hover 1 px gold ring at 0.5; selected solid gold border + 3 px bar at x 0–2; never desaturated (R > G) | `RailTest.java` | ✅ both `Tokens.DARK` and `Tokens.LIGHT` |
| `RailTest.tooltipsOnNavyCard`: tooltip text = `label()`, accessible name unchanged | `RailTest.java` | ✅ plus navy background / white text |
| `SourcesPageIconTest.questSectionsCarryTheQuestIcon` on "Starts quests" until Task 14 | `SourcesPageIconTest.java` | ✅ (also "Ends quests") |
| Commit subject and trailer | 8bbae34 | ✅ `feat(ui): rail icons from client art on navy plates`, `Co-Authored-By: Claude Fable 5.1` |
| Global: client art for icons only; plates navy in both themes; no animations; headless tests; no DB on the EDT | — | ✅ only the six `ui_asset` textures; `Theme.plate()`; glow is two static rings; no JFrame in tests; pages only read the in-memory `RailIcons.shared()` |

### Specific checks requested

- **Paint states and test honesty.** Plate is at button x 10–37, icon 12–35, so the hover/selected ring (x 10) never
  overlaps the icon and the AA'd 1 px stroke at x + 0.5 with `STROKE_PURE` fully covers one column; the test's
  `blend(gold, navy, 0.5)` at `plate.x` is therefore an honest measurement (`HOVER_ALPHA` 128 = 0.502). The idle
  pixel is solid red composited at 0.85 over navy, measured ±3 per channel. The desaturation check is meaningful: a
  greyscaled red over navy at 0.85 gives R ≈ 66 < G ≈ 68, so it would fail. The bar check pins x 0–2 gold, x 3 not
  gold, and nothing above the plate. The glow is not asserted (it is not in the brief).
- **Tooltips/accessible names** unchanged: `setToolTipText(label())` and `setAccessibleName(label())` untouched;
  `setIcons` changes only icons; `iconsSwapInWithoutALayoutChange` re-asserts the names.
- **Images cached.** `load` decodes each PNG once into `BufferedImage`s held by the record; `ArtIcon.paintIcon` only
  draws the cached image (bicubic, scaled to `UIScale`). No per-paint decode.
- **Static state and test isolation.** `shared` is `volatile`, published from the EDT callback after a worker load
  (invokeLater gives happens-before). Every test that installs restores in `finally`, matching the `IconCache`
  pattern; surefire has no parallel configuration, so there is no cross-test leakage.
- **Section icon rule.** `railTarget` uses each row's first link column, skips link-less rows (group headings,
  dividers), returns empty on mixed kinds, objects, or no linked rows. Spawns (built via `addSection(SourceSection)`)
  get no icon, which I agree with: its Zone column is hidden when all spawns are in the page's zone, so an inferred
  icon would come and go. `InstancePage` is not a `SourcesPage` and is not in the brief; see Minor 4.
- **Startup race (implementer's first concern): not a real defect; no fix required.** At startup the controller opens
  Home, which is the `welcome(meta)` panel with no section headings, so nothing built at startup carries an icon. Rail
  clicks before `attach` are no-ops (`controller == null`). The only exposure is a page the user opens and whose own
  worker load completes in the short window between `attach` and the icon callback (ten small blob reads plus six PNG
  decodes, dominated by ImageIO's first-use initialisation; well under a second). Because `controller.worker()` is
  `Async.SWING_WORKER` (a thread pool, not a FIFO), such a load can finish first, in practice only for the small
  Zones/Dungeons lists. The page then shows the 16 px monochrome SVG (same size, no layout change) until it is
  rebuilt, and every navigation (including back/forward) rebuilds pages. The cosmetic window is too small to justify
  more code now; an optional cheap fix is noted in Minor 1.

## Code quality ✅

Clean, small, well-documented units: `RailTarget` owns the mapping, `RailIcons` is a pure value plus a pure
`plateArt`, `RailButtonUI` paints, `Rail` only wires. Crop copy-instead-of-subimage is the right call (no bevel
bleed into the bicubic kernel). Theme gold is read per paint, so theme switches need no listener. `RailButton.updateUI`
always reinstalls the UI, so LaF/theme switches keep the plates. Error handling is total (missing, corrupt, and a
throwing loader all leave the SVGs).

### Minor

1. **Heading icons are captured at build time** — `SourcesPage.java:105-108`, `ListPage.java:81`.
   *What:* the 16 px icon is resolved from `RailIcons.shared()` when the section is built. *Why it matters:* the
   startup race above (cosmetic, self-healing), and the same for a kept `ListPage` that only refilters. *Fix
   (optional, ~15 lines):* give `RailIcons` a static `lazySmall(RailTarget, int px)` returning an `Icon` whose
   `paintIcon` delegates to `shared().small(t, px)` and whose size is `UIScale.scale(px)`, and have `MainFrame`
   repaint the frame after `install`. The equality-based assertions in `SourcesPageIconTest` would then compare the
   resolved icon. Not required for approval.
2. **`cropsCentralEightySixPercent` does not pin 86 %** — `RailIconsTest.java:50-54`. *What:* with a 1 px frame any
   crop that removes ≥ 1 px per side (anything up to ~96 %) passes. *Why:* a regression of `CROP` to 0.95 would not be
   caught, and the bevel on the real art is ~4 px. *Fix:* keep the brief's assertion and add one more source with a
   4 px red frame (x/y 0–3 and 60–63); 86 % (offset 4, 55 px) shows no red, 90 % (offset 3) would. Optionally assert
   a marker at 6 px in survives, to bound the crop from below.
3. **Same heading, different icon across pages** — `SourcesPage.java:116-139`. *What:* the icon depends on the rows
   at build time (and, for filtered sections, on the rows kept), so e.g. "Dropped by" carries the NPC icon on an item
   only NPCs drop and none on an item NPCs and objects drop, and a filtered section whose rows are all hidden loses its
   icon. *Why:* this is literal compliance with "rows are one entity kind" (the implementer flagged it as choice 9),
   but users may read the inconsistency as noise. *Fix:* none for this task; raise with the UI expert for Task 5's
   section work (option: icon by the section's declared kind rather than by row inspection).
4. **HiDPI softness and `InstancePage`** — `RailIcons.java:90,131-138`. *What:* the plate art is pre-reduced to 24 px
   and `ArtIcon` upscales it at `UIScale` > 1, although a 55 px crop of the 64 px texture exists; `InstancePage`'s boss
   "Loot" sections have no item icon. *Why:* slightly soft rail art at 150–200 % scaling; one page type lacks the
   section icon the spec asks for. *Fix:* optional — keep the cropped native image and scale to
   `UIScale.scale(24)` (cache per scale) at paint; carry the `InstancePage` icon into Task 5, which already edits its
   sections.

### Nits

1. `RailIconsTest.java:143` `sharedIsNoneUntilInstalled` only asserts that `install` makes the icons shared; rename
   (e.g. `installReplacesAndReturnsTheSharedIcons`) or also assert the returned previous value.
2. `SourcesPageIconTest.java:88-95` `withoutClientArtTheHeadingsShowTheSvgIcons` checks only the width; add
   `isInstanceOf(FlatSVGIcon.class)` so it actually proves the fallback.
3. Javadoc reflow leftovers: `ListPage.java:27` ("Filter changes go to {@code onFilter}; the" on its own short line)
   and `RailIcons.java:70` ("bicubic. A").
4. `RailButtonUI.createUI` is unused (`RailButton.updateUI` constructs the UI directly); harmless Swing convention,
   could be dropped or used.
