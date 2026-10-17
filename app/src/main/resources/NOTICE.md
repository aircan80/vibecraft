# Third-party data and credits

This project builds its item and drop database from the following upstream sources.

- QuestieDB (https://github.com/Questie/QuestieDB), commit 49e7dbca1f18eb3b8e57ee5d9562ac3e5c9adcc4. No license file is published; used with attribution.
- AtlasLootClassic (https://github.com/Hoizame/AtlasLootClassic), commit 8e99341e4e779328460bf7684c0d5b22ce50ddf1. Licensed under GPL-2.0.

## AllTheThings Forever

AllTheThings (https://github.com/ATTWoWAddon/AllTheThings), commit a8c2fce5e8e52af3925352f5e61cbf7520ae3148,
provides authored Forever prerequisite evidence. Copyright (c) 2026 AllTheThings WoW Addon. Licensed under MIT.
The exact upstream LICENSE accompanies captured source snapshots; an attributed fixture license is also retained
under importer/src/test/resources/fixtures/allthethings/LICENSE.att. The app also bundles the license as
licenses/AllTheThings-MIT.txt. Source attribution does not imply verification
of quest eligibility in-game.

## VMaNGOS world database

Community drop rates (provenance source `vmangos`) are computed from the VMaNGOS world database (https://github.com/vmangos/core), release `db_latest`, asset `db-sqlite-4641790.zip` (commit 464179081673cfd240f7ffe7f0daf96bca0b5a70, published 2026-09-28). The VMaNGOS project and its database are licensed under GPL-2.0. `tools/serverdb/serverdb.lock` pins the asset and the licence by SHA-256, and the fetch tool keeps the licence text beside the downloaded data. The browser database stores only the derived percentages per NPC and item.

## Blizzard client data

Item quality, stats, tooltips, icons, zone maps and the navigation rail's UI icons are derived from World of Warcraft client data (product `wow_classic_beta`, build 1.60.1.70450, with the Classic Era `ItemSparse` and `ItemEffect` of build 1.15.9.70003 as a fallback). The tables and files were exported through wago.tools; `tools/clientdata/build.lock` records the build and a SHA-256 of every table.

World of Warcraft® and Blizzard Entertainment® are trademarks or registered trademarks of Blizzard Entertainment, Inc. Icons, UI textures and map art are © Blizzard Entertainment and are included for reference; no affiliation.

## Fonts

- Marcellus and Marcellus SC by Brian J. Bonislawsky (Astigmatic), from Google Fonts (https://github.com/google/fonts, `ofl/marcellus` at commit 9712ecf0710335ef60aa998f5ee3b3c7e40f5e7c and `ofl/marcellussc` at commit 1e838fdd5dbb8daec48f4de4f76a1cfff4b3d1ed). Licensed under the SIL Open Font License 1.1; the licence ships beside the fonts as `ui/fonts/OFL.txt`. The headings use them.
- Inter by Rasmus Andersson (https://rsms.me/inter/), bundled through FlatLaf Fonts Inter 4.1 (`com.formdev:flatlaf-fonts-inter`). Licensed under the SIL Open Font License 1.1. Body text, tables and lists use it.

## Third-party libraries

The application bundles these libraries:

- FlatLaf and FlatLaf Extras (https://github.com/JFormDesigner/FlatLaf). Licensed under Apache-2.0.
- JSVG (https://github.com/weisJ/jsvg), included through FlatLaf Extras. Licensed under MIT.
- SQLite JDBC (https://github.com/xerial/sqlite-jdbc). Licensed under Apache-2.0.
- SLF4J API and SLF4J Simple (https://www.slf4j.org). Licensed under MIT.
