-- WoW Forever item & drop browser database (design spec section 6).
-- Statements end with ';' at the end of a line. Lines starting with '--' are comments.
-- Everything above the '-- @indexes' marker runs before the bulk inserts, everything below it after.

CREATE TABLE meta (
  key   TEXT PRIMARY KEY,
  value TEXT
);

-- kind: zone | subzone | dungeon | raid | city | continent | unknown (placeholder for an id only spawns reference)
-- ui_map_id: the zone's UiMap (phase 2), NULL until resolved
CREATE TABLE zone (
  id        INTEGER PRIMARY KEY,
  name      TEXT,
  parent_id INTEGER,
  kind      TEXT,
  ui_map_id INTEGER
);

-- name is NULL for stub entities that corrections created without a name.
-- Client columns (phase 2, from quality on): with an ItemSparse row the client's name, item_level, required_level,
-- class_id and subclass_id replace Questie's (differing Questie values go to item_alt_value); has_client_data is 1
-- iff that row exists; client_source: forever | era (the Classic Era fallback, ruling 17) | NULL without one.
CREATE TABLE item (
  id                  INTEGER PRIMARY KEY,
  name                TEXT,
  item_level          INTEGER,
  required_level      INTEGER,
  class_id            INTEGER,
  subclass_id         INTEGER,
  flags               INTEGER,
  food_type           INTEGER,
  ammo_type           INTEGER,
  start_quest_id      INTEGER,
  teaches_spell_id    INTEGER,
  quality             INTEGER,
  inventory_type      INTEGER,
  bonding             INTEGER,
  sell_price          INTEGER,
  buy_price           INTEGER,
  max_stack           INTEGER,
  icon_id             INTEGER,
  item_set_id         INTEGER,
  description         TEXT,
  allowable_class     INTEGER,
  allowable_race      INTEGER,
  required_skill      INTEGER,
  required_skill_rank INTEGER,
  has_client_data     INTEGER NOT NULL DEFAULT 0,
  client_source       TEXT
);

-- Questie values the client data replaced (ruling 14); field is the item column name, source 'questie'
CREATE TABLE item_alt_value (
  item_id INTEGER NOT NULL,
  field   TEXT NOT NULL,
  source  TEXT NOT NULL,
  value   TEXT
);

-- computed stat values (e.g. +8 Stamina), resistances included; ordinal is the tooltip order
CREATE TABLE item_stat (
  item_id INTEGER NOT NULL,
  ordinal INTEGER NOT NULL,
  stat_id INTEGER NOT NULL,
  value   INTEGER NOT NULL
);

CREATE TABLE item_damage (
  item_id  INTEGER PRIMARY KEY,
  min      INTEGER,
  max      INTEGER,
  school   INTEGER,
  delay_ms INTEGER,
  dps      REAL
);

CREATE TABLE item_armor (
  item_id INTEGER PRIMARY KEY,
  armor   INTEGER,
  block   INTEGER
);

-- trigger: 0 Use, 1 Equip, 2 Chance on hit; text already formatted
CREATE TABLE item_effect (
  item_id  INTEGER NOT NULL,
  ordinal  INTEGER NOT NULL,
  trigger  INTEGER,
  spell_id INTEGER,
  text     TEXT
);

CREATE TABLE item_set (
  id   INTEGER PRIMARY KEY,
  name TEXT
);

CREATE TABLE item_set_item (
  set_id  INTEGER NOT NULL,
  item_id INTEGER NOT NULL
);

CREATE TABLE item_set_bonus (
  set_id    INTEGER NOT NULL,
  threshold INTEGER NOT NULL,
  spell_id  INTEGER,
  text      TEXT
);

-- the rendered tooltip in game order (the UI renders lines, it does not recompute); kind e.g. name, binding,
-- unique, slot, damage, stat, use, equip, set_bonus, flavour, sell_price
CREATE TABLE item_tooltip_line (
  item_id INTEGER NOT NULL,
  ordinal INTEGER NOT NULL,
  kind    TEXT NOT NULL,
  text    TEXT,
  colour  TEXT
);

-- 56x56 RGBA PNG; id 0 is the question-mark fallback
CREATE TABLE icon (
  id  INTEGER PRIMARY KEY,
  png BLOB NOT NULL
);

-- client UI textures by name (e.g. rail.quests, rail.quests@24), PNG (schema v3)
CREATE TABLE ui_asset (
  name TEXT PRIMARY KEY,
  png  BLOB NOT NULL
);

-- fully revealed zone map, one per UiMap
CREATE TABLE zone_map (
  ui_map_id INTEGER PRIMARY KEY,
  width     INTEGER,
  height    INTEGER,
  jpeg      BLOB NOT NULL
);

CREATE TABLE item_class (
  id   INTEGER PRIMARY KEY,
  name TEXT NOT NULL
);

CREATE TABLE item_subclass (
  class_id INTEGER NOT NULL,
  id       INTEGER NOT NULL,
  name     TEXT NOT NULL,
  PRIMARY KEY (class_id, id)
);

CREATE TABLE npc (
  id          INTEGER PRIMARY KEY,
  name        TEXT,
  sub_name    TEXT,
  min_level   INTEGER,
  max_level   INTEGER,
  rank        INTEGER,
  zone_id     INTEGER,
  faction_id  INTEGER,
  friendly_to TEXT,
  npc_flags   INTEGER
);

CREATE TABLE game_object (
  id         INTEGER PRIMARY KEY,
  name       TEXT,
  zone_id    INTEGER,
  faction_id INTEGER
);

CREATE TABLE quest (
  id               INTEGER PRIMARY KEY,
  name             TEXT,
  required_level   INTEGER,
  quest_level      INTEGER,
  zone_or_sort     INTEGER,
  required_races   INTEGER,
  required_classes INTEGER,
  objectives_text  TEXT,
  quest_flags      INTEGER,
  special_flags    INTEGER,
  -- schema v3 (phase 3A): repeatable = special_flags & 1; required skill {skill, value} and minimum reputation
  -- {faction, value}; sort_label names the QuestSort when zone_or_sort < 0 (a zone id > 0 stays in zone_or_sort)
  repeatable               INTEGER NOT NULL DEFAULT 0,
  required_skill_id        INTEGER,
  required_skill_value     INTEGER,
  required_min_rep_faction INTEGER,
  required_min_rep_value   INTEGER,
  sort_label               TEXT
);

-- kind: npc | object | item; role: start | end
CREATE TABLE quest_giver (
  quest_id  INTEGER NOT NULL,
  kind      TEXT NOT NULL,
  entity_id INTEGER NOT NULL,
  role      TEXT NOT NULL
);

-- schema v3 (phase 3A). Questie objectives in its order; kind: creature | object | item | reputation | spell |
-- explore. killCredit members share a credit_group (the base creature is the group's representative); reputation
-- rows carry the faction id and value in text; explore rows carry the triggerEnd text, their points are in
-- quest_objective_point. Questie records no counts, so there is deliberately no count column.
CREATE TABLE quest_objective (
  quest_id     INTEGER NOT NULL,
  ordinal      INTEGER NOT NULL,
  kind         TEXT NOT NULL,
  entity_id    INTEGER,
  text         TEXT,
  credit_group INTEGER
);

-- the area points of an explore objective, by zone
CREATE TABLE quest_objective_point (
  quest_id INTEGER NOT NULL,
  ordinal  INTEGER NOT NULL,
  zone_id  INTEGER NOT NULL,
  x        REAL,
  y        REAL
);

-- kind: provided (sourceItemId, handed over by the starter) | required (requiredSourceItems)
CREATE TABLE quest_source_item (
  quest_id INTEGER NOT NULL,
  item_id  INTEGER NOT NULL,
  kind     TEXT NOT NULL,
  PRIMARY KEY (quest_id, item_id, kind)
);

-- kind: pre_single | pre_group | next | child | parent | exclusive | group | breadcrumb_for | breadcrumb;
-- related_id may name a quest absent from the quest table (kept on purpose, counted as quest.relation.missing)
CREATE TABLE quest_relation (
  quest_id   INTEGER NOT NULL,
  related_id INTEGER NOT NULL,
  kind       TEXT NOT NULL,
  PRIMARY KEY (quest_id, related_id, kind)
);

-- origin: questie | atlasloot | both; disagrees / source_count describe the drop_rate_source rows (phase 2)
CREATE TABLE item_npc_drop (
  item_id      INTEGER,
  npc_id       INTEGER,
  drop_pct     REAL,
  pct_source   TEXT,
  origin       TEXT NOT NULL,
  disagrees    INTEGER NOT NULL DEFAULT 0,
  source_count INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (item_id, npc_id)
);

-- every drop rate source's value for an npc/item pair; source: questie-correction | atlasloot-override |
-- questie-wowhead | vmangos | atlasloot. quest_only (phase 3A, additive to schema v3) is 1 for a VMaNGOS quest-only
-- chance: the chance while the looter is on the quest, not per kill.
CREATE TABLE drop_rate_source (
  npc_id     INTEGER NOT NULL,
  item_id    INTEGER NOT NULL,
  source     TEXT NOT NULL,
  pct        REAL,
  quest_only INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (npc_id, item_id, source)
);

CREATE TABLE item_object_drop (
  item_id   INTEGER,
  object_id INTEGER,
  PRIMARY KEY (item_id, object_id)
);

CREATE TABLE item_container_drop (
  item_id           INTEGER,
  container_item_id INTEGER,
  PRIMARY KEY (item_id, container_item_id)
);

CREATE TABLE item_vendor (
  item_id INTEGER,
  npc_id  INTEGER,
  PRIMARY KEY (item_id, npc_id)
);

CREATE TABLE quest_reward_item (
  quest_id INTEGER,
  item_id  INTEGER,
  PRIMARY KEY (quest_id, item_id)
);

CREATE TABLE item_related_quest (
  item_id  INTEGER,
  quest_id INTEGER,
  PRIMARY KEY (item_id, quest_id)
);

CREATE TABLE npc_spawn (
  npc_id  INTEGER NOT NULL,
  zone_id INTEGER NOT NULL,
  x       REAL,
  y       REAL
);

-- NPC patrol routes (phase 3A, additive to schema v3): Questie npcKeys.waypoints {[zoneId] = {route, ...}}, each
-- route an ordered list of {x, y}; route is the 1-based list index within the zone, ordinal the 1-based point index.
-- Questie records no despawn points.
CREATE TABLE npc_waypoint (
  npc_id  INTEGER NOT NULL,
  zone_id INTEGER NOT NULL,
  route   INTEGER NOT NULL,
  ordinal INTEGER NOT NULL,
  x       REAL,
  y       REAL,
  PRIMARY KEY (npc_id, zone_id, route, ordinal)
);

CREATE TABLE object_spawn (
  object_id INTEGER NOT NULL,
  zone_id   INTEGER NOT NULL,
  x         REAL,
  y         REAL
);

CREATE TABLE instance (
  id           INTEGER PRIMARY KEY,
  key          TEXT NOT NULL,
  name         TEXT,
  map_id       INTEGER,
  instance_id  INTEGER,
  content_type TEXT,
  level_min    INTEGER,
  level_max    INTEGER,
  zone_id      INTEGER
);

CREATE TABLE boss (
  id            INTEGER PRIMARY KEY,
  instance_id   INTEGER NOT NULL,
  name          TEXT,
  npc_id        INTEGER,
  object_id     INTEGER,
  level         INTEGER,
  special_type  TEXT,
  is_extra_list INTEGER NOT NULL,
  sort_order    INTEGER NOT NULL
);

CREATE TABLE boss_loot (
  boss_id      INTEGER NOT NULL,
  item_id      INTEGER NOT NULL,
  difficulty   TEXT NOT NULL,
  slot         INTEGER,
  drop_pct     REAL,
  pct_source   TEXT,
  disagrees    INTEGER NOT NULL DEFAULT 0,
  source_count INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (boss_id, item_id, difficulty)
);

-- Crafting (phase 3A, additive to schema v3), from the client's spell tables: a spell whose Create Item effect makes
-- item_id, quantity_min to quantity_max of it. The skill columns come from its SkillLineAbility row and are all NULL
-- without one; recipe_item_id is the lowest id of its craft_recipe_item rows, NULL when no item teaches it.
-- spell_id and item_id may name spells and items absent from the item table.
CREATE TABLE craft (
  spell_id       INTEGER PRIMARY KEY,
  spell_name     TEXT,
  item_id        INTEGER NOT NULL,
  quantity_min   INTEGER NOT NULL,
  quantity_max   INTEGER NOT NULL,
  skill_line_id  INTEGER,
  skill_name     TEXT,
  min_skill      INTEGER,
  trivial_low    INTEGER,
  trivial_high   INTEGER,
  recipe_item_id INTEGER
);

-- the reagents of a craft, inserted in SpellReagents slot order (rowid order)
CREATE TABLE craft_reagent (
  spell_id INTEGER NOT NULL,
  item_id  INTEGER NOT NULL,
  count    INTEGER NOT NULL,
  PRIMARY KEY (spell_id, item_id)
);

-- every item that teaches a craft (a recipe, pattern, plans or formula; some crafts have several)
CREATE TABLE craft_recipe_item (
  spell_id INTEGER NOT NULL,
  item_id  INTEGER NOT NULL,
  PRIMARY KEY (spell_id, item_id)
);

-- kind: item | npc | object | quest | zone | instance
CREATE VIRTUAL TABLE search_index USING fts5(kind UNINDEXED, id UNINDEXED, name, tokenize = 'unicode61 remove_diacritics 2');

-- @indexes

CREATE INDEX idx_item_npc_drop_npc ON item_npc_drop(npc_id);
CREATE INDEX idx_item_object_drop_obj ON item_object_drop(object_id);
CREATE INDEX idx_item_container_drop_container ON item_container_drop(container_item_id);
CREATE INDEX idx_item_vendor_npc ON item_vendor(npc_id);
CREATE INDEX idx_quest_reward_item_item ON quest_reward_item(item_id);
CREATE INDEX idx_item_related_quest_quest ON item_related_quest(quest_id);
CREATE INDEX idx_npc_spawn_npc ON npc_spawn(npc_id);
CREATE INDEX idx_npc_spawn_zone ON npc_spawn(zone_id);
CREATE INDEX idx_npc_waypoint_zone ON npc_waypoint(zone_id);
CREATE INDEX idx_object_spawn_object ON object_spawn(object_id);
CREATE INDEX idx_object_spawn_zone ON object_spawn(zone_id);
CREATE INDEX idx_npc_zone ON npc(zone_id);
CREATE INDEX idx_object_zone ON game_object(zone_id);
CREATE INDEX idx_quest_zone ON quest(zone_or_sort);
CREATE INDEX idx_quest_giver_entity ON quest_giver(kind, entity_id);
CREATE INDEX idx_quest_giver_quest ON quest_giver(quest_id);
CREATE INDEX idx_zone_parent ON zone(parent_id);
CREATE INDEX idx_instance_zone ON instance(zone_id);
CREATE INDEX idx_boss_instance ON boss(instance_id);
CREATE INDEX idx_boss_loot_item ON boss_loot(item_id);
CREATE INDEX idx_item_class ON item(class_id, subclass_id);
CREATE INDEX idx_item_quality ON item(quality);
CREATE INDEX idx_item_icon ON item(icon_id);
CREATE INDEX idx_item_alt_value_item ON item_alt_value(item_id);
CREATE INDEX idx_item_stat_item ON item_stat(item_id);
CREATE INDEX idx_item_effect_item ON item_effect(item_id);
CREATE INDEX idx_item_set_item_set ON item_set_item(set_id);
CREATE INDEX idx_item_set_item_item ON item_set_item(item_id);
CREATE INDEX idx_item_tooltip_line_item ON item_tooltip_line(item_id);
CREATE INDEX idx_drop_rate_source_item ON drop_rate_source(item_id);
CREATE INDEX idx_quest_relation_related ON quest_relation(related_id);
CREATE INDEX idx_quest_objective_entity ON quest_objective(entity_id, kind);
CREATE INDEX idx_quest_objective_quest ON quest_objective(quest_id, ordinal);
CREATE INDEX idx_quest_objective_point_quest ON quest_objective_point(quest_id, ordinal);
CREATE INDEX idx_quest_giver_entity_role ON quest_giver(entity_id, kind, role);
CREATE INDEX idx_quest_source_item_item ON quest_source_item(item_id);
CREATE INDEX idx_craft_item ON craft(item_id);
CREATE INDEX idx_craft_recipe_item ON craft(recipe_item_id);
CREATE INDEX idx_craft_reagent_item ON craft_reagent(item_id);
CREATE INDEX idx_craft_recipe_item_item ON craft_recipe_item(item_id);
