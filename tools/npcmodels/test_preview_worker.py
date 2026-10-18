import copy
import unittest
from preview_worker import validate

class PreviewRequestTest(unittest.TestCase):
    def setUp(self):
        self.request=dict(product='wow_classic_beta',build='1.60.1.70450',npcId=2671,displayId=7937,modelId=134,modelFileDataId=126061,textures=[126062,0,0],displayScale=1.3,appearanceKey='native-appearance')
    def test_real_request_preserves_texture_slots(self):
        self.assertEqual(validate(self.request)['textures'],[126062,0,0])
    def test_missing_identity_and_invalid_parameters_fail_before_tools_or_network(self):
        for key,value in [('build',''),('product',None),('npcId',True),('modelFileDataId',-1),('textures',[1,-2]),('displayScale',float('nan')),('modelScale',0)]:
            with self.subTest(key=key):
                request=copy.deepcopy(self.request);request[key]=value
                with self.assertRaises(ValueError):validate(request)

if __name__=='__main__':unittest.main()
