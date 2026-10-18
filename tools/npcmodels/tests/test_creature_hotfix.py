import importlib.util
from pathlib import Path
import struct
import unittest
FILE=Path(__file__).resolve().parents[1]/'audit_creature_hotfix.py'
spec=importlib.util.spec_from_file_location('audit_creature_hotfix',FILE);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

DEFINITION='''COLUMNS
int ID
int CreatureDisplayInfoID
float Probability
float Scale
int OrderIndex
int Field_11_0_0_54210_004
int CreatureID

BUILD 1.60.1.70450
$noninline,id$ID<32>
CreatureDisplayInfoID<32>
Probability
Scale
OrderIndex<u8>
Field_11_0_0_54210_004<u16>
$noninline,relation$CreatureID<32>
'''

def cache(events,build=70450):
    result=struct.pack('<4sII',b'XFTH',9,build)+bytes(32)
    for push,status,display,npc in events:
        raw=struct.pack('<iffBHi',display,0,1,0,0,npc) if status==1 else b''
        result+=struct.pack('<4siiIIIIB3s',b'XFTH',70,push,1,module.table_hash(module.TABLE),690799,len(raw),status,bytes(3))+raw
    return result

class CreatureHotfixTest(unittest.TestCase):
    def test_explicit_native_identity_and_zero_probability_are_preserved(self):
        result=module.audit(cache([(100,1,8289,278644)]),DEFINITION,'1.60.1.70450',baseline=[],targets=[246,278644])
        self.assertEqual(result['targets']['246'],[])
        row=result['targets']['278644'][0];self.assertEqual(row['CreatureDisplayInfoID'],8289);self.assertEqual(row['Probability'],0)
        self.assertTrue(row['new_vs_baseline']);self.assertEqual(row['source_push'],100)
    def test_invalidated_push_restores_previous_and_tombstone_removes_evidence(self):
        result=module.audit(cache([(100,1,8289,278644),(101,1,9000,278644),(101,3,0,0)]),DEFINITION,'1.60.1.70450')
        self.assertEqual(result['mappings'][0]['CreatureDisplayInfoID'],8289)
        result=module.audit(cache([(100,1,8289,278644),(100,2,0,0)]),DEFINITION,'1.60.1.70450')
        self.assertEqual(result['mappings'],[])
    def test_wrong_build_or_relation_layout_fails_closed(self):
        with self.assertRaises(ValueError):module.audit(cache([],70003),DEFINITION,'1.60.1.70450')
        with self.assertRaises(ValueError):module.audit(cache([]),DEFINITION.replace('$noninline,relation$CreatureID<32>','CreatureID<32>'),'1.60.1.70450')

if __name__=='__main__':unittest.main()
