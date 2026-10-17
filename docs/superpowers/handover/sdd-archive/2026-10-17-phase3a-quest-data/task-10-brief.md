### Task 10: Regenerate the shipped database once for Tasks 7–9 (controller addition, 2026-10-17)

Ruling: Tasks 7 (waypoints), 8 (crafting) and 9 (VMaNGOS rates) each add tables but do NOT regenerate the shipped
database — every regeneration commits a ~67 MB blob, so one regeneration covers all three. Until then the committed
database lacks those tables; the new readers are not called by any UI (plan 3B) and the shipped-data assertions those
tasks name move here.

**Files:**
- Modify: `app/src/main/resources/data/*` (regenerated as in Task 5), `ShippedDataTest` (+ 550 has ≥ 30 waypoints in
  zone 40 and `npc.waypoints.npcs` reported; the crafting and VMaNGOS assertions from Tasks 8–9), README rebuild notes.
- [ ] Run, test, commit `data: NPC waypoints, crafting and community drop rates` + `docs: …`.
