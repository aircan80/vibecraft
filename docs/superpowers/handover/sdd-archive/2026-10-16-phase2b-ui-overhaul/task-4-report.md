# Task 4 report: search ranking, merged instance hits, worded labels

**Status:** DONE_WITH_CONCERNS
**Commit:** `f517b28 feat(ui): search ranking, merged instance hits and worded labels` (branch `feature/phase2b-ui`, not pushed)

## What was implemented

- **`db/SearchRepository`**: one ranking SQL (below) over `search_index` in a `MATERIALIZED` CTE, so `bm25()` stays
  in the FTS query, joined to the entity tables for subtitle fields and kind priority. Fetches `limit × 3` rows, then
  `collapse(rows, limit)` in Java:
  1. drops a repeated ref (a zone merged into its instance when the instance matched too, kept once at the better
     position);
  2. folds hits with the same kind and the same name into the first one (ranking order, ties broken by `kind, id`),
     which carries `count`;
  3. cuts to `limit`.
  - Exact match compares `lower(s.name)` with the search text stripped, whitespace runs collapsed and lower-cased
    (`exactText`).
  - Zone → instance merge happens in SQL. The `sole` join picks the instance only when the zone is the `zone_id` of
    exactly one instance. The hit becomes `EntityRef(INSTANCE, id)` with the instance's name and subtitle, and gets
    kind priority 0.
  - Merge is on only without a kind filter. A search filtered to zones keeps zone hits, because turning them into
    instances would break the filter.
  - FTS escaping (`matchExpression`) and the never-throw `catch` are unchanged. Every value is a JDBC parameter. The
    only `formatted` insertion is the constant `"AND kind = ?"`.
- **`model/SearchHit`**: `int count` added, validated `>= 1`. A 3-arg constructor defaults it to 1.
- **`ui/Labels`** (new, public; replaces `Subtitles`): `contentType`, `zoneKind`, `rank`, `levels`, `itemSubtitle`,
  `npcSubtitle`, `instanceSubtitle`, `zoneSubtitle`, `questSubtitle`, `objectSubtitle`, plus three helpers:
  - `withKind(kind, facts)` puts "NPC · ", "Quest · " or "Object · " in front of facts that don't name their kind.
    It also turns empty item, zone or instance facts into the kind label.
  - `withCount(subtitle, n)` appends " · ×n" when n > 1.
  - `join(parts…)` joins the parts that are not blank with " · ".
- **`AppController`**: list entries are built through `Labels`. A search entry renders `"<subtitle> · ×4"` when
  `count > 1`. An instance list entry leaves out its zone name when it equals the instance name ("Molten Core · …
  · Molten Core").
- **`Columns.contentTypeText` removed.** Its callers, the `Columns.instances` Type column and `InstanceDetailPanel`,
  now use `Labels.contentType`. `Columns.rankText` stays because the NPC table column needs "Normal" for rank 0.
  `Columns.Levels` stays because it is a sortable value type.
- `Subtitles` and `SubtitlesTest` deleted.

## Final ranking SQL

```sql
WITH s AS MATERIALIZED (
  SELECT kind, id, name, bm25(search_index) AS score FROM search_index
  WHERE search_index MATCH ? AND name IS NOT NULL AND name NOT LIKE 'Zone #%' [AND kind = ?])
SELECT s.kind, s.id, s.name,
       sole.id AS sole_id, sole.name AS sole_name,
       ic.name AS class_name, isc.name AS subclass_name, i.item_level, i.required_level,
       n.rank, n.min_level, n.max_level, nz.name AS npc_zone,
       oz.name AS object_zone,
       q.quest_level, qz.name AS quest_zone,
       z.kind AS zone_kind, zp.name AS zone_parent,
       inst.content_type, inst.level_min, inst.level_max,
       CASE WHEN iz.name = inst.name THEN izp.name ELSE iz.name END AS instance_zone
FROM s
LEFT JOIN instance sole ON ? = 1 AND s.kind = 'zone'
    AND sole.id = (SELECT CASE WHEN count(*) = 1 THEN min(id) END FROM instance WHERE zone_id = s.id)
LEFT JOIN item i ON s.kind = 'item' AND i.id = s.id
LEFT JOIN item_class ic ON ic.id = i.class_id
LEFT JOIN item_subclass isc ON isc.class_id = i.class_id AND isc.id = i.subclass_id
LEFT JOIN npc n ON s.kind = 'npc' AND n.id = s.id
LEFT JOIN zone nz ON nz.id = n.zone_id
LEFT JOIN game_object o ON s.kind = 'object' AND o.id = s.id
LEFT JOIN zone oz ON oz.id = o.zone_id
LEFT JOIN quest q ON s.kind = 'quest' AND q.id = s.id
LEFT JOIN zone qz ON q.zone_or_sort > 0 AND qz.id = q.zone_or_sort
LEFT JOIN zone z ON s.kind = 'zone' AND sole.id IS NULL AND z.id = s.id
LEFT JOIN zone zp ON zp.id = z.parent_id
LEFT JOIN instance inst ON inst.id = CASE WHEN s.kind = 'instance' THEN s.id ELSE sole.id END
LEFT JOIN zone iz ON iz.id = inst.zone_id
LEFT JOIN zone izp ON izp.id = iz.parent_id
ORDER BY lower(s.name) = ? DESC,
         CASE WHEN s.kind = 'instance' OR sole.id IS NOT NULL THEN 0
              WHEN s.kind = 'zone' THEN 1
              WHEN n.rank IN (3, 4) OR n.id IN (SELECT npc_id FROM boss WHERE npc_id IS NOT NULL) THEN 2
              WHEN s.kind = 'item' THEN 3
              WHEN s.kind = 'quest' THEN 4
              WHEN s.kind = 'npc' THEN 5
              WHEN s.kind = 'object' THEN 6
              ELSE 7 END,
         s.score, s.name, s.kind, s.id
LIMIT ?          -- limit × 3
```

The instance subtitle's place is the instance zone's parent when the zone has the instance's own name, as in
Molten Core → Burning Steppes. Otherwise it is the zone's name.

## TDD evidence

- **RED:** after writing `LabelsTest`, `SearchRankingTest` and the adjusted tests, `./mvnw -B -q -pl app -am test`
  failed to compile because `Labels` and `SearchHit.count()` were missing.
- **First green run:** 3 failures, all from expectations I had guessed about fixture values:
  - Subterranean Cape is iLvl 18 / Req 13.
  - Unsent letter has Req 16.
  - "deadmines" also matches zone 10029, which has no instance and correctly stays a zone hit.

  I fixed these expectations. Hogger is rank 1 in the data, so its subtitle is now "NPC · Elite · Level 11 · Elwynn
  Forest", and I updated that expectation too.
- **Final:** `./mvnw -B -q -pl app -am test` reports 229 app tests, 0 failures. `./mvnw -B -q verify` exits 0.
- **New tests:**
  - `SearchRankingTest`: `exactNameMatchRanksFirst`, `exactMatchIgnoresCaseAndSpacing`, `kindPriorityOrder` (all
    eight priority slots with exact subtitles), `raidZoneCollapsesIntoItsInstance` (fixture Molten Core and The
    Deadmines), `zoneOfOneInstanceBecomesThatInstanceEvenWhenOnlyTheZoneMatches`, `zoneWithTwoInstancesStaysAZone`,
    `zoneFilterKeepsZoneHits`, `sameNameSameKindCollapse` (4 "Westfall" objects become one hit with count 4),
    `limitAppliesAfterCollapsing`.
  - `LabelsTest`: every mapping, including nulls, unknown passthrough and a "never null / iLvl 1 / RAID40" check.
  - `AppControllerTest.collapsedSearchHitsShowTheirCount` checks the "Object · Westfall · ×4" entry.
  - The existing `specialCharactersDoNotThrow` test is still green.
- **Manual timing on a copy of the real database** (`%LOCALAPPDATA%\WoWForeverBrowser\wowforever.sqlite`, limit 200;
  the throwaway test was deleted afterwards):

  | Query | Time |
  |---|---|
  | "a" | 17–45 ms |
  | "s" | 42 ms |
  | "the" | 13 ms |
  | Named searches | ≤ 1 ms |

  - "westfall" gives Zone Westfall first, then "Westfall" objects ×4.
  - "molten core" gives Instance Molten Core "Raid (40) · Burning Steppes", then the quest.
  - "deadmines" gives Instance The Deadmines "Dungeon · Level 10–17 · Westfall" first.

## Files changed

- `app/src/main/java/dev/wowforever/app/db/SearchRepository.java`
- `app/src/main/java/dev/wowforever/app/model/SearchHit.java`
- `app/src/main/java/dev/wowforever/app/ui/Labels.java` (new)
- `app/src/main/java/dev/wowforever/app/ui/AppController.java`
- `app/src/main/java/dev/wowforever/app/ui/Columns.java`
- `app/src/main/java/dev/wowforever/app/ui/detail/InstanceDetailPanel.java`
- `app/src/main/java/dev/wowforever/app/ui/Subtitles.java` (deleted)
- `app/src/test/java/dev/wowforever/app/db/SearchRankingTest.java` (new)
- `app/src/test/java/dev/wowforever/app/ui/LabelsTest.java` (new)
- `app/src/test/java/dev/wowforever/app/db/SearchRepositoryTest.java`
- `app/src/test/java/dev/wowforever/app/ui/AppControllerTest.java`
- `app/src/test/java/dev/wowforever/app/ui/ResultsTableModelTest.java`
- `app/src/test/java/dev/wowforever/app/ui/SubtitlesTest.java` (deleted)

## Concerns and judgement calls

1. **Collapsing hides distinct instances that share a name.** In the real data, "Scarlet Monastery" collapses ×4
   (its wings) and "Blackrock Spire" ×2, because they are separate `instance` rows with the same name. The rule
   applied exactly as decided, so the first wing opens and the others are not reachable from search. Excluding
   INSTANCE from collapsing, or adding the wing to the name, may be better. This is the lead's call.
2. **The kind prefix is an addition to the spec.** `Labels.npcSubtitle` and `questSubtitle` return exactly the
   specified strings. In a mixed list, though, a normal NPC "Level 11 · Elwynn Forest" and a quest "Level 10 ·
   Elwynn Forest" would read the same. So list and search entries go through `Labels.withKind`, which prefixes
   "NPC", "Quest" or "Object". Items, zones and instances name themselves through their class, zone kind or content
   type. The old "NPC · …" and "Object · Elwynn Forest" list subtitles therefore stay as they were.
3. **Item subtitles lead with the subclass alone** when it differs from the class ("Cloth · iLvl 18 · Req 13"), per
   the "Mail waist" style. There is no inventory-slot data, so there is no "waist" part. "Armor › Cloth" list pages
   now show "Cloth · …" instead of "Armor · Cloth · …", and the `AppControllerTest` expectations were updated to
   match.
4. **Exact match uses SQLite `lower()`, which folds ASCII only.** A name with non-ASCII capitals would not count as
   an exact match. It still ranks by kind and bm25.
5. **`collapse` counts only within the `limit × 3` fetched rows,** so a very common name could under-count.
6. **Package dependency:** `db.SearchRepository` now imports `ui.Labels`, as decided ("SearchRepository builds
   subtitles through Labels").

Nothing outside the worktree was touched. Two temporary probe and timing tests were created and deleted before the
commit. Their stale reports remain only in `app/target`.

---

# Fix round 1: never collapse instance or zone hits; disambiguate instance wings

**Commit:** `07f4046 fix(ui): never collapse instance or zone hits; disambiguate instance wings`

## What changed

1. **Instances and zones are never collapsed.** In `SearchRepository.collapse`, INSTANCE and ZONE rows get no group
   key (`app/src/main/java/dev/wowforever/app/db/SearchRepository.java:160`). Each one stays its own hit with
   count 1. Items, NPCs, objects and quests still collapse. The class doc, the `search` javadoc and the `collapse`
   javadoc were updated to say so.
2. **Same-named instances are titled with their wing.** I put the wing in the row title, for example
   "Scarlet Monastery › Library", so it is visible on line 1 of every list row.
   - The search SQL now selects `inst.key AS instance_key` and a `shared_name` flag (`SearchRepository.java:52-53`).
     The flag says whether another instance in the instance table has the same name. Using the whole table rather
     than only the result set means a search that finds just one wing still shows which wing it is.
   - `instanceTitle(rs, name)` (`SearchRepository.java:207`) applies the wing to direct instance hits and to merged
     zone hits.
   - `Labels.instanceWing(name, key)` (`app/src/main/java/dev/wowforever/app/ui/Labels.java:138`) splits the key at
     CamelCase and punctuation and drops the words the name already has. A key word made of several name words run
     together, such as `Ruinsof`, is dropped too (`madeOf`, `Labels.java:190`), and so are "the" and "of". It
     returns null when nothing is left. `Labels.instanceTitle` (`Labels.java:160`) gives "name › wing" or the name
     alone.
   - The wing is applied only when the name is shared, not to every key with extra words. On the real data, keys
     like `TheRuinsofAhnQiraj` and `TheTempleofAhnQiraj` would otherwise risk spurious wings. The word stripping
     already handles them, as tested.
   - `InstanceSummary` gains `String key`, and the old 7-argument constructor is kept with key null
     (`app/src/main/java/dev/wowforever/app/model/InstanceSummary.java:11`). `InstanceRepository.SUMMARY_SELECT`
     selects `i.key` (`InstanceRepository.java:20`, `:70`).
   - `AppController.instanceEntries` (`app/src/main/java/dev/wowforever/app/ui/AppController.java:371`) titles an
     instance with its wing in the Dungeons & Raids list when another instance in the list has the same name.

## Covering tests

- `SearchRankingTest.sameNamedInstancesStaySeparateHitsNamedByWing` (`:189`): four "Scarlet Monastery" instances in
  a temp copy give four INSTANCE hits titled Graveyard, Library, Armory and Cathedral, each with count 1, followed by
  the zone. The zone has four instances, so it stays a zone.
- `SearchRankingTest.sameNamedZonesStaySeparateHits` (`:203`): two zones named "Gloomhold" give two ZONE hits.
- `LabelsTest.instanceWingFromKey` (`:109`) covers:
  - `ScarletMonasteryLibrary` → Library, `LowerBlackrockSpire` → Lower, `UpperBlackrockSpire` → Upper,
    `DireMaulNorth` → North;
  - keys that say nothing beyond the name give null: `MoltenCore`, `Zul'Farrak`, `Onyxia` for "Onyxia's Lair",
    `TheRuinsofAhnQiraj`;
  - a null key gives null.
- `LabelsTest.instanceTitle` (`:123`).
- `AppControllerTest.instanceListNamesTheWingsOfSameNamedInstances` (`:137`): the list shows "Blackrock Spire ›
  Lower" and "Blackrock Spire › Upper", and "Molten Core" stays plain.
- **RED:** the build failed to compile because `Labels.instanceWing` and `Labels.instanceTitle` were missing.

## Commands and output

- `./mvnw -B -q -pl app -am test` exits 0 with 234 app tests, 0 failures and 0 errors, counted from the surefire
  reports. All new test names appear in the reports.
- `./mvnw -B -q verify` exits 0.
- A throwaway test on a copy of the real database was deleted afterwards. Its results:

  | Search | Result |
  |---|---|
  | "scarlet monastery" | Graveyard, Library, Armory, Cathedral as four INSTANCE rows, then the zone |
  | "blackrock spire" | Lower, Upper, then the zone |
  | "dire maul" | East, West, North |
  | "westfall" | Still has "Westfall" objects ×4 |
  | "s" | 80 ms |

## New concern

The real database has four zones named "Dire Maul": one with parent Feralas and three dungeon sub-zones whose parent
is the "Dire Maul" zone. They are now separate hits as ruled. Three of them read exactly alike, "Dire Maul ·
Dungeon · Dire Maul", because zones have no key to tell them apart. Telling them apart would take importer data,
such as the wing a sub-zone belongs to, or hiding a zone whose parent has the same name. This is out of scope here.
