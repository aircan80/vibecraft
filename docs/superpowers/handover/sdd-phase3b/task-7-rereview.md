Approved (0 Critical, 0 Important, 0 Minor open)

# Task 7 fix round 1 re-review (5fccf8f)

Read-only; diff and committed sources read, no Maven run (controller reports verify green: 61 / 449 / 934).

| Finding | Closed | Evidence |
|---|---|---|
| Important 1: compact tooltip loses the boss note's line break | yes | `CompactColumns.plainHtml` escapes, then `\n` becomes `<br>`, for row and plain cell. `CompactColumnsTest.joinKeepsTheLineBreaksOfPlainParts` fails on the old join (`\n` stayed raw). Existing escape/HTML-cell expectations unchanged. |
| Minor 1: World Bosses list unnoted | yes | `NpcRepository.bosses` joins `instance` and requires `zone_id IS NOT NULL`; `InstancePage.dungeonBosses = zoneId != null`; `ItemPage.onBoss` for a loot list needs its boss NPC in the set. Tests: `NpcRepositoryTest.worldBossesAreNoDungeonBosses` (fixture), `ItemPageTest.aWorldBossLootListsVanillaRateSaysNothingOfForever` (loot-list row), `ShippedProvenanceDataTest.worldBossVanillaRatesSayNothingOfForever` (six ids, Azuregos NPC page, World Bosses encounter; also pins instance 26 as the only zoneless instance and all zoned ones dungeon/raid). |
| Minor 2: text.primary pixel test | yes | Table foreground/background set from theme tokens; renderer fg/bg asserted equal to them; number pixels measured against `textPrimary`, V pixels against muted and none in primary. |
| Minor 3: zone-kind assertion | yes | 11520's zone set to open-world in the copy; `containsExactlyInAnyOrder(639, 448)`. |
| Minor 4: loot-list branch untested | yes | `ItemPageTest.aDungeonLootListsVanillaRateIsMarkedAndSaysForeverReworkedTheLoot` asserts the Loot row type, "20.00 % V" and the exact tooltip with the note. |
| Minor 5: id binding | yes | `WITH ids(id) AS (VALUES (?), ...)` binds each id once; `BOSS_CHUNK` 900; test asserts `< 999` and sends `2 * BOSS_CHUNK + 1` ids plus 11520. |

## Regression checks

- VanCleef: the boss-note shipped test (NPC page tooltip ends with `\n` + note, item page, instance page) is untouched. VanCleef's instance has a zone, so the encounter branch still holds.
- NPC in a dungeon zone without a boss row: the zone-kind branch of the union is unchanged; the fixture copy (boss rows deleted, 448 moved to a raid zone) still expects 639 and 448.
- Item page loot-list boss NPCs are in the set: `DetailPanels.ratedNpcs` already adds `l.bossNpcId()` for every rated loot list, so a dungeon list is looked up and noted.
- "on quest" and V markers: no change to `RateBarRenderer` or `RateSource` in the diff.
- Gap: `InstancePage` takes its flag from `d.summary().zoneId()` rather than from `bosses()`. Both agree for all shipped data (every zoned instance is dungeon/raid), which the shipped test pins.

## Information, not a finding

Maws 15571 (AQ40 boss, no zone, no boss row) gets no note, as before. No ruling covers it.
