import hashlib
import importlib.util
from pathlib import Path
import struct
import unittest
import zlib

spec = importlib.util.spec_from_file_location('archive_partial', Path(__file__).with_name('archive_partial.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class AvailableChunksTest(unittest.TestCase):
    def framed(self, chunks):
        header = b'BLTE' + struct.pack('>I', 12 + 24 * len(chunks)) + b'\x0f' + len(chunks).to_bytes(3, 'big')
        header += b''.join(struct.pack('>II', len(encoded), length) + hashlib.md5(encoded).digest() for encoded, length in chunks)
        return header + b''.join(encoded for encoded, _ in chunks), hashlib.md5(header).hexdigest()

    def encrypted(self):
        return b'E\x08' + bytes.fromhex('b6d54b5714c87d05') + b'\x04\x01\x02\x03\x04S' + b'ciphertext'

    def test_normal_chunks_and_unknown_key_have_exact_erasure_offsets(self):
        raw, key = self.framed([(b'Nabc', 3), (self.encrypted(), 7), (b'Z' + zlib.compress(b'def'), 3)])
        decoded, chunks, holes = module.decode_available(raw, key, {}, lambda *a: self.fail('Unknown key was attempted'))
        self.assertEqual(decoded, b'abc' + bytes(7) + b'def')
        self.assertEqual([(h['start'], h['length'], h['key_name']) for h in holes], [(3, 7, '057dc814574bd5b6')])
        self.assertEqual([chunk['decoded_start'] for chunk in chunks], [0, 3, 10])

    def test_public_key_uses_reversed_name_and_chunk_index_nonce(self):
        raw, key = self.framed([(b'Nx', 1), (self.encrypted(), 3)])
        calls = []
        def decrypt(ciphertext, nonce, public_key):
            calls.append((ciphertext, bytes(nonce), public_key))
            return b'Nabc'
        decoded, _, holes = module.decode_available(raw, key, {'057dc814574bd5b6': '11' * 16}, decrypt)
        self.assertEqual(decoded, b'xabc')
        self.assertEqual(holes, [])
        self.assertEqual(calls, [(b'ciphertext', b'\x00\x02\x03\x04\x00\x00\x00\x00', '11' * 16)])

    def test_rejects_header_hash_mismatch(self):
        raw, _ = self.framed([(b'Nabc', 3)])
        with self.assertRaisesRegex(ValueError, 'header MD5'):
            module.decode_available(raw, '00' * 16, {}, None)

    def test_rejects_mutated_chunk(self):
        raw, key = self.framed([(b'Nabc', 3)])
        with self.assertRaisesRegex(ValueError, 'chunk MD5'):
            module.decode_available(raw[:-1] + b'z', key, {}, None)

    def test_rejects_mismatched_decoded_length(self):
        raw, key = self.framed([(b'Z' + zlib.compress(b'abc'), 2)])
        with self.assertRaises(ValueError):
            module.decode_available(raw, key, {}, None)

    def test_rejects_trailing_data(self):
        raw, key = self.framed([(b'Nabc', 3)])
        with self.assertRaisesRegex(ValueError, 'Trailing'):
            module.decode_available(raw + b'x', key, {}, None)

if __name__ == '__main__':
    unittest.main()
