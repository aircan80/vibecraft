import copy
import unittest
from equipment_recipe import equipment_recipe,choose_component
from customization_recipe import PRODUCT,BUILD,DECODER


def doc(name,rows):
    return dict(table=name,product=PRODUCT,build=BUILD,decoderCommit=DECODER,
                sourceSha256='a'*64,definitionSha256='b'*64,skippedEncryptedSections=[],rows=rows)


def inputs():
    return [doc('NPCModelItemSlotDisplayInfo',[dict(ID=1,NpcModelID=77,ItemSlot=0,ItemDisplayInfoID=10)]),
            doc('ItemDisplayInfo',[dict(ID=10,ModelResourcesID=[500,0],ModelMaterialResourcesID=[600,0],HelmetGeosetVis=[20,30],GeosetGroup=[0]*6)]),
            doc('TextureFileData',[dict(ID=900,FileDataID=900,MaterialResourcesID=600,UsageType=0)]),
            doc('ModelFileData',[dict(ID=800,ModelResourcesID=500),dict(ID=801,ModelResourcesID=500)]),
            doc('ComponentModelFileData',[dict(ID=800,RaceID=7,GenderIndex=0),dict(ID=801,RaceID=8,GenderIndex=0)]),
            doc('HelmetGeosetData',[dict(ID=2,HelmetGeosetVisDataID=20,RaceID=7,HideGeosetGroup=1),dict(ID=3,HelmetGeosetVisDataID=30,RaceID=7,HideGeosetGroup=2)])]


class EquipmentRecipeTest(unittest.TestCase):
    def recipe(self,values=None,**kw):
        values=values or inputs()
        return equipment_recipe(*values[:3],77,7,0,*values[3:],**kw)

    def test_exact_head_chain_does_not_authorize_transform_or_render(self):
        r=self.recipe();head=r['slots'][0]
        self.assertFalse(r['readyToRender'])
        self.assertEqual(head['modelDependencies'][0]['selection']['fileDataIds'],[800])
        self.assertEqual(head['textureDependencies'][0]['fileDataIds'],[900])
        self.assertEqual(head['attachment']['id'],11)
        self.assertEqual(head['helmetVisibility']['hideGroups'],[1])
        self.assertEqual(head['helmetVisibility']['selectedId'],20)
        self.assertEqual(head['helmetVisibility']['hideGeosetRanges'],[dict(first=101,last=199)])
        self.assertEqual(head['attachment']['transformState'],'UNRESOLVED_NATIVE_ANCHOR_AND_BONE')

    def test_partial_tables_require_explicit_flag_and_preserve_missing_records(self):
        values=inputs();values[0]['skippedEncryptedSections']=[dict(recordCount=6)]
        values[0]['inputCompleteness']=dict(erasedRanges=[dict(offset=40,length=10)])
        with self.assertRaisesRegex(ValueError,'incomplete encrypted'):self.recipe(values)
        r=self.recipe(values,allow_incomplete_prototype=True)
        self.assertEqual(r['state'],'INCOMPLETE_GEAR_PROTOTYPE')
        self.assertEqual(r['inputIncompleteness']['NPCModelItemSlotDisplayInfo']['inputCompleteness'],values[0]['inputCompleteness'])
        self.assertEqual(r['slots'][0]['helmetVisibility']['state'],'KNOWN_ROWS_ONLY')

    def test_wrong_build_rejected(self):
        values=inputs();values[1]['build']='1.15.9.70003'
        with self.assertRaises(ValueError):self.recipe(values)

    def test_missing_display_row_not_interpreted_as_naked(self):
        values=inputs();values[1]['rows']=[]
        self.assertIsNone(self.recipe(values)['slots'][0]['display'])
        self.assertIn('missing',self.recipe(values)['slots'][0]['blockers'][0])

    def test_ambiguous_native_component_is_not_first_fallback(self):
        values=inputs();values[4]['rows'][1]['RaceID']=7
        head=self.recipe(values)['slots'][0]
        self.assertEqual(head['modelDependencies'][0]['selection']['state'],'AMBIGUOUS')
        self.assertEqual(choose_component([800],{800:dict(RaceID=8,GenderIndex=0)},7,0)['state'],'MISSING_COMPONENT_MATCH')

    def test_gender_neutral_native_match_is_explicit_tier(self):
        result=choose_component([800],{800:dict(RaceID=7,GenderIndex=2)},7,0)
        self.assertEqual(result['tier'],1)
        self.assertEqual(result['fileDataIds'],[800])

    def test_missing_dependency_tables_are_blocked(self):
        r=equipment_recipe(*inputs()[:3],77,7,0)
        self.assertEqual(r['slots'][0]['modelDependencies'][0]['state'],'MISSING_MODEL_FILE_TABLE')
        self.assertEqual(r['slots'][0]['helmetVisibility']['state'],'UNRESOLVED')

    def test_duplicate_texture_resources_are_ambiguous(self):
        values=inputs();values[2]['rows'].append(dict(ID=901,FileDataID=901,MaterialResourcesID=600,UsageType=0))
        self.assertEqual(self.recipe(values)['slots'][0]['textureDependencies'][0]['state'],'MISSING_OR_AMBIGUOUS')


if __name__=='__main__':unittest.main()
