---@class QuestieItemDropCorrections
local QuestieItemDropCorrections = QuestieLoader:CreateModule("QuestieItemDropCorrections")
-------------------------
--Import modules.
-------------------------
---@type DropDB
local DropDB = QuestieLoader:ImportModule("DropDB")

local DropKeys = DropDB.correctionKeys

-- These tables contain manual drop corrections.
--
-- Use the following format:
-- [itemID] = { -- Item Name
--    [npcID] = 80.0,
-- }
--
-- This example would be an 80% drop rate.
--
-- Corrections propagate up to the current expansion. For instance, if you make a correction in Era, that
-- correction will be loaded in MoP unless an expansion after Era made a different correction for the same ID.
--
-- This is the same way our normal database corrections system behaves as well.
--
-- You can also use reference corrections. For instance:
--
-- [itemID] = { -- Item Name
--    [npcID] = DropDB.correctionKeys.WOWHEAD,
-- }
--
-- will point to the currently loaded Wowhead data for that item:NPC pair, if it exists.
-- Using a reference correction in an earlier expansion will propagate to later ones, pointing to their own DBs.
-- For instance, if you put the above example into Era corrections, but loaded up MoP, it would show MoP Wowhead data,
-- regardless of whatever data exists in the MoP pserver DB.

QuestieItemDropCorrections.Era = {
    [725] = { -- Gnoll Paw
        [98] = DropKeys.WOWHEAD,
        [117] = DropKeys.WOWHEAD,
        [123] = DropKeys.WOWHEAD,
        [124] = DropKeys.WOWHEAD,
        [125] = DropKeys.WOWHEAD,
        [452] = DropKeys.WOWHEAD,
        [453] = DropKeys.WOWHEAD,
        [500] = DropKeys.WOWHEAD,
        [501] = DropKeys.WOWHEAD,
        [506] = DropKeys.WOWHEAD,
        [1065] = DropKeys.WOWHEAD,
        [1426] = DropKeys.WOWHEAD,
    },
    [2874] = { -- An Unsent Letter
        [639] = 60.0,
    },
}

QuestieItemDropCorrections.Tbc = { -- Corrections for Old World data should only be put here if they change going from Era->TBC! Otherwise, put them in the Era section!

}

QuestieItemDropCorrections.Wotlk = {

}

QuestieItemDropCorrections.Cata = {

}

QuestieItemDropCorrections.MoP = {

}
