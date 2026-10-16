# UI design review: WoW Forever Item & Drop Browser

Date 2026-10-16. Review of v0.1. Inputs: screenshots `.superpowers/ui-review/01–06` (1280×800 window at 200 %), the `app/.../ui` code, `schema.sql`, `docs/ROADMAP.md`.

Use case: a player keeps the app open on a second screen and asks "what drops this, where, how often, what else does that boss drop". Every recommendation is judged against that: answer at a glance, in one keystroke, with no dead ends.

## 1. Assessment of the current UI

**What works.** Everything is a link, back/forward exist, loads are async, tabs carry counts and open on the first non-empty one (03, 04). The detail header with a linked zone (04, "Elwynn Forest") is the right pattern.

**The wrong pane gets the space.** On every detail screenshot (03–06) the results list keeps ~45 % of the width for two or three stale rows, while the detail pane, the actual answer, gets ~35 % and truncates ("Formula: Enchant Bracer - Minor Str…", "Molten …" in 04/05). The tree spends a third of the width on six labels (01).

**The tree repeats itself.** "NPCs by zone", "Objects by zone" and "Quests by zone" each repeat the whole zone list, yet the zone page already has those tabs. "Zones" has no expander but "Dungeons & Raids" does (01).

**Search ranks poorly and leaks raw values.** `SearchRepository` orders by `bm25, name` only. "Westfall" (06) puts four signpost objects above the zone, so the "zone" screenshot actually shows Object #94. "Molten Core" (05) returns a Zone and an Instance with the same name; the zone opened, so the "instance" screenshot is the zone page and the loot tables were never reached. Subtitles read "Zone · raid", "Zone · zone", "Instance · RAID40", "Item · Quest · iLvl 1".

**Links do not behave like links.** Table cells are link blue but need a double-click (`TableLinks.bindActivation`). Back/forward are "←/→" glyphs and ignore mouse buttons 4/5, which most gaming mice have.

**Tables show columns, not answers.** Hogger's drops (04): 25 rows sorted alphabetically, one rate (84 %), 24 "—", "questie" 25 times in Origin, an empty Slot column, and Item level for Shiny Red Apple. Scarlet Belt (03): two rows that read identically ("Scarlet Monastery · Trash · —"), told apart only by Slot 24 vs 27, an internal index. The MC NPC table (05) has an empty Title column and a Zone column linking to the page you are on; bosses are mixed alphabetically with trash.

**Tabs hide the answer.** Item pages have six tabs, four "(0)" and disabled (03). The strip overflows into scroll arrows (03 "Sold by (0) ›", 05 "S…").

**Home and chrome.** Home (01) is an empty table, a text blurb and a 64-character build hash shown twice. IDs ("Item #10329") lead every subtitle.

**Readability at 200 %.** Segoe UI is crisp; rows (~16 logical px) are dense but usable. Numbers collide ("1 84.00 %" in 04 reads as one value). The light default is the brightest object next to a dark game.

**What a Wowhead user would miss.** Quality colours, icons and a tooltip (phase 2), rates sorted first, a map, `/way` coordinates, list filters (level, slot, class), type-ahead, and boss loot grouped like the game's loot window.

## 2. Design principles

1. **Source first.** An item page answers "where and how likely" above the fold; an NPC page answers "what is worth killing it for".
2. **Glanceable from across the desk.** The key fact of a row shows in colour and position before it is read: quality, rate bar, rank chip.
3. **Colour is information.** Quality, rate band, origin and disagreement each own one channel. Item names are not link blue once they carry quality colour.
4. **Keyboard and mouse buttons first.** Search, open, back, forward and copy never need a precise click.
5. **Never a dead end.** Unknown IDs, empty sections and missing rates explain themselves and offer a next step.
6. **Quiet chrome, one bold element.** The WoW-style item card is the only ornamental surface; the rest is flat, dark and disciplined.

## 3. Recommendations

Data tags: **now** = today's schema; **P2-data** = needs phase-2 pipeline output.

### P1: must

**P1.1 Dark theme by default, with a token set.**
Problem: light default, stock FlatLaf colours, link blue everywhere. Change: a custom dark theme as default, light kept.

| Token | Dark | Light |
|---|---|---|
| bg.base | `#14171C` | `#F4F5F7` |
| bg.surface (tables, panels) | `#1B2027` | `#FFFFFF` |
| bg.raised (hover, headers) | `#242A33` | `#E9ECF0` |
| text.primary / text.muted | `#E4E1D8` / `#8E97A5` | `#1C1F24` / `#5C6470` |
| accent.gold (focus, selection edge, section titles) | `#D6B25E` | `#9A7419` |
| link (NPC, zone, quest names) | `#9CC3E6` | `#1F5FA8` |
| card.bg / card.border (item card, always dark) | `#0C1424` / `#46587A` | same |

Swing: subclass `FlatDarkLaf` with a `.properties` file (`@background`, `@foreground`, `@accentColor`, `Component.linkColor`, `Table.selectionBackground`, `Table.alternateRowColor`); `ThemeManager` defaults to dark. Data: now.

**P1.2 Detail gets the room; results become a narrow master list.**
Change: results become a ~300 logical-px list of two-line rows (name; kind and key facts); detail takes the rest; the tree becomes a rail (P2.5). Up/Down in the list previews the row in the detail pane after 120 ms; Enter or click commits it to history. After following a link inside a page, the list stays visible under a "Search: westfall" heading and Ctrl+B hides it.
Swing: one `JSplitPane` (list | detail), divider persisted. Preview needs `NavigationHistory.replaceCurrent(target)` so arrowing through 20 rows does not push 20 entries. Data: now.

**P1.3 Links behave like links.**
Change: single click on a link cell opens it; hover shows hand cursor and underline; double-click stays. Mouse buttons 4/5 go back/forward. Back/forward become SVG icons.
Swing: in `TableLinks`, a motion listener tracks the hovered cell for `LinkRenderer`; open on `getClickCount()==1` in link columns. Mouse buttons via `Toolkit.addAWTEventListener(..., MOUSE_EVENT_MASK)`, `getButton()` 4/5 on release. `FlatSVGIcon` from flatlaf-extras. Data: now.

**P1.4 Search ranking, labels and grouping.**
Change: order by exact name match, then kind priority (Instance > Zone > Boss/Rare NPC > Item > Quest > NPC > Object), then bm25, then name. A dungeon/raid zone and its instance become one hit that opens the instance page. Same-name, same-kind hits collapse into one row with a count ("Westfall · 4 signposts"). Subtitles in words: "Raid (40) · Level 60–63 · Burning Steppes", "Quest item", "Mail waist · iLvl 37 · Req 32"; never "iLvl 1".
SQL: `ORDER BY (lower(name)=lower(?)) DESC, kind_priority, score, name`; reuse `Columns.contentTypeText`. Data: now.

**P1.5 Item page: one scrolling sources page instead of six tabs.**
Change: under the header (and the phase-2 card) a one-line summary, then one section per non-empty source kind: Dropped by, Boss loot, Contained in, Sold by, Quest reward, Related quests. Empty kinds are not shown. Drop tables put the rate right after the name as a number plus a 48 px bar, sorted known-rate descending, unknown rows last under a muted "Rate unknown (n)". Bar bands: ≥ 25 % `#7FBF6A`, 5–25 % `#C9B458`, 1–5 % `#C98A4E`, < 1 % `#B3625A`; the number is always printed. Origin: nothing for Questie-only rows (98 % of rows); an outlined "AtlasLoot" or "Questie + AtlasLoot" chip otherwise. Boss loot rows read "Scarlet Monastery › Trash"; Slot leaves the view and only orders rows, and a loot-list name is appended when two rows would read the same.
Swing: `JScrollPane` over a vertical `BoxLayout` of section panels, each a heading plus a `JTable` without its own scroll pane (header from `getTableHeader()`, viewport height = all rows). Bar and chip painted in a `TableCellRenderer`, sizes via `UIScale.scale()`. Data: now; disagreement marker P2-data (P2.8).

**P1.6 NPC and boss loot answers "what is worth it".**
Change: sort by rate descending, unknown last; group by item class under headings (Gear, Recipes, Quest items, Trade goods, Consumables, Junk). Hide columns empty for every row (Slot, Title, uniform Origin, Zone equal to the page's zone). Rank as a header chip (Elite gold outline, Rare silver, Boss gold fill). Spawns read "42.3, 67.4" with a "Copy /way" button producing `/way Elwynn Forest 42.3 67.4` (TomTom syntax): the most useful thing the app can hand back to the game today.
Swing: one table per group inside the P1.5 section layout. Data: now.

**P1.7 Window state persistence.**
Change: remember bounds, maximised state, monitor, dividers, compact mode, always-on-top, text size, column widths and sort per table kind, last search kind.
Swing: `Preferences` node shared with `ThemeManager`; save on `windowClosing` and a 500 ms debounced move/resize. On restore, check the bounds intersect a current screen, else centre 1280×800. Data: now.

**P1.8 Empty, unknown and not-found states.**
Change: "Unknown NPC #1234" renders muted italic, still links, tooltip "Referenced by Questie but not in this database". Not found says what happened and what next: "Item #123 is not in this database. It may come from a later patch." with "Search for 123" and "Back". Empty lists say why ("No quests start in Deadwind Pass"). Home never shows an empty table.
Swing: `DetailPanels.notFound` becomes a small panel. Data: now.

### P2: should

**P2.1 Type-ahead, kind chips, recent searches, paste parsing.**
Change: typing opens a dropdown with the top 8 hits grouped by kind; Enter opens the top hit; Shift+Enter or "Show all 22 results" fills the list. An empty focused field shows the last 10 searches and pages. The "All" combo becomes toggle chips with counts: All 22 · Items 9 · Quests 8 · Objects 5 · NPCs 1 · Zones 1. Pasting is parsed: `|Hitem:10329:…|h[Scarlet Belt]|h`, `item:10329`, a Wowhead URL with `item=10329`, `[Scarlet Belt]`, a bare number; an ID opens the page directly.
Swing: non-focusable `JWindow` with a `JList` under the field; Up/Down/Enter/Esc forwarded from the field's `InputMap`. Chips: `JToggleButton`s, `FlatClientProperties.STYLE` `"arc:999"`. Counts from one `GROUP BY kind`. Data: now.

**P2.2 Home as a launch pad.**
Change: three blocks: Recent (last 12 pages), Pinned (starred items and NPCs: a farming list), and Dungeons & Raids by level band. One muted line of shortcuts. The build hash moves to Help › About; the status bar shows "Data 2026-10-16". A star toggle sits in every detail header. Data: now.

**P2.3 Instance page.**
Change: bosses as a numbered encounter list (a real sequence, so numbers carry meaning) with level and rank; AtlasLoot's extra lists sit under "Other loot lists" instead of "[extra]" suffixes. Loot grouped by slot type (Weapons, Armour, Jewellery, Recipes, Other), quality-coloured in phase 2. If `boss_loot.difficulty` has more than one value, a segmented control above the loot. Header "Raid (40) · Level 60–63 · Burning Steppes" plus a link to the zone's NPCs and objects.
Swing: `JList` renderer with a fixed number column; segmented control = `JToggleButton` row. Data: now; quality P2-data.

**P2.4 Zone page.**
Change: header adds a level range computed from NPC levels. Bosses and Rares listed first under their own heading; no Zone column. Quests sorted by level. The top of the page is reserved for the map (P2.7); until then instances and sub-zones sit there as compact links. Data: now; map P2-data.

**P2.5 Category rail and filterable lists instead of the tree.**
Change: a 48 px icon rail (Home, Items, NPCs, Quests, Zones, Dungeons & Raids; tooltips name them). Each opens a list page with a filter bar: Items by class › subclass, level range, later slot and quality; NPCs by zone, rank, level; Quests by zone and level. The "X by zone" sections disappear.
Swing: vertical `JToolBar` of `JToggleButton`s with SVG icons; combos and spinners feed the existing list queries. Data: now.

**P2.6 Keyboard map.**

| Key | Action |
|---|---|
| Ctrl+F, Ctrl+L, `/` outside text fields | Focus search |
| Esc | Close dropdown, then clear search, then focus list |
| Alt+Left/Right, mouse 4/5 | Back / forward |
| Up/Down, Enter | Preview, open |
| Ctrl+1…6 | Jump to source section |
| Ctrl+C / Ctrl+Shift+C on a row | Copy name / copy `item:ID` or `/way` |
| Ctrl+D | Pin page |
| Ctrl+B | Toggle results list |
| F10, Ctrl+Shift+T | Compact mode, always on top |
| Ctrl+= / Ctrl+- / Ctrl+0 | Text size |

Bound through the root pane `InputMap` as today; Help › Keyboard shortcuts shows this table. Data: now.

**P2.7 Tooltip card, icons, quality colour, map pins.** Each becomes P1 the day its data lands.
- *Tooltip card* at the top of the item page: always-dark navy card, 1 px `card.border`, 4 px radius, 56 px icon beside the name, lines in in-game order (section 5). Swing: a `JPanel` with custom `paintComponent` and a stack of `JLabel`s, no HTML, so colours stay exact.
- *Quality colour* on every item name in every table, dropdown and loot list; icons 18–20 logical px in rows, 28 in search results. Item names then drop link blue; hover underline marks them as links.
- *Map* on zone pages and the NPC spawns section: pins hover to a name and click to open; NPC pages pin that NPC's spawns, zone pages pin Bosses and Rares with a toggle for all. Pins within 8 px cluster into a count. Swing: a `JComponent` painting a cached `BufferedImage`, pins at x%·w, y%·h, hit-testing on `mouseMoved`.
Data: P2-data.

**P2.8 Provenance markers.**
Change: when sources disagree by more than 2 points the rate gets an amber "≠"; hover lists each source and value. A setting hides AtlasLoot-only or Questie-only rows (ROADMAP B10). Swing: `getToolTipText(MouseEvent)` on the table. Data: P2-data.

**P2.9 Compact mode for the second screen.**
Change: F10 turns the same window into a 420×720 single column: search with dropdown, then the detail page. Optional always-on-top, separate saved bounds. Tables show name, rate and one chip; other columns move into the row tooltip. Swing: swap the content pane, same controller; `setAlwaysOnTop`. Data: now.

**P2.10 Accessibility.**
- Text ≥ 4.5:1 contrast. Canonical Rare `#0070DD` and Epic `#A335EE` are about 3.5:1 on `#1B2027`, so tables use the text variants in section 5; canonical colours stay on icon borders and the card.
- Text size 90–150 % (Ctrl+=/-): `UIManager.put("defaultFont", …)` then `FlatLaf.updateUI()`; row heights from `UIScale.scale()` follow.
- Colour is never alone: the card names the quality, bars print the number, disagreement uses a glyph.
- Visible gold focus ring (`Component.focusWidth`); accessible names on icon-only buttons.
- Keep Segoe UI: its digits are tabular, so right-aligned rates and levels line up. Inter's default digits are proportional and Java2D cannot switch them.

### P3: nice

- **P3.1** Pre-rendered 36-frame turntable in the card, dragged to rotate. P2-data.
- **P3.2** Two-item compare (ROADMAP A6) via Ctrl+click.
- **P3.3** Undecorated translucent overlay variant of compact mode (`setOpacity(0.92f)`) for borderless-windowed WoW on one screen.
- **P3.4** "Usable by my character" filter once a character profile exists.
- **P3.5** History dropdown on the back button.

## 4. Layout proposal

Main window after P1/P2 (rail icons shown as letters):

```
+----------------------------------------------------------------------------------+
| <  >  [ Search items, NPCs, quests, zones...          ] (All)(Items)(NPCs)(...)  |
+----+--------------------------+--------------------------------------------------+
| H  | Search: westfall      22 |  Westfall                               *  #40   |
| I  | [ic] Westfall            |  Zone · Level 10-20 · Eastern Kingdoms           |
| N  |      Zone · Level 10-20  |  +--------------------------------------------+  |
| Q  | [ic] Westfall Stew       |  |          zone map with pins (phase 2)      |  |
| Z  |      Consumable · iLvl 15|  +--------------------------------------------+  |
| D  | [ic] Chausses of Westfall|  Bosses and rares                                |
|    |      Mail legs · iLvl 24 |    Edwin VanCleef       Boss   Deadmines        |
|    | [ic] Westfall  x4        |    (rare NPC)           Rare   20               |
|    |      Signposts           |  NPCs (214)                                      |
|    |                          |  Quests (62)                                     |
+----+--------------------------+--------------------------------------------------+
| 22 results                                                      Data 2026-10-16  |
+----------------------------------------------------------------------------------+
 rail 48 px   list ~300 px              detail: the rest
```

Item page (detail pane; NPC rows and stats illustrative):

```
+--------------------------------------------------------------+
| +------+  Scarlet Belt                        (rare colour)  |
| | icon |  Binds when equipped                                |
| | 56px |  Waist                                        Mail  |
| +------+  196 Armor                                          |
|           +8 Strength  +7 Stamina                            |
|           Durability 35 / 35                                 |
|           Requires Level 32                                  |
|           Item Level 37                       Sells 52s 10c  |
+--------------------------------------------------------------+
| 2 loot lists · 3 NPCs · no vendor              *   #10329    |
| Dropped by (3)                                               |
|   Scarlet Myrmidon    34-35 Elite  [#######.]  2.31 %        |
|   Scarlet Defender    33-34 Elite  [####....]  1.10 %        |
|   Rate unknown (1)                                           |
|   Scarlet Sentry      34    Elite                            |
| Boss loot (2)                                                |
|   Scarlet Monastery > Armory trash          (AtlasLoot)      |
|   Scarlet Monastery > Cathedral trash       (AtlasLoot)      |
+--------------------------------------------------------------+
```

## 5. Phase-2 asset specs for the data pipeline

**Icons.** PNG RGBA sRGB at 56×56 with the in-game border cropped, plus 36×36 for rows (56→18 at runtime blurs). Keyed by FileDataID, with `item.icon_id`. Ship in one table `icon(id, png56, png36)` or a zip, not loose files; include the question-mark fallback. At ~6 KB per 56 px icon, ~6k icons cost roughly 35–45 MB for both sizes, which exceeds the roadmap's "a few MB".

**Quality.** `item.quality` 0–7. Canonical colours for icon borders and the card; text variants for tables:

| Quality | Canonical | Dark text | Light text |
|---|---|---|---|
| 0 Poor | `#9D9D9D` | `#9D9D9D` | `#6B6B6B` |
| 1 Common | `#FFFFFF` | `#E4E1D8` | `#1C1F24` |
| 2 Uncommon | `#1EFF00` | `#3FE23A` | `#137A0E` |
| 3 Rare | `#0070DD` | `#4A9EFF` | `#0058B0` |
| 4 Epic | `#A335EE` | `#B861FF` | `#7E2BC4` |
| 5 Legendary | `#FF8000` | `#FF9A33` | `#B35A00` |
| 6 Artifact | `#E6CC80` | `#E6CC80` | `#8A6D1E` |

**Maps.** Stitch each zone's world-map tiles (classic: 4×3 tiles of 256 px) and crop to the 1002×668 visible area that Questie's percentages refer to. JPEG q85, about 150–250 KB each. Provide `zone_map(zone_id, ui_map_id, width, height, file)` and confirm that x/y 0–100 % map to that crop. List which dungeons have interior maps; the UI falls back to a list.

**Tooltip fields**, in display order, each nullable: name and quality; binding (BoP, BoE, BoU, Quest item); Unique; "This item begins a quest"; slot and armour/weapon type on one line; damage min–max and school, speed, DPS; armour, block; primary stats; resistances; durability; required classes and races; required level; required skill and rank; required reputation; equip, use and chance-on-hit texts (green); set name, pieces and bonuses with thresholds; charges, duration, bag slots, max stack; flavour text (yellow, quoted); item level (muted); sell price in copper; random-enchant flag.

**Provenance.** Per fact: source id, short label ("Questie", "AtlasLoot", "VMaNGOS", "Client"), value and data date, plus a precomputed `disagrees` flag per rate row so lists stay one query.

**3D renders (P3).** 36 frames at 256×256 per item as a PNG sprite sheet (WebP needs TwelveMonkeys in Java).

## 6. Open questions for the product owner

1. Is the main setup a second monitor beside fullscreen WoW, or one screen with alt-tab? This decides whether compact mode moves to P1.
2. May a dungeon/raid zone and its instance appear as one page, while staying two entities in the data?
3. Will the app know the player's class, level and faction, for "usable by me" filters and unmet-requirement lines?
4. Is the icon and map redistribution risk in the roadmap accepted, so phase-2 visual work can assume those assets?
5. Does WoW Forever have difficulty or phase variants of loot, or is `boss_loot.difficulty` always one value?
