### Task 15: Quest-giver pins on zone maps (owner request, 2026-10-17)

Owner's words: "when looking at an area map to be able to see where quests are given (with the questie '!' symbol).
When you click on it you can go to that quest."

**Data:** `quest_giver(quest_id, kind npc|object|item, entity_id, role start|end)` (5,293 start rows, 4,994 end rows)
joined through `npc_spawn(npc_id, zone_id, x, y)` / `object_spawn(object_id, zone_id, x, y)` for the zone; `quest(id,
name, quest_level, …)`. Item givers have no spawn and are skipped.

**Files:**
- `db/ZoneRepository.questGiversInZone(zoneId) -> List<QuestPin>` with
  `record QuestPin(EntityRef giver, String giverName, double x, double y, List<QuestRef> quests)` and
  `record QuestRef(long questId, String name, Integer level, boolean starts)` — one pin per giver spawn point, quests
  grouped per giver (both roles loaded; `starts` tells them apart). Parameterised SQL, on-map filter
  (`MapCoordinates`), ordered by giver name then quest level.
- `ui/widgets/MapView`: new `PinKind.QUEST_START` (Questie-style yellow "!" glyph: a bold "!" in `#FFD100` with a
  1 px dark outline, 14 px tall at 100 %) and `PinKind.QUEST_END` (grey "?" glyph, same style), drawn above circle
  pins; a quest pin's tooltip lists the giver and its quests ("Marshal Dughan — Wanted: Hogger (11), A Bundle of
  Trouble (5)"); click on a pin with ONE quest opens that quest (`nav.open(QUEST ref)`); click on a pin with several
  opens the existing cluster-style popup listing the quests (each item opens its quest; a last item "Giver: <name>"
  opens the giver). Pins of the same giver at the same spot are one pin; quest pins cluster with each other but NOT
  with NPC/object pins (separate layer; a cluster of quest pins shows "!" with a count badge).
- `ui/pages/ZoneMapPanel`: a fourth toggle "Quests" (off by default; on in the compact "Pins ▾" menu too) that loads
  quest pins through the existing PinSource/worker path (generation guard, status "Loading pins…", error → revert);
  `UiState.questPinsShown` remembers the toggle (default false). "Quests" can be combined with the other toggles.
- `ui/pages/QuestPage`: the "Given by" section gets the map of the giver's zone with the giver's QUEST_START pin
  (SELF-style emphasis), and "Turned in to" the QUEST_END pin, reusing the NPC/object spawn-map machinery
  (`DetailPanels.load` loads the maps off the EDT; `MapCache`).
**Tests:** `ZoneRepositoryTest.questGiversInZone` (fixture: a Westfall giver with two quests, an object giver, an item
giver skipped, an off-map spawn skipped), `MapViewTest` (quest glyph painted, quest pins cluster only with quest
pins, single-quest click opens the quest, multi-quest click opens the menu with quest + giver items),
`ZonePageTest.questToggleLoadsAndShowsQuestPins` (toggle persisted; combined with "All NPCs"), `QuestPageTest.giverOnMap`,
`UiStateTest` round-trip. Visual check by the controller: Elwynn Forest with "Quests" on — "!" pins at Northshire
Abbey and Goldshire; click Marshal Dughan's pin → quest list.
- [ ] Commit `feat(ui): quest-giver pins on zone maps`.
