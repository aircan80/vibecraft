import importlib.util, io, json, os, pathlib, tarfile, tempfile, unittest, zipfile
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
   (self.root/(label+'.json')).write_text(json.dumps(dict(version='0.4.1',source_commit='a'*40,target=target,archive=name,sha256=digest,game_data_bundled=False,native_smoke=dict(formatVersion=1,osName=('Linux' if target.startswith('linux') else 'Windows 2022' if target.startswith('windows') else 'Mac OS X'),osArch=('aarch64' if target=='macos-arm64' else 'amd64'),success=True,productVersion='0.4.1',marker='VIBECRAFT_PORTABLE_SELF_TEST_OK',javaFeature=21))))
  self.pom.unlink()
 def test_all_platform_set_and_tampered_archive(self):
  self.fixture_set();r.verify_set(self.root,'0.4.1','a'*40);next(self.root.glob('*.zip')).write_bytes(b'changed')
  with self.assertRaises(ValueError):r.verify_set(self.root,'0.4.1','a'*40)
 def test_extra_release_payload_rejected(self):
  self.fixture_set();(self.root/'unexpected.sqlite').write_bytes(b'db')
  with self.assertRaises(ValueError):r.verify_set(self.root,'0.4.1','a'*40)
 def test_platform_schema_and_commit_mismatch_rejected(self):
  self.fixture_set()
  with self.assertRaises(ValueError):r.verify_set(self.root,'0.4.1','b'*40)
  file=next(self.root.glob('*linux-x64.json')); original=json.loads(file.read_text())
  for key,value in [('formatVersion',2),('osName','Mac OS X'),('osArch','aarch64')]:
   broken=json.loads(json.dumps(original));broken['native_smoke'][key]=value;file.write_text(json.dumps(broken))
   with self.subTest(key=key),self.assertRaises(ValueError):r.verify_set(self.root,'0.4.1','a'*40)
 @unittest.skipIf(os.name=="nt", "POSIX executable bits and symlinks; Windows release uses ZIP")
 def test_tar_extract_preserves_launcher_mode_and_internal_link(self):
  source=self.root/'Vibecraft';source.mkdir();launcher=source/'launcher';launcher.write_text('native');launcher.chmod(0o755);(source/'alias').symlink_to('launcher')
  archive=self.root/'app.tar.gz'
  with tarfile.open(archive,'w:gz') as t:t.add(source,arcname='Vibecraft')
  dest=self.root/'space path';dest.mkdir();r.extract_archive(archive,dest,'Vibecraft')
  self.assertTrue(os.access(dest/'Vibecraft/launcher',os.X_OK));self.assertTrue((dest/'Vibecraft/alias').is_symlink())
 def test_archive_traversal_and_escaping_symlink_rejected(self):
  for name,link in [('Vibecraft/../../escape',None),('Vibecraft/alias','../../escape')]:
   archive=self.root/'bad.tar.gz'
   with tarfile.open(archive,'w:gz') as t:
    member=tarfile.TarInfo(name)
    if link:member.type=tarfile.SYMTYPE;member.linkname=link;t.addfile(member)
    else:member.size=1;t.addfile(member,io.BytesIO(b'x'))
   dest=self.root/('dest'+str(bool(link)));dest.mkdir()
   with self.assertRaises(ValueError):r.extract_archive(archive,dest,'Vibecraft')
 def test_windows_zip_round_trip_retains_native_files(self):
  archive=self.root/'windows.zip'
  entries={'Vibecraft/Vibecraft.exe':b'MZ-authored-launcher','Vibecraft/runtime/bin/java.dll':b'MZ-authored-runtime','Vibecraft/app/wow-forever-browser.jar':b'authored-jar'}
  with zipfile.ZipFile(archive,'w') as z:
   for name,payload in entries.items():z.writestr(name,payload)
  dest=self.root/'Windows path with spaces';dest.mkdir();r.extract_archive(archive,dest,'Vibecraft')
  for name,payload in entries.items():self.assertEqual((dest/name).read_bytes(),payload)
 def test_zip_traversal_rejected(self):
  archive=self.root/'bad.zip'
  with zipfile.ZipFile(archive,'w') as z:z.writestr('Vibecraft/../../escape','bad')
  dest=self.root/'dest';dest.mkdir()
  with self.assertRaises(ValueError):r.extract_archive(archive,dest,'Vibecraft')
if __name__=='__main__':unittest.main()
