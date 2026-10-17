### Task 3: Rail icons from client art

**Files:**
- Create `{main}ui/widgets/RailIcons.java` and `{main}ui/widgets/RailButtonUI.java`.
- Modify `{main}ui/widgets/RailTarget.java` (+ `assetName()`, `gossip()`), `{main}ui/widgets/Rail.java` (+ `setIcons(RailIcons)`, 28 px plates, states), `{main}ui/MainFrame.java` (`attach` loads the icons on the worker through `Async.run`, then calls `rail.setIcons`), `{main}ui/pages/ListPage.java` (16 px icon in the heading) and `{main}ui/pages/SourcesPage.java` (sections whose rows are one entity kind get that kind's 16 px icon).
- Tests: create `{test}ui/widgets/RailIconsTest.java` and `{test}ui/pages/SourcesPageIconTest.java`; modify `{test}ui/widgets/RailTest.java`.

**Interfaces:**
- Consumes: `AssetRepository.uiIcon(String) -> Optional<byte[]>` (3A). The names are `rail.home`, `rail.items`, `rail.npcs`, `rail.quests`, `rail.zones` and `rail.instances`, each with an `@24` variant. The two GossipFrame glyphs (`rail.npcs`, `rail.quests`) are 16 px art; their `@24` variant is centred in a 24 px canvas.
- Produces:
  - `RailTarget.assetName()`: `"rail.home"` … with `"rail.instances"` for `INSTANCES`. `RailTarget.gossip()` is true for NPCS and QUESTS.
  - `record RailIcons(Map<RailTarget, BufferedImage> plates, Map<RailTarget, BufferedImage> small)` with `static RailIcons load(Function<String, Optional<byte[]>> uiAsset)`, `static RailIcons none()`, `Optional<Icon> plate(RailTarget)`, `Icon small(RailTarget, int px)` (falls back to the SVG) and `static RailIcons shared()` / `install(RailIcons)`.
  - `static BufferedImage plateArt(BufferedImage src, boolean gossip)`. For Interface/Icons art: crop to the central 86 %, then bicubic to 24 px. For gossip art: the native 16 px glyph scaled 1.5× bicubic to 24 px; see the open questions for this default.
  - `Rail.setIcons(RailIcons)`.

- [ ] **Step 1: failing tests.**
  - `RailIconsTest.cropsCentralEightySixPercent`: a 64 px source with a 1 px red frame and a green centre gives a 24 px plate image with no red pixels.
  - `RailIconsTest.gossipGlyphFillsTheIconBox`: a 16 px glyph becomes 24 × 24, and its opaque bounds are ≥ 20 px wide.
  - `RailIconsTest.missingAssetFallsBackToSvg`: `load(name -> Optional.empty()).plate(QUESTS)` is empty, and `small(QUESTS, 16)` is a `FlatSVGIcon`.
  - `RailIconsTest.loadsAllSixFromTheShippedDatabase`: on the shipped database every `RailTarget` has a plate.
  - `RailTest.plateStates` paints each state into images.
    - Idle: the plate background is `#0C1424` in both themes, and the icon is drawn at alpha 0.85.
    - Hover: a 1 px gold ring at alpha 0.5.
    - Selected: a solid gold border, plus a 3 px gold bar on the rail's left edge (x 0–2 gold).
    - Never desaturated: an idle red icon pixel keeps R > G.
  - `RailTest.tooltipsOnNavyCard`: each button's tooltip text equals `label()`, and its accessible name is unchanged.
  - `SourcesPageIconTest.questSectionsCarryTheQuestIcon`: the NPC page's "Quests" section heading icon is the 16 px quests icon. (That section arrives in Task 14; until then assert it on "Starts quests".)
- [ ] **Step 2:** run the tests and expect FAIL.
- [ ] **Step 3:** implement. Decode PNGs with `Images.decode`. Icons load once per start, off the EDT. `Rail` paints SVG icons until `setIcons` arrives, and the icons swap in without a layout change.
- [ ] **Step 4:** run and expect PASS, then the full verify.
- [ ] **Step 5:** commit `feat(ui): rail icons from client art on navy plates`.

