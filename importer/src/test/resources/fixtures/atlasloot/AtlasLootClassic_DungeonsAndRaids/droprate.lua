local Droprate = _G.AtlasLoot.Data.Droprate
--##START-DATA##
local DropRateData = {
	[639] = {
		[2874] = 56.07,
		[5191] = 15.7,
		[5193] = 23.08,
		[5202] = 23.19,
		[10399] = 14.66
	},
	[11520] = {
		[14149] = 33.3,
		[14148] = 33.3,
		[14145] = 33.3
	}
}
--##END-DATA##
Droprate:AddData(DropRateData)
DropRateData = nil
