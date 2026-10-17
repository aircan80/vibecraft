#!/usr/bin/env python3
"""Acquire a small native NPC model dependency set, without touching the app DB.

Uses the existing throttled HTTP client. Build/product are explicit request parameters;
the manifest distinguishes requested identity from server-attested identity.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import struct
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'clientdata'))
from clientdata.http import Client


def chunks(data: bytes) -> list[tuple[str, bytes]]:
    if data[:4] != b'MD21':
        return []
    result = []
    offset = 0
    while offset < len(data):
        if offset + 8 > len(data):
            raise ValueError('truncated M2 chunk header')
        tag = data[offset:offset + 4].decode('ascii')
        length = struct.unpack_from('<I', data, offset + 4)[0]
        end = offset + 8 + length
        if end > len(data):
            raise ValueError(f'truncated {tag} chunk')
        result.append((tag, data[offset + 8:end]))
        offset = end
    return result


def dependencies(data: bytes) -> list[tuple[int, str]]:
    result = []
    for tag, payload in chunks(data):
        if tag in {'SFID', 'TXID', 'SKID', 'BFID'}:
            if len(payload) % 4:
                raise ValueError(f'invalid {tag} size')
            result.extend((value, tag) for value in struct.unpack('<' + 'I' * (len(payload) // 4), payload) if value)
        elif tag == 'AFID':
            if len(payload) % 8:
                raise ValueError('invalid AFID size')
            result.extend((struct.unpack_from('<HHI', payload, offset)[2], tag) for offset in range(0, len(payload), 8) if struct.unpack_from('<HHI', payload, offset)[2])
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build', required=True)
    parser.add_argument('--product', required=True)
    parser.add_argument('--model', type=int, required=True)
    parser.add_argument('--texture', type=int, action='append', default=[])
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    client = Client('https://wago.tools', 'wow-forever-browser-pipeline/0.4.1', rate_per_second=2)
    pending = [(args.model, 'model')] + [(value, 'display-texture') for value in args.texture]
    rows: dict[int, dict] = {}
    while pending:
        fdid, reason = pending.pop(0)
        if fdid in rows:
            if reason not in rows[fdid]['reasons']:
                rows[fdid]['reasons'].append(reason)
            continue
        if len(rows) >= 64:
            raise ValueError('proof-of-concept dependency limit exceeded')
        path = args.output / f'{fdid}.bin'
        endpoint = f'/api/casc/{fdid}?download&version={args.build}&branch={args.product}'
        response = client.get_response(endpoint)
        data = response.content
        if len(data) > 32 * 1024 * 1024:
            raise ValueError(f'{fdid} exceeds proof-of-concept file limit')
        if data[:1] == b'<' or 'text/html' in response.headers.get('Content-Type', ''):
            raise ValueError(f'{fdid}: expected raw asset, got HTML')
        path.write_bytes(data)
        deps = dependencies(data)
        rows[fdid] = dict(file_data_id=fdid, reasons=[reason], path=str(path.resolve()), bytes=len(data), magic=data[:4].decode('ascii', errors='replace'), sha256=hashlib.sha256(data).hexdigest(), url=client.base_url + endpoint, response_headers=dict(response.headers), dependencies=[dict(file_data_id=value, chunk=tag) for value, tag in deps])
        pending.extend(deps)
        print(f'{fdid}: {len(data)} bytes, {data[:4]!r}', flush=True)
        manifest = dict(requested_build=args.build, requested_product=args.product, fetched_at=datetime.now(timezone.utc).isoformat(), build_attestation='Unverified asset build: version parameter is not validated by server (invalid version negative control also returned HTTP 200). Native display tables are build-pinned; raw assets are branch-resolved only.', model_file_data_id=args.model, texture_file_data_ids=args.texture, files=list(rows.values()))
        (args.output / 'assets-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')


if __name__ == '__main__':
    main()
