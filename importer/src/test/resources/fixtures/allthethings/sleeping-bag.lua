-- Exact q(...) rows from ATT commit a8c2fce5e8e52af3925352f5e61cbf7520ae3148.
-- Paths are .contrib/.db/forever/zones/{eastern kingdoms,kalimdor}/<file>.
-- MIT license: see LICENSE.att. No executable evaluation is performed.
return {
-- the barrens.lua:118
			q(79007, {	-- ... and that note you found (H)
				providers = {
					{ "o", 415106 },	-- Burned-Out Remains
					{ "o", 424010 },	-- Nailed Plank
				},
				coords = {
					{ 46.4, 73.9, THE_BARRENS },
					{ 37.5, 50.8, WESTFALL },
				},
				timeline = { TIMELINE.ADDED_1_60_1 },
				races = HORDE_ONLY,
				lvl = 14,
				groups = {
					i(2459),	-- Swiftness Potion
					i(3388),	-- Strong Troll's Blood Potion
				},
			}),
-- the barrens.lua:2075
			q(79192, {	-- Stepping Stones
				sourceQuests = {
					79008,	-- ... and that note you found (A)
					79007,	-- ... and that note you found (H)
				},
				providers = {
					{ "o", 417072 },	-- Nailed Plank
					{ "o", 424010 },	-- Nailed Plank
				},
				coords = {
					{ 46.4, 73.8, THE_BARRENS },
					{ 37.5, 50.8, WESTFALL },
				},
				timeline = { TIMELINE.ADDED_1_60_1 },
				maps = { STONETALON_MOUNTAINS },
				lvl = 14,
				groups = {
					i(3334),	-- Farmer's Shovel
					i(221498),	-- Sturdy Lunchbox
					i(1652),	-- Sturdy Lunchbox
					i(2901),	-- Mining Pick
					i(4470),	-- Simple Wood
					i(4471),	-- Flint and Tinder
				},
			}),
-- westfall.lua:64
			q(79008, {	-- ... and that note you found (A)
				providers = {
					{ "o", 415107 },	-- Burned-Out Remains
					{ "o", 417072 },	-- Nailed Plank
				},
				coords = {
					{ 37.5, 50.7, WESTFALL },
					{ 46.4, 73.8, THE_BARRENS },
				},
				timeline = { TIMELINE.ADDED_1_60_1 },
				races = ALLIANCE_ONLY,
				lvl = 14,
				groups = {
					i(2459),	-- Swiftness Potion
					i(3388),	-- Strong Troll's Blood Potion
				},
			}),
-- stonetalon mountains.lua:20
			q(79980, {	-- Scramble
				sourceQuest = 79192,	-- Stepping Stones
				provider = { "o", 424005 },	-- Pocket Litter
				coord = { 40.8, 52.5, STONETALON_MOUNTAINS },
				lvl = 14,
				timeline = { TIMELINE.ADDED_1_60_1 },
				groups = {
					i(216619, {	-- Student Fodder
						description = "This item gives you 4 bars of Rested Experience when consumed.",
					}),
					i(217314),	-- Moonsight Rifle
					i(217315),	-- Precision Bow
					i(3463),	-- Silver Star
					i(3464),	-- Feathered Arrow
					i(3465),	-- Exploding Shot
				},
			}),
-- stonetalon mountains.lua:51
			q(79974, {	-- Wet Job
				sourceQuest = 79980,	-- Scramble
				provider = { "o", 424012 },	-- Mound of Dirt
				coord = { 39.6, 49.9, STONETALON_MOUNTAINS },
				maps = { LOCH_MODAN },
				lvl = 14,
				timeline = { TIMELINE.ADDED_1_60_1 },
				groups = {
					i(20709),	-- Rumsey Rum Light
					i(5432),	-- Hickory Pipe
				},
			}),
-- loch modan.lua:378
			q(79975, {	-- Eagle's Fist
				sourceQuest = 79974,	-- Wet Job
				provider = { "o", 424007 },	-- Carved Figurine
				coord = { 49.5, 12.8, LOCH_MODAN },
				timeline = { TIMELINE.ADDED_1_60_1 },
				maps = { ARATHI_HIGHLANDS },
				lvl = 14,
			}),
-- arathi highlands.lua:728
			q(79976, {	-- This Must Be The Place
				sourceQuest = 79975,	-- Eagle's Fist
				providers = {
					{ "o", 406918 },	-- Messenger Bag
					{ "o", 424006 },	-- Hastily Rolled-Up Satchel
				},
				coord = { 22.5, 24.2, ARATHI_HIGHLANDS },
				timeline = { TIMELINE.ADDED_1_60_1 },
				lvl = 14,
				groups = {
					i(211527),	-- Cozy Sleeping Bag
					i(216619),	-- Student Fodder
				},
			}),
}
