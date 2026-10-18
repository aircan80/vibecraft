import importlib.util,pathlib,unittest
s=importlib.util.spec_from_file_location('plan',pathlib.Path(__file__).with_name('plan-materials.py'));m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
class MaterialPlanTest(unittest.TestCase):
 def inputs(self):
  base={'product':m.PRODUCT,'build':m.BUILD}
  c={**base,'catalogId':'test','appearances':{'246':{'npcId':246,'variants':[{'displayId':10,'evidence':[{'table':'CreatureDisplayInfoExtra','payload':{'ID':'20','BakeMaterialResourcesID':'30','HDBakeMaterialResourcesID':'31'}}]}]}}}
  t={**base,'table':'TextureFileData','sourceSha256':'abc','rows':[{'UsageType':0,'MaterialResourcesID':30,'FileDataID':100},{'UsageType':1,'MaterialResourcesID':30,'FileDataID':999}]}
  p={**base,'file_data_id':982459,'exact_root_membership_verified':True,'sha256':'abc','build_config_md5':'84bea4233331e390fda8724c6f91ffa5','root_content_md5':'8a0ca94be2f6ace93991c04ca6e19b57'}
  return c,t,p
 def test_usage_and_missing(self):
  out=m.plan(*self.inputs())['appearances'][0];self.assertEqual(out['materials'][0]['fileDataIds'],[100]);self.assertEqual(out['materials'][1]['state'],'MISSING');self.assertEqual(out['state'],'DEPENDENCY_PLAN_ONLY')
 def test_ambiguous_not_arbitrarily_selected(self):
  c,t,p=self.inputs();t['rows'].append({'UsageType':0,'MaterialResourcesID':30,'FileDataID':101});self.assertEqual(m.plan(c,t,p)['appearances'][0]['materials'][0]['state'],'AMBIGUOUS')
 def test_wrong_build(self):
  c,t,p=self.inputs();t['build']='1.15.9.70003'
  with self.assertRaises(ValueError):m.plan(c,t,p)
 def test_unverified(self):
  c,t,p=self.inputs();p['exact_root_membership_verified']=False
  with self.assertRaises(ValueError):m.plan(c,t,p)
 def test_decode_proof_mismatch(self):
  c,t,p=self.inputs();t['sourceSha256']='wrong'
  with self.assertRaises(ValueError):m.plan(c,t,p)
if __name__=='__main__':unittest.main()
