# Phase 3B — Quest page and the WoW look: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the v0.3.0 UI on the phase 3A data. That means:
- a quest page that answers "where do I pick it up, what do I do and where, where do I hand it in, what comes next" above the fold at 1280×800;
- the quest chain tree and stepper;
- objective, giver and turn-in pins;
- "Involved in quests" on NPC, object and item pages;
- NPC patrol routes;
- client-art rail icons on navy plates;
- the WoW chrome with Marcellus headings and Inter body text;
- the Parchment light theme;
- the information-architecture clean-ups;
- community ("Vanilla") drop rates;
- map coordinates with copy-to-game helpers.

**Architecture:** Pure `app` module work on Swing + FlatLaf 3.6, on top of the 2B page structure.
- The 2B page structure is `SourcesPage` + `SourceSection` + `LinkTable`, `MapView` with layered pins, `DetailPanels.load` off the EDT and `render` on it.
- One new database-side assembler, `QuestLocator`, resolves a quest's objectives, givers and sources to spawn points in one read. Pages stay passive: they get records and decoded maps.
- The chain tree is a pure view model (`ChainRows`) over the 3A `QuestChain`. It is unit-tested without Swing.
- The chrome is token-driven: `Theme` gains plate, hairline, bar and row tokens, and `Fonts` owns typography. Both themes share one code path.

**Tech Stack:** Java 21, Swing, FlatLaf 3.6, `flatlaf-extras` (`FlatSVGIcon`), `com.formdev:flatlaf-fonts-inter` (new; version pinned by the builder from Maven Central, matching the FlatLaf line), JUnit 5, AssertJ. Tests are headless only.

**Spec:** `docs/superpowers/specs/2026-10-17-phase3-quest-companion-and-wow-look-design.md` §4 (quest page), §5 (rail icons, chrome), §6 (IA), §7 (rulings), §8 (acceptance quests), §10 (patrol routes), §11 (research features). Scope is fixed by `docs/superpowers/specs/2026-10-17-product-backlog.md` v4 §2 row v0.3.0 and decisions 1–7, 19.

**Path shorthands used below:** `{main}` = `app/src/main/java/dev/wowforever/app/`, `{test}` = `app/src/test/java/dev/wowforever/app/`, `{res}` = `app/src/main/resources/dev/wowforever/app/`.

## Global Constraints

**Branch, build and commits**
- Create branch `feature/phase3b-quest-ui` from `main` once phase 3A, Tasks 1–10, has merged. That merge brings the regenerated database with `npc_waypoint`, the craft tables and the `vmangos` rows of `drop_rate_source`.
- Work in the main checkout `H:\claude_java` unless the controller assigns a worktree.
- Every 2B and 3A test keeps passing.
- Prefix every shell with `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"; export PATH="$JAVA_HOME/bin:$PATH"`, because Java is not on the subagents' PATH.
- Run focused tests with `./mvnw -B -q -pl app -am test -Dtest=<Classes> -Dsurefire.failIfNoSpecifiedTests=false`.
- Run the full `./mvnw -B -q verify` before each commit, and commit after every task.
- End every commit with the trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

**Inherited from 2B**
- Headless tests only. Never instantiate a `JFrame` or `JDialog`. Pages and widgets are `JPanel`s, and `AppController` tests use `Runnable::run` as the worker.
- No database access on the EDT. Everything goes through `DetailPanels.load` or the `Async` seam. The `InstancePage` boss-loot exception stays the only one.
- Keyboard: every key is declared in `KeyMap.BINDINGS`, the help table and the README table, which `ReadmeKeyMapTest` keeps equal.
- Contrast: small text is ≥ 4.5:1 on every background it is drawn on. Colour is never the only sign; numbers and glyphs are printed.
- `/way` uses TomTom syntax `/way <Zone Name> <x> <y>` with one decimal.

**Typography**
- Headings use Marcellus and Marcellus SC (SIL OFL 1.1). Bundle both as TTF under `{res}ui/fonts/` with `OFL.txt`, and credit them in `NOTICE.md` (the app's NOTICE copy is kept equal to the root one by `MainFrameTest`).
- Heading fallback order: Cinzel, then Georgia, then `Font.SERIF`.
- Body text is Inter via `flatlaf-fonts-inter`.
- Never bundle Friz Quadrata. Never use a fantasy font in body text or tables.
- Page titles are 20 pt (items in their quality colour). Section headings are Marcellus SC, gold, with a 1 px gold rule fading right.

**Slate theme (dark, default)**

| Token | Value |
|---|---|
| bg.base | `#12161D` (was `#14171C`) |
| bg.surface | `#1B2027` |
| bg.raised | `#242A33` |
| text.primary | `#E4E1D8` |
| text.muted | `#8E97A5` |
| accent.gold | `#D6B25E` (gold text is the same) |
| link | `#9CC3E6` |
| card.bg (plate) | `#0C1424` |
| card.border | `#46587A` |
| warn | `#E0A83A` |
| Rare text | `#4A9EF0` (was `#4A9EFF`) |
| Epic text | `#B864F5` (was `#B861FF`) |
| Table alternate row | `#1E232B` (new; see the open questions) |

The other quality colours stay as in 2B. The alternate row changes because epic `#B864F5` on `bg.raised` is 4.25:1.

**Parchment theme (light; replaces the 2B light theme, decision 5)**

| Token | Value |
|---|---|
| base | `#F3E9D2` |
| surface | `#EADCBB` |
| raised | `#FBF5E6` |
| text | `#2B2416` |
| muted | `#6B5E45` |
| link | `#1F4E8C` |
| gold (borders, bars, large headings) | `#8A6D1E` |
| gold text < 18 pt | `#7A5E14` |
| poor | `#6B6B6B` |
| common | `#2B2416` |
| uncommon | `#1A7A00` |
| rare | `#0057B0` |
| epic | `#7B22B8` |
| legendary | `#A85300` |
| artifact | `#7A5E14` (new) |
| heirloom | `#00687F` (new) |
| warn | `#8A5A00` (new) |
| Table, list and text-field background | raised `#FBF5E6` |
| Table alternate row | `#F6EEDC` |

Tables and lists sit on raised, not surface. Measured on surface, poor reads 3.92:1, uncommon 4.04, legendary 3.96 and gold text 4.49. Surface is only for chrome that carries no small quality text (toolbar, rail, status bar).

**Plates, pins and chips**
- The tooltip card and the rail plates stay navy `#0C1424` in both themes.
- Map pins use the same art in both themes, with a 1.5 px `#0C1424` outline and a 1 px `#F4EBD0` halo.
  - The quest page's start pin is a gold `#FFD100` disc with a navy "!". The turn-in pin is the same disc with "?".
  - Kill: a red `#E5705C` circle with the objective number. Collect: a green `#7CC46B` diamond. Interact: a blue `#6FA8DC` rounded square. Explore: a hollow ring.
  - The zone map's Questie "!" and "?" glyphs from 2B stay as they are.
- Chips are outline-only. Elite: gold outline, gold text. Rare: silver `#C0C6CF` outline. Rare Elite: silver outline, gold text. Boss: red `#C8463C` outline.
  - Chip text is text.primary unless it is gold. `#C8463C` text on Slate is 3.8:1, so the red is only ever an outline.

**Chrome**
- Raised panels get a 1 px gold hairline at 35 % alpha and a 4 px radius.
- Rate bars are 6 px, a dark-gold → gold gradient on bg.raised, with faint ticks every 10 %. This replaces the 2B band colours on the bar; the number stays.
- The status bar is 22 px with a gold top hairline. Provenance sits on the left ("Forever beta 1.60.1 · 20,492 items"), the page message in the centre, and the theme and layout toggles on the right.

**Client art, scope and data**
- Client art is for icons only: the six `ui_asset` rail textures. No frames, textures, portraits or cursors from the game UI. Gold only for headings, selection, borders and bars. No pulsing glows or animations. Compact mode stays intact.
- No objective counts anywhere (decision 2).
- Every VMaNGOS rate reads "Vanilla rate", and on dungeon and raid bosses "Vanilla rate; Forever loot reworked" (decision 19).
- Out of scope: the crafting UI ("Created by / Reagent for / Teaches") ships in v0.4.1 (new-recipes list) and v0.6.0 (crafting graph). The vendor and trainer finder ships in v0.6.0, and the dungeon prep tab in v0.5.0. 3A's readers for them stay unused in 3B.

**Acceptance quests (real ids, checked in the shipped DB on 2026-10-17)**
- **176 `Wanted:  "Hogger"`** (two spaces after the colon). Starts at Wanted Poster objects 68 (Elwynn 24.6, 74.7) and 156561 (24.6, 78.2). Ends at Marshal Dughan 240 (Elwynn 42.1, 65.9). One item objective: Huge Gnoll Claw 1931, dropped by Hogger 448 at 84 %. No chain.
- **166 The Defias Brotherhood**, Lv 22.
  - The chain is 65 → 132 → 135 → 141 → 142 → 155 → 166, all pre_single/next, so 166 is step 7 of 7.
  - 155 also unlocks 214 Red Silk Bandanas.
  - The giver and turn-in is Gryan Stoutmantle 234 (Westfall 56.3, 47.5).
  - The objective is Head of VanCleef 3637 from Edwin VanCleef 639 at 29.5 %. VanCleef spawns inside The Deadmines (zone 1581, coordinates −1, −1, no map).
  - 155's objective is an escort: an explore row "Escort The Defias Traitor…" without points.
- **6502 Drakefire Amulet.** Haleh 10929 in Winterspring gives and takes it.
  - pre_single 6501, and 7495 requires 6502. The chain spans 15 prerequisites.
  - The objective is Blood of the Black Dragon Champion 16663 from General Drakkisath 10363, inside Blackrock Spire (zone 1583, two instances: 19 LBRS and 20 UBRS).
  - The Horde counterpart 6602 is not linked by any relation; see the open questions.
- **7787 Rise, Thunderfury!**
  - pre_single 7786, which needs 7785; sort label "Legendary".
  - Starts from item Dormant Wind Kissed Blade 19018, dropped by Prince Thunderaan 14435 at an unknown rate with no spawns. 19018 is also its provided source item.
  - Ends at Highlord Demitrian 14347 (Silithus 21.7, 8.6). It has no objective rows.
- **7791 A Donation of Wool**, Stormwind City.
  - Not repeatable in the data.
  - It is in a group with 7793 and 7794. 7795 requires all of 7791, 7793 and 7794. 7795 → 7796 Additional Runecloth, which is repeatable.
  - The objective is Wool Cloth 2592: no NPC drops, 16 object sources.
- **Spare cases:**
  - 351 Find OOX-17/TN! starts from item OOX-17/TN Distress Beacon 8623 and leads to 648 Rescue OOX-17/TN!, an escort without points (Tanaris).
  - 452 Pyrewood Ambush has a kill-credit group of nine NPCs, 2060–2068.
  - 4604 has pre_single 2955 (missing), 4605 and 4606, plus exclusive 2953 and 4603.
  - 239 is a breadcrumb for 11 Riverpaw Gnoll Bounty.
  - 783 A Threat Within has 87 "Camping 101" unlocks at depth 6.

## Review Focus

1. **Plain-letter keys while typing.** The quest page's plain keys (G/T/O/M/C/R, 1–9, `[` `]`, Z) must never fire while the focus is in the search field or any text component; typing "Hogger" would jump sections. Pinned in Task 15 by `KeyMapTest.pageKeysIgnoredInTextComponents` and `QuestPageKeysTest.typingInSearchDoesNotJump`.
2. **Chain fans and nesting.** Quest 783's 87 same-named "Camping 101" unlocks must render as one row. Nested prerequisite groups must stay under their own parent: the 3A review case 11:ALL:−1, 13:ONE:−2, 14:ONE:−2, 12:ALL:−1 must not split the ALL group. Pinned in Task 13 by `ChainRowsTest.sameNamedUnlocksCollapse` and `nestedGroupsKeepTheirParent`.
3. **Cursor coordinates.** The coordinates and `/way` a map reports for the cursor must invert the drawing maths at every scale. That includes 150 % UI scaling and the spare height below the image that `MapView.needsLayout` keeps. A pointer below the drawn image must report nothing rather than y > 100. Pinned in Task 8 by `MapViewTest.cursorCoordinatesInvertTheProjection` and `pointerBelowTheImageHasNoCoordinates`.
4. **"Vanilla rate" labelling.** A drop rate whose headline or any listed source is VMaNGOS must say "Vanilla rate" (decision 19, a v0.3.0 ship gate). A dungeon or raid boss's must say "Vanilla rate; Forever loot reworked". Pinned in Task 7 by `RateSourceTest.vmangosReadsVanillaRate`, `RateBarRendererTest.vmangosHeadlineIsMarked` and `bossTooltipSaysLootReworked`.
5. **Master-list auto-collapse.** The list must collapse only when a result is opened, never on an arrow-key preview while the user browses it. Ctrl+B must keep it open, and at ≥ 1600 px it must stay visible. Pinned in Task 6 by `ListVisibilityTest.previewNeverCollapses`, `ctrlBKeepsTheListOpen` and `wideWindowsKeepTheList`.

---

### Task 1: Fonts and theme tokens

**Files:**
- Create `{main}ui/theme/Fonts.java`.
- Create `{res}ui/fonts/Marcellus-Regular.ttf`, `{res}ui/fonts/MarcellusSC-Regular.ttf` and `{res}ui/fonts/OFL.txt`. Download them once from `github.com/google/fonts` (`ofl/marcellus`, `ofl/marcellussc`) and commit them.
- Modify `{main}ui/theme/Theme.java`, `{main}ui/theme/Tokens.java`, `{main}ui/theme/QualityColors.java` (Slate rare/epic text), `{res}ui/theme/WowForeverDarkLaf.properties` (`@background #12161D`, `Table.alternateRowColor #1E232B`), `{main}Main.java` (calls `Fonts.install()` before `ThemeManager.applySaved()`), `app/pom.xml` (+ `flatlaf-fonts-inter`), `NOTICE.md` and the app's NOTICE copy.
- Tests: create `{test}ui/theme/FontsTest.java`; modify `{test}ui/theme/TokensTest.java` and `{test}ui/theme/QualityColorsTest.java`.

**Interfaces:**
- Consumes: 2B `Theme` record, `Tokens.DARK`/`LIGHT`, `Contrast.ratio`.
- Produces:
  - `Fonts.install()`, which registers the two TTFs with the `GraphicsEnvironment` and installs Inter as FlatLaf's preferred family.
  - `Fonts.heading(float pt) -> Font` (Marcellus), `Fonts.headingSmallCaps(float pt) -> Font` (Marcellus SC) and `Fonts.headingFamily() -> String`.
  - `Fonts.FALLBACKS = List.of("Cinzel", "Georgia", Font.SERIF)`, plus a test seam `Fonts.resolve(Predicate<String> available, boolean resourcesLoaded) -> String`.
  - `Theme` gains the components `plate`, `hairline` (gold at 35 % alpha), `barStart`, `barEnd`, `altRow` and `selection`.

- [ ] **Step 1: failing tests.**
  - `FontsTest.marcellusLoadsFromResources`: after `Fonts.install()`, `headingFamily()` is `"Marcellus"`, and `headingSmallCaps(14f).getFamily()` is `"Marcellus SC"`.
  - `FontsTest.fallsBackToCinzelThenGeorgia`: `resolve(name -> name.equals("Georgia"), false)` returns `"Georgia"`, and `resolve(n -> false, false)` returns `Font.SERIF`.
  - `FontsTest.oflLicenceShips`: the resource `ui/fonts/OFL.txt` exists and contains "SIL OPEN FONT LICENSE".
  - `TokensTest.slateValues`: `DARK.bgBase() == #12161D`, `plate() == #0C1424`, `altRow() == #1E232B`, and `hairline()` has alpha 89 (35 % of 255).
  - `QualityColorsTest.slateRareEpic`: `text(3, true) == #4A9EF0` and `text(4, true) == #B864F5`.
  - `TokensTest.slateSmallTextContrast`: every quality text colour 0–7, text.primary, muted, link and gold is ≥ 4.5:1 on bg.base, bg.surface, `altRow` and `selection`.
- [ ] **Step 2:** run `-Dtest=FontsTest,TokensTest,QualityColorsTest` and expect FAIL.
- [ ] **Step 3:** implement the signatures above. Fonts load with `Font.createFont(TRUETYPE_FONT, stream)` and `GraphicsEnvironment.registerFont`. A load failure is logged and falls back; it never throws.
- [ ] **Step 4:** run again and expect PASS. Then run the full `./mvnw -B -q verify`.
- [ ] **Step 5:** commit `feat(ui): Marcellus headings, Inter body and Slate theme tokens`.

### Task 2: WoW chrome widgets

**Files:**
- Create `{main}ui/widgets/SectionHeading.java`, `{main}ui/widgets/Chip.java` and `{main}ui/widgets/RaisedBorder.java`.
- Modify `{main}ui/widgets/SourceSection.java` (its heading becomes a `SectionHeading`), `{main}ui/detail/HeaderPanel.java` (title `Fonts.heading(20f)`), `{main}ui/widgets/RateBarRenderer.java` (6 px gradient bar, 10 % ticks), `{main}ui/StatusBar.java`, `{main}ui/AppController.java` (passes meta to `StatusBar.setProvenance`) and `{main}ui/widgets/ItemCardPanel.java` (keeps its navy card; border uses `card.border`).
- Delete `{main}ui/widgets/RankChip.java`; its callers (`NpcPage`, `InstancePage`) use `Chip.rank`.
- Tests: create `{test}ui/widgets/SectionHeadingTest.java` and `{test}ui/widgets/ChipTest.java` (absorbs `RankChipTest`); modify `RateBarRendererTest`, `StatusBarTest` and `SourceSectionTest`.

**Interfaces:**
- Consumes: Task 1 `Fonts`, `Theme.hairline/barStart/barEnd/plate`.
- Produces:
  - `SectionHeading(String title, int count)`, with `setCount(int)`, `text() -> String` ("Dropped by (34)"), `setIcon(Icon)` (16 px, Task 3) and `setTrailing(JComponent)` (Task 6's filter control).
  - `Chip(String text, Chip.Style style)` with `enum Style { PLAIN, ELITE, RARE, RARE_ELITE, BOSS, REPEATABLE, FACTION }`, plus `static Optional<Chip> rank(Integer rank)`, `outline() -> Color` and `textColor() -> Color`.
  - `RaisedBorder.create() -> Border`.
  - `StatusBar.setProvenance(Map<String,String> meta)` and `static String provenanceText(Map<String,String> meta)`.
  - `StatusBar.themeToggle() -> AbstractButton` and `StatusBar.layoutToggle() -> AbstractButton`, wired by `MainFrame` to `ThemeManager.toggle` and the F10 switch.
  - `RateBarRenderer.BAR_HEIGHT = 6` and `RateBarRenderer.barPaint() -> Paint`.

- [ ] **Step 1: failing tests.**
  - `SectionHeadingTest.headingUsesSmallCapsGoldAndCount`: the font family is `Fonts.headingSmallCaps(..)`'s, the foreground is `Theme.current().goldText()`, and `text()` is `"Dropped by (34)"`.
  - `SectionHeadingTest.ruleFadesRight`: painting into a 300×24 image, the pixel under the heading at x = 290 has lower alpha than at x = 120.
  - `ChipTest.rankStyles`: rank 1 → ELITE, gold outline and gold text; rank 4 → RARE, outline `#C0C6CF`; rank 2 → RARE_ELITE, silver outline and gold text; rank 3 → BOSS, outline `#C8463C` and text text.primary; rank 0 → empty.
  - `ChipTest.chipTextContrast`: every style's text is ≥ 4.5:1 on bg.base and bg.surface in both themes. Parchment is added in Task 4, so this test then iterates both palettes.
  - `RateBarRendererTest.barIsSixPxGradientWithTicks`: `barTrack().height == UIScale.scale(6)`; at 50 % the fill colour at the left differs from the right end (gradient); a tick pixel at 10 % of the track has alpha > 0; the number text is unchanged ("60.00 %").
  - `StatusBarTest.provenanceFromMeta`: meta `client_product=wow_classic_beta, client_build=1.60.1.70425, client_item_count=20492` gives `"Forever beta 1.60.1 · 20,492 items"`; missing keys give `""`; the bar's preferred height is `UIScale.scale(22)`.
  - `StatusBarTest.goldTopHairline`: row 0 of a painted 200×22 image is `hairline()`.
  - `SourceSectionTest` (existing tests) updated to read `heading().text()`.
- [ ] **Step 2:** run the tests and expect FAIL.
- [ ] **Step 3:** implement. `RaisedBorder` is a `FlatLineBorder`-style 1 px rounded border in `hairline()`. Apply it to `ItemCardPanel`'s holder, the zone map panel and the quest map panel.
- [ ] **Step 4:** run and expect PASS, then the full verify.
- [ ] **Step 5:** commit `feat(ui): WoW chrome — section headings, outline chips, gold bars and status bar`.

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

### Task 4: Parchment theme

**Files:**
- Modify `{main}ui/theme/Tokens.java` (`LIGHT` becomes the Parchment palette from the Global Constraints, renamed `PARCHMENT`; `LIGHT` is removed and every caller updated), `{main}ui/theme/QualityColors.java` (light array = Parchment quality colours), `{res}ui/theme/WowForeverLightLaf.properties`, `{main}ui/ThemeManager.java` and `{main}ui/WindowMenus.java` (the menu item reads "Parchment theme" / "Slate theme").
  - The properties: `@background #F3E9D2`, `@surface #EADCBB`, `@raised #FBF5E6`, `Table.background`, `List.background` and `TextField.background` = `@raised`, `Table.alternateRowColor #F6EEDC`, a selection colour chosen by the contrast test, `@accentColor #8A6D1E`, `Component.linkColor #1F4E8C`.
  - `ThemeManager`: `Mode.label()` returns "Slate" / "Parchment". The saved values stay `dark` / `light`, so existing preferences keep working.
- Tests: modify `{test}ui/theme/TokensTest.java`, `{test}ui/theme/QualityColorsTest.java` and `{test}ui/ThemeManagerTest.java`; create `{test}ui/theme/ParchmentContrastTest.java`.

**Interfaces:**
- Consumes: Tasks 1–2 tokens and widgets.
- Produces: `Tokens.PARCHMENT` and `ThemeManager.Mode.label()`. `Theme.current().plate()` and `cardBg()` are `#0C1424` in both themes.

- [ ] **Step 1: failing tests.**
  - `TokensTest.parchmentValues` asserts every value in the Global Constraints table.
  - `QualityColorsTest.parchmentQualities`: `text(0..5, false)` = `#6B6B6B, #2B2416, #1A7A00, #0057B0, #7B22B8, #A85300`.
  - `ParchmentContrastTest.smallTextOnTableBackgrounds`: every quality 0–7, text, muted, link, gold text and warn is ≥ 4.5:1 on raised `#FBF5E6`, the alternate row `#F6EEDC` and the selection colour.
  - `ParchmentContrastTest.headingsOnPage`: gold text `#7A5E14` and muted are ≥ 4.5:1 on base `#F3E9D2`.
  - `ParchmentContrastTest.platesStayNavy`: `PARCHMENT.plate() == PARCHMENT.cardBg() == #0C1424`.
  - `ThemeManagerTest.labelsAndSavedValues`: `Mode.LIGHT.label()` is "Parchment"; a saved `light` preference still restores Parchment.
- [ ] **Step 2:** run the tests and expect FAIL.
- [ ] **Step 3:** implement. Map pins and the tooltip card keep their fixed art.
- [ ] **Step 4:** run and expect PASS, then the full verify.
- [ ] **Step 5:** commit `feat(ui): Parchment light theme`.

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

### Task 6: Merged Dropped by, section filter, search chips and master-list auto-collapse

**Files:**
- Modify `{main}ui/DropRow.java` (+ instance-loot rows), `{main}ui/Columns.java` (`dropSourcesMerged()`), `{main}ui/pages/ItemPage.java` and `{main}ui/pages/NpcPage.java`.
  - `NpcPage`: drops are grouped "Notable" first (quality ≥ 4 or item class 12 Quest), then by category.
- Create `{main}ui/widgets/SourceFilterControl.java` (a small combo beside the heading, with the "≠" legend in its tooltip). Modify `{main}ui/WindowMenus.java`, which drops the View › Drop sources menu now that the filter lives on the sections.
- Modify `{main}ui/SearchBar.java` and `{main}ui/widgets/SearchDropdown.java`: kind chips move into the dropdown's top row, and the toolbar has none.
- Create `{main}ui/ListVisibility.java`. Modify `{main}ui/MainFrame.java` (wires `ListVisibility`; `toggleList` goes through it), `{main}ui/AppController.java` and `{main}ui/MainView.java` (+ `onResultOpened()`, `onResultsShown()`).
- Tests: create `{test}ui/ListVisibilityTest.java` and `{test}ui/widgets/SourceFilterControlTest.java`; modify `ItemPageTest`, `NpcPageTest`, `SearchBarTest`, `SearchDropdownTest`, `KindChipsTest`, `MainFrameContentTest`, `AppControllerTest` and `WindowMenusTest`.

**Interfaces:**
- Consumes: 2B `SourceFilters.apply`, `UiState.sourceFilter`, `InstanceLoot`, `LootListRow`.
- Produces:
  - `sealed interface DropRow` gains `record Loot(InstanceLoot loot)`. `DropRow.merged(List<DropSource> npcRows, List<InstanceLoot> loot) -> List<DropRow>` sorts by rate descending, then unknown rates after the divider. In the Source column, the instance shows as a `Chip` ("Molten Core").
  - `SourceFilterControl(SourceFilter current, Consumer<SourceFilter> onChange)`, with `static final String LEGEND`.
    - The `LEGEND` tooltip reads "≠ The sources disagree on this rate; hover the rate to see each source." Task 7 adds the line "V Vanilla rate (VMaNGOS); hover for details."
  - `final class ListVisibility`:
    - `ListVisibility(boolean savedVisible)`; `static final int WIDE_PX = 1600`.
    - `void resultsShown()`, `void previewed()` (no change), `void resultOpened(int windowWidth)`, `void toggle()` (Ctrl+B), `boolean visible()`, `boolean pinned()`.
- [ ] **Step 1: failing tests.**
  - `ItemPageTest.droppedByMergesInstanceLoot`: Felheart Belt 16806 lists its AtlasLoot boss row inside "Dropped by", with a "Molten Core" chip, sorted by rate among the NPC rows. No "In instance loot" section exists.
  - `ItemPageTest.filterNeverHidesInstanceRows`: with `SourceFilter` hiding AtlasLoot-only rows, the instance loot row stays and the NPC rows are filtered; the note counts NPC rows only.
  - `SourceFilterControlTest.changesFilterAndRerenders`: selecting "Hide AtlasLoot-only" calls `onChange`. `UiState.sourceFilter` round-trips. The tooltip equals `LEGEND`.
  - `NpcPageTest.notableDropsFirst`: an epic and a quest item lead the Drops table under "Notable".
  - `SearchBarTest.noKindChipsInToolbar`: the toolbar component tree contains no `KindChips`. `SearchDropdownTest.kindChipsInDropdownFilter`: clicking "NPCs" in the dropdown row narrows its hits to NPCs.
  - `ListVisibilityTest.previewNeverCollapses`: `resultsShown(); previewed();` → `visible()`.
  - `ListVisibilityTest.openingAResultCollapses`: `resultsShown(); resultOpened(1280)` → not visible.
  - `ListVisibilityTest.ctrlBKeepsTheListOpen`: `toggle()` while hidden → visible and `pinned()`; `resultOpened(1280)` → still visible; `toggle()` → hidden and unpinned.
  - `ListVisibilityTest.wideWindowsKeepTheList`: `resultOpened(1600)` → visible.
  - `ListVisibilityTest.searchingShowsTheList`: hidden, then `resultsShown()` → visible.
  - `AppControllerTest.commitCollapsesPreviewDoesNot`: with a `StubView` recording calls, `preview(ref)` makes no `onResultOpened`; `commit(ref)` and `openHit(...)` make one each.
- [ ] **Step 2:** run the tests and expect FAIL.
- [ ] **Step 3:** implement. `UiState.listVisible` keeps storing the pinned state only.
- [ ] **Step 4:** run and expect PASS, then the full verify.
- [ ] **Step 5:** commit `feat(ui): merged Dropped by, section source filter, search kind chips and master-list auto-collapse`.

### Task 7: Community drop rates labelled "Vanilla rate"

**Files:**
- Modify `{main}model/RateSource.java` (+ `vmangos`), `{main}model/LootRow.java` (+ `pctSource` as the last component; every existing constructor passes null), `{main}db/NpcRepository.java` and `{main}db/InstanceRepository.java` (select `pct_source` into `LootRow`), `{main}ui/widgets/RateBarRenderer.java` (headline marker, boss suffix), `{main}ui/Columns.java` (`VANILLA = " V"` marker beside `DISAGREES`), `{main}ui/pages/ItemPage.java`, `NpcPage.java` and `InstancePage.java` (pass "is a boss" to the tooltip), and `{main}ui/widgets/SourceFilterControl.java` (legend line).
- Tests: modify `{test}model/RateSourceTest.java` and `{test}ui/widgets/RateBarRendererTest.java`; extend `{test}ShippedDataTest.java` (or the split `ShippedQuestDataTest`, see Task 10).

**Interfaces:**
- Consumes: `drop_rate_source(source='vmangos')`, `item_npc_drop.pct_source`, `disagrees`, `source_count` (3A Task 9/10); `boss.npc_id` to tell bosses.
- Produces:
  - `RateSource.ORDER` is questie, questie-wowhead, questie-correction, atlasloot, atlasloot-override, vmangos, others.
  - `RateSource.label()` for `vmangos` is `"Vanilla rate (VMaNGOS)"`.
  - `static String RateSource.BOSS_NOTE = "Vanilla rate; Forever loot reworked"`.
  - `RateBarRenderer(Function<Object, List<RateSource>> sourcesOf, Predicate<Object> onBoss, Function<Object, String> headlineSource)`.
  - `static String RateBarRenderer.sourcesText(List<RateSource> sources, boolean boss)`.

- [ ] **Step 1: failing tests.**
  - `RateSourceTest.vmangosReadsVanillaRate`: `new RateSource("vmangos", 12.5).label()` is `"Vanilla rate (VMaNGOS)"`, and it sorts after `atlasloot-override`.
  - `RateBarRendererTest.vmangosHeadlineIsMarked`: a row whose headline `pct_source` is `vmangos` renders `"12.50 % V"`. The `V` is painted muted and the number in text.primary. The tooltip's first line starts with "Vanilla rate (VMaNGOS) 12.50 %".
  - `RateBarRendererTest.bossTooltipSaysLootReworked`: with `onBoss` true and a vmangos source, the tooltip contains "Vanilla rate; Forever loot reworked". Without a vmangos source it contains no such line.
  - `RateBarRendererTest.disagreementStillMarked`: questie 2.31 and vmangos 9.0 with `disagrees` give `"2.31 % ≠"` and both sources in the tooltip.
  - `ShippedQuestDataTest.hoggerClawHasAVanillaRate`: `drop_rate_source` holds a `vmangos` row for (448, 1931). The NPC page of Hogger 448 lists it in the Huge Gnoll Claw rate tooltip.
- [ ] **Step 2:** run the tests and expect FAIL.
- [ ] **Step 3:** implement. The boss test is `boss.npc_id = npc` (instance pages always are) or the NPC's zone kind `dungeon`/`raid`. It is loaded with the rate sources in `DetailPanels.load` as a `Set<Long> bossNpcs`.
- [ ] **Step 4:** run and expect PASS, then the full verify. Also check that `NOTICE.md` carries the VMaNGOS GPL-2.0 note from 3A Task 9; add it if missing.
- [ ] **Step 5:** commit `feat(ui): community drop rates read Vanilla rate`.

### Task 8: Map layers, objective pins, coordinates and /way on every map

**Files:**
- Modify `{main}ui/widgets/MapView.java`:
  - new `PinKind`s, `Pin.number`, layers, focus and per-kind visibility;
  - quest-page giver discs;
  - zone caption, cursor-coordinates corner label, right-click "Copy /way";
  - `Projection.toMap`.
- Modify `{main}ui/widgets/PinClusters.java` (clusters per layer and objective number), `{main}ui/widgets/PinGlyphs.java` (+ gold disc), `{main}ui/widgets/PinLegend.java` (+ interactive layer toggles), `{main}ui/pages/SpawnSection.java` and `{main}ui/pages/ZoneMapPanel.java` (zone names into their maps).
- Create `{main}ui/WayCommand.java`, which moves `SpawnSection.wayCommand` here.
- Tests: modify `{test}ui/widgets/MapViewTest.java` and `{test}ui/widgets/PinLegendTest.java`; create `{test}ui/WayCommandTest.java`.

**Interfaces:**
- Consumes: 2B `MapView`, `Pin`, `Cluster`, `ClipboardPort`.
- Produces:
  - `PinKind` gains `KILL, COLLECT, INTERACT, EXPLORE`, plus `Layer layer()` with `enum Layer { GIVERS, OBJECTIVES, SPAWNS }`. Hit-test and paint order is givers > objectives > spawns.
  - `Pin` gains `int number` (the 1-based objective number, 0 for none). `static Pin objective(EntityRef ref, String name, double x, double y, PinKind kind, int number)`. The existing constructors pass 0.
  - `MapView.setZoneName(String)` and `MapView.setClipboard(ClipboardPort)`.
  - `MapView.setFocus(Integer objectiveNumber)` (null = all). Other objectives' pins are hidden and giver pins are drawn at 50 % alpha.
  - `MapView.setKindVisible(PinKind, boolean)`.
  - `Optional<Point2D.Double> MapView.coordinatesAt(int x, int y)`, `String MapView.coordinatesText()` (shown in the corner, e.g. "45.1, 69.3"), and `JPopupMenu MapView.contextMenuAt(int x, int y)` ("Copy /way Elwynn Forest 45.1 69.3").
  - `Projection.toMap(double cx, double cy) -> Optional<Point2D.Double>`, empty outside the drawn image.
  - `WayCommand.of(String zoneName, double x, double y) -> String`.
  - `PinLegend.interactive(MapView map)`, whose entries become toggles for the kinds present.

- [ ] **Step 1: failing tests.**
  - `MapViewTest.cursorCoordinatesInvertTheProjection`: run at `UIScale` 1.0 and 1.5 on a 1002×668 image in components 640 and 1002 px wide. For 20 random map points, `coordinatesAt(toComponent(p))` equals `p` within 0.2.
  - `MapViewTest.pointerBelowTheImageHasNoCoordinates`: a component taller than the drawn height, with a pointer at y = drawnHeight + 5, gives empty coordinates, an empty corner label and a context menu without a "/way" item.
  - `MapViewTest.rightClickCopiesWay`: zone "Elwynn Forest", a click at map (42.1, 65.9) → the menu item text is "Copy /way Elwynn Forest 42.1 65.9". Activating it puts that command, without "Copy ", in a fake `ClipboardPort`.
  - `MapViewTest.objectivePinsClusterPerObjective`: two KILL pins of objective 1 within 8 px form one cluster of 2. A KILL pin of objective 2 at the same spot is its own cluster. A QUEST_START at the same spot is hit first.
  - `MapViewTest.focusDimsGiversAndHidesOtherObjectives`: after `setFocus(2)`, `visiblePins()` has no objective-1 pins and keeps the givers. The painted giver disc pixel has alpha ≈ 128.
  - `MapViewTest.objectiveShapes`: painting KILL n=3 shows `#E5705C` at the centre ring and the digit "3"; COLLECT shows a `#7CC46B` diamond (corner pixels transparent); INTERACT shows `#6FA8DC`; EXPLORE has a transparent centre. Every shape has a `#0C1424` outline pixel and a `#F4EBD0` halo pixel.
  - `MapViewTest.questPageGiverIsAGoldDisc`: an emphasised QUEST_START paints `#FFD100` at the disc edge and `#0C1424` in the "!" glyph, in both themes.
  - `MapViewTest.zoneCaptionShown`: `setZoneName("Westfall")` paints a caption; its accessible description reads "Westfall, 3 pins". This closes the backlog §3 item "spawn maps do not name the zone".
  - `PinLegendTest.togglesHideKinds`: clicking "Kill" in an interactive legend calls `setKindVisible(KILL, false)`; KILL pins leave `visiblePins()`.
  - `WayCommandTest`: `of("Westfall", 42.34, 67.45)` gives "/way Westfall 42.3 67.5", and a null zone gives "/way 42.3 67.5".
- [ ] **Step 2:** run the tests and expect FAIL.
- [ ] **Step 3:** implement. Coordinates use the same `Projection` as painting, divided by `UIScale` nowhere else.
- [ ] **Step 4:** run and expect PASS, then the full verify.
- [ ] **Step 5:** commit `feat(ui): objective pin layers, map coordinates and /way on every map`.

### Task 9: NPC patrol routes

**Files:**
- Create `{main}model/NpcRoute.java` and `{main}ui/widgets/RouteGeometry.java`.
- Modify `{main}db/ZoneRepository.java` (+ `npcRoutesInZone`), `{main}db/NpcRepository.java` (`routes` stays), `{main}ui/widgets/MapView.java` (+ route layer under the pins), `{main}ui/pages/SpawnSection.java` (the NPC's own routes in SELF gold) and `{main}ui/pages/ZoneMapPanel.java` (+ "Routes" toggle, off by default, also in the compact "Pins ▾" menu).
- Modify `{main}ui/UiState.java` (+ `routesShown`, default false), `{main}ui/detail/DetailPanels.java` (`LoadedNpc` + routes) and `{main}ui/pages/ZoneMapPanel.PinSource` (+ `routes(long zoneId)`).
- Tests: create `{test}ui/widgets/RouteGeometryTest.java`; modify `ZoneRepositoryTest`, `MapViewTest`, `NpcPageTest`, `ZonePageTest` and `UiStateTest`; extend the shipped-data test.

**Interfaces:**
- Consumes: 3A `Route(npcId, zoneId, route, points, loop)`, `Route.isLoop` (≥ 3 points, last within 2.0 of the first), `NpcRepository.routes(npcId)` and `ZoneRepository.routesInZone(zoneId)`.
- Produces:
  - `record NpcRoute(EntityRef npc, String name, Integer rank, Route route)`.
  - `ZoneRepository.npcRoutesInZone(long zoneId) -> List<NpcRoute>`.
  - `MapView.setRoutes(List<MapView.RouteLine>)` with `record RouteLine(EntityRef npc, String name, PinKind colourKind, List<Route.Point> points, boolean loop)`.
  - `RouteGeometry.arrowAnchors(List<Point2D> pts) -> List<Anchor>` with `record Anchor(Point2D at, double angle)`, at point indices 10, 20, …, angled along the segment into that point.
  - `RouteGeometry.distanceToPolyline(Point2D p, List<Point2D> pts, boolean closed) -> double`.
  - `MapView.END_TOOLTIP = "Last known point of the patrol; the NPC may despawn here"`.

- [ ] **Step 1: failing tests.**
  - `RouteGeometryTest.arrowsEveryTenPoints`: 35 points give anchors at indices 10, 20 and 30. The angle of a segment going right is 0.
  - `RouteGeometryTest.distanceToClosedPolygonIncludesTheClosingSegment`.
  - `MapViewTest.routeDrawsDashedTwoPxLine`: a non-loop 3-point route paints the colour of its kind along the first segment with gaps (dashed). The line is ≈ 2 px wide.
  - `MapViewTest.nonLoopGetsEndMarker`: a hollow square at the last point; the tooltip there equals `END_TOOLTIP`.
  - `MapViewTest.loopIsClosedWithoutEndMarker`: a loop paints the closing segment and no square.
  - `MapViewTest.lineHitOpensTheNpc`: a click 3 px from a segment opens `ViewTarget.Entity(npc)`. A pin under the pointer wins over the line. Routes never cluster.
  - `ZonePageTest.routesToggleOffByDefaultAndRemembered`: Westfall 40's "Routes" toggle is off. Turning it on loads through `PinSource.routes` on the worker and persists `UiState.routesShown`.
  - `NpcPageTest.defiasMessengerShowsItsRoute`: NPC 550's Location map has ≥ 1 `RouteLine` with ≥ 30 points in Westfall. Its loop flag equals the data (3A measured `true`).
  - `ShippedQuestDataTest.someRoutesEndOpen`: `npc_waypoint` has ≥ 1 route with `loop=false` (3A counted 111 of 564). The test names the first such NPC id for the screenshot.
- [ ] **Step 2:** run the tests and expect FAIL.
- [ ] **Step 3:** implement. The route layer is drawn under all pins, and its colour comes from `MapView.colour(kind)` (rank: boss gold, rare silver, NPC muted; SELF gold on the NPC page).
- [ ] **Step 4:** run and expect PASS, then the full verify.
- [ ] **Step 5:** commit `feat(ui): NPC patrol routes on maps`.

### Task 10: Quest page data (QuestLocator)

**Files:**
- Create `{main}db/QuestLocator.java`, `{main}model/ObjectiveVerb.java`, `{main}model/ObjectiveSource.java`, `{main}model/ObjectiveRow.java`, `{main}model/GiverSpot.java`, `{main}model/QuestFacts.java` and `{main}model/QuestPageData.java`.
- Modify `{main}db/QuestRepository.java` (+ `facts`), `{main}db/Repositories.java` (+ `questLocator`) and `{main}ui/detail/DetailPanels.java` (`LoadedQuest(QuestPageData data, Map<Long, BufferedImage> maps)`, with the maps decoded through `MapCache`).
- Tests: create `{test}db/QuestLocatorTest.java` (fixture DB) and `{test}model/ObjectiveVerbTest.java`. Split `{test}ShippedDataTest.java` by creating `{test}ShippedQuestDataTest.java` (3A ledger note) plus a shared helper `{test}ShippedDb.java` (`static Repositories repos()`).

**Interfaces:**
- Consumes: 3A `QuestRepository.objectives/sourceItems/chain`, `QuestObjective` (kind as a raw String, ordinals from 0, credit groups share an ordinal), `QuestChain`; 2B `QuestDetail`, `QuestGiver`, `Spawn`, `MapCoordinates.onMap`, `InstanceSummary`, `item_npc_drop`, `item_object_drop`.
- Produces:
  - `enum ObjectiveVerb { KILL("Kill"), COLLECT("Collect"), USE("Use"), EXPLORE("Explore"), REPUTATION("Reputation"), CAST("Cast"), ESCORT("Escort") }` with `word()` and `static ObjectiveVerb of(String kind, String text)`.
    - The kinds map as creature→KILL, object→USE, item→COLLECT, reputation→REPUTATION and spell→CAST.
    - An `explore` row becomes USE when its text starts with "Use ", ESCORT when it starts with "Escort " or "Protect ", and EXPLORE otherwise. This follows the 3A ledger: extraObjectives arrive as `explore`.
  - `record ObjectiveSource(EntityRef ref, String name, Double dropPct, String pctSource, List<Spawn> spawns)`.
  - `record ObjectiveRow(int number, ObjectiveVerb verb, List<EntityRef> targets, String targetName, String text, List<ObjectiveSource> sources, List<QuestObjective.Point> points, InstanceSummary instance)`.
    - `number` is 1-based over the distinct ordinals. `targets` lists the credit group's members, base first.
    - `sources` are sorted: rate descending, unknown last, then NPCs before objects.
    - `instance` is non-null when every located spawn is inside an instance. The instance is the one whose boss list holds the NPC, else the zone's only instance.
  - `record GiverSpot(QuestGiver giver, List<Spawn> spawns, List<ObjectiveSource> itemSources)`. `itemSources` is used when the giver is an item.
  - `record QuestFacts(Long zoneId, String zoneName, String sortLabel, Integer requiredRaces, Integer requiredClasses, boolean repeatable)`.
  - `record QuestPageData(QuestDetail detail, QuestFacts facts, List<ObjectiveRow> objectives, List<QuestSourceItem> sourceItems, List<GiverSpot> starts, List<GiverSpot> ends, QuestChain chain)`, with `List<Long> mapZones()`.
    - `mapZones()` returns every zone holding an on-map point, in the order: start zone, objective zones, turn-in zone.
  - `QuestLocator(Database db)` and `Optional<QuestPageData> load(long questId)`. It makes one `db.read` and caps spawns at `ZoneRepository.PIN_CAP` per source.
  - `QuestRepository.facts(long questId) -> Optional<QuestFacts>`.

- [ ] **Step 1: failing tests.**
  - `ObjectiveVerbTest`: ("creature", null) → KILL; ("explore", "Use a Fresh Carcass at the Flame of Uzel") → USE; ("explore", "Escort OOX-17/TN to Steamwheedle Port") → ESCORT; ("explore", "Explore the Cave") → EXPLORE; an unknown kind → EXPLORE.
  - `ShippedQuestDataTest.hoggerLocates` (176): one objective, number 1, COLLECT, target 1931 "Huge Gnoll Claw". The first source is Hogger 448 at ≈ 84.0 with spawns in Elwynn 12. The starts are objects 68 and 156561 with Elwynn spawns. The end is NPC 240 at (42.1, 65.9). `mapZones()` is `[12]`.
  - `ShippedQuestDataTest.vanCleefIsInsideTheDeadmines` (166): objective 1's `instance.name()` is "The Deadmines" (instance 3). No VanCleef spawn appears in `mapZones()`, which is `[40]`.
  - `ShippedQuestDataTest.drakkisathPicksUpperBlackrockSpire` (6502): `instance.id()` is 20 (the boss list holds 10363). `mapZones()` is `[618]`.
  - `ShippedQuestDataTest.thunderfuryStartsFromAnItem` (7787): `starts.get(0).giver().ref()` is ITEM 19018, and its `itemSources` holds Prince Thunderaan 14435 with no spawns. There are no objectives. `sourceItems` holds 19018 `provided`. `mapZones()` is `[1377]`.
  - `ShippedQuestDataTest.woolComesFromObjects` (7791): objective 1 is COLLECT Wool Cloth 2592, with no NPC sources and ≥ 16 object sources.
  - `ShippedQuestDataTest.pyrewoodKillCreditIsOneRow` (452): one row with `targets.size() == 9`, base 2060 first, spawns in Silverpine 130.
  - `ShippedQuestDataTest.escortWithoutPoints` (648): objective 1 is ESCORT with no points and no sources.
  - `QuestLocatorTest.unknownQuestIsEmpty`; `QuestLocatorTest.offMapSpawnsDropOut` (a fixture spawn at −1, −1 in a zone with a map leaves no point).
- [ ] **Step 2:** run the tests and expect FAIL.
- [ ] **Step 3:** implement with parameterised SQL and the existing indexes: `quest_objective(entity_id, kind)`, `idx_item_npc_drop_npc`, the spawn indexes and `idx_instance_zone`.
- [ ] **Step 4:** run and expect PASS. Then the full verify, and check that a warm `QuestLocator.load` of 6502 takes ≤ 300 ms on the shipped DB (backlog gate; log it, do not assert in CI).
- [ ] **Step 5:** commit `feat(app): quest page locator for objectives, givers and sources`.

### Task 11: Quest page skeleton — header, stepper, map and givers

**Files:**
- Rewrite `{main}ui/pages/QuestPage.java`.
- Create `{main}ui/pages/quest/QuestHeader.java`, `{main}ui/pages/quest/ChainStepper.java`, `{main}ui/pages/quest/QuestMapPanel.java` (zone tabs, edge banner, no-map fallback) and `{main}ui/pages/quest/GiverSections.java`.
- Modify `{main}ui/detail/DetailPanels.java` (render) and `{main}ui/Labels.java` (+ `questLevels(Integer level, Integer required)` → "Lv 7 (req. 4)", + `factions(Integer raceMask)`).
- Tests: rewrite `{test}ui/pages/QuestPageTest.java`; create `{test}ui/pages/quest/QuestMapPanelTest.java`, `{test}ui/pages/quest/ChainStepperTest.java` and `{test}ui/pages/QuestPageScanTest.java`.

**Interfaces:**
- Consumes: Task 10 `QuestPageData` and the maps; Task 8 `MapView`, `Pin.giver`, `PinLegend.interactive`; Task 5 `HeaderPanel` and the section rules; Task 2 `Chip`.
- Produces:
  - `QuestPage(QuestPageData data, Map<Long, BufferedImage> maps, Navigator nav, ClipboardPort clipboard)`.
  - `QuestPage.header()`, `stepper()`, `mapPanel()`, `objectivesPanel()` (filled by Task 12), `chainPanel()` (filled by Task 13), `section(String)`, `sections()`, `body()` and `isSideBySide()`.
  - `body()` is the `JPanel` holding Objectives (left) and Map (right). They sit side by side when the page is ≥ `QuestPage.SIDE_BY_SIDE_PX = 900` wide; below that, and in compact, they stack with the map above.
  - `ChainStepper(QuestChain chain, Navigator nav)`: `text()` gives "‹ Previous · This quest · Next ›  Step 7 of 7", or in compact "‹ Prev · 7/7 · Next ›". It is hidden when `chain.spine().size() <= 1`.
  - `QuestMapPanel(QuestPageData data, Map<Long, BufferedImage> maps, Navigator nav, ClipboardPort clipboard)`.
    - `tabs() -> List<ZoneTab>` with `record ZoneTab(long zoneId, String zoneName, String glyphs)`, e.g. "Elwynn Forest ! ? ①".
    - `shownZone() -> long`, `showZone(long)`, `nextZone()` (for Z), `banner() -> JButton` and `noMapNote() -> JLabel`.
  - `Labels.questLevels(7, 4)` gives "Lv 7 (req. 4)". `Labels.factions(77)` gives "Alliance", `factions(178)` "Horde", and a mixed or null mask gives null.

- [ ] **Step 1: failing tests.**
  - `QuestPageTest.hoggerHeader` (176):
    - The title is `Wanted:  "Hogger"`. The facts read "Lv 11 (req. 5)". There is an "Elwynn Forest" zone chip link to ZONE 12, no "Repeatable" chip, and a plate icon with "!".
    - No stepper. The chain section reads "Standalone quest — no prerequisites or follow-ups." (Task 13 fills it; here assert that the chain panel exists.)
  - `QuestPageTest.repeatableChip` (7796): the header holds a REPEATABLE `Chip` reading "Repeatable".
  - `QuestPageTest.sortLabelChip` (7787): a "Legendary" chip replaces the zone chip.
  - `ChainStepperTest.defiasStepSevenOfSeven` (166): "Step 7 of 7"; Previous opens 155; Next is disabled.
  - `ChainStepperTest.hiddenForATrivialSpine` (176 and a chain whose spine is 1): not visible, so no "Step 1 of 1".
  - `QuestMapPanelTest.hoggerPinsGiversOnElwynn` (176): `shownZone()` is 12. The pins hold two QUEST_START (objects 68 and 156561) and one QUEST_END (240), all emphasised gold discs. `tabs()` is one tab "Elwynn Forest ! ?". There is no banner.
  - `QuestMapPanelTest.defiasSharesOneSpot` (166): Gryan's "!" and "?" at one spot show as one marker whose menu lists both roles. Tab: "Westfall ! ?".
  - `QuestMapPanelTest.turnInElsewhereShowsBanner` (6502 with a fixture turn-in in another zone): the banner reads "Turn-in is in Stormwind City ›", and pressing it switches `shownZone()`.
  - `QuestMapPanelTest.noMapForZone`: a quest whose only zone has no map shows `noMapNote()` "No map for <zone> yet." and a coordinate list.
  - `QuestPageTest.itemStartLine` (7787): Given by reads "Starts from item: Dormant Wind Kissed Blade — drops from ›". The map pins no "!" for it; it pins the item's sources, none here, and opens on Silithus with the "?" for Highlord Demitrian 14347.
  - `QuestPageTest.giverTablesHaveZoneAndCoordinates` (176): the "Given by" rows read "Wanted Poster · Elwynn Forest · 24.6, 74.7"; "Turned in to" reads "Marshal Dughan · Elwynn Forest · 42.1, 65.9".
  - `QuestPageTest.compactLayout`: within `CompactColumns`, the map is 400 px wide and 267 high, above the objectives; zone tabs become a combo; Given by and Turned in to are one line each.
  - `QuestPageScanTest.giverAndTurnInAboveTheFold`, parameterised over 176, 166, 6502, 7787 and 7791. Lay the page out headless at `SCAN_WIDTH = 1216` × `SCAN_HEIGHT = 680`: a 1280×800 window minus the rail, toolbar, menu and status bar, with the master list collapsed.
    - The header, the stepper (if any) and the map panel bounds end ≤ 680 px from the page top.
    - Every start and end pin's component point lies inside the map's visible rectangle, or the banner naming the other zone is above 680.
- [ ] **Step 2:** run the tests and expect FAIL.
- [ ] **Step 3:** implement. Rewards and "Related items" follow the chain section; "Related items" is collapsed. Section titles are "Objectives", "Given by", "Turned in to", "Quest chain", "Rewards" and "Related items". `SourcesPage.bindJumpKeys` (Ctrl+1…6) keeps working.
- [ ] **Step 4:** run and expect PASS, then the full verify.
- [ ] **Step 5:** commit `feat(ui): quest page header, chain stepper, zone map and givers`.

### Task 12: Objectives section and map filtering

**Files:**
- Create `{main}ui/pages/quest/ObjectivesPanel.java` and `{main}ui/pages/quest/ObjectiveText.java`.
- Modify `{main}ui/pages/quest/QuestMapPanel.java` (objective pins, focus chip) and `{main}ui/pages/QuestPage.java`.
- Tests: create `{test}ui/pages/quest/ObjectiveTextTest.java` and `{test}ui/pages/quest/ObjectivesPanelTest.java`; extend `{test}ui/pages/QuestPageScanTest.java` and `{test}ui/pages/quest/QuestMapPanelTest.java`.

**Interfaces:**
- Consumes: Task 10 `ObjectiveRow`, `ObjectiveSource`, `QuestSourceItem`; Task 8 `Pin.objective`, `MapView.setFocus`.
- Produces:
  - `ObjectiveText.where(ObjectiveRow row) -> String`, with these cases:
    - Kill: "Elwynn Forest · 23 spawns", or "Silverpine Forest · 41 spawns of 9 NPCs".
    - Collect, up to 3 named: "Drops from Hogger 84 %", or "Drops from A 34 %, B 28 %, +3 more".
    - Objects: "Found in Battered Chest, Alterac Granite, +14 more".
    - Nothing located: "Source unknown — not in the quest database yet."
    - Instance: "Inside The Deadmines — open dungeon page ›".
    - Escort: "Escort — from the quest giver's spot".
  - `ObjectiveText.rate(Double pct) -> String`: "84 %" when ≥ 1, one decimal below 1 ("0.5 %").
  - `ObjectivesPanel(List<ObjectiveRow> rows, List<QuestSourceItem> items, Navigator nav, IntConsumer onFocus)`, with `rows() -> List<ObjectiveRowView>`, `carryLine() -> JLabel` ("You carry: Sealed Letter (given at start)") and `needLine() -> JLabel` ("You will need: …").
  - `QuestMapPanel.focus(int number)`, `clearFocus()`, `focusChip() -> JButton` ("Showing objective 2 ✕") and `showAllSources(int number)`.
  - `QuestMapPanel.TOP_SOURCES = 5`, with the link "Show all 9 sources".

- [ ] **Step 1: failing tests.**
  - `ObjectiveTextTest`: the cases above on hand-built rows. Three NPC sources plus three more give "+3 more". Unknown rates print the name without "%". Kill-credit spawns sum over the members.
  - `ObjectivesPanelTest.hoggerRow` (176): one row with badge "1", verb "Collect", a target link "Huge Gnoll Claw" to ITEM 1931, and the where line "Drops from Hogger 84 %". No count appears anywhere in the row text (decision 2).
  - `ObjectivesPanelTest.defiasInstanceLink` (166): the where line is "Inside The Deadmines — open dungeon page ›", and clicking it opens INSTANCE 3.
  - `ObjectivesPanelTest.woolFromObjects` (7791): the where line starts "Found in ".
  - `ObjectivesPanelTest.killCreditOneRow` (452): one row, verb "Kill", the where line "Silverpine Forest · n spawns of 9 NPCs"; the map holds KILL pins of number 1 for every member.
  - `ObjectivesPanelTest.carriedItems` (7787): `carryLine()` reads "You carry: Dormant Wind Kissed Blade (given at start)"; `needLine()` is hidden; no pins for carried items.
  - `ObjectivesPanelTest.escortWithoutPoints` (648): verb "Escort", the where line "Escort — from the quest giver's spot".
  - `QuestMapPanelTest.clickingARowFocusesTheMap`: on 452 with a fixture second objective, clicking row 2 shows only objective-2 pins and dims the givers. The chip reads "Showing objective 2 ✕"; clicking it clears the focus.
  - `QuestMapPanelTest.topFiveSourcesThenShowAll`: a collect objective with 9 sources pins the spawns of the first 5. The link reads "Show all 9 sources" and pins all 9 after a click.
  - `QuestPageScanTest.firstObjectiveAboveTheFold` (the five quests): the first objective row with its where line ends ≤ 680 px. For 7787, which has no rows, the "Objectives" body shows the Questie objectives text and "No objective locations in the quest database." above 680.
- [ ] **Step 2:** run the tests and expect FAIL.
- [ ] **Step 3:** implement. Pins per verb: KILL for creature targets' spawns, COLLECT for collect sources' spawns, INTERACT for object targets, EXPLORE for explore points. They are numbered by `ObjectiveRow.number`.
- [ ] **Step 4:** run and expect PASS, then the full verify.
- [ ] **Step 5:** commit `feat(ui): quest objectives with where lines and map filtering`.

### Task 13: Quest chain tree

**Files:**
- Modify `{main}model/QuestChain.java` (+ `Connector.LEADS_TO("Leads to:")`) and `{main}db/QuestChainBuilder.java`. The builder gives a breadcrumb target (`breadcrumb_for`) the LEADS_TO connector where it now gives UNLOCKS.
- Create `{main}ui/pages/quest/ChainRows.java` (a pure view model) and `{main}ui/pages/quest/ChainPanel.java`.
- Modify `{main}ui/pages/QuestPage.java`.
- Tests: create `{test}ui/pages/quest/ChainRowsTest.java` and `{test}ui/pages/quest/ChainPanelTest.java`; extend `{test}db/QuestChainBuilderTest.java` (or the 3A `QuestChainTest`) and `ShippedQuestDataTest`.

**Interfaces:**
- Consumes: 3A `QuestChain(steps, currentIndex, spine, truncated)`, `Step(questId, name, level, zoneName, connector, parentQuestId, depth, loop)`, `MAX_DEPTH = 50`.
- Produces:
  - `record ChainRows.Row(Kind kind, int indent, String text, QuestChain.Step step, String fanKey, int hidden)` with `enum Kind { CONNECTOR, STEP, CURRENT, FAN, SHOW_EARLIER, SHOW_LATER, NOTE }`.
  - `static List<Row> ChainRows.of(QuestChain chain, Set<String> expandedFans, boolean expandEarlier, boolean expandLater)`.
  - `ChainRows.WINDOW = 2` and `ChainRows.FAN_MIN = 3`.
  - Rules:
    - Rows follow `steps` order. A parent's children are grouped by `(parentQuestId, connector)`, and each group prints its connector label once, above its first member.
    - A one-member REQUIRES_ONE, REQUIRES_ALL or UNLOCKS group whose member is a spine step prints no connector (the plain linear chain).
    - EXCLUSIVE members are joined by "or".
    - Siblings in one group sharing a name, at least FAN_MIN of them, collapse into one FAN row "Camping 101 ×87 ›".
    - Steps with depth < −WINDOW sit behind "Show n earlier steps", and those with depth > WINDOW behind "Show n later steps".
    - A loop step's text ends " (loop in data)". A missing quest reads "Unknown quest #2955", muted, not a link.
    - `truncated` adds a NOTE "The chain goes on beyond 50 steps." A standalone chain is one NOTE "Standalone quest — no prerequisites or follow-ups.".
    - Step text is "Lv 10  Name — Zone", with the level and zone left out when null.
  - `ChainPanel(QuestChain chain, Navigator nav)`, with `rows() -> List<ChainRows.Row>`, `previous()` and `next()` (for `[` `]`). The current row is painted bold, with a 3 px gold bar on the left.

- [ ] **Step 1: failing tests.**
  - `ChainRowsTest.defiasShowsTwoBeforeAndFourEarlier` (166): the rows are SHOW_EARLIER "Show 4 earlier steps", STEP 142, STEP 155, then CURRENT 166 "Lv 22  The Defias Brotherhood — The Deadmines". There are no CONNECTOR rows. Expanding earlier adds 65, 132, 135 and 141 in that order.
  - `ChainRowsTest.andOrExclusiveAndMissing` (4604):
    - CONNECTOR "Requires one of:" is followed by the three members 2955, 4605 and 4606. 2955 reads "Unknown quest #2955" and its `step.name()` is null.
    - CONNECTOR "Choose one (exclusive):" is followed by 2953 "or" 4603.
  - `ChainRowsTest.requiresAllOf` (7795): CONNECTOR "Requires all of:" with 7791, 7793 and 7794. From 7791's page, 7795 sits under "Unlocks:" and 7796 under 7795.
  - `ChainRowsTest.breadcrumbBothWays`: on 11, "Optional lead-in:" is followed by 239. On 239, "Leads to:" is followed by 11 (the new connector).
  - `ChainRowsTest.sameNamedUnlocksCollapse` (783, both expand flags on): exactly one FAN row whose text starts "Camping 101 ×87"; no STEP row is named "Camping 101". Expanding its `fanKey` shows 87 STEP rows.
  - `ChainRowsTest.nestedGroupsKeepTheirParent`: a hand-built chain with steps 11 (ALL, parent 10, depth −1), 13 (ONE, parent 11, depth −2), 14 (ONE, parent 11, depth −2) and 12 (ALL, parent 10, depth −1).
    - Exactly one "Requires all of:" row at indent 1, covering 11 and 12.
    - One "Requires one of:" at indent 2, covering 13 and 14.
  - `ChainRowsTest.loopAndTruncated`: a fixture cycle shows "(loop in data)" once; `truncated=true` adds the NOTE.
  - `ChainRowsTest.standalone` (176): one NOTE "Standalone quest — no prerequisites or follow-ups.".
  - `QuestChainBuilderTest.breadcrumbTargetLeadsTo`: a fixture breadcrumb_for edge yields `Connector.LEADS_TO`. Every other 3A chain test is unchanged.
  - `ChainPanelTest.currentRowIsBoldWithGoldBar`: the current row's label font is bold; the painted x 0–2 pixels are gold.
  - `ChainPanelTest.compactHidesStandaloneAndCollapses`: in compact, the standalone NOTE is hidden and a chain section is collapsed to "Quest chain (7)".
- [ ] **Step 2:** run the tests and expect FAIL.
- [ ] **Step 3:** implement. Clicking a STEP opens its quest. Clicking FAN, SHOW_EARLIER or SHOW_LATER re-renders the panel in place, with no database access.
- [ ] **Step 4:** run and expect PASS, then the full verify.
- [ ] **Step 5:** commit `feat(ui): quest chain tree with grouped connectors and collapsed fans`.

### Task 14: "Involved in quests" on NPC, object and item pages

**Files:**
- Create `{main}model/QuestInvolvement.java`.
- Modify `{main}db/QuestRepository.java` (+ `involving`), `{main}ui/detail/DetailPanels.java` (loads it with the NPC, object and item pages), `{main}ui/Columns.java` (+ `questInvolvements()`), and in `{main}ui/pages/`: `NpcPage.java` (one "Quests" section replaces "Starts quests"/"Ends quests"), `ObjectPage.java` (the same), `ItemPage.java` (the "Quests" section replaces "Related quests") and `InstancePage.java` (its "Quests" section from Task 5 shows the role column too).
- Tests: extend `QuestRepositoryTest`, `NpcPageTest`, `ObjectPageTest`, `ItemPageTest` and `ShippedQuestDataTest`.

**Interfaces:**
- Consumes: `quest_giver(entity_id, kind, role)`, `quest_objective(entity_id, kind)` (npc→`creature`, object→`object`, item→`item`), `item_npc_drop` joined to item objectives, `quest_source_item(item_id)`, `item_related_quest`.
- Produces:
  - `record QuestInvolvement(QuestSummary quest, Set<Role> roles, String viaItemName)` with `enum Role { STARTS, ENDS, OBJECTIVE, DROPS_OBJECTIVE_ITEM, PROVIDED, REQUIRED, RELATED }`.
  - `QuestInvolvement.glyphs()` returns "!", "?" or "! ?".
  - `QuestInvolvement.rolesText()` returns e.g. "Starts · Ends", "Kill objective", "Drops Huge Gnoll Claw", "Given at start" or "Needed".
  - `QuestRepository.involving(EntityRef ref) -> List<QuestInvolvement>`: one row per quest with its roles merged, sorted by level then name, capped at `QuestRepository.INVOLVING_CAP = 500`, with the cap reported in the section note.

- [ ] **Step 1: failing tests.**
  - `ShippedQuestDataTest.hoggerIsInvolvedIn176`: `involving(NPC 448)` holds 176 with roles `{DROPS_OBJECTIVE_ITEM}` and `viaItemName` "Huge Gnoll Claw".
  - `ShippedQuestDataTest.dughanEnds176`: `involving(NPC 240)` holds 176 with `{ENDS}`.
  - `ShippedQuestDataTest.gryanStartsAndEndsTheDefiasChain`: NPC 234 holds 166 with `{STARTS, ENDS}` and glyphs "! ?".
  - `ShippedQuestDataTest.clawIsAnObjective`: ITEM 1931 holds 176 with `{OBJECTIVE}`.
  - `ShippedQuestDataTest.bladeStartsAndIsProvided`: ITEM 19018 holds 7787 with `{STARTS, PROVIDED}`.
  - `ShippedQuestDataTest.posterStartsHogger`: OBJECT 68 holds 176 with `{STARTS}`.
  - `NpcPageTest.oneQuestsSection` (234): there is no "Starts quests" or "Ends quests" section. "Quests (n)" has a glyph column first and a "Role" column.
  - `ItemPageTest.questsSectionReplacesRelated` (1931): "Quests (1)" with "Collect objective"; no "Related quests".
  - `QuestRepositoryTest.involvingCaps`: a fixture with 501 quests on one NPC returns 500, and `capped` is true.
- [ ] **Step 2:** run the tests and expect FAIL.
- [ ] **Step 3:** implement in one `db.read` with `UNION ALL` subqueries over the indexed columns.
- [ ] **Step 4:** run and expect PASS, then the full verify.
- [ ] **Step 5:** commit `feat(ui): involved in quests on NPC, object and item pages`.

### Task 15: Quest page keys and copy-to-game links

**Files:**
- Modify `{main}ui/KeyMap.java` (+ page key rows and `installPageKeys`), `{main}ui/MainFrame.java` (the ESCAPE chain asks the page first), `{main}ui/pages/QuestPage.java` (binds its keys), `{main}ui/pages/ItemPage.java` and `QuestPage.java` (the "Copy in-game link" button in the header) and `README.md` (keyboard table between the keymap markers, plus the quest page in "Using the app").
- Create `{main}ui/GameLinks.java` and `{main}ui/pages/PageEscape.java`, an interface `boolean escape()` that a page implements to clear its own state.
- Tests: extend `{test}ui/KeyMapTest.java` and `{test}ui/ReadmeKeyMapTest.java`; create `{test}ui/pages/QuestPageKeysTest.java` and `{test}ui/GameLinksTest.java`.

**Interfaces:**
- Consumes: Tasks 11–13 `QuestPage.section`, `QuestMapPanel.focus/nextZone`, `ChainPanel.previous/next`; 2B `KeyMap.Binding`, `ClipboardPort`.
- Produces:
  - `KeyMap.QUEST_PAGE = "questPage"`, with these new `BINDINGS` rows:
    - "G / T / O / M / C / R": "Quest page: Given by, Turned in to, Objectives, Map, Chain, Rewards"
    - "1…9": "Quest page: show objective n on the map"
    - "[ / ]": "Quest page: previous / next quest in the chain"
    - "Z": "Quest page: next zone on the map"
    - The Esc row now reads "Close dropdown, then clear the map filter, then clear search, then focus list".
  - `KeyMap.installPageKeys(JComponent page, Map<String, Runnable> handlers, Supplier<Component> focusOwner)`. The bindings use `WHEN_IN_FOCUSED_WINDOW` and are enabled only while the focus owner is not a `JTextComponent`.
  - `GameLinks.item(long id, String name)` gives `"|Hitem:19019:0:0:0:0:0:0:0:60|h[Name]|h"`, and `GameLinks.quest(long id, int level, String name)` gives `"|Hquest:176:11|h[Name]|h"`. This is the spec's format; see the open questions on colour codes.

- [ ] **Step 1: failing tests.**
  - `KeyMapTest.pageKeysDeclared`: `BINDINGS` holds the four rows above; `strokes` for `QUEST_PAGE` include `typed g`, `typed t`, `typed o`, `typed m`, `typed c`, `typed r`, `typed 1`…`typed 9`, `typed [`, `typed ]` and `typed z`.
  - `KeyMapTest.pageKeysIgnoredInTextComponents`: with a focus owner that is a `JTextField`, every page action reports `isEnabled() == false`; with a `JTable` it reports true.
  - `QuestPageKeysTest.typingInSearchDoesNotJump`: typing "Hogger" into a `SearchBar` field leaves the page scroll at 0 and the map unfocused.
  - `QuestPageKeysTest.lettersJumpToSections` (166): G scrolls "Given by" to the top; C scrolls "Quest chain" to the top.
  - `QuestPageKeysTest.digitFiltersObjective` (452): pressing "1" sets the map focus to 1; Esc clears it, and a second Esc reaches the search clear (stub).
  - `QuestPageKeysTest.bracketsWalkTheChain` (166): `[` opens 155; `]` does nothing at the end.
  - `QuestPageKeysTest.zCyclesZoneTabs`: a two-zone fixture alternates `shownZone()`.
  - `ReadmeKeyMapTest` (existing) passes with the updated README table.
  - `GameLinksTest.thunderfuryLink`: `item(19019, "Thunderfury, Blessed Blade of the Windseeker")` gives `"|Hitem:19019:0:0:0:0:0:0:0:60|h[Thunderfury, Blessed Blade of the Windseeker]|h"`. `quest(176, 11, "Wanted:  \"Hogger\"")` gives `"|Hquest:176:11|h[Wanted:  \"Hogger\"]|h"`. `PasteParser.parse` of the item link resolves to ITEM 19019 (round trip).
  - `ItemPageTest.copyInGameLink` and `QuestPageTest.copyInGameLink`: the header button copies the link through a fake `ClipboardPort`.
- [ ] **Step 2:** run the tests and expect FAIL.
- [ ] **Step 3:** implement.
- [ ] **Step 4:** run and expect PASS, then the full verify.
- [ ] **Step 5:** commit `feat(ui): quest page keys and copy-to-game links`.

### Task 16: Final polish, quality-fix audit, screenshots and docs

**Files:**
- Modify `README.md` (screenshots, "Using the app" for the quest page, routes, coordinates, themes), `docs/ROADMAP.md` (mark the v0.3.0 items done; log the deferred ones) and `docs/screenshots/*` (captured with the controller's script).
- Modify `{test}ShippedQuestDataTest.java` and `{test}ui/detail/DetailPanelsTest.java` ("every page kind renders" now includes a quest page with chain and objectives for the five acceptance quests).

**Steps:**
- [ ] **Step 1: backlog §3 audit.** Each item becomes a test or a recorded check, so the v0.3.0 gate "every §3 fix closed" is evidence, not memory.
  - `LabelsTest.questItemSubtitle`: Westfall Deed does not read "Quest · iLvl 8".
  - `ItemCardPanelTest.thunderfuryEraFooterAndNature`: the 19019 card holds the "Stats from Classic Era data; Forever values may differ" line and a Nature damage line. If the shipped data lacks either, report it as a data defect, not a UI fix.
  - `SearchBarTest.searchBoxFollowsThePage`: opening a link from a page does not leave stale search text.
  - `MapViewTest.zoneCaptionShown`, from Task 8.
  - Record the result of each remaining §3 item in the task report, with the commit that closed it in 2B Task 14: zone level bands, the Slot column, world drops, spell tokens, friendly NPCs, VanCleef's rank, recent searches.
- [ ] **Step 2: performance.** Warm page load stays ≤ 300 ms p95 on the shipped DB for 176, 166, 6502, 7787, 7791, NPC 550 and Westfall. Measure with the existing timing harness (or a JUnit `@Tag("perf")` excluded from `verify`) and record the numbers in the report.
- [ ] **Step 3: screenshots.** The controller takes them; the implementer prepares the list.
  - The five acceptance quests in Slate, Parchment and compact (420×720).
  - NPC 550's route, the non-loop route NPC from Task 9, and the rail in both themes.
  - The scan test is confirmed visually at 1280×800.
- [ ] **Step 4: full verify.** Run `./mvnw -B -q verify`, then commit `docs: v0.3.0 screenshots, usage guide and roadmap`.

### Task 17: Client data 70450 and the Forever dungeons (data)

Added 2026-10-17 as an owner-bug exception to backlog decision 7 (frozen v0.3.0 scope): the owner reported that the new WoW: Forever dungeons do not appear and asked whether the data is current. Facts verified on wago.tools (builds 1.60.1.70425 and 1.60.1.70450, identical for these tables): `Map` rows with `InstanceType = 1` and `ExpansionID = 0` for City of Dalaran 2959, Excavation Site: Wetlands 2998, Ruins of Lordaeron 2999 and The Hall of Thanes 3065; `LFGDungeons` 3271 "City of Dalaran" (ContentTuningID 4512), 3272 "Ruins of Lordaeron" (5256), 3273 "Hall of Thanes" (7000), 3274 "Excavation Site: Wetlands" (6874), all with `MapID = 0`; `ContentTuning.MinLevelSquish` 4512 → 28, 5256 → 15, 7000 → 13, 6874 → 26 (`MaxLevelSquish` equal, `LfgMin/MaxLevel` 0 or inconsistent — ignore them); `DungeonEncounter` (columns `Name_lang,ID,MapID,DifficultyID,OrderIndex,CompleteWorldStateID,Bit,Flags,SpellIconFileID,Faction,ItemSequenceLevel`) names 9 bosses for 2959, 4 for 2998, 7 for 2999 and 4 for 3065, all `DifficultyID 0`; map 3002 has encounters but no name (excluded). No loot exists client-side. The installed client is 1.60.1.70450. Runs after Task 5 and before Task 6 so later shipped-data tests and screenshots pin final data.

**Files:**
- Modify `tools/clientdata/fetch.py` / `convert.py` table lists (+ `DungeonEncounter`, `LFGDungeons`, `ContentTuning`; `Map` is already fetched), `tools/clientdata/build.lock` (build `1.60.1.70450`), `tools/clientdata/README.md`.
- Modify `importer/src/main/resources/dev/wowforever/importer/schema.sql` (`instance` + `source TEXT NOT NULL DEFAULT 'atlasloot'`, `client_map_id INTEGER`, `tuned_min_level INTEGER`, `bosses_state TEXT`, `loot_state TEXT`), `SqliteWriter.SCHEMA_VERSION = 4`.
- Create `importer/src/main/java/dev/wowforever/importer/clientdata/ClientInstanceLoader.java` and `ClientInstance` / `ClientEncounter` records; modify `ImportPipeline` (after the AtlasLoot instances; ids continue the AtlasLoot sequence), `AtlasWriter` or a new `ClientInstanceWriter`, `report/UiFields` if instances are UI-field checked.
- Modify `app/src/main/java/dev/wowforever/app/model/InstanceSummary.java` (+ `String source`, `Integer tunedMinLevel`, `String bossesState`, `String lootState`), `app/.../db/InstanceRepository.java` and `SearchRepository` (client instances are searchable like AtlasLoot ones).
- Regenerate `app/src/main/resources/data/wowforever.sqlite`, `build-id.txt`, `import-report.txt`.
- Tests: create `importer/.../clientdata/ClientInstanceLoaderTest.java` (fixture CSVs with the four maps, the 3002 unnamed map, one LFGDungeons row without a matching map); modify `SchemaTest` (v4 columns), `InstanceRepositoryTest`, `ShippedDataTest`, `ShippedProvenanceDataTest`.

**Interfaces:**
- Consumes: the client CSVs in `out/current/csv/`; the optional-table pattern from 3A Task 8 (`optionalTable`: absent tables → the loader is skipped with `clientdata.instances.skipped = 1` and a note).
- Produces:
  - `ClientInstanceLoader.load(Path csvDir, ImportReport) -> List<ClientInstance>` with `record ClientInstance(long mapId, String name, Integer tunedMinLevel, Integer areaTableId, List<ClientEncounter> encounters)` and `record ClientEncounter(long id, String name, int orderIndex)`.
    - A `Map` row qualifies when `InstanceType = 1`, `ExpansionID = 0` and its `MapName_lang` equals an `LFGDungeons.Name_lang` after both are lower-cased and a leading `"the "` is removed. Unmatched dungeon maps are counted as `clientdata.instances.unmatched` (3002 today).
    - `tunedMinLevel` = `ContentTuning.MinLevelSquish` of the LFG row's `ContentTuningID`; null when 0 or absent (counted `clientdata.instances.noTuning`).
    - Encounters: every `DungeonEncounter` row with that `MapID` and `DifficultyID = 0`, sorted by `OrderIndex` then `ID`.
  - Writer: one `instance` row per `ClientInstance` with `key = "client:" + mapId`, `name` from `Map.MapName_lang` (so "The Hall of Thanes"), `map_id = mapId`, `content_type = 'dungeon'`, `level_min = tunedMinLevel`, `level_max = NULL`, `zone_id` = the AtlasLoot/Questie zone whose name equals the Map's `AreaTableID` area name when one exists, else NULL; `source = 'client'`, `client_map_id = mapId`, `tuned_min_level`, `bosses_state = 'client_named'`, `loot_state = 'none'`. One `boss` row per encounter: `name`, `npc_id NULL`, `level NULL`, `is_extra_list 0`, `sort_order` = position from 1. No `boss_loot`. AtlasLoot rows keep `source = 'atlasloot'`, `bosses_state = 'atlasloot'`, `loot_state = 'atlasloot'`.
  - Report counters: `clientdata.instances` (4), `clientdata.encounters` (24), `clientdata.instances.unmatched`, `clientdata.instances.noTuning`, `clientdata.instances.skipped`.
  - `InstanceSummary` carries the four new fields; `InstanceRepository.list/summary` read them.

- [ ] **Step 1: failing tests.**
  - `ClientInstanceLoaderTest.fourDungeonsWithTheirEncountersInOrder`: the fixture gives 4 instances; 2959 has 9 encounters starting "Arcane Anomaly" and ending "Mana Wraith"; 2998's order is Saltspine, Shadetooth, Highland Horror, Relic Guardian (OrderIndex 0, 1000, 1500, 2000); 3065 has `tunedMinLevel 13`; the unnamed map 3002 is counted unmatched, not loaded.
  - `ClientInstanceLoaderTest.theArticleDoesNotBreakTheJoin`: `Map` "The Hall of Thanes" matches LFG "Hall of Thanes".
  - `ClientInstanceLoaderTest.missingTablesSkipTheLoader`: a copy of the fixture without `DungeonEncounter.csv` yields no instances and `clientdata.instances.skipped = 1`.
  - `SchemaTest.v4InstanceColumns`.
  - `InstanceRepositoryTest.clientInstanceCarriesItsState` on the fixture DB.
  - `ShippedDataTest.foreverDungeonsAreShipped`: `instance` rows with `source = 'client'` are exactly the client map ids {2959, 2998, 2999, 3065}; 2959 has 9 bosses, `tuned_min_level 28`, first boss "Arcane Anomaly"; 3065's first boss is "Faldrim Anvilmar"; none has `boss_loot`; each is found by `SearchRepository` by name.
  - `ShippedProvenanceDataTest.clientBuildIsCurrent`: `meta.client_build = "1.60.1.70450"`, `client_item_count ≥ 20492` (a floor, not an equality), `schema_version = 4`.
- [ ] **Step 2:** run the importer tests and expect FAIL; implement; run again and expect PASS.
- [ ] **Step 3: refresh and regenerate.** Bump `build.lock` to `1.60.1.70450`, fetch every table (plus the three new ones, the Era fallback and UI icons as the README describes), convert, run `./mvnw -B -pl importer -am -Pimport-data verify`, copy `build.lock`, update `README.md` (build line, "Dungeons & raids" sentence) and `tools/clientdata/README.md`. Record in the report every shipped count that changed (items, quests, drop rows) and the import-report deltas.
- [ ] **Step 4:** full `./mvnw -B -q verify`; fix shipped tests that pinned totals by turning them into floors or ids (never by loosening a fact about a named entity).
- [ ] **Step 5:** commit `data: client build 1.60.1.70450 and the four Forever dungeons from Map, LFGDungeons and DungeonEncounter`.

### Task 18: Forever dungeon pages (UI)

Runs after Task 17 and before Task 16, so the screenshots include it. Copy is the product manager's (memo `pm-forever-dungeons.md`): describe what the data knows, name its source, say what is not recorded yet; never a recommendation, a progress tick or guessed loot.

**Files:**
- Modify `{main}ui/pages/InstancePage.java`, `{main}ui/pages/ListPage.java` (Dungeons & raids list), `{main}ui/Labels.java` (+ `tunedLevel(Integer)` → "Level 28+"), `{main}ui/detail/HeaderPanel.java` callers, `README.md` ("Using the app": Forever dungeons).
- Tests: modify `InstancePageTest`, `ListPageTest`, `ShippedDataTest` (page facts for City of Dalaran).

**Interfaces:**
- Consumes: Task 17 `InstanceSummary.source/tunedMinLevel/bossesState/lootState`; Task 2 `Chip`; Task 5 `HeaderPanel.addChip/setFacts`, `SectionOrder.INSTANCE`.
- Produces, for an instance with `source = 'client'`:
  - Header chips, in order: `Chip.Style.PLAIN "New in Forever"`, `PLAIN "Level 28+"` (tooltip "Tuning level from the game client"); no player-count chip (the client gives none).
  - `InstancePage.COVERAGE_LINE = "Bosses: %d named in client data · Loot: %d confirmed items · Client build %s"` rendered under the header from the boss count, the confirmed loot count (0) and `meta.client_build`.
  - The "Encounters" section lists the bosses in `sort_order` with the muted note "Loot not recorded yet" per row and the section note `InstancePage.BOSSES_NOTE = "Boss names come from the game client. WoW: Forever has no Dungeon Journal, so abilities and loot are recorded only after players meet these bosses."`.
  - The "Loot" section shows `InstancePage.NO_LOOT_NOTE = "No loot recorded yet for this dungeon. Drops appear here when a data update records them."` and no table; "Other loot lists" is absent.
  - The Dungeons & raids list sorts client dungeons among the Classic ones by `level_min`, shows "Level 28+" in the level column and a "New in Forever" chip, and ends with the footnote `ListPage.FOREVER_FOOTNOTE = "Announced by Blizzard, not yet in this client build: Drowned City, Krol'dok Stronghold, Alcaz Island Prison, Blackmaw Hold, The Shaper's Terrace; raids Barrow Deeps and Hyjal Summit (open 9 Dec 2026)."` with one source link (the announcement URL recorded in the research memo, opened in the browser like other external links).
  - AtlasLoot instances are unchanged.

- [ ] **Step 1: failing tests.**
  - `InstancePageTest.foreverDungeonReadsItsState`: a fixture client instance with 2 bosses shows both chips, `COVERAGE_LINE` with "2 named", "0 confirmed", the fixture build; the Encounters rows carry "Loot not recorded yet"; the Loot section shows `NO_LOOT_NOTE`; no "Other loot lists" section; no boss loot table.
  - `InstancePageTest.atlasLootInstanceIsUnchanged` (The Deadmines fixture: no Forever chips, no coverage line).
  - `ListPageTest.foreverDungeonsSortAmongClassicByLevel`: a client dungeon at 28 sits between Classic dungeons at 26 and 30; its level cell reads "Level 28+"; the footnote is the last row.
  - `ShippedDataTest.cityOfDalaranReadsAsPlanned`: the page for `client_map_id 2959` shows "New in Forever", "Level 28+", 9 encounter rows (first "Arcane Anomaly"), and the coverage line "Bosses: 9 named in client data · Loot: 0 confirmed items · Client build 1.60.1.70450"; in `CompactColumns` the page lays out within 420 px.
  - `LabelsTest.tunedLevel`: 28 → "Level 28+", null → null.
- [ ] **Step 2:** run the tests and expect FAIL; implement; run and expect PASS, then the full verify.
- [ ] **Step 3:** commit `feat(ui): Forever dungeon pages with client-named bosses and no-loot states`.

### Task 19: Hidden-item hotfix overlay, build-watch and AtlasLoot Classic Forever (data)

Added 2026-10-17 from the owner's data-source review (controller ruling; PM memo `pm-data-sources.md` in the plan workspace is the authority for every rule below and must be read first). Runs after Task 17 and before Task 18.

**Files:**
- Modify `tools/clientdata/fetch.py` (+ `--hotfix-cache` snapshot of `https://storage.googleapis.com/raidbots-static/wow/classic_beta/enUS/DBCache.bin` into `~/.cache/wowforever-clientdata/hotfix/<sha256>.bin`, recording header build, SHA-256, Last-Modified and fetch time in `build.lock`), `tools/clientdata/convert.py` (+ hotfix overlay CSVs `ItemSparse.hotfix.csv`, `Item.hotfix.csv`, `ItemEffect.hotfix.csv` from the cache's valid rows, status 1, using WoWDBDefs definitions for the pinned build; invalidated rows undo only that push), `tools/clientdata/README.md`, `tools/clientdata/tests/`.
- Create `tools/build_watch.py` (+ tests): compares the patch service versions for `wow_classic_beta` and `wowf`, wago's `/api/builds`, the Raidbots `Last-Modified` plus the header build from a 44-byte range GET, and the QuestieDB master commit against `build.lock` and `sources.properties`; one line per upstream; exit 1 when anything is stale; an unreachable upstream reads "unknown", never "fresh"; the fork reads "manual check" with its pinned version. It never imports or updates anything.
- Modify `sources.properties` (`atlasloot.*` → the AtlasLoot Classic Forever zip: version, SHA-256, the same three data files plus LICENSE; downloaded by hand into the pinned cache because CurseForge refuses automation), `NOTICE.md` + the app copy (Raidbots named as the transport of Blizzard hotfix rows; the fork's author and GPL-2.0).
- Modify the importer: `clientdata` item loading (overlay precedence 1 hotfix, 2 forever, 3 era; `client_source` gains `hotfix`), `DropRateLoader` (positions 2 and 5 read the fork; label "AtlasLoot Forever"; a Classic boss row the old pin had and the fork dropped is kept when Questie or VMaNGOS still list it, counted `atlasloot.forever.removedVsHoizame`; fork additions counted `atlasloot.forever.additions`), the report counters `hotfix.itemsparse.valid/.applied/.invalidated/.notInItemTable`, `hotfix.fromEra`, `hotfix.fromNoClient`, and a build-mismatch failure (cache header build ≠ `build.lock` build) with the build-watch text.
- Modify the app: `ItemCardPanel`/tooltip footers per `client_source`: `hotfix` → "Forever stats revealed by a Blizzard hotfix · beta <build> · cache <date>", `era` → "Classic Era values. Forever stats are still hidden.", no client data → "Stats hidden until this item drops on a realm", `forever` → none. `RateSource` label for the fork rows "AtlasLoot Forever". Never the words "live" or "confirmed" for beta hotfix data.
- Regenerate the shipped database once (the second and last 3B regeneration).
- Tests: tools tests for the overlay and the watch; importer tests on fixtures (precedence, invalidation, build mismatch, fork diff counters); `ShippedDataTest`/`ShippedProvenanceDataTest` acceptance ids: 286978 Rotmender's Garb (`hotfix`, `item_set_id 2133`, stats present, hotfix footer), 869 Dazzling Longsword (`era` → `hotfix`), 16806 Felheart Belt (stays `era`, new footer; negative control), 273643 Worgenbane Talisman (`hotfix`; Questie's name equal or kept as alt value); counters within recorded ranges (≈ 3,173 fromEra and ≈ 626 fromNoClient on today's data — record the actual numbers after Task 17's refresh); City of Dalaran 2959 either has ≥ 1 fork loot row or the report states the fork has none for the four beta dungeons (both honest).

**Interfaces:** Produces `client_source = 'hotfix'`, `RateSource` label "AtlasLoot Forever" with state `Reported` for Forever-boss rows (Task 18 shows the chip "AtlasLoot Forever, unverified" on those sections), the three footers above, `tools/build_watch.py`. Spot-check: 10 fork rates against the Wowhead tooltip test oracle, result recorded in the report (if all match Wowhead to the decimal, the controller tells the owner before release).

- [ ] Steps: failing tests → implement tools, importer, app → refresh (hotfix snapshot + fork) → regenerate → full verify → commit `data: hotfix-revealed item stats, build watch and AtlasLoot Classic Forever`.

### Task 20: In-game collector addon and its import (two halves)

Added 2026-10-17 (PM memo `pm-data-sources.md`, section "Task 20"). The addon half touches no Java and starts at once in a worktree on branch `feature/collector-addon` from `main`; it must be ready for the owner by 19 October because the beta ends about 22 October. The importer half runs on the 3B branch after Task 19 and before Task 16.

**Addon half — files:** create `tools/collector/WowForeverCollector/WowForeverCollector.toc` (Interface version of the beta client, title, `## SavedVariables: WowForeverCollectorDB`), `Core.lua`, `README.md` (install into the beta's `Interface/AddOns` by the owner — the app never writes there; the run plan below; where the SavedVariables file lands: `WTF/Account/<account>/SavedVariables/WowForeverCollector.lua`), `tools/collector/fixtures/WowForeverCollector.sample.lua` (a hand-written SavedVariables file in the exact schema), and a `lua-table` module test that parses the sample with the existing Lua table reader.

**Schema 1 of `WowForeverCollectorDB`** (account-wide; never a character name, realm or guild anywhere):
- `session`: build (`GetBuildInfo`), date, addon version.
- `loot`: on `LOOT_OPENED`, each slot's item link and quantity, each `GetLootSourceInfo` GUID reduced to type and id (Creature or GameObject), instance name, mapID and difficulty (`GetInstanceInfo`), uiMapID and player x,y; de-duplicated per source GUID so a corpse counts once.
- `encounters`: `ENCOUNTER_START` / `ENCOUNTER_END` with encounterID, name, success.
- `vendors`: on `MERCHANT_SHOW`, the NPC id, then each item's link, price, stack, limited stock and extended cost.
- `trainers`: on `TRAINER_SHOW`, the NPC id, then each service's name, cost, required level and required skill.
- `quests`: on accept and turn-in, questID, giver or ender id and type, objectives text, rewards, uiMapID and x,y.
- `apiFailures`: every call that returned a secret or nil value where data was expected, with the call name and where it happened.

**Owner's run plan (README):** 19 Oct install; session 1 (~1 h): one run of City of Dalaran (28+) or Excavation Site (26+), one vendor and one trainer in a city, accept and turn in one quest, `/reload`, log out; share the SavedVariables file; 20–21 Oct session 2 only if fixes were needed. Acceptance: at least one Forever boss → item row "Observed in game", or `apiFailures` explains why not.

**Importer half — files:** importer option `--collector-file <path>` (repeatable, opt-in), parsed with the lua-table reader; provenance source `collector` reading "Observed in game"; loot sources become presence rows on `item_npc_drop` / `item_object_drop` with no rate, plus encounters; vendors into `item_vendor`; trainers and quests kept raw in a `collector_observation` table; an observation upgrades a `Reported` fork row to `Known`. Tests: a fixture file imports deterministically; the schema has no name/realm/guild fields; duplicate source GUIDs count once.

- [ ] Addon half: write the sample fixture and its parser test first; implement the addon; commit `feat(collector): in-game collector addon, schema 1` on `feature/collector-addon`.
- [ ] Importer half: failing tests → implement → full verify → commit `feat(importer): import collector observations as Observed in game`.

---

## Self-review

### Spec coverage

| Requirement (source) | Task |
|---|---|
| §4.1 header: "!" plate, name, "Lv 7 (req. 4)", zone, faction/class, Repeatable chips; stepper "Step 2 of 4", hidden for standalone | 11 |
| §4.2 Objectives + Map side by side (stacked in compact) | 11, 12 |
| §4.2 rows: number, verb, target link, muted text, where line; "You carry" / "You will need"; row click filters, chip "Showing objective 2 ✕"; top 5 sources + "Show all 9 sources"; killCredit one row; instance link; escort | 10, 12 |
| §4.3 Given by / Turned in to with zone and coordinates; item start line, map pins the item's sources | 11 |
| §4.4 chain tree, every connector, 2 steps either side + "Show n earlier", depth cap, loop, standalone copy | 13 |
| §4.5 Rewards, then Related items (collapsed) | 11 (choice vs always and reputation: open question 5) |
| §4 map: zone chips with glyphs, opens on the giver's zone, edge banner, pin art, legend toggles, no-map note | 8, 11 |
| §4 keys G/T/O/M/C/R, 1–9, Esc, `[` `]`, Z in KeyMap and help | 15 |
| §4 compact quest page | 11, 12, 13 |
| §4 scan test at 1280×800 on the five quests | 11, 12, 16 |
| §5 rail icons: plates, crop, gossip glyphs, states, tooltips, 16 px section icons, SVG fallback | 3 |
| §5 chrome: hairline panels, Marcellus SC headings with rule, 20 pt titles, 6 px gold bars with ticks, outline chips, 22 px status bar with provenance and toggles | 1, 2 |
| §5 typography: Marcellus/Marcellus SC (OFL), Cinzel/Georgia fallback, Inter | 1 |
| §5 themes: Slate values; Parchment values; navy card and plates in both | 1, 4 |
| §6 skeleton: header, positive facts, fixed section orders, "Name (count)", 8 rows then "Show all" | 5 |
| §6 merged Dropped by (instance chip, by rate, filter on NPC rows only) | 6 |
| §6 NPC notable drops first; Quests merged; Location | 6, 14, 5 |
| §6 zone and instance section orders; instance Quests | 5 |
| §6 master list shows while searching, auto-collapses, Ctrl+B keeps, visible ≥ 1600 px | 6 |
| §6 kind chips only in the search dropdown; filter beside headings; "≠" hover legend | 6 |
| §6 empty/loading copy ("Loading…" after 150 ms) | 5 |
| §6 compact sections collapsed except the first | 5 |
| §7 rulings (icons, order, no counts, merged filter, fonts) | 3, 11, 12, 6, 1 |
| §10 patrol routes: dashed 2 px, arrowheads every ~10 points, end marker plus tooltip, loops closed, line hit-test, zone toggle off by default | 9 |
| §11.1 community drop rates; decision 19 "Vanilla rate" plus the boss note | 7 |
| §11.4 cursor coordinates on every map, right-click /way, in-game item and quest links | 8, 15 |
| Backlog v0.3.0 "Involved in quests" | 14 |
| Backlog §3 quality fixes closed | 16 (audit), 8 (zone caption) |
| Release gates: verify, ≤ 300 ms p95, contrast, screenshots dark/light/compact | 16, 1, 4 |
| §6 Home dashboard; compact pinned tabs with Ctrl+1..6; always-on-top mini tooltip | Not planned (open question 1; v0.7.0 / v0.8.0 in the backlog) |
| §11.2 dungeon prep tab, §11.3 vendor/trainer finder, §11.5 crafting UI | Not planned: backlog v4 puts them in v0.5.0, v0.6.0 and v0.4.1/v0.6.0 |

### Type consistency across tasks

- `QuestPageData`, `ObjectiveRow`, `ObjectiveSource`, `GiverSpot` and `QuestFacts` are produced in Task 10 and consumed in Tasks 11–12 with the same names.
  - `ObjectiveRow.number` is 1-based throughout: the Task 8 `Pin.number`, Task 12 badges, and the Task 15 "1…9" keys.
  - The 3A ordinals start at 0 and are never shown.
- `PinKind.KILL/COLLECT/INTERACT/EXPLORE` and `PinKind.Layer` come from Task 8 and are used by Task 12. Routes are `MapView.RouteLine` (Task 9), not pins, so they never cluster.
- `QuestChain.Connector.LEADS_TO` is added in Task 13, both in the builder and in `ChainRows`. No earlier task switches on `Connector`, so adding the constant breaks no exhaustive switch until Task 13. Task 13 updates `Connector.label()` users.
- `Chip` (Task 2) replaces `RankChip`. Tasks 5, 6, 11 and 14 use `Chip`; nothing keeps `RankChip`.
- `Tokens.PARCHMENT` replaces `Tokens.LIGHT` in Task 4. Tasks 1–3 refer to `Tokens.LIGHT` only through `Theme.current()`, so the rename touches `ThemeManager` and the tests only.
- `LootRow` gains `pctSource` (Task 7). `DropSource` already has it. `RateBarRenderer`'s new three-argument constructor (Task 7) keeps the two older constructors, so the Task 2 tests stay valid.
- `SourceSection.heading()` changes type from `JLabel` to `SectionHeading` (Task 2). Task 5 (`showAllButton`) and Task 6 (`setTrailing`) build on that.
- `WayCommand.of` (Task 8) replaces `SpawnSection.wayCommand`, and `NpcPageTest`'s "/way Westfall 42.3 67.4" assertion moves with it.
- `Repositories` gains `questLocator()` (Task 10). Every `Repositories` built in tests goes through `Repositories.of(db)`, so no positional constructor call breaks. Verify that in Task 10 and fix any direct `new Repositories(...)`.
- `DetailPanels.LoadedQuest` changes shape in Task 10, consumed in Task 11. The old `QuestPage.GiverMap` is deleted in Task 11.

### Open questions, with the controller's rulings (2026-10-17)

**Ruling:** all fifteen defaults below are accepted as written. Two carry a condition: Q4 is confirmed by the controller's visual check in Task 3 (if the 1.5× glyphs disappoint, Task 16 records a data task to fetch the Interface/Icons second choices); Q7 is checked in game by the owner once, with the colour-wrapped form as the fallback. Q1, Q14 and Q15 stay out of 3B per backlog v4 decision 7 (frozen v0.3.0 scope).

Each question has the default applied.

1. **Home dashboard and compact pinned tabs.**
   - The conflict: spec §6 and §7 put both in. Backlog v4's v0.3.0 row does not list them, places compact pinned tabs and always-on-top in v0.8.0, Home as a learning entry in v0.7.0, and decision 7 freezes the scope.
   - **Default:** out of 3B. Home keeps its welcome text, and pinned pages wait for ROADMAP D17.
2. **Repeatable acceptance quest.**
   - The conflict: 7791 A Donation of Wool is not repeatable in the data. `special_flags` is null and `repeatable` is 0 for every "A Donation of …" quest.
   - **Default:** 7791 is the per-city/"Requires all of" acceptance case. 7796 Additional Runecloth, the repeatable follow-up of 7795, is the "Repeatable" chip case.
3. **Drakefire Amulet's Horde branch.**
   - The conflict: 6602 is not linked to 6502 by any relation (3A Task 5 finding).
   - **Default:** 6502 tests the 15-step prerequisite chain and the instance objective. 4604 tests exclusive alternatives, and no test claims a 6502↔6602 branch.
4. **Gossip glyphs on the rail.**
   - The issue: `rail.npcs` and `rail.quests` are 16 px art and look small on a 28 px plate (3A controller note).
   - **Default:** a bicubic 1.5× scale to 24 px from the native PNG. The controller's visual check in Task 3 may switch to 2× nearest-neighbour cropped to 24, or to the Interface/Icons second choices. The second choices need a 3A data re-fetch.
5. **Rewards "choice vs always, reputation".**
   - The issue: 3A imported no reward-choice or reputation-reward fields; only `quest_reward_item` exists.
   - **Default:** Rewards stays one list. The split waits for a data task.
6. **Missing chain targets.**
   - The conflict: spec §3 says "Quest #id (not in database)", while the 3A ruling says "Unknown quest #id".
   - **Default:** "Unknown quest #2955", muted, not a link (the later ruling).
7. **In-game link colour codes.**
   - The issue: spec §11.4 gives `|Hitem:…|h[Name]|h` without the `|cff…|`/`|r` colour wrapper. The game client may reject a pasted link without it.
   - **Default:** the spec's exact form, and the controller checks it in game once. If it fails, switch to the colour-wrapped form, which `PasteParser` already accepts.
8. **Headline "Vanilla" marker.**
   - The issue: decision 19 calls it "a label in the existing provenance tooltip".
   - **Default:** the tooltip label plus a muted " V" glyph after a rate whose headline source is VMaNGOS, explained in the section's "≠"/"V" legend. Without the glyph, a headline vanilla rate reads as a Forever fact until hovered.
9. **Rate bar colour.**
   - The conflict: spec §5's "6 px dark-gold→gold" replaces the 2B rate-band colours on the bar.
   - **Default:** follow the spec. `QualityColors.rateBand` stays for any other caller. The number still carries the value, so nothing relies on colour.
10. **Contrast adjustments to the spec palettes.**
    - Slate's alternate row moves to `#1E232B`, because epic text on `#242A33` is 4.25:1.
    - Parchment tables, lists and fields sit on raised `#FBF5E6`, with alternate row `#F6EEDC` and warn `#8A5A00`. On surface `#EADCBB`, poor is 3.92, uncommon 4.04, legendary 3.96 and gold text 4.49.
    - Parchment artifact `#7A5E14` and heirloom `#00687F` are additions.
    - **Default:** apply them as written in the Global Constraints.
11. **Status bar versus a map corner label for coordinates.**
    - The conflict: the brief says status bar, spec §11.4 says a corner label on every map.
    - **Default:** the corner label, which also works in compact mode where the status bar is narrow.
12. **Spawn counts per sub-zone.**
    - The issue: spec §4's "Fargodeep Mine · 23 spawns" needs sub-zone per spawn, which the data lacks (`npc_spawn` has only `zone_id`).
    - **Default:** the zone name ("Elwynn Forest · 23 spawns").
13. **Reputation and spell objective names.**
    - The issue: there is no faction or spell name table in the DB.
    - **Default:** "Reputation: Faction #id, value" and "Cast: Spell #id", shown muted, with no pins.
14. **Zone NPC filters "rares, vendors, trainers" in spec §6.**
    - The overlap: they touch the v0.6.0 vendor/trainer finder.
    - **Default:** not in 3B. The zone NPC section keeps the 2B sort (bosses and rares first).
15. **Instance "Entrance" section (spec §6).**
    - The issue: there is no entrance data.
    - **Default:** left out.
