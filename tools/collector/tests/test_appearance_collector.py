"""Synthetic API contracts only; requires pip install lupa. No client acceptance implied."""
import unittest
from pathlib import Path
from lupa import LuaRuntime
CORE = Path(__file__).parents[1] / 'WowForeverAppearanceCollector/Core.lua'
class AppearanceCollectorTest(unittest.TestCase):
    def runtime(self):
        l=LuaRuntime(unpack_returned_tuples=True)
        l.execute('''
        SlashCmdList = {}; C_Timer = {}; timers = {}; frames = {}; messages = {}
        function print(s) messages[#messages+1] = s end
        function GetBuildInfo() return "1.60.1", "70450" end
        guid = "Creature-0-1-2-3-247003-0000000001"; display = 7937
        function UnitGUID() return guid end
        function GetServerTime() return 12345 end
        function issecretvalue(v) return v == "SECRET" end
        function C_Timer.After(delay, callback) timers[#timers+1] = callback end
        function CreateFrame(kind)
          local f = {}
          function f:RegisterEvent() end; function f:UnregisterEvent() end
          function f:SetScript(_, cb) self.event = cb end
          function f:SetSize() end; function f:SetPoint() end; function f:SetAlpha() end
          function f:ClearModel() end; function f:Show() end; function f:Hide() end
          function f:SetUnit() end; function f:GetDisplayInfo() return display end
          frames[#frames+1] = f; return f
        end
        ''')
        l.execute('local run = assert(load(...)); run("WowForeverAppearanceCollector")',CORE.read_text())
        l.execute('frames[1].event(frames[1], "ADDON_LOADED", "WowForeverAppearanceCollector")')
        return l
    def test_valid_observation_no_identity(self):
        l=self.runtime(); l.execute('SlashCmdList.FOREVERAPPEARANCE(); timers[1]()')
        row=l.globals().WowForeverAppearanceCollectorDB.observations[1]
        self.assertEqual(row.npcId,247003);self.assertEqual(row.displayId,7937)
        self.assertEqual(row.scope,'observed-instance');self.assertIsNone(row.guid);self.assertIsNone(row.name)
    def test_wrong_build_rejected(self):
        l=self.runtime();l.execute('function GetBuildInfo() return "1.15.9", "70450" end; SlashCmdList.FOREVERAPPEARANCE()')
        self.assertEqual(len(l.globals().timers),0)
    def test_target_change_discards(self):
        l=self.runtime();l.execute('SlashCmdList.FOREVERAPPEARANCE(); guid="Creature-0-1-2-3-2671-0000000002"; timers[1]()')
        self.assertEqual(len(l.globals().WowForeverAppearanceCollectorDB.observations),0)
    def test_secret_display_discards(self):
        l=self.runtime();l.execute('SlashCmdList.FOREVERAPPEARANCE(); display="SECRET"; timers[1]()')
        self.assertEqual(len(l.globals().WowForeverAppearanceCollectorDB.observations),0)
    def test_overlapping_capture_only_current_finishes(self):
        l=self.runtime();l.execute('SlashCmdList.FOREVERAPPEARANCE(); SlashCmdList.FOREVERAPPEARANCE(); timers[1](); timers[2]()')
        self.assertEqual(len(l.globals().WowForeverAppearanceCollectorDB.observations),1)
    def test_duplicate_preserved_once(self):
        l=self.runtime();l.execute('SlashCmdList.FOREVERAPPEARANCE(); timers[1](); SlashCmdList.FOREVERAPPEARANCE(); timers[2]()')
        self.assertEqual(len(l.globals().WowForeverAppearanceCollectorDB.observations),1)
    def test_model_rejects_setunit(self):
        l=self.runtime();l.execute('local old=CreateFrame; function CreateFrame(kind) local f=old(kind); function f:SetUnit() return false end; return f end; SlashCmdList.FOREVERAPPEARANCE()')
        self.assertEqual(len(l.globals().timers),0)
    def test_secret_identity_discards(self):
        l=self.runtime();l.execute('guid="SECRET"; SlashCmdList.FOREVERAPPEARANCE()')
        self.assertEqual(len(l.globals().timers),0)
    def test_players_not_captured(self):
        l=self.runtime();l.execute('guid="Player-1-12345"; SlashCmdList.FOREVERAPPEARANCE()')
        self.assertEqual(len(l.globals().timers),0)
if __name__=='__main__':unittest.main()
