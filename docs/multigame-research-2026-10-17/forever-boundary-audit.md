# Forever data boundary audit

Read-only audit, 2026-10-17. No database rewrite, archive removal, or production changes were made for this report. Both the original browser and Explorer are interfaces to the Forever game; the original interface is not a Classic game selection.

## Confirmed defect

Forever's default item UI can present Classic Era client values as the main tooltip even while a nearby knowledge chip says Forever stats are unknown. Beta status does not authorize that fallback.

The bundled database contains 1,639 `client_source='era'` items with full legacy client cards, 19,170 Forever client cards, and 4,864 hotfix cards. A reproducible example is item 16806, Felheart Belt: its stored level 66 / required level 60 comes from Era. Item 17783 is now genuinely completed from Forever native data; items 869 and 286978 are genuine hotfix cards. Item 287012 remains unnamed/hidden and has separate native metadata. Do not blanket-remove items by ID range or infer that old IDs mean Era.

## Affected paths

| Path | Finding |
| --- | --- |
| `db/ItemRepository.java`, `card()` | `has_client_data=1` is the only eligibility condition. Era tooltip lines become the main game card. |
| `db/ItemRepository.java`, `COLUMNS`, `summary()` | Item level, required level, quality, class/subclass and name have no source qualification. `ItemSummary` carries no source information. |
| `db/ItemRepository.java`, `query()` | Level, quality and stat filters accept Era values, so hiding only the card does not fix filtering. |
| `ui/pages/ItemPage.java` | Renders any nonempty card before knowledge states; there is no source gate. |
| `ui/journal/JournalDetails.java` | Reuses ItemPage, so Explorer shares the defect. Its evidence tab explains states but does not establish a data boundary. |
| `db/SearchRepository.java`, `SQL` | Search subtitles select raw Era levels and quality. |
| `db/NpcRepository.java`, item drop columns | Drop lists select raw item levels and quality. |
| `db/QuestRepository.java` | Reward/source item rows select raw quality. |
| `db/ItemRepository.java`, craft/container paths | Reagent/result/container rows also expose raw item quality. |
| `db/ItemContentRepository.java`, `stats()` | Raw item_stat joins are presented as Known in Forever stat/trinket catalogs, including Era facts. |
| `db/FieldKnowledgeRepository.java` | Explicitly labels Era provenance, but name/level/required-level fallback values are still returned as Reported main fields. `classic_stats` is separate, correctly labelled. |
| `db/NativeItemMetadataRepository.java` | Starts with canonical slot/icon/set and can use canonical set fallback even after finding a Forever native row. Native item slot/icon replace canonical values correctly, but canonical Era set membership must not leak into native metadata. |

## Recommended minimal correction

Introduce one reusable read boundary for the selected game, initially Forever only. Never mutate the archived canonical Era rows to implement display policy.

1. Main item cards require current Forever native or Forever hotfix evidence. Missing Forever stats remain Unknown/Hidden according to assertions; they never become Era stats.
2. Main levels, quality and other scalar game facts must be independently supported by Forever evidence. Mask Era-derived scalar fallback values in lists, search, detail summaries and filter matching. Genuine retained Forever native slot, icon and set facts can still be displayed with their source. Preserve supported zero values.
3. Restrict stat/trinket catalog numeric values to Forever/hotfix cards. Keep direct native Forever effects and still-hidden native budgets explicitly qualified.
4. Keep a collapsed, explicit **Compare with Classic Era** control for archived Classic data. It should load away from the EDT, show its own build, and never overwrite the current game. Canonical legacy Era tooltip text was partly generated with Forever spell text; a fully trustworthy Classic tooltip requires Era-only spell resolution, not simply exposing that legacy blob.
5. Keep original UI mode and Explorer on the same data policy. Game selection and interface selection are independent choices.
6. Long-term schema: game/product/build/locale belongs on datasets, assertions, projections and queries. Numeric IDs alone do not identify cross-game entities. Store multiple games in one database with composite identities and scoped evidence, not a shared last-writer-wins item row.

## Preservation and scope

Do not delete Era archives, stored history, item identities, loot links, rates, quest involvement or asset bytes. Those can remain useful comparison/report evidence. This audit establishes item-client fallback defects; it does not establish that the entire NPC, quest, map or addon corpus is invalid. Assess those providers' declared game and evidence independently, and label reports explicitly.

## Required regression examples

- 16806: no Era tooltip/66/60 as default Forever fact; archived Classic comparison stays available and labelled.
- 17783: genuine Forever stats remain visible.
- 869 / 286978: hotfix stats remain visible with current provenance.
- 287012: true hidden name/stats stay hidden; verified native slot/set metadata remains.
- Era-only stat must not match Forever stat/level/quality filters or appear as Known in a Forever catalog.
- Search, NPC drops, quest rewards and crafted results cannot reintroduce masked facts.
- Interface switch does not change selected-game data semantics.

## Implemented read boundary

`ForeverItemProjection` now installs a connection-local temporary `item` view when `Database.open` opens the read-only file. It masks Era client scalar values and sets its card availability false while retaining ID, source, and independently imported Questie flags/food/ammo/quest/spell links. Every ordinary repository join uses that view. Archived bytes remain available through explicit `main.item` access; the file is never rewritten. Search rejects Era-only indexed names, stat filters/catalogs reject Era numeric stats, and native metadata refuses the canonical Era set fallback. The explicit `ItemRepository.classicComparison(id)` API reads genuine archived Era ItemSparse, retaining source path, line, snapshot and build; it does not reuse mixed-origin legacy tooltip text.

An isolated regression passed covering default masking, current hotfix cards, level/quality/stat filtering, search, stat catalog, NPC drops, quest rewards, retained links, preserved raw archive and native-only Classic comparison. No Maven ran concurrently with the root build.

A direct probe against the shipped database produced:

| ID | Forever default | Explicit comparison/native metadata |
| --- | --- | --- |
| 16806 | Unknown name/level, zero tooltip lines | Era 1.15.9.70003 Felheart Belt, level 66 / required 60, native source line retained |
| 17783 | Talisman of Binding Fragment, level 80, 12 tooltip lines | Genuine Forever card preserved |
| 286978 | Rotmender's Garb, level 23, 19 tooltip lines | Genuine hotfix card preserved |
| 287012 | Unknown name/level, zero tooltip lines | Genuine Forever slot 10 and set 2135 retained |

Legacy tests expecting the old Era fallback must be updated to the stricter game boundary. For example, fixture `ItemRepositoryTest.listWithLevelRange` previously included Crystalline Cuffs, Cursed Felblade and Subterranean Cape through Era levels; their exclusion is now intended behavior.
