import importlib.util, json, pathlib, tempfile, unittest
spec=importlib.util.spec_from_file_location('source_release',pathlib.Path(__file__).with_name('source_release.py'))
r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)
class SourceReleaseTest(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=pathlib.Path(self.temp.name)
  self.pom=self.root/'pom.xml';self.pom.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><version>0.4.1-SNAPSHOT</version></project>')
 def test_valid_semver_and_separate_mac_version(self):
  self.assertEqual(r.version('0.4.1-preview.2+build.4',self.pom),dict(version='0.4.1-preview.2+build.4',native='0.4.1',mac_native='1.4.1',prerelease='true'))
 def test_untrusted_versions_and_mismatched_tag_rejected(self):
  for v in ['v0.4.1','0.04.1','0.4.1-01','0.4.1;echo nope','0.4.1\nx=y','../0.4.1','0.4.2','999999.4.1','0.4.1-'+('a'*150)]:
   with self.subTest(v=v),self.assertRaises(ValueError):r.version(v,self.pom)
 def test_asset_or_tool_rejected(self):
  image=self.root/'image';image.mkdir();(image/'wow-forever-browser.jar').write_bytes(b'fixture')
  self.assertEqual(r.inspect_image(image).name,'wow-forever-browser.jar')
  (image/'wowforever.sqlite').write_bytes(b'db')
  with self.assertRaises(ValueError):r.inspect_image(image)
 def fixture_set(self):
  for target in r.TARGETS:
   label=f'vibecraft-0.4.1-{target}';name=label+('.zip' if target=='windows-x64' else '.tar.gz');a=self.root/name;a.write_bytes(target.encode());digest=r.sha(a)
   (self.root/(label+'.sha256')).write_text(f'{digest}  {name}\n')
   (self.root/(label+'.json')).write_text(json.dumps(dict(version='0.4.1',target=target,archive=name,sha256=digest,game_data_bundled=False,native_smoke=dict(success=True,productVersion='0.4.1',marker='VIBECRAFT_PORTABLE_SELF_TEST_OK',javaFeature=21))))
  self.pom.unlink()
 def test_all_platform_set_and_tampered_archive(self):
  self.fixture_set();r.verify_set(self.root,'0.4.1');next(self.root.glob('*.zip')).write_bytes(b'changed')
  with self.assertRaises(ValueError):r.verify_set(self.root,'0.4.1')
 def test_extra_release_payload_rejected(self):
  self.fixture_set();(self.root/'unexpected.sqlite').write_bytes(b'db')
  with self.assertRaises(ValueError):r.verify_set(self.root,'0.4.1')
if __name__=='__main__':unittest.main()
