-- Experimental manual capture only: /wfappearance while targeting an NPC.
-- Observed instance appearance is evidence, never the NPC's canonical default.
local ADDON = ...
local EXPECTED_VERSION, EXPECTED_BUILD = "1.60.1", "70450"
local generation, model = 0, nil
local function plain(v)
  if type(issecretvalue) == "function" then
    local ok, secret = pcall(issecretvalue, v)
    if not ok or secret then return nil end
  end
  if type(v) == "string" or type(v) == "number" or type(v) == "boolean" then return v end
end
local function call(fn, ...)
  if type(fn) ~= "function" then return nil end
  local ok, value = pcall(fn, ...)
  if ok then return plain(value) end
end
local function say(text) print("Forever appearances: " .. text) end
local function capture()
  generation = generation + 1
  local current = generation
  if type(GetBuildInfo) ~= "function" then return say("GetBuildInfo unavailable.") end
  local ok, version, build = pcall(GetBuildInfo)
  version, build = plain(version), plain(build)
  if not ok or version ~= EXPECTED_VERSION or build ~= EXPECTED_BUILD then
    return say("Only verified Forever 1.60.1.70450 is supported; nothing captured.")
  end
  local guid = call(UnitGUID, "target")
  if type(guid) ~= "string" then return say("Target identity unavailable or secret.") end
  local kind, id = guid:match("^(%a+)%-[^%-]+%-[^%-]+%-[^%-]+%-[^%-]+%-(%d+)%-")
  id = tonumber(id)
  if (kind ~= "Creature" and kind ~= "Vehicle") or not id or id <= 0 then return say("Target an NPC.") end
  if type(C_Timer) ~= "table" or type(C_Timer.After) ~= "function" then return say("Timer unavailable.") end
  if not model then
    local made, frame = pcall(CreateFrame, "PlayerModel", nil, UIParent)
    if not made then return say("PlayerModel unavailable.") end
    model = frame
    model:SetSize(1, 1); model:SetPoint("BOTTOMLEFT"); model:SetAlpha(0)
  end
  local loaded, accepted = pcall(function() model:ClearModel(); model:Show(); return model:SetUnit("target") end)
  if not loaded or plain(accepted) == false then model:Hide(); return say("Target model restricted or unavailable.") end
  C_Timer.After(1, function()
    if current ~= generation then return end
    local same = call(UnitGUID, "target")
    local display = call(model.GetDisplayInfo, model)
    model:Hide()
    if same ~= guid then return say("Target changed; observation discarded.") end
    if type(display) ~= "number" or display <= 0 or display % 1 ~= 0 then
      return say("Display ID unavailable or secret; nothing captured.")
    end
    local db = WowForeverAppearanceCollectorDB
    if type(db) ~= "table" or db.schema ~= 1 then return say("Storage unavailable.") end
    local key = id .. ":" .. display
    for _, row in ipairs(db.observations) do
      if row.key == key then return say("Appearance already recorded.") end
    end
    if #db.observations >= 20000 then return say("Observation limit reached.") end
    db.observations[#db.observations + 1] = {
      key = key, npcId = id, displayId = display, product = "wow_classic_beta",
      build = "1.60.1.70450", source = "PlayerModel:SetUnit(target)/GetDisplayInfo",
      scope = "observed-instance", observedAt = call(GetServerTime),
    }
    say("Recorded NPC " .. id .. " display " .. display .. ". /reload saves to disk.")
  end)
end
local frame = CreateFrame("Frame")
frame:RegisterEvent("ADDON_LOADED")
frame:SetScript("OnEvent", function(self, event, addon)
  if addon ~= ADDON then return end
  self:UnregisterEvent("ADDON_LOADED")
  if WowForeverAppearanceCollectorDB == nil then
    WowForeverAppearanceCollectorDB = { schema = 1, observations = {} }
  end
  SLASH_FOREVERAPPEARANCE1 = "/wfappearance"
  SlashCmdList.FOREVERAPPEARANCE = function()
    local ok = pcall(capture)
    if not ok then if model then pcall(model.Hide, model) end; say("Capture failed safely; nothing inferred.") end
  end
end)
