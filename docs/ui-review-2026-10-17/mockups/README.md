# Explorer journal concept

Open `index.html` locally or through a local HTTP server. This is a review prototype, not a production app change. It is self-contained and makes no network requests.

Four wired screens:

- `index.html?screen=home`: task-based Home, labelled navigation and suggested reference paths.
- `index.html?screen=dungeons`: Dalaran encounter detail, reported loot and floor-map tabs.
- `index.html?screen=maps`: Westfall, clickable continent/zone breadcrumbs, marker selection and layer toggles.
- `index.html?screen=items`: Known Rotmender’s Garb versus hidden Item #287012, with a source drawer.

Append `&theme=light` for the light concept. Dark is the default. The theme button switches immediately. Responsive layouts support 420px and 1280px widths. Navigation uses local state; refresh resets the selection. Back/forward buttons preserve screen and map ancestor state. The search filters four wired examples. Ctrl/Cmd+K focuses search. Drawers support Escape, focus return and a contained Tab order.

Filters are explicitly demo selections; they do not claim to query the entire collection. Non-wired destinations display a message rather than suggesting complete functionality. No player profile, saved build, ownership, live game connection or fabricated statistics are shown. No production source was modified.

## Data and artwork

The actual bundled database supplies the displayed facts: City of Dalaran is level 28+ with nine client-listed encounters. Arcane Anomaly’s Runemender’s Seal (273031), Ephemeral Grips (273034) and Mana-Warped Chain Shirt (273035) are actual AtlasLoot-reported rows. They have no invented drop rates. Addon-only Arcanic Enigma is not silently counted as a client-listed encounter. Hyjal/Wild King remains Reported. Item 286978 has the Known hotfix name Rotmender’s Garb. Item 287012 has a not-yet-discovered name, stats hidden by Blizzard, Known Hands metadata and Known set 2135. The Known item card uses verified current hotfix facts from `item`, `item_stat` and `item_tooltip_line`: Cloth Chest, 41 Armor, +10 Intellect, Requires Level 18, Item Level 23, +11 healing and +4 magical damage; set 2133 is Rotmender’s Raiment. It does not assert an acquisition source. The prototype deliberately omits unspecified before/after source values and full snapshot metadata.

`assets/westfall.jpg` and `assets/dalaran.jpg` were extracted byte-for-byte from `app/src/main/resources/data/wowforever.sqlite`; provenance keys, lengths and SHA-256 hashes are in `assets/manifest.json`. Artwork was not redrawn or edited. Dalaran artwork is credited to Dan Gilbert, Lothaer, Arith Hsu and the Atlas Team, distributed under the copied `assets/Atlas-GPL-2.0.txt` license. Westfall is original archived client artwork and remains subject to its existing game-art rights. Map pins are explicitly illustrative placements and are not evidence of coordinates. The prototype makes no new coverage or certainty claims.

The source-comparison drawer demonstrates progressive disclosure using real provider identities, not fabricated conflicts. A temporal hidden-to-known update is explained as such. A complete production implementation would render the existing repository's exact field history and archived source evidence here.

## New design studies

- [City guide](cities.html): source-qualified native districts, overview/map navigation and honest music states.
- [Equipment research](loot-research.html): persistent results/detail, responsive filters, missing-field semantics and query states.

These are additional review concepts; original four screens remain available.
