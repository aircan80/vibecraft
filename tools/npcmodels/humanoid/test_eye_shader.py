import copy
import unittest
from eye_shader import ALPHA_TEST, mod_add, sample_mod_add, validate_eye_contract

class EyeShaderTest(unittest.TestCase):
    def test_adds_second_rgb_without_clamping_hdr(self):
        self.assertEqual(mod_add((.8,.2,.1,.3),(.7,.1,.2,.3)),(1.5,.30000000000000004,.30000000000000004,1.))
    def test_alpha_sum_cutout_is_not_alpha_over(self):
        self.assertIsNone(mod_add((1,1,1,.25),(1,1,1,.25)))
        self.assertEqual(mod_add((1,1,1,ALPHA_TEST),(1,1,1,0))[-1],1.)
    def test_native_opacity_is_mesh_alpha_not_sum(self):
        self.assertEqual(mod_add((1,1,1,1),(1,1,1,1),mesh=(1,1,1,.4))[-1],.4)
    def test_lighting_affects_diffuse_not_additive(self):
        self.assertEqual(mod_add((.2,.4,.6,1),(.1,.2,.3,1),lighting=(0,0,0)),(.1,.2,.3,1.))
    def test_distinct_native_uv_sets_are_sampled(self):
        calls=[]
        def sample(uv):
            calls.append(uv);return (uv[0],uv[1],0.,1.)
        self.assertEqual(sample_mod_add(sample,(.1,.2),(.8,.6)),(.9,.8,0.,1.))
        self.assertEqual(calls,[(.1,.2),(.8,.6)])
    def test_native_contract_retains_uv2_and_flags(self):
        x=dict(subMeshes=[dict(submeshID=3301)],textureUnits=[dict(skinSectionIndex=0,materialIndex=0,shaderID=16403,textureCount=2,textureComboIndex=0)],materials=[dict(blendingMode=1,flags=0)],textureCombos=[0,0],textureTypes=[19])
        self.assertEqual(validate_eye_contract(x)['uvSets'],[0,1])
        for key,value in [('shaderID',16402),('textureCount',1)]:
            bad=copy.deepcopy(x);bad['textureUnits'][0][key]=value
            with self.assertRaises(ValueError):validate_eye_contract(bad)
        x['materials'][0]['flags']=4
        with self.assertRaises(ValueError):validate_eye_contract(x)

if __name__=='__main__':unittest.main()
