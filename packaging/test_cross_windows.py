"""Developer-only tests for archive extraction boundaries; no Windows execution claim."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('cross_windows', Path(__file__).with_name('cross-windows.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class RuntimeArchiveTests(unittest.TestCase):
    def test_extracts_runtime_without_dropping_licenses(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with zipfile.ZipFile(root / 'jre.zip', 'w') as archive:
                for name in ('bin/java.exe', 'bin/javaw.exe', 'release', 'legal/java.base/LICENSE'):
                    archive.writestr('jre-21/' + name, 'fixture')
            module.safe_runtime_extract(root / 'jre.zip', root / 'runtime')
            self.assertEqual((root / 'runtime/legal/java.base/LICENSE').read_text(), 'fixture')
            self.assertTrue((root / 'runtime/bin/javaw.exe').is_file())

    def test_rejects_paths_and_symlinks_that_escape_extraction(self):
        for name in ('jre/../../escape', '/absolute/path', 'jre/C:/drive', 'jre\\windows\\path'):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                with zipfile.ZipFile(root / 'bad.zip', 'w') as archive:
                    archive.writestr(name, 'fixture')
                with self.assertRaises(ValueError):
                    module.safe_runtime_extract(root / 'bad.zip', root / 'runtime')
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with zipfile.ZipFile(root / 'link.zip', 'w') as archive:
                link = zipfile.ZipInfo('jre/link')
                link.create_system = 3
                link.external_attr = 0o120777 << 16
                archive.writestr(link, '../../escape')
            with self.assertRaises(ValueError):
                module.safe_runtime_extract(root / 'link.zip', root / 'runtime')

    def test_download_rejects_unpinned_runtime_origin(self):
        with self.assertRaises(ValueError):
            module.fetch('https://example.org/runtime.zip', Path('unused'))


if __name__ == '__main__':
    unittest.main()
