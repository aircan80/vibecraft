### Task 4: Client UI icon textures

**Files:**
- Modify: `tools/clientdata/clientdata/icons.py`/`fetch.py` (`--ui-icons` fetching a fixed list of (name, fdid) pairs resolved from the client listfile names in the spec §5 — resolve names → fdids with the listfile endpoint or a committed `ui_icons.json`; verify each exists, log the fallback choice when the first choice is missing), `convert.py` (writes `ui_icons/<name>.png` at native size, plus a 24 px cropped variant `<name>@24.png` using the central 86 % crop for Interface/Icons art and native centring for GossipFrame art), `importer/.../clientdata/UiAssetLoader.java` (embeds both variants into `ui_asset` as `<name>` and `<name>@24`), `ClientDataStage` wiring, README + NOTICE.
- Test: `UiAssetLoaderTest` on a fixture with two PNGs; Python tests for the crop maths and the name→fdid resolution with a fake listfile.

**Interfaces:**
- Produces: `ui_asset` rows `rail.home`, `rail.items`, `rail.npcs`, `rail.quests`, `rail.zones`, `rail.instances` (+ `@24`), `report: clientdata.uiAssets = 6`.

- [ ] Tests red → green; commit `feat(clientdata): client UI icon textures for the rail`.

