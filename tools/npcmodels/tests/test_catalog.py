import csv
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from build_appearance_catalog import build

class CatalogTest(unittest.TestCase):
    def fixture(self,root):
        rows={
            'Creature':[
                {'ID':'1','Name_lang':'First','DisplayID_0':'10','DisplayID_1':'11','AlwaysItem_0':'0'},
                {'ID':'2','Name_lang':'Shared','DisplayID_0':'10','DisplayID_1':'0','AlwaysItem_0':'0'},
                {'ID':'3','Name_lang':'Equipped','DisplayID_0':'10','DisplayID_1':'0','AlwaysItem_0':'90'},
                {'ID':'4','Name_lang':'Missing','DisplayID_0':'12','DisplayID_1':'0','AlwaysItem_0':'0'}],
            'CreatureDisplayInfo':[
                {'ID':'10','ModelID':'20','CreatureModelScale':'1.5','CreatureModelAlpha':'255','ExtendedDisplayInfoID':'0','TextureVariationFileDataID_0':'8000','TextureVariationFileDataID_2':'9000'},
                {'ID':'11','ModelID':'20','CreatureModelScale':'1','CreatureModelAlpha':'255','ExtendedDisplayInfoID':'30','TextureVariationFileDataID_0':'0','TextureVariationFileDataID_2':'0'}],
            'CreatureModelData':[{'ID':'20','FileDataID':'7000','ModelScale':'2'}],
            'CreatureDisplayInfoExtra':[{'ID':'30','DisplayRaceID':'1'}]}
        requests=[]
        for table,values in rows.items():
            path=root/(table+'.csv');columns=list(values[0])
            with path.open('w',newline='') as f:
                writer=csv.DictWriter(f,fieldnames=columns);writer.writeheader();writer.writerows(values)
            requests.append(dict(table=table,status=200,url='https://source.invalid/'+table,sha256=hashlib.sha256(path.read_bytes()).hexdigest(),row_count=len(values),columns=columns))
        attestation=root/'attestation.json';attestation.write_text(json.dumps(dict(product='wow_classic_beta',build='1.60.1.70450',locale='enUS',requests=requests)))
        return attestation
    def test_every_variant_identity_and_unsupported_reason_survive(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);attestation=self.fixture(root);result=build(root,attestation);npcs=result['appearances']
            self.assertEqual(npcs['1']['state'],'PARTIAL')
            self.assertEqual([v['displayId']for v in npcs['1']['variants']],[10,11])
            first=npcs['1']['variants'][0]
            self.assertEqual(first['textures'],[8000,0,9000,0])
            self.assertEqual((first['displayScale'],first['modelScale']),(1.5,2.0))
            self.assertEqual(first['appearanceKey'],npcs['2']['variants'][0]['appearanceKey'])
            self.assertNotEqual(first['appearanceKey'],npcs['3']['variants'][0]['appearanceKey'])
            self.assertEqual(npcs['3']['state'],'UNSUPPORTED')
            self.assertEqual(npcs['4']['state'],'UNRESOLVED')
            self.assertEqual(first['evidence'][0]['sourceLine'],2)
            self.assertEqual(result,build(root,attestation))
    def test_tampered_native_bytes_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);attestation=self.fixture(root)
            with (root/'Creature.csv').open('a')as f:f.write('\n')
            with self.assertRaisesRegex(ValueError,'Unattested source bytes'):build(root,attestation)

if __name__=='__main__':unittest.main()
