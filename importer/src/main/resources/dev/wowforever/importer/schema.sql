-- WoW Forever item & drop browser database (design spec section 6).
-- Statements end with ';' at the end of a line. Lines starting with '--' are comments.
-- Everything above the '-- @indexes' marker runs before the bulk inserts, everything below it after.

CREATE TABLE meta (
  key   TEXT PRIMARY KEY,
  value TEXT
);

-- kind: zone | subzone | dungeon | raid | city | continent | unknown (placeholder for an id only spawns reference)
CREATE TABLE zone (
  id        INTEGER PRIMARY KEY,
  name      TEXT,
  parent_id INTEGER,
  kind      TEXT
);

-- name is NULL for stub entities that corrections created without a name
CREATE TABLE item (
  id               INTEGER PRIMARY KEY,
  name             TEXT,
  item_level       INTEGER,
  required_level   INTEGER,
  class_id         INTEGER,
  subclass_id      INTEGER,
  flags            INTEGER,
  food_type        INTEGER,
  ammo_type        INTEGER,
  start_quest_id   INTEGER,
  teaches_spell_id INTEGER
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
  special_flags    INTEGER
);

-- kind: npc | object | item; role: start | end
CREATE TABLE quest_giver (
  quest_id  INTEGER NOT NULL,
  kind      TEXT NOT NULL,
  entity_id INTEGER NOT NULL,
  role      TEXT NOT NULL
);

-- origin: questie | atlasloot | both
CREATE TABLE item_npc_drop (
  item_id    INTEGER,
  npc_id     INTEGER,
  drop_pct   REAL,
  pct_source TEXT,
  origin     TEXT NOT NULL,
  PRIMARY KEY (item_id, npc_id)
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
  boss_id    INTEGER NOT NULL,
  item_id    INTEGER NOT NULL,
  difficulty TEXT NOT NULL,
  slot       INTEGER,
  drop_pct   REAL,
  pct_source TEXT,
  PRIMARY KEY (boss_id, item_id, difficulty)
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
