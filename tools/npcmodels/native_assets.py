#!/usr/bin/env python3
"""Exact-build Blizzard CASC assets. No cross-product or cross-build fallback.

NativeAssets(cache_root, product, build, metadata_dir=None).get(file_data_id)
returns a decoded, content-MD5-verified file. WOWFOREVER_CASC_METADATA can point
at an offline metadata seed; its filenames are never treated as proof.
"""
from __future__ import annotations
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import tempfile
import threading
import time
import zlib
import requests
try:
    from .attest import encoding_find, root_find
except ImportError:
    from attest import encoding_find, root_find

BASE = 'https://us.cdn.blizzard.com/tpr/wow'
VERSIONS = 'https://us.version.battle.net/{product}/versions'
# Recorded official versions/config proof; remains valid after beta moves on.
PINNED = {('wow_classic_beta', '1.60.1.70450'): '84bea4233331e390fda8724c6f91ffa5'}
MAX_BYTES = 512 * 1024 * 1024
_RATE_LOCK = threading.Lock()
_NEXT_REQUEST = 0.0
HEX = re.compile(r'^[0-9a-f]{32}$')


def _md5(raw: bytes) -> str:
    return hashlib.md5(raw).hexdigest()


def _verified(raw: bytes, key: str, label: str) -> bytes:
    if not HEX.fullmatch(key) or _md5(raw) != key:
        raise ValueError(f'{label}: content MD5 mismatch')
    return raw


def _atomic(path: Path, raw: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix='.download-', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as output:
            output.write(raw)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)


def decode_blte(raw: bytes, limit: int = MAX_BYTES) -> bytes:
    """Bounded N/Z decoder, validating chunk checksums, lengths and framing."""
    if len(raw) < 9 or raw[:4] != b'BLTE':
        raise ValueError('Not BLTE')
    header = struct.unpack_from('>I', raw, 4)[0]
    def block(data: bytes, bound: int) -> bytes:
        if not data:
            raise ValueError('Empty BLTE block')
        if data[0:1] == b'N':
            decoded = data[1:]
        elif data[0:1] == b'Z':
            decoder = zlib.decompressobj()
            decoded = decoder.decompress(data[1:], bound + 1)
            if not decoder.eof or decoder.unused_data or decoder.unconsumed_tail:
                raise ValueError('BLTE compressed block exceeds bound or has invalid framing')
        else:
            raise ValueError('Unsupported/encrypted BLTE mode')
        if len(decoded) > bound:
            raise ValueError('BLTE decoded size exceeds bound')
        return decoded
    if header == 0:
        return block(raw[8:], limit)
    if len(raw) < 12 or raw[8] != 15:
        raise ValueError('Unsupported BLTE header')
    count = int.from_bytes(raw[9:12], 'big')
    if count == 0 or header != 12 + 24 * count or header > len(raw):
        raise ValueError('Invalid BLTE chunk table')
    offset = header
    output = bytearray()
    for index in range(count):
        size, unpacked = struct.unpack_from('>II', raw, 12 + 24 * index)
        checksum = raw[20 + 24 * index:36 + 24 * index]
        if size < 1 or offset + size > len(raw) or unpacked > limit - len(output):
            raise ValueError('Invalid/excessive BLTE chunk size')
        encoded = raw[offset:offset + size]
        if hashlib.md5(encoded).digest() != checksum:
            raise ValueError('BLTE chunk checksum mismatch')
        decoded = block(encoded, unpacked)
        if len(decoded) != unpacked:
            raise ValueError('BLTE chunk decoded length mismatch')
        output.extend(decoded)
        offset += size
    if offset != len(raw):
        raise ValueError('Trailing BLTE bytes')
    return bytes(output)


def _version_config(raw: bytes, build: str) -> str:
    lines = raw.decode('utf-8-sig').splitlines()
    header = next((line for line in lines if line and not line.startswith('#')), '')
    names = [field.split('!')[0] for field in header.split('|')]
    matches = set()
    for line in lines[1:]:
        if not line or line.startswith('#'):
            continue
        record = dict(zip(names, line.split('|')))
        if record.get('VersionsName') == build or record.get('BuildId') == build:
            key = record.get('BuildConfig', '')
            if HEX.fullmatch(key):
                matches.add(key)
    if len(matches) != 1:
        raise ValueError(f'No unique official configuration for exact build {build}')
    return matches.pop()


class NativeAssets:
    def __init__(self, cache_root, product, build, metadata_dir=None):
        if not re.fullmatch(r'wow(?:_[a-z0-9]+)*', str(product)):
            raise ValueError('Invalid Blizzard product')
        if not re.fullmatch(r'[0-9]+(?:\.[0-9]+){0,3}', str(build)):
            raise ValueError('Invalid exact build')
        self.cache_root = Path(cache_root)
        self.product, self.build = str(product), str(build)
        self.metadata_dir = Path(metadata_dir or os.environ['WOWFOREVER_CASC_METADATA']) if metadata_dir or os.environ.get('WOWFOREVER_CASC_METADATA') else None
        self._lock = threading.RLock()
        self._root = self._encoding = None
        self._paths = {}
        self._encoding_entries = {}
        self._session = requests.Session()

    def _fetch(self, url: str, limit: int = MAX_BYTES) -> bytes:
        # Shared ceiling across store instances: at most two transport starts/sec.
        global _NEXT_REQUEST
        with _RATE_LOCK:
            delay = _NEXT_REQUEST - time.monotonic()
            if delay > 0:
                time.sleep(delay)
            _NEXT_REQUEST = time.monotonic() + .5
        deadline = time.monotonic() + 120
        # URLs are generated from fixed official/Wago bases and checked identity.
        with self._session.get(url, timeout=(15, 90), stream=True, allow_redirects=False) as response:
            response.raise_for_status()
            if response.status_code != 200:
                raise ValueError(f'Unexpected CDN status {response.status_code}')
            declared = response.headers.get('Content-Length')
            if declared is not None and int(declared) > limit:
                raise ValueError('CDN response exceeds size bound')
            output = bytearray()
            for chunk in response.iter_content(chunk_size=1024 * 1024):
                if time.monotonic() > deadline:
                    raise TimeoutError('Asset transport exceeded 120-second deadline')
                if len(output) + len(chunk) > limit:
                    raise ValueError('CDN response exceeds size bound')
                output.extend(chunk)
            return bytes(output)

    def _seed(self, name: str):
        path = self.metadata_dir / name if self.metadata_dir else None
        if path is not None and path.is_file():
            if path.stat().st_size > MAX_BYTES:
                raise ValueError('Seed exceeds size bound')
            return path.read_bytes()
        return None

    def _config_key(self) -> str:
        pin = PINNED.get((self.product, self.build))
        if pin:
            archived = self._seed('versions')
            if archived is not None and _version_config(archived, self.build) != pin:
                raise ValueError('Archived versions/config does not match pinned exact build')
            return pin
        # An arbitrary local versions file is not an authenticated release pointer.
        return _version_config(self._fetch(VERSIONS.format(product=self.product), 1024 * 1024), self.build)

    def _blob(self, encoding_key: str, seed_name=None) -> bytes:
        if not HEX.fullmatch(encoding_key):
            raise ValueError('Invalid encoding key')
        path = self.cache_root / 'encoded' / (encoding_key + '.blte')
        if path.exists():
            if path.stat().st_size > MAX_BYTES:
                raise ValueError('Cached encoded file exceeds size bound')
            return path.read_bytes()
        raw = self._seed(seed_name or (encoding_key + '.blte'))
        if raw is None:
            raw = self._fetch(f'{BASE}/data/{encoding_key[:2]}/{encoding_key[2:4]}/{encoding_key}')
        # Encoded keys are not necessarily the MD5 of the complete BLTE file.
        # The caller must verify decoded content before this blob is trusted.
        return raw

    def _decode_content(self, content_key: str, encoding_keys: list[str], seed_name=None) -> bytes:
        content_path = self.cache_root / 'content' / (content_key + '.bin')
        if content_path.exists():
            if content_path.stat().st_size > MAX_BYTES:
                raise ValueError('Cached content exceeds size bound')
            return _verified(content_path.read_bytes(), content_key, 'Cached content')
        failures = []
        for encoding_key in encoding_keys:
            try:
                encoded = self._blob(encoding_key, seed_name)
                decoded = _verified(decode_blte(encoded), content_key, 'Decoded CDN asset')
                _atomic(self.cache_root / 'encoded' / (encoding_key + '.blte'), encoded)
                _atomic(content_path, decoded)
                return decoded
            except (ValueError, requests.RequestException, zlib.error, AssertionError) as failure:
                failures.append(str(failure))
        raise ValueError('No verified encoding for content ' + content_key + ': ' + '; '.join(failures))

    def _prepare(self):
        if self._root is not None:
            return
        config_key = self._config_key()
        config_path = self.cache_root / 'config' / config_key
        if config_path.exists() and config_path.stat().st_size > 1024 * 1024:
            raise ValueError('Cached configuration exceeds size bound')
        config = config_path.read_bytes() if config_path.exists() else self._seed('build-config')
        if config is None:
            config = self._fetch(f'{BASE}/config/{config_key[:2]}/{config_key[2:4]}/{config_key}', 1024 * 1024)
        if len(config) > 1024 * 1024:
            raise ValueError('Configuration exceeds size bound')
        _verified(config, config_key, 'Build configuration')
        values = dict(line.split(' = ', 1) for line in config.decode().splitlines() if ' = ' in line)
        build_id = self.build.split('.')[-1]
        prefix = r'WOW-' + re.escape(build_id) + r'patch'
        if '.' in self.build:
            prefix += re.escape(self.build.rsplit('.', 1)[0]) + r'(?:_|$)'
        if not re.match(prefix, values.get('build-name', '')):
            raise ValueError('Configuration build-name does not match requested build')
        encoding_content, encoding_key = values['encoding'].split()
        root_key = values['root']
        if not HEX.fullmatch(root_key):
            raise ValueError('Invalid root key')
        encoding = self._decode_content(encoding_content, [encoding_key], 'encoding.blte')
        entry = encoding_find(encoding, {root_key}).get(root_key)
        if not entry:
            raise ValueError('Exact-build root not present in verified encoding')
        seed_root = self._seed('root.decoded')
        if seed_root is not None:
            root = _verified(seed_root, root_key, 'Seed root')
            _atomic(self.cache_root / 'content' / (root_key + '.bin'), root)
        else:
            root = self._decode_content(root_key, entry['encoding_keys'])
        if len(root) != entry['size']:
            raise ValueError('Root length does not match encoding entry')
        _atomic(config_path, config)
        self._encoding, self._root = encoding, root
        self.config_key, self.root_key = config_key, root_key

    @property
    def identity(self) -> dict:
        """Authenticated exact-build identity; preparing it may perform I/O."""
        with self._lock:
            self._prepare()
            return dict(product=self.product, build=self.build,
                        build_config_md5=self.config_key, root_content_md5=self.root_key)

    def get(self, file_data_id: int) -> Path:
        if isinstance(file_data_id, bool) or not isinstance(file_data_id, int) or not 0 < file_data_id < 2**32:
            raise ValueError('Invalid native file data ID')
        with self._lock:
            self._prepare()
            if file_data_id in self._paths:
                path, key = self._paths[file_data_id]
                if path.stat().st_size > MAX_BYTES:
                    raise ValueError('Cached asset exceeds size bound')
                _verified(path.read_bytes(), key, 'Cached asset')
                return path
            records = root_find(self._root, {file_data_id})
            if not records:
                raise FileNotFoundError(f'File ID {file_data_id} absent from {self.product} {self.build}')
            # Match pinned wow.export's CASC reader: first neutral/enUS root
            # entry with no LowViolence flag. Root order also distinguishes
            # native texture-resolution variants; both belong to this build.
            records = [record for record in records
                       if (record['locale_flags'] == 0 or record['locale_flags'] & 2)
                       and not record['content_flags'] & 0x80]
            if not records:
                raise ValueError(f'File ID {file_data_id}: no neutral/enUS root content')
            selected = records[0]
            key = selected['content_key']
            entry = self._encoding_entries.get(key)
            if entry is None:
                entry = encoding_find(self._encoding, {key}).get(key)
                if entry is None:
                    raise FileNotFoundError(f'No encoding for file ID {file_data_id}')
                self._encoding_entries[key] = entry
            transport = 'official CDN / verified content cache'
            try:
                raw = self._decode_content(key, entry['encoding_keys'])
            except ValueError as unavailable:
                # Some official assets are archive-only, not available as loose
                # encoding-key URLs. Wago is a byte transport, never build proof.
                transport = f'https://wago.tools/api/casc/{file_data_id}?download&version={self.build}&branch={self.product}'
                try:
                    raw = _verified(self._fetch(transport), key, 'Root-verified asset transport')
                except (ValueError, requests.RequestException) as failure:
                    raise ValueError(f'No verified native asset {file_data_id}: {unavailable}; {failure}') from failure
                _atomic(self.cache_root / 'content' / (key + '.bin'), raw)
            if len(raw) != entry['size']:
                raise ValueError('Asset length does not match encoding entry')
            path = self.cache_root / 'content' / (key + '.bin')
            proof = dict(product=self.product, build=self.build, file_data_id=file_data_id,
                         build_config_md5=self.config_key, root_content_md5=self.root_key,
                         content_md5=key, sha256=hashlib.sha256(raw).hexdigest(), bytes=len(raw),
                         transport=transport, exact_root_membership_verified=True,
                         root_selection='first neutral/enUS non-LowViolence entry in native root order',
                         selected_root_entry=selected, root_variants=records)
            _atomic(self.cache_root / 'proof' / self.product / self.build / (str(file_data_id)+'.json'),
                    (json.dumps(proof, indent=2)+'\n').encode())
            self._paths[file_data_id] = (path, key)
            return path
