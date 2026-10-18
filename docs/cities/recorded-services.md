# Recorded city services

Explorer Journal city guides now have **People & services**, with in-memory vendor/quest filters, exact NPC/item/quest navigation and recorded city coordinates. All labels derive from relationships, never names or NPC flag guesses.

The worker loads an immutable `CityServicesRepository.Snapshot` alongside `GuideSnapshot`. Rendering and filtering execute no SQL. Selection starts with `npc_spawn.zone_id` using `idx_npc_spawn_zone`, deduplicates NPC IDs, joins by primary key and checks the existing vendor/giver indexes. It loads at most 60 people alphabetically, 12 stock links and 12 quest-role links per person, and four distinct valid coordinate pairs. Truncation is stated; complete relations remain on the NPC page. Counts describe the actual joinable relations, with start/end roles counted separately.

Scope is exact-area only: a spawn in Valley of Heroes is not silently moved to Stormwind. The district directory remains the route to district guides. Native Forever AreaTable city identities and Questie placement/quest coverage are explicitly distinguished. Vendor relations may include archived imports and collector observations; the directory does not assert exact Forever vendor verification or complete services. No schema/index migration or database mutation is required.

Read-only selection check on the installed database: Stormwind reaches the 61-row overflow probe in about 7 ms; EXPLAIN uses `idx_npc_spawn_zone`, NPC rowid, `idx_item_vendor_npc`, and `idx_quest_giver_entity`. This is selection latency, not an end-to-end benchmark.
