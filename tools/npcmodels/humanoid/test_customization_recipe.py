"""Synthetic joins exercise failures; no fixture is production appearance evidence."""
import copy
import unittest
from customization_recipe import BUILD, PRODUCT, DECODER, TABLES, build_recipe, texture_dependencies


def documents():
    docs = {n: dict(product=PRODUCT, build=BUILD, table=n, decoderCommit=DECODER,
                    sourceSha256='a'*64, definitionSha256='b'*64,
                    skippedEncryptedSections=[], rows=[]) for n in TABLES}
    data = {
        'CreatureDisplayInfoOption': [dict(ID=1, CreatureDisplayInfoExtraID=77, ChrCustomizationOptionID=5, ChrCustomizationChoiceID=11)],
        'ChrCustomizationOption': [dict(ID=5, ChrModelID=9)],
        'ChrCustomizationChoice': [dict(ID=i, ChrCustomizationOptionID=5) for i in (10,11)],
        'ChrCustomizationElement': [dict(ID=i, ChrCustomizationChoiceID=c, ChrCustomizationGeosetID=g) for i,c,g in ((1,10,100),(2,11,101))],
        'ChrCustomizationGeoset': [dict(ID=100, GeosetType=1, GeosetID=1, Modifier=-1), dict(ID=101, GeosetType=1, GeosetID=2, Modifier=-1)],
        'ChrModel': [dict(ID=9, CharComponentTextureLayoutID=55)],
        'ChrModelMaterial': [dict(ID=1, CharComponentTextureLayoutsID=55, TextureType=1), dict(ID=2, CharComponentTextureLayoutsID=66)]}
    for name, rows in data.items():
        docs[name]['rows'] = rows
    return docs


class CustomizationRecipeTest(unittest.TestCase):
    def recipe(self, docs=None):
        return build_recipe(docs or documents(), 77, [0,101,102,1701,3200,3501], 9)

    def test_selected_choice_disables_alternative_defaults_and_keeps_base(self):
        result = self.recipe()
        self.assertEqual({g['id']:g['checked'] for g in result['customizationGeosetMask']},
                         {0:True,101:False,102:True,1701:False,3200:True,3501:False})
        self.assertTrue(result['geometryResolved'])
        self.assertFalse(result['readyToRender'])
        self.assertIn('Equipment', result['blockers'][0])
        self.assertEqual(result['geosets'][0]['row']['ID'], 101)
        self.assertEqual(result['modelMaterials'], [documents()['ChrModelMaterial']['rows'][0]])
        self.assertEqual(result['provenance']['ChrModel']['sourceSha256'], 'a'*64)

    def test_wrong_build_rejected(self):
        docs=documents(); docs['ChrModel']['build']='1.15.9.70003'
        with self.assertRaisesRegex(ValueError,'wrong product/build'): self.recipe(docs)

    def test_missing_selected_choice_blocks_instead_of_default(self):
        docs=documents(); docs['ChrCustomizationChoice']['rows'].pop()
        result=self.recipe(docs)
        self.assertFalse(result['geometryResolved'])
        self.assertTrue(any('Missing or mismatched selected choice' in b for b in result['blockers']))

    def test_unknown_extra_does_not_guess_defaults(self):
        result=build_recipe(documents(), 999, [0,101], 9)
        self.assertFalse(result['geometryResolved'])
        self.assertEqual(result['selected'], [])

    def test_missing_options_table_is_not_empty_options(self):
        docs=documents();del docs['CreatureDisplayInfoOption']
        with self.assertRaises(KeyError): self.recipe(docs)

    def test_conditional_and_attached_model_fail_closed_with_raw_ids(self):
        for field in ('RelatedChrCustomizationChoiceID','ChrCustomizationCondModelID','ChrCustomizationSkinnedModelID','ChrCustomizationMaterialID'):
            with self.subTest(field=field):
                docs=documents();docs['ChrCustomizationElement']['rows'][1][field]=123
                result=self.recipe(docs)
                self.assertFalse(result['geometryResolved'])
                self.assertEqual(result['elements'][0][field],123)

    def test_missing_alternative_geoset_blocks_mask(self):
        docs=documents(); docs['ChrCustomizationGeoset']['rows'].pop(0)
        self.assertFalse(self.recipe(docs)['geometryResolved'])

    def test_encrypted_sections_rejected(self):
        docs=documents();docs['ChrModel']['skippedEncryptedSections']=[{'id':1}]
        with self.assertRaisesRegex(ValueError,'incomplete encrypted'): self.recipe(docs)

    def test_duplicate_selected_option_is_ambiguous(self):
        docs=documents();r=copy.deepcopy(docs['CreatureDisplayInfoOption']['rows'][0]);r['ID']=2
        docs['CreatureDisplayInfoOption']['rows'].append(r)
        self.assertFalse(self.recipe(docs)['geometryResolved'])

    def test_no_model_or_actual_geosets_never_geometry_ready(self):
        result=build_recipe(documents(),77)
        self.assertFalse(result['geometryResolved'])
        self.assertIsNone(result['customizationGeosetMask'])

    def test_wrong_model_and_geoset_modifier_block(self):
        docs=documents(); docs['ChrCustomizationOption']['rows'][0]['ChrModelID']=77
        docs['ChrCustomizationGeoset']['rows'][1]['Modifier']=2
        result=self.recipe(docs)
        self.assertFalse(result['geometryResolved'])
        self.assertTrue(any('another ChrModel' in b for b in result['blockers']))
        self.assertTrue(any('Modifier=2' in b for b in result['blockers']))

    def test_duplicate_geoset_elements_block_last_row_wins_guess(self):
        docs=documents(); docs['ChrCustomizationElement']['rows'].append(dict(ID=9, ChrCustomizationChoiceID=11, ChrCustomizationGeosetID=100))
        self.assertFalse(self.recipe(docs)['geometryResolved'])


    def test_explicit_incomplete_prototype_preserves_evidence_and_never_ready(self):
        docs=documents()
        missing=dict(state='PARTIAL_AUTHENTICATED_CHUNKS', erasedRanges=[dict(offset=12,length=4,keyName='unknown')], skippedSections=[dict(fileOffset=12,recordCount=9)])
        docs['CreatureDisplayInfoOption']['inputCompleteness']=missing
        docs['CreatureDisplayInfoOption']['skippedEncryptedSections']=[dict(recordCount=9)]
        result=build_recipe(docs,77,[0,101,102],9,allow_incomplete_prototype=True)
        self.assertEqual(result['state'],'INCOMPLETE_RECONSTRUCTION_PROTOTYPE')
        self.assertEqual(result['incompleteness']['CreatureDisplayInfoOption']['inputCompleteness'],missing)
        self.assertFalse(result['readyToRender'])
        self.assertFalse(result['geometryResolved'])
        self.assertEqual(len(result['selected']),1)

    def test_partial_prototype_requires_missing_section_evidence(self):
        docs=documents();docs['CreatureDisplayInfoOption']['skippedEncryptedSections']=[dict(recordCount=9)]
        with self.assertRaisesRegex(ValueError,'missing incompleteness evidence'):
            build_recipe(docs,77,allow_incomplete_prototype=True)

    def test_texture_join_uses_related_choice_and_layout_target_not_body_fallback(self):
        recipe=self.recipe()
        recipe['elements']=[dict(ID=1,ChrCustomizationMaterialID=5,RelatedChrCustomizationChoiceID=11),
                            dict(ID=2,ChrCustomizationMaterialID=6,RelatedChrCustomizationChoiceID=10)]
        def doc(name,rows):
            result=copy.deepcopy(documents()['ChrModel']);result.update(table=name,rows=rows);return result
        mats=doc('ChrCustomizationMaterial',[dict(ID=5,ChrModelTextureTargetID=10,MaterialResourcesID=700)])
        layers=doc('ChrModelTextureLayer',[dict(ID=1,CharComponentTextureLayoutsID=55,ChrModelTextureTargetID=[10,0],TextureType=6)])
        textures=doc('TextureFileData',[dict(ID=1,MaterialResourcesID=700,UsageType=0,FileDataID=800),dict(ID=2,MaterialResourcesID=700,UsageType=1,FileDataID=999)])
        result=texture_dependencies(recipe,mats,layers,textures)
        self.assertEqual(len(result['dependencies']),1)
        dep=result['dependencies'][0]
        self.assertEqual(dep['textureType'],6)
        self.assertEqual(dep['textureFileDataIds'],[800])
        self.assertEqual(dep['state'],'UNIQUE_DEPENDENCY')
        self.assertFalse(result['readyToRender'])
        textures['rows'].append(dict(ID=3,MaterialResourcesID=700,UsageType=0,FileDataID=801))
        self.assertEqual(texture_dependencies(recipe,mats,layers,textures)['dependencies'][0]['state'],'MISSING_OR_AMBIGUOUS')


if __name__=='__main__': unittest.main()
