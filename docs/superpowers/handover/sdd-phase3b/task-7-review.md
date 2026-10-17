Needs fixes — spec ✅, quality ❌ (0 Critical, 1 Important, 5 Minor)

# Task 7 review: community drop rates read "Vanilla rate" (cc4d7c2)

Reviewed `git show cc4d7c2` and the review diff d985c88..cc4d7c2, read-only. The shipped database at cc4d7c2 was queried read-only (`?mode=ro&immutable=1`). No Maven run; the controller reported verify green on cc4d7c2 (lua-table 61, importer 449, app 918).

## Spec compliance: ✅

| Requirement | Result |
|---|---|
| `RateSource.ORDER`: questie, questie-wowhead, questie-correction, atlasloot, atlasloot-override, (atlasloot-forever, atlasloot-forever-override), vmangos, others by name | ✅ `RateSource.java` KNOWN list; the two Forever sources were already there and stay before vmangos |
| `label()` for vmangos is "Vanilla rate (VMaNGOS)" | ✅ |
| `BOSS_NOTE = "Vanilla rate; Forever loot reworked"` verbatim | ✅ |
| `RateBarRenderer(Function, Predicate, Function)`, older constructors kept and delegating | ✅ plus a matching `install` overload |
| `static sourcesText(List<RateSource>, boolean boss)`, old one-argument form kept | ✅ |
| "12.50 % V", V muted, number in the usual foreground | ✅ `paintMarked` paints the V in `textMuted` (selection fg on a selected row) |
| Boss line only when a vmangos source exists | ✅ `RateBarRenderer.java:150` |
| Disagreement still marked ("2.31 % ≠", both sources in the tooltip) | ✅ and "9.00 % V ≠" with V before ≠ (controller ruling) |
| LEGEND line appended verbatim: "V Vanilla rate (VMaNGOS); hover for details." | ✅ matches plan line 328 |
| `LootRow.pctSource` last; every existing constructor passes null | ✅ the old 16-arg canonical became a secondary passing null; shorter ones chain to it |
| NpcRepository / InstanceRepository select `pct_source` | ✅ (and ItemRepository's loot lists, `InstanceLoot.pctSource`, per ruling) |
| Boss set loaded in `DetailPanels.load` off the EDT | ✅ `DetailPanels.java:136`, `:142` |
| Shipped: Hogger claw 448/1931 vmangos row and tooltip; quest-only headline example | ✅ `ShippedProvenanceDataTest` (ruled location); DB confirms (448, 1931): questie-wowhead 83.9965 and vmangos 100.0 quest_only=1; Morgan 473 / 2239 headline vmangos, source_count 1 |
| NOTICE carries the VMaNGOS GPL-2.0 note | ✅ root and app copy (unchanged, from 3A Task 9) |

Specific checks asked by the controller:

- **Boss test once per page load.** ✅ One `bosses` query per item or NPC load. The NPC page reduces it to one boolean; the item page does an O(1) set lookup per rendered row; the instance page is a constant.
- **Boss line on every dungeon/raid boss row with a vmangos source.** ✅ NPC page, item page NPC rows, item page loot-list rows and instance page all pass the flag. Every vmangos pair has `source_count > 0` in both `item_npc_drop` and `boss_loot` (0 exceptions in the shipped DB), so the sources (and therefore the line) always load. **Never elsewhere:** holds for ordinary world NPCs; see Minor 1 for the AtlasLoot "World Bosses" list.
- **"on quest" for every quest-only headline.** ✅ 208 `item_npc_drop` rows headline a quest-only vmangos rate (plus 1 in `boss_loot`); all 208 have `source_count > 0`, so their tooltips load and read "on quest". The note never enters the cell text; in the tooltip it follows the quest-only source itself, headline or not, as ruled (Hogger's non-headline 100 % reads "on quest").
- **Pixel tests honest.** ✅ The muted V is measured: muted pixels present and no foreground pixels inside `vanillaBounds`, number pixels in the foreground, and the V/≠ test checks no amber in the V. Caveat in Minor 2.
- **Tooltip HTML/escaping.** ✅ for the normal window: the tooltip is plain text of source labels and numbers, no item names, never starting with `<html>`. ❌ for the compact window's line break (Important 1). Escaping itself stays safe there (`HtmlText.escape`).
- **No EDT database access.** ✅ Only the pre-existing InstancePage boss-loot exception, untouched.
- **Changed expectations honest.** ✅ Only `SourceFilterControlTest`'s legend string, as asked; the fixture DB has no vmangos rows, so no other expectation could move.

## Code quality: ❌

### Important

1. **The boss note loses its line break in the compact window.**
   - Where: `RateBarRenderer.java:150` joins the lines with `"\n"`; `app/src/main/java/dev/wowforever/app/ui/detail/LinkTable.java:46` passes the cell tooltip through `CompactColumns.join`, which (`CompactColumns.java:84`) wraps it as `"<html>" + escape(row) + "<br>" + escape(cell) + "</html>"`.
   - What is wrong: inside a compact table the row tooltip is non-null (Item level and Classes are hidden), so the rate tooltip becomes HTML, and HTML collapses `\n` to a space. A boss rate then reads as one run-on line: `Questie 20.00 % · Vanilla rate (VMaNGOS) 30.00 % Vanilla rate; Forever loot reworked`. The SourceFilterControl legend is not affected (it is not in a LinkTable).
   - Why it matters: the boss note is the decision 19 ship-gate text and the plan requires compact mode to stay intact. Run on, the note reads like part of the source list. The report's "FlatLaf renders `\n` as multi-line" holds only outside compact mode.
   - Fix: in `CompactColumns.join`, convert line breaks of a plain cell after escaping, e.g. `HtmlText.escape(cell).replace("\n", "<br>")` (and the same for `row` for symmetry). Add a `CompactColumnsTest` case: `join("Item level 23", "a\nb")` is `"<html>Item level 23<br>a<br>b</html>"`.

### Minor

1. **AtlasLoot's "World Bosses" list makes six open-world bosses say "Forever loot reworked".**
   - Where: `NpcRepository.java:100-121` (`boss.npc_id` branch), and `InstancePage.java` passing `true` for every encounter.
   - What: instance 26 "World Bosses" (content_type RAID40, zone_id NULL) holds Azuregos 6109, Lord Kazzak 12397, Ysondre 14887, Lethon 14888, Emeriss 14889 and Taerar 14890. They have 17–22 vmangos rates each, and every one now carries the boss line. Decision 19 grounds the note in "Blizzard reworked all dungeon loot"; world-boss loot is not dungeon loot. The implementation follows the brief's literal rule (`boss.npc_id`, "instance pages always are"), so this needs a controller ruling rather than being an implementer error.
   - Data aside, for the same ruling: Maws 15571 (an AQ40 boss) has zone NULL and no `boss` row, so its one vmangos rate gets no boss line.
   - Fix if ruled out: in `bosses`, join `boss b JOIN instance i ON i.id = b.instance_id` and require `i.zone_id IS NOT NULL` (or exclude key `WorldBosses`); in `InstancePage`, pass `detail.summary().zoneId() != null` instead of `true`.

2. **The "number in text.primary" half of the pixel test measures the label foreground, not text.primary.**
   - Where: `RateBarRendererTest.java:270-271` (`vmangosHeadlineIsMarked`).
   - What: the test only swaps `Theme.current()`; no FlatLaf theme is installed, so `r.getForeground()` and `r.getBackground()` are whatever LaF the JVM has. The V is honestly measured against the theme's muted token, but "the number is text.primary" is only proven as "the number is the label foreground". The same pattern was already in the ≠ test, so this is consistent with precedent.
   - Fix: also assert `pixelsNear(img, 0, v.x, Theme.current().textPrimary(), bg)` after setting the table foreground from the theme, or install the theme's LaF for this parameterised test.

3. **The zone-kind assertion in `bossesAreEncountersAndNpcsOfDungeonsAndRaids` is looser than its message.**
   - Where: `NpcRepositoryTest.java:142`.
   - What: `.contains(639L, 448L)` with the description "Ragefire Chasm's zone kind decides for 11520" never asserts anything about 11520, and would pass if the query returned extra ids.
   - Fix: `containsExactlyInAnyOrder(639L, 448L, 11520L)` (if Ragefire Chasm's zone is kind dungeon in the fixture), or drop 11520 from the message.

4. **The item page's loot-list branch is untested.**
   - Where: `ItemPage.java:188` (`case DropRow.Loot l -> true`) and the `l.list().pctSource()` headline branch of `headlineSource`.
   - What: the shipped test reaches only VanCleef's merged `DropRow.Source` row; no test renders a standalone loot-list row with a vmangos headline or the boss line. The fixture has no vmangos rows, so `ItemPageTest` never exercises the new wiring.
   - Fix: an `ItemPageTest` case built from an `ItemDetail` with one `InstanceLoot(..., pctSource "vmangos")` and a vmangos source, asserting "… V" and the boss line.

5. **`bosses` binds every id twice: 1,000 parameters per chunk.**
   - Where: `NpcRepository.java:107-119`.
   - What: 500 ids × 2 placeholders exceed classic SQLite's 999-variable limit. It works because sqlite-jdbc raises the limit (the 1,201-id test proves it), so the chunk size documents a limit it does not respect.
   - Fix (optional): bind each id once, e.g. `WITH ids(id) AS (VALUES (?), (?), …) SELECT b.npc_id FROM boss b JOIN ids USING… UNION SELECT n.id FROM npc n JOIN ids … JOIN zone z …`, or halve `BOSS_CHUNK` and say why.

## Strengths

- The headline/source split is clean: `pct_source` drives the cell's V and `RateSource.vanilla()` drives the tooltip, so a vmangos source that is not the headline still reads "Vanilla rate" without marking the number.
- `getText()` in number-only mode now returns `percentText(pct)` instead of suffix-stripping, which removes a fragile string operation once there are two markers.
- `fits()` keeps the old behaviour: when the text does not fit, both markers fall back to the label's own truncation.
- Repository test on a fixture copy covers `pct_source` and `quest_only` across all five read paths.
