#!/usr/bin/env python3
"""Prepare hash-pinned preview input. Developer/CI tooling only; never uploads."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import sqlite3
import sys
import urllib.parse
import urllib.request
import zipfile

FIXTURE_SHA = 'f527ff9ea91b6dba8a8a710d33a187d864afe4ee37454b4e4e1b5610fbd2c772'
MAX_ARCHIVE = 2 * 1024**3


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024**2), b''):
            digest.update(block)
    return digest.hexdigest()


def pins(path):
    entries = {}
    for line in path.read_text().splitlines():
        if not line or line.startswith('#'):
            continue
        digest, size, name = line.split('\t')
        if not re.fullmatch('[a-f0-9]{64}', digest) or name in entries or not name.startswith('data/') or '..' in name.split('/') or '\\' in name:
            raise ValueError('Invalid reviewed input manifest')
        entries[name] = (digest, int(size))
    if 'data/wowforever.sqlite' not in entries:
        raise ValueError('Compact gameplay database must be pinned')
    return entries


class HttpsRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        validate_url(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def validate_url(url):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError('A credential-free HTTPS download URL is required')


def download(url, expected, target, limit):
    validate_url(url)
    if not re.fullmatch('[a-f0-9]{64}', expected):
        raise ValueError('An explicit lowercase SHA256 is required')
    if target.exists():
        raise ValueError('Download destination already exists')
    partial = target.with_suffix(target.suffix + '.part')
    try:
        opener = urllib.request.build_opener(HttpsRedirect())
        with opener.open(url, timeout=120) as response, partial.open('xb') as out:
            total = 0
            while block := response.read(1024**2):
                total += len(block)
                if total > limit:
                    raise ValueError('Download exceeds size limit')
                out.write(block)
        if sha(partial) != expected:
            raise ValueError('Downloaded content does not match expected SHA256')
        partial.replace(target)
    finally:
        partial.unlink(missing_ok=True)


def unpack_reviewed(archive, target, manifest):
    expected = pins(manifest)
    if target.exists():
        raise ValueError('Data destination already exists')
    with zipfile.ZipFile(archive) as zipped:
        files = [info for info in zipped.infolist() if not info.is_dir()]
        if len(files) != len(expected) or {info.filename for info in files} != set(expected):
            raise ValueError('Dataset ZIP must contain exactly the reviewed five paths')
        for info in files:
            digest, size = expected[info.filename]
            if info.file_size != size or (info.external_attr >> 16) & 0o170000 == 0o120000:
                raise ValueError('Dataset entry size/type differs from reviewed manifest')
        target.mkdir(parents=True)
        try:
            for info in files:
                output = target / info.filename
                output.parent.mkdir(parents=True, exist_ok=True)
                with zipped.open(info) as source, output.open('xb') as stream:
                    shutil.copyfileobj(source, stream, 1024**2)
                if sha(output) != expected[info.filename][0]:
                    raise ValueError('Dataset entry SHA256 differs from reviewed manifest')
            db = target / 'data/wowforever.sqlite'
            with sqlite3.connect(db.resolve().as_uri() + '?mode=ro', uri=True) as connection:
                meta = dict(connection.execute('SELECT key,value FROM meta'))
                if meta.get('client_product') != 'wow_classic_beta' or meta.get('release_dataset_profile') != 'runtime-v1':
                    raise ValueError('Dataset is not the reviewed Forever runtime export')
                if connection.execute('PRAGMA integrity_check').fetchall() != [('ok',)]:
                    raise ValueError('Dataset integrity check failed')
        except Exception:
            shutil.rmtree(target)
            raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--fixture', required=True, type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r'0\.4\.1-preview\.[1-9][0-9]*', os.environ.get('PRODUCT_VERSION', '')):
        print('Use a concrete 0.4.1-preview.N version; native numeric metadata is pinned to this train.', file=sys.stderr)
        return 1
    args.output.mkdir(parents=True, exist_ok=True)
    archive = args.output / 'reviewed-data.zip'
    try:
        download(os.environ.get('DATASET_URL', ''), os.environ.get('DATASET_SHA256', ''), archive, MAX_ARCHIVE)
        unpack_reviewed(archive, args.output / 'reviewed', Path(__file__).with_name('preview-data.tsv'))
        if not args.fixture.exists():
            args.fixture.parent.mkdir(parents=True, exist_ok=True)
            supplied_sha = os.environ.get('TEST_FIXTURE_SHA256', '')
            if supplied_sha != FIXTURE_SHA:
                raise ValueError('Missing original full-test DB; provide the pinned fixture URL and SHA256')
            download(os.environ.get('TEST_FIXTURE_URL', ''), supplied_sha, args.fixture, 3 * 1024**3)
        if sha(args.fixture) != FIXTURE_SHA:
            raise ValueError('Full-test development DB is not the pinned original fixture')
    except Exception:
        # Do not leak signed URL/query strings through network exceptions into logs.
        print('Preview input preparation failed. Check HTTPS URLs, pinned SHA256 values, ZIP paths and fixture availability.', file=sys.stderr)
        return 1
    print('Reviewed compact data and original full-test fixture verified. No URLs were logged.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
