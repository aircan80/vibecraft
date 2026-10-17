# Third-party data and credits

This project builds its item and drop database from the following upstream sources.

- QuestieDB (https://github.com/Questie/QuestieDB), commit 49e7dbca1f18eb3b8e57ee5d9562ac3e5c9adcc4. No license file is published; used with attribution.
- AtlasLootClassic (https://github.com/Hoizame/AtlasLootClassic), commit 8e99341e4e779328460bf7684c0d5b22ce50ddf1. Licensed under GPL-2.0.

## Blizzard client data

Item quality, stats, tooltips, icons, zone maps and the navigation rail's UI icons are derived from World of Warcraft client data (product `wow_classic_beta`, build 1.60.1.70425, with the Classic Era `ItemSparse` and `ItemEffect` of build 1.15.9.70003 as a fallback). The tables and files were exported through wago.tools; `tools/clientdata/build.lock` records the build and a SHA-256 of every table.

World of Warcraft® and Blizzard Entertainment® are trademarks or registered trademarks of Blizzard Entertainment, Inc. Icons, UI textures and map art are © Blizzard Entertainment and are included for reference; no affiliation.
