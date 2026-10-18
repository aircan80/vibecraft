import hashlib
import subprocess
import tempfile
import unittest
from pathlib import Path
from export_source import export

class ExportTest(unittest.TestCase):
    def test_exact_reviewed_revision_without_history(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); repo = root / 'repo'; repo.mkdir()
            subprocess.run(['git','init','-q',str(repo)],check=True)
            (repo/'hello.py').write_text('print("synthetic")\n')
            subprocess.run(['git','-C',str(repo),'add','.'],check=True)
            subprocess.run(['git','-C',str(repo),'-c','user.name=Test','-c','user.email=test@example.invalid','commit','-qm','synthetic'],check=True)
            revision = subprocess.check_output(['git','-C',str(repo),'rev-parse','HEAD']).decode().strip()
            entry={'path':'hello.py','approved':True,'review_note':'Invented example', 'sha256':hashlib.sha256((repo/'hello.py').read_bytes()).hexdigest()}
            manifest={'revision':revision,'files':[entry]}
            (repo/'hello.py').write_text('uncommitted secret')
            export(repo,revision,manifest,root/'export')
            self.assertEqual((root/'export/hello.py').read_text(),'print("synthetic")\n')
            self.assertFalse((root/'export/.git').exists())
            for bad in ('../escape.py','.git/config','fixtures/input.lua','data/image.png'):
                with self.assertRaises(ValueError): export(repo,revision,{'revision':revision,'files':[dict(entry,path=bad)]},root/'bad')
            with self.assertRaises(ValueError): export(repo,revision,{'revision':revision,'files':[dict(entry,approved=False)]},root/'bad')
            with self.assertRaises(ValueError): export(repo,revision,{'revision':revision,'files':[dict(entry,sha256='wrong')]},root/'bad')
            self.assertFalse((root/'bad').exists())

if __name__ == '__main__': unittest.main()
