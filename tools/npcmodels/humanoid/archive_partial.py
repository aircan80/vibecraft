#!/usr/bin/env python3
"""Authenticated available BLTE chunks, with explicit holes for unknown public keys.

Outputs are deliberately NOT NativeAssets content-cache objects: their complete
content MD5 cannot be validated. A section-aware consumer must exclude every WDC
section touching erased ranges, including its records, strings and relationships.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import sys
import zlib

PIN = 'c2fd7bde36a712be78a5da896c995b84fbfa2545'
MAX = 512 * 1024 * 1024


def decode_available(raw, encoding_key, keys, decrypt):
    if raw[:4] != b'BLTE' or len(raw) < 12:
        raise ValueError('Not chunked BLTE')
    header = struct.unpack_from('>I', raw, 4)[0]
    count = int.from_bytes(raw[9:12], 'big')
    if not count or raw[8] != 15 or header != 12 + 24 * count or header > len(raw):
        raise ValueError('Invalid BLTE header')
    if hashlib.md5(raw[:header]).hexdigest() != encoding_key:
        raise ValueError('Encoding header MD5 mismatch')
    output, chunks, holes = bytearray(), [], []
    offset = header
    for index in range(count):
        size, unpacked = struct.unpack_from('>II', raw, 12 + 24 * index)
        checksum = raw[20 + 24 * index:36 + 24 * index]
        if not size or offset + size > len(raw) or unpacked > MAX - len(output):
            raise ValueError('Invalid/excessive chunk length')
        encoded = raw[offset:offset + size]
        if hashlib.md5(encoded).digest() != checksum:
            raise ValueError('Encoded chunk MD5 mismatch')
        block = encoded
        missing = None
        key_name = None
        if block[:1] == b'E':
            if len(block) < 16 or block[1] != 8:
                raise ValueError('Unsupported encryption key framing')
            key_name = block[2:10][::-1].hex()
            iv_size = block[10]
            if iv_size not in (4, 8) or len(block) <= 12 + iv_size or block[11 + iv_size] != 0x53:
                raise ValueError('Unsupported encryption IV/type')
            if key_name not in keys:
                missing = key_name
            else:
                nonce = bytearray(block[11:11 + iv_size]) + bytes(8 - iv_size)
                for byte in range(4):
                    nonce[byte] ^= (index >> (8 * byte)) & 255
                block = decrypt(block[12 + iv_size:], nonce, keys[key_name])
        start = len(output)
        if missing:
            decoded = bytes(unpacked)
            holes.append(dict(start=start, end=start + unpacked, length=unpacked,
                              key_name=missing, chunk_index=index, reason='Public key unavailable; zero placeholder'))
        elif block[:1] == b'N':
            decoded = block[1:]
        elif block[:1] == b'Z':
            decoder = zlib.decompressobj()
            decoded = decoder.decompress(block[1:], unpacked + 1)
            if not decoder.eof or decoder.unused_data or decoder.unconsumed_tail:
                raise ValueError('Invalid/excessive zlib block')
        else:
            raise ValueError('Unsupported nested/unknown block mode')
        if len(decoded) != unpacked:
            raise ValueError('Decoded chunk length mismatch')
        output.extend(decoded)
        chunks.append(dict(index=index, encoded_start=offset, encoded_length=size,
                           encoded_md5=checksum.hex(), decoded_start=start,
                           decoded_end=len(output), decoded_length=unpacked,
                           status='erased-unknown-key' if missing else 'decoded', key_name=key_name, mode=encoded[:1].decode('ascii')))
        offset += size
    if offset != len(raw):
        raise ValueError('Trailing BLTE data')
    return bytes(output), chunks, holes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['native-tools', 'metadata', 'cache', 'keys', 'wow-export', 'node']:
        parser.add_argument('--' + name, required=True)
    parser.add_argument('file_ids', nargs='+', type=int)
    args = parser.parse_args()
    sys.path.insert(0, args.native_tools)
    from native_assets import NativeAssets, _atomic
    from attest import root_find, encoding_find
    upstream = Path(args.wow_export).resolve()
    commit = subprocess.check_output(['git', '-C', str(upstream), 'rev-parse', 'HEAD'], text=True).strip()
    if commit != PIN:
        raise ValueError('Unreviewed Salsa20 implementation')
    key_bytes = Path(args.keys).read_bytes()
    keys = {}
    for line in key_bytes.decode().splitlines():
        pieces = line.split()
        if len(pieces) == 2 and len(pieces[0]) == 16 and len(pieces[1]) == 32:
            keys[pieces[0].lower()] = pieces[1].lower()
    js = """const Module=require('module'),load=Module._load;
Module._load=function(n,...a){return n==='webp-wasm'?{}:load.call(this,n,...a)};
const fs=require('fs'),path=require('path'),p=JSON.parse(fs.readFileSync(0,'utf8'));
const Salsa=require(path.join(p.upstream,'src/js/casc/salsa20'));
const BufferWrapper=require(path.join(p.upstream,'src/js/buffer'));
process.stdout.write(new Salsa(p.nonce,p.key).process(new BufferWrapper(Buffer.from(p.data,'hex'))).raw);"""
    def decrypt(raw, nonce, key):
        payload = dict(upstream=str(upstream), nonce=list(nonce), key=key, data=raw.hex())
        return subprocess.check_output([args.node, '-e', js], input=json.dumps(payload).encode(), timeout=30)
    store = NativeAssets(args.cache, 'wow_classic_beta', '1.60.1.70450', args.metadata)
    store._prepare()
    for file_id in args.file_ids:
        records = [r for r in root_find(store._root, {file_id}) if (r['locale_flags'] == 0 or r['locale_flags'] & 2) and not r['content_flags'] & 0x80]
        if not records:
            raise ValueError('No eligible exact-build root entry')
        selected = records[0]
        entry = encoding_find(store._encoding, {selected['content_key']})[selected['content_key']]
        encoding = next((key for key in entry['encoding_keys'] if (Path(args.cache) / 'encoded' / (key + '.blte')).exists()), None)
        if encoding is None:
            raise FileNotFoundError(f'No preserved encoded archive bytes for {file_id}')
        encoded = (Path(args.cache) / 'encoded' / (encoding + '.blte')).read_bytes()
        decoded, chunks, holes = decode_available(encoded, encoding, keys, decrypt)
        if len(decoded) != entry['size']:
            raise ValueError('Encoding decoded length mismatch')
        if not holes and hashlib.md5(decoded).hexdigest() != selected['content_key']:
            raise ValueError('Complete content MD5 mismatch')
        output = Path(args.cache).parent / 'partial' / (str(file_id) + '.partial.db2')
        proof = dict(**store.identity, file_data_id=file_id, selected_root_entry=selected,
                     expected_content_md5=selected['content_key'], encoding_key=encoding,
                     encoded_sha256=hashlib.sha256(encoded).hexdigest(),
                     partial_sha256=hashlib.sha256(decoded).hexdigest(), bytes=len(decoded),
                     content_md5_verified=not bool(holes), status='PARTIAL_WITH_EXPLICIT_ERASURES' if holes else 'COMPLETE_VERIFIED',
                     key_registry_sha256=hashlib.sha256(key_bytes).hexdigest(), decoder_commit=commit,
                     chunks=chunks, erased_ranges=holes,
                     consumption_rule='Exclude every WDC section touching erased bytes; never use placeholders as actual data')
        proof.update(encoding_table_content_md5=hashlib.md5(store._encoding).hexdigest(), schema='authenticated-partial-blte-v1', fileDataId=file_id, encodingKey=encoding,
                     expectedContentMD5=selected['content_key'], decodedSha256=hashlib.sha256(decoded).hexdigest(),
                     decodedSize=len(decoded), erasedRanges=[dict(offset=h['start'], length=h['length'], keyName=h['key_name']) for h in holes],
                     chunkProofs=chunks)
        _atomic(output, decoded)
        _atomic(output.with_suffix('.json'), (json.dumps(proof, indent=2)+'\n').encode())
        print(json.dumps(dict(file_data_id=file_id, path=str(output), erased_ranges=holes)), flush=True)

if __name__ == '__main__':
    main()
