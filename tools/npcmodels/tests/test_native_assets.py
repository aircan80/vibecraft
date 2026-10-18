import hashlib
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch
import zlib
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import native_assets as native


def md5(raw): return hashlib.md5(raw).hexdigest()
def blte(raw): return b'BLTE'+b'\0'*4+b'N'+raw


def encoding(entries):
    header=bytearray(22)
    header[:2]=b'EN';header[2:5]=bytes((1,16,16))
    struct.pack_into('>H',header,5,1);struct.pack_into('>I',header,9,1)
    page=bytearray()
    for content, key, size in entries:
        page+=b'\1'+size.to_bytes(5,'big')+bytes.fromhex(content)+bytes.fromhex(key)
    return bytes(header)+bytes(32)+bytes(page)+bytes(1024-len(page))


class NativeAssetsTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.directory=Path(self.temp.name);self.seed=self.directory/'seed';self.seed.mkdir()
        self.cache=self.directory/'cache'
        self.assets={7:b'Native M2 example',9:b'Native BLP example'}
        self.keys={fd:md5(data) for fd,data in self.assets.items()}
        root=struct.pack('<4sIIIII',b'TSFM',24,1,2,2,0)+struct.pack('<IIIii',2,0,2,7,1)
        root+=b''.join(bytes.fromhex(self.keys[fd]) for fd in (7,9))+bytes(16)
        self.root=root
        self.encoded={md5(b'encoded'+data):blte(data) for data in self.assets.values()}
        entries=[(md5(root),md5(b'root encoding'),len(root))]
        entries += [(md5(data),md5(b'encoded'+data),len(data)) for data in self.assets.values()]
        en=encoding(entries)
        config=(f'root = {md5(root)}\nencoding = {md5(en)} {md5(b"encoding key")}\nbuild-name = WOW-12345patch1.60.1_ForeverBeta\n').encode()
        self.config_key=md5(config)
        (self.seed/'build-config').write_bytes(config)
        (self.seed/'encoding.blte').write_bytes(blte(en))
        (self.seed/'root.decoded').write_bytes(root)
        (self.seed/'versions').write_text('Region!STRING:0|BuildConfig!HEX:16|BuildId!DEC:4|VersionsName!STRING:0\nus|'+self.config_key+'|12345|1.60.1.12345\n')
        self.pin=patch.dict(native.PINNED,{('wow_classic_beta','1.60.1.12345'):self.config_key});self.pin.start();self.addCleanup(self.pin.stop)

    def store(self):
        store=native.NativeAssets(self.cache,'wow_classic_beta','1.60.1.12345',self.seed)
        store._fetch=lambda url,limit=native.MAX_BYTES:self.encoded[url.rsplit('/',1)[-1]]
        return store

    def test_two_exact_root_assets_are_verified_and_cached(self):
        store=self.store()
        for fd,data in self.assets.items():
            path=store.get(fd)
            self.assertEqual(path.read_bytes(),data)
            self.assertEqual(path.name,md5(data)+'.bin')
        store._fetch=lambda *args: (_ for _ in ()).throw(AssertionError('network unexpected'))
        self.assertEqual(store.get(7).read_bytes(),self.assets[7])

    def test_absent_id_never_falls_back_to_another_product(self):
        with self.assertRaises(FileNotFoundError):self.store().get(8)

    def test_corrupt_asset_cache_is_rejected(self):
        store=self.store();path=store.get(7);path.write_bytes(b'wrong')
        with self.assertRaisesRegex(ValueError,'MD5'):store.get(7)

    def test_corrupt_seed_root_is_rejected(self):
        (self.seed/'root.decoded').write_bytes(b'wrong')
        with self.assertRaisesRegex(ValueError,'Seed root'):self.store().get(7)

    def test_corrupt_seed_encoding_is_rejected(self):
        (self.seed/'encoding.blte').write_bytes(blte(b'wrong'))
        with self.assertRaisesRegex(ValueError,'MD5'):self.store().get(7)

    def test_corrupt_configuration_is_rejected(self):
        with (self.seed/'build-config').open('ab') as output:output.write(b'changed')
        with self.assertRaisesRegex(ValueError,'configuration'):self.store().get(7)

    def test_wrong_archived_build_pointer_is_rejected(self):
        (self.seed/'versions').write_text('Region|BuildConfig|BuildId|VersionsName\nus|'+self.config_key+'|77777|1.60.1.77777\n')
        with self.assertRaisesRegex(ValueError,'exact build'):self.store().get(7)

    def test_unpinned_build_requires_live_official_versions(self):
        store=native.NativeAssets(self.cache,'wow_classic_era','1.15.9.12345',self.seed)
        called=[]
        def fetch(url,limit):
            called.append(url)
            return (self.seed/'versions').read_bytes()
        store._fetch=fetch
        with self.assertRaisesRegex(ValueError,'exact build'):store.get(7)
        self.assertEqual(called,['https://us.version.battle.net/wow_classic_era/versions'])

    def test_wrong_cdn_asset_content_is_rejected(self):
        store=self.store();store._fetch=lambda *args:blte(b'wrong')
        with self.assertRaisesRegex(ValueError,'MD5'):store.get(7)
        self.assertFalse((self.cache/'content'/(self.keys[7]+'.bin')).exists())

    def test_blte_n_z_and_decoded_bound(self):
        self.assertEqual(native.decode_blte(blte(b'ok')),b'ok')
        encoded=b'BLTE'+bytes(4)+b'Z'+zlib.compress(b'a'*100)
        self.assertEqual(native.decode_blte(encoded),b'a'*100)
        with self.assertRaisesRegex(ValueError,'bound'):native.decode_blte(encoded,10)
        with self.assertRaises(ValueError):native.decode_blte(b'BLTE'+bytes(4)+b'E'+bytes(20))

    def test_blte_chunk_checksum_and_length(self):
        block=b'Nhello'
        raw=b'BLTE'+struct.pack('>I',36)+b'\x0f\0\0\1'+struct.pack('>II',len(block),5)+hashlib.md5(block).digest()+block
        self.assertEqual(native.decode_blte(raw),b'hello')
        with self.assertRaisesRegex(ValueError,'checksum'):native.decode_blte(raw[:-1]+b'x')
        with self.assertRaisesRegex(ValueError,'Trailing'):native.decode_blte(raw+b'x')

    def test_archive_only_transport_requires_exact_root_hash(self):
        store=self.store();calls=[]
        def fetch(url,limit=native.MAX_BYTES):
            calls.append(url)
            if url.startswith(native.BASE):raise native.requests.HTTPError('404 archive-only')
            return self.assets[7]
        store._fetch=fetch
        self.assertEqual(store.get(7).read_bytes(),self.assets[7])
        self.assertIn('https://wago.tools/api/casc/7?download&version=1.60.1.12345&branch=wow_classic_beta',calls)
        self.assertEqual(store.identity['build_config_md5'],self.config_key)

    def test_native_root_variant_order_is_explicit_in_proof(self):
        store=self.store();original=native.root_find
        def variants(raw,wanted):
            records=original(raw,wanted)
            return records+[dict(records[0],content_key='a'*32,content_flags=1)]
        with patch.object(native,'root_find',variants):
            self.assertEqual(store.get(7).read_bytes(),self.assets[7])
        import json
        proof=json.loads((self.cache/'proof'/'wow_classic_beta'/'1.60.1.12345'/'7.json').read_text())
        self.assertEqual(proof['selected_root_entry']['content_key'],self.keys[7])
        self.assertEqual(len(proof['root_variants']),2)
        self.assertIn('native root order',proof['root_selection'])

    def test_invalid_identifiers(self):
        with self.assertRaises(ValueError):native.NativeAssets(self.cache,'../../bad','1')
        store=self.store()
        for value in (0,-1,True,'7',2**32):
            with self.assertRaises(ValueError):store.get(value)

if __name__=='__main__':unittest.main()
