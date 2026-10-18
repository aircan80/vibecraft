import copy
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
import json
import subprocess
import sys

MODULE = importlib.util.spec_from_file_location('inventory', Path(__file__).with_name('inventory.py'))
inventory = importlib.util.module_from_spec(MODULE)
MODULE.loader.exec_module(inventory)


class InventoryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        for name in ('game.sqlite', 'linux.tar.gz', 'test.txt'):
            (self.base / name).write_bytes(name.encode())
        self.spec = {
            'version': '0.4.1-preview.1', 'source_commit': 'a' * 40,
            'database': {'path': 'game.sqlite', 'sha256': hashlib.sha256(b'game.sqlite').hexdigest(),
                         'build_id': 'b' * 64, 'product': 'wow_classic_beta'},
            'evidence': [{'name': 'Linux launch', 'result': 'passed', 'path': 'test.txt'}],
            'artifacts': [{'os': 'linux', 'arch': 'x64', 'path': 'linux.tar.gz', 'includes_database': True,
                           'bundled_java': True, 'java_version': '21.0.9+10', 'launch_test': 'passed',
                           'launch_evidence_index': 0}]}

    def test_hashes_and_no_publication_claim(self):
        result = inventory.inventory(self.spec, self.base)
        self.assertEqual(result['artifacts'][0]['sha256'], hashlib.sha256(b'linux.tar.gz').hexdigest())
        self.assertEqual(result['publication_status'], 'local-inventory-only')

    def test_missing_file(self):
        (self.base / 'linux.tar.gz').unlink()
        with self.assertRaises(ValueError):
            inventory.inventory(self.spec, self.base)

    def test_no_database_or_java_rejected(self):
        for field in ('includes_database', 'bundled_java'):
            spec = copy.deepcopy(self.spec)
            spec['artifacts'][0][field] = False
            with self.assertRaises(ValueError):
                inventory.inventory(spec, self.base)

    def test_wrong_database_hash_rejected(self):
        self.spec['database']['sha256'] = 'c' * 64
        with self.assertRaises(ValueError):
            inventory.inventory(self.spec, self.base)

    def test_placeholder_and_snapshot_rejected(self):
        for value in ('TODO', '0.4.1-SNAPSHOT', 'replace-me'):
            self.spec['version'] = value
            with self.assertRaises(ValueError):
                inventory.inventory(self.spec, self.base)

    def test_unproven_launch_cannot_pass(self):
        self.spec['evidence'][0] = {'name': 'Windows launch', 'result': 'not-run', 'reason': 'No Windows host yet'}
        with self.assertRaises(ValueError):
            inventory.inventory(self.spec, self.base)

    def test_explicit_untested_platform_allowed_but_labelled(self):
        self.spec['artifacts'][0].update(os='windows', launch_test='not-run', launch_note='No Windows host yet')
        result = inventory.inventory(self.spec, self.base)
        self.assertEqual(result['artifacts'][0]['launch_test'], 'not-run')

    def test_duplicate_platform_and_symlink_rejected(self):
        self.spec['artifacts'].append(copy.deepcopy(self.spec['artifacts'][0]))
        with self.assertRaises(ValueError):
            inventory.inventory(self.spec, self.base)
        self.spec['artifacts'].pop()
        (self.base / 'link.tar.gz').symlink_to(self.base / 'linux.tar.gz')
        self.spec['artifacts'][0]['path'] = 'link.tar.gz'
        with self.assertRaises(ValueError):
            inventory.inventory(self.spec, self.base)

    def test_cli_preserves_existing_output_and_invalid_input_creates_nothing(self):
        spec_path = self.base / 'candidate.json'
        output = self.base / 'inventory'
        spec_path.write_text(json.dumps(self.spec))
        command = [sys.executable, str(Path(__file__).with_name('inventory.py')),
                   str(spec_path), '--output', str(output)]
        self.assertEqual(subprocess.run(command, capture_output=True).returncode, 0)
        before = (output / 'manifest.json').read_bytes()
        self.assertNotEqual(subprocess.run(command, capture_output=True).returncode, 0)
        self.assertEqual((output / 'manifest.json').read_bytes(), before)
        self.spec['database']['sha256'] = 'e' * 64
        spec_path.write_text(json.dumps(self.spec))
        command[-1] = str(self.base / 'invalid')
        self.assertNotEqual(subprocess.run(command, capture_output=True).returncode, 0)
        self.assertFalse((self.base / 'invalid').exists())


if __name__ == '__main__':
    unittest.main()
