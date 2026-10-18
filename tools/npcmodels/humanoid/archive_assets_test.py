import importlib.util
from pathlib import Path
import struct
import unittest

spec = importlib.util.spec_from_file_location('archive_assets', Path(__file__).with_name('archive_assets.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class ArchiveIndexTest(unittest.TestCase):
    def index(self, records, count):
        # Footer follows native index structure; entry count is 12 bytes from EOF.
        return records + bytes(16) + struct.pack('<I', count) + bytes(8)

    def test_selected_key_uses_unsigned_network_size_and_offset(self):
        key = '0123456789abcdef0123456789abcdef'
        raw = self.index(bytes.fromhex(key) + struct.pack('>II', 0x80000001, 0xf0000000), 1)
        self.assertEqual(module.parse_index(raw, {key}), {key:dict(size=0x80000001, offset=0xf0000000)})

    def test_zero_padding_matches_native_parser(self):
        key = '0123456789abcdef0123456789abcdef'
        raw = self.index(bytes(16) + bytes.fromhex(key) + struct.pack('>II', 300, 123), 1)
        self.assertEqual(module.parse_index(raw, {key}), {key:dict(size=300, offset=123)})

    def test_ignores_unrequested_keys(self):
        raw = self.index(bytes.fromhex('11' * 16) + struct.pack('>II', 3, 4), 1)
        self.assertEqual(module.parse_index(raw, {'22' * 16}), {})

    def test_rejects_invalid_count_or_truncation(self):
        for raw in [b'', bytes(20), self.index(bytes(24), 999)]:
            with self.assertRaises(ValueError):
                module.parse_index(raw, set())


class ArchiveRangeTest(unittest.TestCase):
    def response(self, status, content_range, raw):
        import io
        from unittest.mock import MagicMock
        response = MagicMock()
        response.status_code = status
        response.headers = {'Content-Range': content_range}
        response.raw = io.BytesIO(raw)
        response.__enter__.return_value = response
        return response

    def test_range_accepted_only_with_exact_response(self):
        response = self.response(206, 'bytes 5-7/100', b'abc')
        self.assertEqual(module.fetch_range('https://cdn.invalid/a', 5, 3, get=lambda *a, **k: response), b'abc')

    def test_rejects_ignored_range_wrong_range_and_overlong_body(self):
        for response in [self.response(200, 'bytes 5-7/100', b'abc'),
                         self.response(206, 'bytes 0-2/100', b'abc'),
                         self.response(206, 'bytes 5-7/100', b'abcd'),
                         self.response(206, 'bytes 5-7/100', b'ab')]:
            with self.assertRaises(ValueError):
                module.fetch_range('https://cdn.invalid/a', 5, 3, get=lambda *a, **k: response)

if __name__ == '__main__':
    unittest.main()
