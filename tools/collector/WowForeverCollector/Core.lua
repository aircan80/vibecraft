-- WoW Forever Collector: records what the game shows about loot sources, boss
-- encounters, vendors, trainers and quests, for import into the WoW: Forever
-- browser. Everything goes into the account-wide WowForeverCollectorDB
-- (schema 1, see README.md).
--
-- Rules this file keeps:
--   * Never a character name, realm or guild. The player's own unit is asked
--     for its map position, and for its name in memory only: quest text
--     carries the name (the client fills in $N), so scrub() replaces it with
--     $N before storing, and drops the text when the name cannot be read.
--     GUIDs are reduced to a type and an id (Creature/GameObject) plus a
--     one-way spawn key, never stored raw.
--   * Forever restricts addons the way Midnight does: many unit and combat
--     values come back as "secret" values that throw when compared, indexed
--     or parsed. Every client call goes through try()/one(), which pcall the
--     call, test each return with issecretvalue, and record what failed in
--     apiFailures (call, where, reason) instead of storing it.
--   * Every event handler and timer runs under pcall; a Lua error becomes an
--     apiFailures row and never stops the addon.

local ADDON = ...

local VERSION = "1.1.0"
local SCHEMA = 1
local MAX_ROWS = 20000        -- per list; a one-week beta run stays far below
local MAX_FAILURES = 500      -- distinct call/where/reason rows
local ENCOUNTER_WINDOW = 300  -- seconds after a boss kill that loot is tied to it
local LOOT_SLOT_MONEY = 2     -- Enum.LootSlotType.Money

local DB                      -- WowForeverCollectorDB once ADDON_LOADED ran
local failIndex = {}          -- "call|where|reason" -> apiFailures row
local seenSpawns = {}         -- spawnKey .. "|" .. lootKind -> true, so one corpse counts once
local lastKill                -- { id = encounterID, at = GetTime(), mapID = instance map }

local type, tostring, tonumber, select, pcall = type, tostring, tonumber, select, pcall
local floor, format = math.floor, string.format

---------------------------------------------------------------------------
-- Secret values and safe calls
---------------------------------------------------------------------------

-- True for a secret value. A client without secret values has no
-- issecretvalue and nothing is secret; if the test itself throws, the value
-- is treated as secret.
local function isSecret(v)
  if v == nil or type(issecretvalue) ~= "function" then return false end
  local ok, secret = pcall(issecretvalue, v)
  if not ok then return true end
  return secret == true
end

-- A plain value that is safe to store: a non-secret string, number or boolean.
local function clean(v)
  local t = type(v)
  if (t == "string" or t == "number" or t == "boolean") and not isSecret(v) then return v end
  return nil
end

local function now()
  local ok, t = pcall(GetServerTime)
  t = ok and clean(t)
  if type(t) == "number" then return t end
  return time()
end

-- GetTime(), or nil when it cannot be read.
local function clock()
  local ok, t = pcall(GetTime)
  t = ok and clean(t)
  if type(t) == "number" then return t end
  return nil
end

local function inInstance()
  local ok, inside = pcall(IsInInstance)
  return ok and clean(inside) == true
end

-- Where a call happened: the event, plus whether the player is in an
-- instance, since Forever hides more inside them.
local function whereOf(event)
  return inInstance() and (event .. " (instance)") or event
end

-- An error message without our file path, and short.
local function shortError(err)
  local s = clean(err) or "non-string error"
  if type(s) ~= "string" then s = tostring(s) end
  s = s:gsub("^[^:]*Core%.lua:%d+: ", "")
  if #s > 240 then
    -- Cut before a UTF-8 continuation byte, so the file stays valid UTF-8.
    local n = 240
    while n > 0 and s:byte(n + 1) >= 128 and s:byte(n + 1) < 192 do n = n - 1 end
    s = s:sub(1, n)
  end
  return s
end

local pendingFailures = {}    -- failures seen before the saved table loaded

-- Records that `call` failed at `where`: reason is "secret", "nil", "error"
-- or "missing" (the client has no such function or event). One row per
-- call/where/reason with a count.
local function fail(call, where, reason, detail)
  if not DB then
    pendingFailures[#pendingFailures + 1] = { call, where, reason, detail }
    return
  end
  local key = call .. "|" .. where .. "|" .. reason
  local row = failIndex[key]
  local t = now()
  if not row then
    if #DB.apiFailures >= MAX_FAILURES then return end
    row = { call = call, where = where, reason = reason, count = 0, first = t }
    DB.apiFailures[#DB.apiFailures + 1] = row
    failIndex[key] = row
  end
  row.count = row.count + 1
  row.last = t
  if detail ~= nil and row.detail == nil then row.detail = shortError(detail) end
end

-- The client function at a dotted path such as "C_Map.GetBestMapForUnit".
local function api(path)
  local t = _G
  for part in path:gmatch("[^.]+") do
    if type(t) ~= "table" then return nil end
    t = t[part]
  end
  return t
end

local function packFrom(ok, ...)
  return ok, { n = select("#", ...), ... }
end

-- Calls the client function `call` with the arguments. Returns its results
-- packed as { n = count, ... }, with every secret result replaced by nil and
-- r.secret set; or nil when the function is missing or threw.
local function try(call, where, ...)
  local fn = api(call)
  if type(fn) ~= "function" then
    fail(call, where, "missing")
    return nil
  end
  local ok, r = packFrom(pcall(fn, ...))
  if not ok then
    fail(call, where, "error", r[1])
    return nil
  end
  local secretAt
  for i = 1, r.n do
    if isSecret(r[i]) then
      r[i] = nil
      secretAt = (secretAt and (secretAt .. ",") or "") .. i
    end
  end
  if secretAt then
    r.secret = true
    fail(call, where, "secret", "return " .. secretAt)
  end
  return r
end

-- The first result of a call where data is expected; a nil result is
-- recorded as a failure. Returns nil when there is nothing usable.
local function one(call, where, ...)
  local r = try(call, where, ...)
  if not r then return nil end
  local v = r[1]
  if v == nil and not r.secret then fail(call, where, "nil") end
  return v
end

-- Runs fn(...) under pcall; an error becomes an apiFailures row.
local function guarded(name, fn, ...)
  local ok, err = pcall(fn, ...)
  if not ok then fail(name, "handler", "error", err) end
end

local function after(seconds, name, fn)
  local timer = api("C_Timer.After")
  if type(timer) ~= "function" then
    fail("C_Timer.After", name, "missing")
    return guarded(name, fn)
  end
  timer(seconds, function() guarded(name, fn) end)
end

local function append(list, row)
  if #list < MAX_ROWS then list[#list + 1] = row end
end

local function round2(v)
  return floor(v * 100 + 0.5) / 100
end

---------------------------------------------------------------------------
-- GUIDs, units, places
---------------------------------------------------------------------------

-- A one-way key for a GUID so the importer can count a corpse once across
-- reloads and files without the GUID itself being stored: two 32-bit string
-- hashes, as 16 hex digits.
local function spawnKey(guid)
  local h1, h2 = 5381, 0
  for i = 1, #guid do
    local b = guid:byte(i)
    h1 = (h1 * 33 + b) % 4294967296
    h2 = (h2 * 65599 + b) % 4294967296
  end
  return format("%04x%04x%04x%04x", floor(h1 / 65536), h1 % 65536, floor(h2 / 65536), h2 % 65536)
end

local KNOWN_KINDS = { Creature = "Creature", Vehicle = "Creature", GameObject = "GameObject",
  Item = "Item", Pet = "Pet", Player = "Player" }

-- "Creature-0-4372-0-17-6-0000123456" -> { type = "Creature", id = 6 } and its
-- spawn key. Only Creature and GameObject keep an id; any other kind keeps
-- its type alone. nil for a value that is not a readable GUID string.
local function parseGUID(guid, where)
  guid = clean(guid)
  if type(guid) ~= "string" or guid == "" then return nil end
  local ok, kind, _, _, _, _, id = pcall(strsplit, "-", guid)
  if not ok then
    fail("strsplit(GUID)", where, "error", kind)
    return nil
  end
  local t = KNOWN_KINDS[kind] or "Other"
  local src = { type = t }
  if t == "Creature" or t == "GameObject" then src.id = tonumber(id) end
  return src, spawnKey(guid)
end

-- The NPC (or object) whose window is open: type, id and name. Falls back to
-- the target when it is the same unit. nil for a player or an unreadable GUID.
local function interactionUnit(where)
  local guid = one("UnitGUID", where, "npc")
  local unit = "npc"
  if guid == nil then
    local same = try("UnitIsUnit", where, "target", "npc")
    if same and same[1] == true then
      guid = one("UnitGUID", where, "target")
      unit = "target"
    end
  end
  local src = parseGUID(guid, where)
  if not src or src.type == "Player" then return nil end
  if src.type == "Creature" or src.type == "GameObject" then
    local name = clean(one("UnitName", where, unit))
    if type(name) == "string" and name ~= "" then src.name = name end
  end
  return src
end

-- uiMapID and the player's x, y in percent on it (2 decimals). x, y are nil
-- where the client hides the position, as it does inside many instances.
local function position(where)
  local map = clean(one("C_Map.GetBestMapForUnit", where, "player"))
  if type(map) ~= "number" then return nil end
  local pos = one("C_Map.GetPlayerMapPosition", where, map, "player")
  if pos == nil then return map end
  local ok, x, y = pcall(function()
    if pos.GetXY then return pos:GetXY() end
    return pos.x, pos.y
  end)
  if not ok then
    fail("C_Map.GetPlayerMapPosition:GetXY", where, "error", x)
    return map
  end
  if isSecret(x) or isSecret(y) then
    fail("C_Map.GetPlayerMapPosition:GetXY", where, "secret")
    return map
  end
  x, y = clean(x), clean(y)
  if type(x) ~= "number" or type(y) ~= "number" or (x == 0 and y == 0) then return map end
  return map, round2(x * 100), round2(y * 100)
end

-- GetInstanceInfo: name, instanceType, difficultyID, difficultyName and the
-- instance's map id (its 8th return).
local function instanceInfo(where)
  local r = try("GetInstanceInfo", where)
  if not r then return nil end
  local info = {
    name = clean(r[1]),
    instanceType = clean(r[2]),
    difficultyID = clean(r[3]),
    difficultyName = clean(r[4]),
    mapID = clean(r[8]),
  }
  local lfg = clean(r[10])
  if type(lfg) == "number" and lfg > 0 then info.lfgDungeonID = lfg end
  return info
end

local function stamp(row, where)
  row.t = now()
  row.uiMapID, row.x, row.y = position(where)
  return row
end

local function itemIDOf(link)
  if type(link) ~= "string" then return nil end
  return tonumber(link:match("|Hitem:(%d+)"))
end

-- Quest text with the character's name replaced by $N, the way quest data
-- writes it. The name is read only here and never stored. Every plain
-- occurrence is replaced, also inside a longer word, so a short name can
-- blank part of a word; leaking it would be worse. nil when the text or the
-- name cannot be read: unscrubbed text is never stored.
local function scrub(text, where)
  text = clean(text)
  if type(text) ~= "string" then return nil end
  local r = try("UnitName", where, "player")
  local name = r and clean(r[1])
  if type(name) ~= "string" or name == "" then
    fail("UnitName(player)", where, (r and r.secret) and "secret" or "nil", "quest text dropped")
    return nil
  end
  local pattern = name:gsub("[%^%$%(%)%%%.%[%]%*%+%-%?]", "%%%0")
  return (text:gsub(pattern, "$N"))
end

---------------------------------------------------------------------------
-- Loot
---------------------------------------------------------------------------

local windowHadSourceless = false   -- one sourceless row per loot window

local LOOT_SLOT_ITEM = 1       -- Enum.LootSlotType.Item
local CAST_WINDOW = 3          -- seconds from a pickpocket or gathering cast to its loot window

-- What kind of looting a window is. GetLootSourceInfo gives the same
-- creature GUID for its corpse, for a pickpocket and for skinning it, so the
-- kind comes from the player's last spell cast. Only "corpse" is the
-- creature's or object's own loot. "pickpocket", "gather" (skinning, mining,
-- herb gathering) and "fishing" never stand for a drop, and "unknown" means
-- the client hid the spell that was just cast.
local PICKPOCKET_SPELLS = { [921] = true }
local GATHER_SPELLS = {
  [8613] = true, [8617] = true, [8618] = true, [10768] = true,   -- Skinning
  [2575] = true, [2576] = true, [3564] = true, [10248] = true,   -- Mining
  [2366] = true, [2368] = true, [3570] = true, [11993] = true,   -- Herb Gathering
}
local KIND_BY_SPELL_NAME = {
  ["Pick Pocket"] = "pickpocket",
  ["Skinning"] = "gather", ["Mining"] = "gather", ["Herb Gathering"] = "gather", ["Herbalism"] = "gather",
}
local lastCast   -- { kind, spellID, at } of the player's last pickpocket or gathering cast

local function spellName(id, where)
  if type(api("C_Spell.GetSpellName")) == "function" then return clean(one("C_Spell.GetSpellName", where, id)) end
  return clean(one("GetSpellInfo", where, id))
end

local function onSpellcast(event, unit, castGUID, spellID)
  if isSecret(unit) or unit ~= "player" then return end
  local where = whereOf(event)
  if isSecret(spellID) then
    fail(event .. " payload", where, "secret", "spellID")
    lastCast = { kind = "unknown", at = clock() }
    return
  end
  local id = clean(spellID)
  if type(id) ~= "number" then return end
  local kind = (PICKPOCKET_SPELLS[id] and "pickpocket") or (GATHER_SPELLS[id] and "gather") or nil
  if not kind then
    local name = spellName(id, where)
    kind = name and KIND_BY_SPELL_NAME[name]
  end
  if kind then lastCast = { kind = kind, spellID = id, at = clock() } end
end

-- The loot kind of the open window, and the spell that made it so.
local function lootKindNow(where)
  local fishing = try("IsFishingLoot", where)
  if fishing and (fishing[1] == true or fishing[1] == 1) then return "fishing" end
  local t = clock()
  if lastCast and lastCast.at and t and t - lastCast.at <= CAST_WINDOW then return lastCast.kind, lastCast.spellID end
  return "corpse"
end

-- slotType is GetLootSlotType's answer; only an item slot must have a link.
local function lootItem(slot, slotType, where)
  local r = try("GetLootSlotLink", where, slot)
  local link = r and clean(r[1])
  if r and link == nil and not r.secret and slotType == LOOT_SLOT_ITEM then fail("GetLootSlotLink", where, "nil") end
  local info = try("GetLootSlotInfo", where, slot)
  local item = { link = link, itemID = itemIDOf(link) }
  if info then
    item.name = clean(info[2])
    item.quantity = clean(info[3])
    item.quality = clean(info[5])
    if clean(info[7]) == true then item.questItem = true end
    local currencyID = clean(info[4])
    if type(currencyID) == "number" and currencyID > 0 then item.currencyID = currencyID end
  end
  if item.link == nil and item.name == nil and item.currencyID == nil then return nil end
  return item
end

-- Reads the open loot window. Items are grouped by source; a source already
-- seen with the same loot kind (same spawn key, also from earlier sessions)
-- is skipped, so a corpse counts once however often its window opens, and a
-- pickpocket or skinning never blocks the corpse's own loot.
local function onLoot(event)
  local where = whereOf(event)
  local count = clean(one("GetNumLootItems", where))
  if type(count) ~= "number" or count < 1 then return end
  local kind, kindSpell = lootKindNow(where)

  local bySource, order = {}, {}
  local sourceless
  for slot = 1, count do
    local typeResult = try("GetLootSlotType", where, slot)
    local slotType = typeResult and clean(typeResult[1])
    if slotType ~= LOOT_SLOT_MONEY then
      local item = lootItem(slot, slotType, where)
      if item then
        local sources = try("GetLootSourceInfo", where, slot)
        local placed = false
        if sources then
          if sources[1] == nil and not sources.secret then fail("GetLootSourceInfo", where, "nil") end
          for i = 1, sources.n, 2 do
            local src, key = parseGUID(sources[i], where)
            if src and src.type ~= "Player" and not seenSpawns[key .. "|" .. kind] then
              local entry = bySource[key]
              if not entry then
                src.spawnKey = key
                entry = { source = src, items = {} }
                bySource[key] = entry
                order[#order + 1] = key
              end
              local copy = {}
              for k, v in pairs(item) do copy[k] = v end
              local qty = clean(sources[i + 1])
              if type(qty) == "number" and qty > 0 then copy.quantity = qty end
              entry.items[#entry.items + 1] = copy
            end
            if src then placed = true end
          end
        end
        if not placed and not windowHadSourceless then
          sourceless = sourceless or {}
          sourceless[#sourceless + 1] = item
        end
      end
    end
  end
  if #order == 0 and not sourceless then return end

  local instance = instanceInfo(where)
  -- A hint, not a link: the last boss killed in this instance within five
  -- minutes. Trash looted after a boss carries it too.
  local encounterID
  local t = clock()
  if lastKill and instance and instance.mapID == lastKill.mapID and t and lastKill.at
      and t - lastKill.at <= ENCOUNTER_WINDOW then
    encounterID = lastKill.id
  end
  local function newRow()
    return stamp({ event = event, lootKind = kind, lootSpellID = kindSpell, instance = instance,
      encounterID = encounterID }, where)
  end
  for _, key in ipairs(order) do
    local row = newRow()
    row.source = bySource[key].source
    row.items = bySource[key].items
    seenSpawns[key .. "|" .. kind] = true
    append(DB.loot, row)
  end
  if sourceless then
    local row = newRow()
    row.sourceHidden = true
    row.items = sourceless
    windowHadSourceless = true
    append(DB.loot, row)
  end
end

---------------------------------------------------------------------------
-- Encounters
---------------------------------------------------------------------------

local function onEncounter(event, encounterID, name, difficultyID, groupSize, success)
  local where = whereOf(event)
  local payload = event .. " payload"
  if isSecret(encounterID) or isSecret(name) or isSecret(difficultyID) or isSecret(groupSize)
      or isSecret(success) then
    fail(payload, where, "secret")
  end
  local id = clean(encounterID)
  if id == nil and not isSecret(encounterID) then fail(payload, where, "nil", "encounterID") end
  local row = {
    t = now(),
    event = event == "ENCOUNTER_START" and "start" or "end",
    encounterID = id,
    name = clean(name),
    difficultyID = clean(difficultyID),
    groupSize = clean(groupSize),
    instance = instanceInfo(where),
  }
  if event == "ENCOUNTER_END" then
    local s = clean(success)
    row.success = s == 1 or s == true
    if row.success and id then
      lastKill = { id = id, at = clock(), mapID = row.instance and row.instance.mapID }
    end
  end
  append(DB.encounters, row)
end

---------------------------------------------------------------------------
-- Vendors
---------------------------------------------------------------------------

local merchantOpen = false
local merchantRow               -- the row this visit writes into
local merchantScanQueued = false

-- One row per NPC: a later visit to the same vendor replaces its row.
local function upsertByNpc(list, row)
  local npc = row.npc
  if npc and npc.id then
    for i = 1, #list do
      local other = list[i].npc
      if other and other.type == npc.type and other.id == npc.id then
        list[i] = row
        return
      end
    end
  end
  append(list, row)
end

local function merchantItem(i, where)
  local link = clean(one("GetMerchantItemLink", where, i))
  local item = { link = link, itemID = clean(one("GetMerchantItemID", where, i)) or itemIDOf(link) }
  if type(api("C_MerchantFrame.GetItemInfo")) == "function" then
    local r = try("C_MerchantFrame.GetItemInfo", where, i)
    local info = r and r[1]
    if type(info) == "table" then
      item.name = clean(info.name)
      item.price = clean(info.price)
      item.stack = clean(info.stackCount)
      item.numAvailable = clean(info.numAvailable)   -- unlimited may read 0 or -1 here
      if clean(info.hasExtendedCost) == true then item.extendedCost = true end
    elseif r and not r.secret then
      fail("C_MerchantFrame.GetItemInfo", where, "nil")
    end
  else
    -- name, texture, price, stack, numAvailable, isPurchasable, isUsable, extendedCost
    local r = try("GetMerchantItemInfo", where, i)
    if r then
      item.name = clean(r[1])
      item.price = clean(r[3])
      item.stack = clean(r[4])
      item.numAvailable = clean(r[5])
      if clean(r[8]) == true then item.extendedCost = true end
    end
  end
  if item.extendedCost then
    local n = clean(one("GetMerchantItemCostInfo", where, i))
    if type(n) == "number" and n > 0 then
      item.costs = {}
      for j = 1, n do
        -- texture, value, link, currencyName
        local c = try("GetMerchantItemCostItem", where, i, j)
        if c then
          item.costs[#item.costs + 1] = { value = clean(c[2]), link = clean(c[3]), currency = clean(c[4]) }
        end
      end
    end
  end
  return item
end

local function scanMerchant(event)
  if not merchantOpen then return end
  local where = whereOf(event)
  local n = clean(one("GetMerchantNumItems", where))
  if type(n) ~= "number" then return end
  local items = {}
  for i = 1, n do items[#items + 1] = merchantItem(i, where) end
  merchantRow.items = items
end

local function onMerchantShow(event)
  local where = whereOf(event)
  merchantOpen = true
  merchantRow = stamp({ npc = interactionUnit(where) }, where)
  upsertByNpc(DB.vendors, merchantRow)
  scanMerchant(event)
  -- Item data can arrive after the window opens; read it again shortly.
  after(1, "MERCHANT_SHOW+1s", function() scanMerchant("MERCHANT_SHOW+1s") end)
end

local function onMerchantUpdate(event)
  if not merchantOpen or merchantScanQueued then return end
  merchantScanQueued = true
  after(0.5, event, function()
    merchantScanQueued = false
    scanMerchant(event)
  end)
end

---------------------------------------------------------------------------
-- Trainers
---------------------------------------------------------------------------

local SERVICE_KINDS = { available = true, unavailable = true, used = true, header = true }
local TRAINER_FILTERS = { "available", "unavailable", "used" }

-- The plain returns of GetTrainerServiceInfo(i), at their positions, and
-- the service kind. Forever's returns differ from Classic Era's (Polestar
-- reads name, kind, _, _, rank), so the kind is found by value.
local function serviceLine(i, where)
  local r = try("GetTrainerServiceInfo", where, i)
  if not r then return nil end
  local raw, kind = {}, nil
  for k = 1, r.n do
    local v = clean(r[k])
    if v ~= nil then raw[k] = v end
    if type(v) == "string" and SERVICE_KINDS[v] and not kind then kind = v end
  end
  return { name = clean(r[1]), kind = kind, raw = raw }
end

local function trainerService(i, where)
  local line = serviceLine(i, where)
  if not line or line.kind == "header" then return nil end
  local service = line
  service.cost = clean(one("GetTrainerServiceCost", where, i))
  service.levelReq = clean(one("GetTrainerServiceLevelReq", where, i))
  local skill = try("GetTrainerServiceSkillReq", where, i)
  if skill and clean(skill[1]) then
    service.skill = { name = clean(skill[1]), rank = clean(skill[2]), met = clean(skill[3]) and true or false }
  end
  return service
end

-- All lines of the trainer list: { name, kind } per index.
local function trainerLines(where)
  local n = clean(one("GetNumTrainerServices", where))
  local lines = {}
  if type(n) ~= "number" then return lines end
  for i = 1, n do lines[i] = serviceLine(i, where) or {} end
  return lines
end

-- A header is collapsed when, with every filter on, the next line is another
-- header or the list ends. The return that says so has a different position
-- on Forever than on other clients, so the shape of the list decides.
local function collapsedHeaders(lines)
  local out = {}
  for i = 1, #lines do
    if lines[i].kind == "header" then
      local nextLine = lines[i + 1]
      if not nextLine or nextLine.kind == "header" then out[#out + 1] = i end
    end
  end
  return out
end

-- Shows every service type and expands collapsed sections while reading, so
-- unavailable, known and hidden services are recorded too, then puts the
-- trainer window back as it was: sections collapsed again, filters reset to
-- the booleans the client reported.
local function scanTrainer(event, row)
  local where = whereOf(event)
  local saved = {}
  local canFilter = type(api("GetTrainerServiceTypeFilter")) == "function"
    and type(api("SetTrainerServiceTypeFilter")) == "function"
  if canFilter then
    for _, f in ipairs(TRAINER_FILTERS) do
      local r = try("GetTrainerServiceTypeFilter", where, f)
      local on = r and r[1]
      if type(on) == "boolean" then
        -- Only a filter whose state was read is changed, so it can be put back.
        saved[f] = on
        if not on then try("SetTrainerServiceTypeFilter", where, f, true) end
      elseif r and not r.secret then   -- try() already recorded a secret
        fail("GetTrainerServiceTypeFilter", where, "nil", f .. " left as it was")
      end
    end
  else
    fail("SetTrainerServiceTypeFilter", where, "missing")
  end

  -- The collapsed test needs every filter on: with one left off, an open
  -- section whose services are all filtered out would look collapsed.
  local allShown = saved.available ~= nil and saved.unavailable ~= nil and saved.used ~= nil
  local collapsedNames = {}
  local ok, err = pcall(function()
    local collapsed = allShown and collapsedHeaders(trainerLines(where)) or {}
    if allShown then row.collapsedHeaders = #collapsed end
    if #collapsed > 0 then
      if type(api("ExpandTrainerSkillLine")) == "function" then
        -- From the end, so expanding a section never moves an earlier one.
        local lines = trainerLines(where)
        for k = #collapsed, 1, -1 do
          local i = collapsed[k]
          if lines[i] and lines[i].name then collapsedNames[lines[i].name] = true end
          try("ExpandTrainerSkillLine", where, i)
        end
      else
        fail("ExpandTrainerSkillLine", where, "missing", #collapsed .. " collapsed sections not read")
      end
    end
    local n = clean(one("GetNumTrainerServices", where))
    if type(n) ~= "number" then return end
    local services = {}
    for i = 1, n do
      local s = trainerService(i, where)
      if s then services[#services + 1] = s end
    end
    row.services = services
  end)
  if not ok then fail("scanTrainer", where, "error", err) end

  if next(collapsedNames) then
    local restored = pcall(function()
      local lines = trainerLines(where)
      for i = #lines, 1, -1 do
        if lines[i].kind == "header" and collapsedNames[lines[i].name] then
          try("CollapseTrainerSkillLine", where, i)
        end
      end
    end)
    if not restored then fail("CollapseTrainerSkillLine", where, "error") end
  end
  for f, on in pairs(saved) do
    if not on then try("SetTrainerServiceTypeFilter", where, f, false) end
  end
end

local function onTrainerShow(event)
  local where = whereOf(event)
  local row = stamp({ npc = interactionUnit(where) }, where)
  local trade = try("IsTradeskillTrainer", where)
  if trade then row.tradeskill = clean(trade[1]) and true or false end
  upsertByNpc(DB.trainers, row)
  -- Let the trainer window finish opening before its filters are touched.
  after(0.3, "TRAINER_SHOW+0.3s", function() scanTrainer("TRAINER_SHOW", row) end)
end

---------------------------------------------------------------------------
-- Quests
---------------------------------------------------------------------------

local offered    -- what QUEST_DETAIL showed: questID, npc, rewards, text
local completing -- what QUEST_COMPLETE showed: questID, npc, rewards

local function rewardList(kind, count, where)
  local list = {}
  for i = 1, count do
    local link = clean(one("GetQuestItemLink", where, kind, i))
    local info = try("GetQuestItemInfo", where, kind, i)   -- name, texture, count, ...
    local entry = { link = link, itemID = itemIDOf(link), quantity = info and clean(info[3]) }
    if not entry.link and info then entry.name = scrub(info[1], where) end
    list[#list + 1] = entry
  end
  return list
end

local function questRewards(where)
  local rewards = {
    money = clean(one("GetRewardMoney", where)),
    xp = clean(one("GetRewardXP", where)),
  }
  local n = clean(one("GetNumQuestRewards", where))
  if type(n) == "number" and n > 0 then rewards.items = rewardList("reward", n, where) end
  local c = clean(one("GetNumQuestChoices", where))
  if type(c) == "number" and c > 0 then rewards.choices = rewardList("choice", c, where) end
  return rewards
end

local function currentQuestID(where)
  local id = clean(one("GetQuestID", where))
  if type(id) == "number" and id > 0 then return id end
  return nil
end

local function onQuestDetail(event, startItemID)
  local where = whereOf(event)
  offered = {
    questID = currentQuestID(where),
    npc = interactionUnit(where),
    rewards = questRewards(where),
    objectivesText = scrub(one("GetObjectiveText", where), where),
    startItemID = clean(startItemID),
  }
  if offered.startItemID == 0 then offered.startItemID = nil end
end

local function onQuestComplete(event)
  local where = whereOf(event)
  completing = {
    questID = currentQuestID(where),
    npc = interactionUnit(where),
    rewards = questRewards(where),
  }
end

local function questTitle(questID, where)
  return scrub(one("C_QuestLog.GetTitleForQuestID", where, questID), where)
end

local function questObjectives(questID, where)
  local r = try("C_QuestLog.GetQuestObjectives", where, questID)
  local list = r and r[1]
  if type(list) ~= "table" then return nil end
  local out = {}
  for i = 1, #list do
    local o = list[i]
    if type(o) == "table" then
      out[#out + 1] = { text = scrub(o.text, where), type = clean(o.type), numRequired = clean(o.numRequired) }
    end
  end
  return #out > 0 and out or nil
end

-- QUEST_ACCEPTED carries (questID) on the modern client and
-- (questLogIndex, questID) on Classic Era; both are handled.
local function onQuestAccepted(event, a, b)
  local where = whereOf(event)
  local questID = clean(b) or clean(a)
  if type(questID) ~= "number" then
    fail(event .. " payload", where, (isSecret(a) or isSecret(b)) and "secret" or "nil")
    return
  end
  local row = stamp({ event = "accept", questID = questID, title = questTitle(questID, where) }, where)
  if offered and offered.questID == questID then
    row.npc = offered.npc
    row.rewards = offered.rewards
    row.objectivesText = offered.objectivesText
    row.startItemID = offered.startItemID
  end
  offered = nil
  row.objectives = questObjectives(questID, where)
  append(DB.quests, row)
  if not row.objectives then
    -- The quest log can lag the accept event; try once more.
    after(1.5, event .. "+1.5s", function()
      row.objectives = questObjectives(questID, event .. "+1.5s")
      row.title = row.title or questTitle(questID, event .. "+1.5s")
    end)
  end
end

local function onQuestTurnedIn(event, questID, xp, money)
  local where = whereOf(event)
  questID = clean(questID)
  if type(questID) ~= "number" then
    fail(event .. " payload", where, "nil", "questID")
    return
  end
  local row = stamp({ event = "turnin", questID = questID, title = questTitle(questID, where) }, where)
  if completing and completing.questID == questID then
    row.npc = completing.npc
    row.rewards = completing.rewards
  end
  completing = nil
  row.rewards = row.rewards or {}
  if type(clean(xp)) == "number" then row.rewards.xp = clean(xp) end
  if type(clean(money)) == "number" then row.rewards.money = clean(money) end
  append(DB.quests, row)
end

---------------------------------------------------------------------------
-- Saved table, session, slash command
---------------------------------------------------------------------------

local LISTS = { "loot", "encounters", "vendors", "trainers", "quests", "apiFailures" }

local function today()
  local ok, d = pcall(date, "%Y-%m-%d")
  return ok and d or nil
end

local function openDB()
  local saved = WowForeverCollectorDB
  if type(saved) ~= "table" or saved.schema ~= SCHEMA then
    saved = { schema = SCHEMA }
    WowForeverCollectorDB = saved
  end
  for _, name in ipairs(LISTS) do
    if type(saved[name]) ~= "table" then saved[name] = {} end
  end
  DB = saved

  for _, row in ipairs(DB.apiFailures) do
    if row.call and row.where and row.reason then failIndex[row.call .. "|" .. row.where .. "|" .. row.reason] = row end
  end
  for _, row in ipairs(DB.loot) do
    if row.source and row.source.spawnKey then
      seenSpawns[row.source.spawnKey .. "|" .. (row.lootKind or "corpse")] = true
    end
  end
  for _, f in ipairs(pendingFailures) do fail(f[1], f[2], f[3], f[4]) end
  pendingFailures = {}

  local s = type(DB.session) == "table" and DB.session or {}
  DB.session = s
  local r = try("GetBuildInfo", "ADDON_LOADED")   -- version, build, date, interface
  if r then
    s.build, s.buildNumber, s.buildDate, s.interface = clean(r[1]), clean(r[2]), clean(r[3]), clean(r[4])
    if s.build and s.buildNumber then
      local full = s.build .. "." .. s.buildNumber
      s.builds = s.builds or {}
      local known = false
      for _, b in ipairs(s.builds) do if b == full then known = true end end
      if not known then s.builds[#s.builds + 1] = full end
    end
  end
  s.addonVersion = VERSION
  s.locale = clean(one("GetLocale", "ADDON_LOADED"))
  s.firstDate = s.firstDate or today()
  s.lastDate = today()
  s.logins = (tonumber(s.logins) or 0) + 1
end

local function say(msg)
  print("|cff33ccffWoW Forever Collector|r: " .. msg)
end

local function status()
  local parts = {}
  for _, name in ipairs(LISTS) do parts[#parts + 1] = format("%s %d", name, #DB[name]) end
  say(table.concat(parts, ", "))
end

local function listFailures()
  if #DB.apiFailures == 0 then return say("no API failures recorded.") end
  for i, row in ipairs(DB.apiFailures) do
    if i > 20 then return say(format("... and %d more.", #DB.apiFailures - 20)) end
    say(format("%s at %s: %s x%d%s", row.call, row.where, row.reason, row.count,
      row.detail and (" (" .. row.detail .. ")") or ""))
  end
end

local function slash(msg)
  msg = type(msg) == "string" and msg:lower():gsub("^%s+", ""):gsub("%s+$", "") or ""
  if msg == "failures" then
    listFailures()
  elseif msg == "reset yes" then
    WowForeverCollectorDB = nil
    DB, failIndex, seenSpawns, lastKill = nil, {}, {}, nil
    openDB()
    say("all recorded data cleared.")
  elseif msg == "reset" then
    say("type /wfc reset yes to clear everything recorded so far.")
  else
    status()
    say("/wfc failures lists what the client hid; /wfc reset yes clears the data.")
  end
end

---------------------------------------------------------------------------
-- Events
---------------------------------------------------------------------------

local HANDLERS = {
  LOOT_READY = onLoot,
  LOOT_OPENED = onLoot,
  LOOT_CLOSED = function() windowHadSourceless = false; lastCast = nil end,
  UNIT_SPELLCAST_SUCCEEDED = onSpellcast,
  ENCOUNTER_START = onEncounter,
  ENCOUNTER_END = onEncounter,
  MERCHANT_SHOW = onMerchantShow,
  MERCHANT_UPDATE = onMerchantUpdate,
  MERCHANT_CLOSED = function() merchantOpen = false; merchantRow = nil end,
  TRAINER_SHOW = onTrainerShow,
  QUEST_DETAIL = onQuestDetail,
  QUEST_COMPLETE = onQuestComplete,
  QUEST_ACCEPTED = onQuestAccepted,
  QUEST_TURNED_IN = onQuestTurnedIn,
}

local frame = CreateFrame("Frame")

frame:SetScript("OnEvent", function(self, event, ...)
  if event == "ADDON_LOADED" then
    if ... ~= ADDON then return end
    self:UnregisterEvent("ADDON_LOADED")
    guarded("ADDON_LOADED", openDB)
    if not DB then return end
    for name in pairs(HANDLERS) do
      local ok, res
      if name == "UNIT_SPELLCAST_SUCCEEDED" and type(self.RegisterUnitEvent) == "function" then
        ok, res = pcall(self.RegisterUnitEvent, self, name, "player")
      else
        ok, res = pcall(self.RegisterEvent, self, name)
      end
      if not ok or res == false then fail("RegisterEvent", name, "missing", not ok and res or nil) end
    end
    SLASH_WOWFORECOLLECTOR1 = "/wfc"
    SlashCmdList.WOWFORECOLLECTOR = function(msg) guarded("/wfc", slash, msg) end
    return
  end
  local handler = HANDLERS[event]
  if handler and DB then guarded(event, handler, event, ...) end
end)

frame:RegisterEvent("ADDON_LOADED")
