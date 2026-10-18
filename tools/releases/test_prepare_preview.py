import hashlib
from pathlib import Path
import sqlite3
import tempfile
import unittest
import zipfile

from prepare_preview import unpack_reviewed, validate_url
from check_portable import summarize_tests


class PreviewInputsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        db = self.root / 'input.sqlite'
        with sqlite3.connect(db) as connection:
            connection.execute('CREATE TABLE meta(key TEXT,value TEXT)')
            connection.executemany('INSERT INTO meta VALUES(?,?)', [('client_product', 'wow_classic_beta'), ('release_dataset_profile', 'runtime-v1')])
        self.content = db.read_bytes()
        self.manifest = self.root / 'pins.tsv'
        self.manifest.write_text(f'{hashlib.sha256(self.content).hexdigest()}\t{len(self.content)}\tdata/wowforever.sqlite\n')
        self.archive = self.root / 'data.zip'

    def zip(self, name='data/wowforever.sqlite', content=None):
        with zipfile.ZipFile(self.archive, 'w') as zipped:
            zipped.writestr(name, self.content if content is None else content)

    def test_valid_reviewed_zip(self):
        self.zip()
        unpack_reviewed(self.archive, self.root / 'output', self.manifest)
        self.assertEqual((self.root / 'output/data/wowforever.sqlite').read_bytes(), self.content)

    def test_extra_or_traversal_paths_rejected_before_output(self):
        for name in ('../escape.sqlite', 'data/unknown.sqlite'):
            self.zip(name)
            with self.assertRaises(ValueError):
                unpack_reviewed(self.archive, self.root / 'output', self.manifest)
            self.assertFalse((self.root / 'output').exists())

    def test_hash_mismatch_removes_partial_output(self):
        changed = bytearray(self.content)
        changed[-1] ^= 1
        self.zip(content=changed)
        with self.assertRaises(ValueError):
            unpack_reviewed(self.archive, self.root / 'output', self.manifest)
        self.assertFalse((self.root / 'output').exists())

    def test_duplicate_zip_path_rejected(self):
        self.zip()
        with zipfile.ZipFile(self.archive, 'a') as zipped:
            import warnings
            with warnings.catch_warnings():
                warnings.simplefilter('ignore')
                zipped.writestr('data/wowforever.sqlite', self.content)
        with self.assertRaises(ValueError):
            unpack_reviewed(self.archive, self.root / 'output', self.manifest)

    def test_only_https_without_embedded_credentials(self):
        validate_url('https://example.test/data.zip')
        for url in ('http://example.test/data.zip', 'file:///tmp/data', 'https://user:password@example.test/data.zip', ''):
            with self.assertRaises(ValueError):
                validate_url(url)

    def test_test_summary_requires_reports_and_zero_failures(self):
        output = self.root / 'tests.json'
        with self.assertRaises(ValueError):
            summarize_tests(self.root, output)
        folder = self.root / 'app/target/surefire-reports'
        folder.mkdir(parents=True)
        report = folder / 'TEST-Example.xml'
        report.write_text('<testsuite tests="7" skipped="1" failures="0" errors="0"/>')
        summarize_tests(self.root, output)
        self.assertIn('"tests": 7', output.read_text())
        report.write_text('<testsuite tests="7" skipped="0" failures="1" errors="0"/>')
        with self.assertRaises(ValueError):
            summarize_tests(self.root, output)


if __name__ == '__main__':
    unittest.main()
