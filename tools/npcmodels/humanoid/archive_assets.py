#!/usr/bin/env python3
"""Retrieve archive-only CASC objects; exact root/decoded MD5 remains authority.

Index format follows pinned wow.export c2fd7bde36a712be78a5da896c995b84fbfa2545.
Archive locations are transport hints, never a substitute for content proof.
"""
import argparse
import concurrent.futures
import hashlib
import json
import re
from pathlib import Path
import struct
import sys
import requests


def parse_index(raw, wanted):
    if len(raw) < 28:
        raise ValueError('Truncated archive index')
    count = struct.unpack_from('<I', raw, len(raw) - 12)[0]
    if count * 24 > len(raw):
        raise ValueError('Invalid archive index entry count')
    found = {}
    offset = 0
    for _ in range(count):
        if offset + 24 > len(raw) - 28:
            raise ValueError('Truncated archive index entry')
        key = raw[offset:offset + 16]
        offset += 16
        if key == bytes(16):
            if offset + 24 > len(raw) - 28:
                raise ValueError('Truncated archive index padding')
            key = raw[offset:offset + 16]
            offset += 16
        size, position = struct.unpack_from('>II', raw, offset)
        offset += 8
        if key.hex() in wanted:
            found[key.hex()] = dict(size=size, offset=position)
    return found



def fetch_range(url, start, size, get=requests.get):
    if start < 0 or not 0 < size <= 512 * 1024 * 1024:
        raise ValueError('Invalid or excessive archive range')
    with get(url, headers={'Range': f'bytes={start}-{start + size - 1}'},
             stream=True, timeout=(15, 90), allow_redirects=False) as response:
        response.raise_for_status()
        expected = f'bytes {start}-{start + size - 1}'
        if response.status_code != 206 or response.headers.get('Content-Range', '').split('/')[0] != expected:
            raise ValueError('CDN did not honor exact byte range')
        encoded = response.raw.read(size + 1)
        if len(encoded) != size:
            raise ValueError('Archive range size mismatch')
        return encoded


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native-tools', required=True)
    parser.add_argument('--metadata', required=True)
    parser.add_argument('--cache', required=True)
    parser.add_argument('--cdn-config', required=True)
    parser.add_argument('--encoded-only', action='store_true', help='Preserve authenticated encoded bytes without claiming decoded content')
    parser.add_argument('--archive', action='append', help='Restrict lookup to an official-config archive (repeatable)')
    parser.add_argument('--build', default='1.60.1.70450')
    parser.add_argument('--product', default='wow_classic_beta')
    parser.add_argument('file_ids', nargs='+', type=int)
    args = parser.parse_args()
    sys.path.insert(0, args.native_tools)
    from native_assets import NativeAssets, BASE, _verified, _atomic, decode_blte
    from attest import root_find, encoding_find
    store = NativeAssets(args.cache, args.product, args.build, args.metadata)
    store._prepare()
    targets = {}
    for file_id in args.file_ids:
        records = [r for r in root_find(store._root, {file_id}) if (r['locale_flags'] == 0 or r['locale_flags'] & 2) and not r['content_flags'] & 0x80]
        if not records:
            raise ValueError(f'No eligible exact-root entry: {file_id}')
        selected = records[0]
        entry = encoding_find(store._encoding, {selected['content_key']})[selected['content_key']]
        targets[file_id] = dict(selected=selected, entry=entry)
    wanted = {key for target in targets.values() for key in target['entry']['encoding_keys']}
    config_key = args.cdn_config
    if not re.fullmatch('[0-9a-f]{32}', config_key):
        raise ValueError('Invalid CDN config key')
    config = store._fetch(f'{BASE}/config/{config_key[:2]}/{config_key[2:4]}/{config_key}', 1024 * 1024)
    _verified(config, config_key, 'CDN configuration')
    _atomic(Path(args.cache) / 'config' / config_key, config)
    values = dict(line.split(' = ', 1) for line in config.decode().splitlines() if ' = ' in line)
    indexes = Path(args.cache) / 'archive-indexes'
    indexes.mkdir(parents=True, exist_ok=True)
    def inspect(archive):
        path = indexes / (archive + '.index')
        if path.exists():
            if path.stat().st_size > 16 * 1024 * 1024:
                raise ValueError('Cached archive index exceeds size bound')
            raw = path.read_bytes()
        else:
            raw = store._fetch(f'{BASE}/data/{archive[:2]}/{archive[2:4]}/{archive}.index', 16 * 1024 * 1024)
            _atomic(path, raw)
        return archive, parse_index(raw, wanted)
    archives = args.archive or values['archives'].split()
    if any(archive not in values['archives'].split() for archive in archives):
        raise ValueError('Archive not in verified CDN configuration')
    locations = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        for number, (archive, matches) in enumerate(pool.map(inspect, archives), 1):
            for key, location in matches.items():
                locations[key] = dict(archive=archive, **location)
                print(json.dumps(dict(found=key, **locations[key])), flush=True)
            if number % 100 == 0:
                print(f'Inspected {number} indexes', flush=True)
            if wanted <= locations.keys():
                break
    for file_id, target in targets.items():
        key = target['selected']['content_key']
        for encoding in target['entry']['encoding_keys']:
            location = locations.get(encoding)
            if not location:
                continue
            archive = location['archive']
            url = f'{BASE}/data/{archive[:2]}/{archive[2:4]}/{archive}'
            start, size = location['offset'], location['size']
            encoded = fetch_range(url, start, size)
            header_size = struct.unpack_from('>I', encoded, 4)[0] if len(encoded) >= 8 else -1
            if encoded[:4] != b'BLTE' or header_size > len(encoded) or header_size < 0:
                raise ValueError('Invalid archive BLTE framing')
            _verified(encoded[:header_size] if header_size else encoded, encoding, 'Archive BLTE encoding header')
            encoded_path = Path(args.cache) / 'encoded' / (encoding + '.blte')
            _atomic(encoded_path, encoded)
            encoded_proof = dict(**store.identity, file_data_id=file_id, expected_content_md5=key,
                                 encoding_key=encoding, archive_location=location, transport=url,
                                 cdn_config_md5=config_key, encoded_sha256=hashlib.sha256(encoded).hexdigest(),
                                 status='AUTHENTICATED_ENCODING_ONLY', content_md5_verified=False)
            _atomic(encoded_path.with_suffix('.json'), (json.dumps(encoded_proof, indent=2)+'\n').encode())
            if args.encoded_only:
                print(json.dumps(dict(path=str(encoded_path), **encoded_proof)), flush=True)
                break
            decoded = _verified(decode_blte(encoded), key, 'Archive decoded content')
            if len(decoded) != target['entry']['size']:
                raise ValueError('Encoding decoded size mismatch')
            output = Path(args.cache) / 'content' / (key + '.bin')
            _atomic(output, decoded)
            proof = dict(**store.identity, file_data_id=file_id, content_md5=key, sha256=hashlib.sha256(decoded).hexdigest(), bytes=len(decoded), transport=url, encoding_key=encoding, archive_location=location, cdn_config_md5=config_key, exact_root_membership_verified=True, selected_root_entry=target['selected'])
            _atomic(Path(args.cache) / 'proof' / args.product / args.build / (str(file_id) + '.json'), (json.dumps(proof, indent=2)+'\n').encode())
            print(json.dumps(dict(file_id=file_id, path=str(output), **proof)), flush=True)
            break
        else:
            raise FileNotFoundError(f'No archived encoding located for {file_id}')

if __name__ == '__main__':
    main()
