#!/usr/bin/env python3
"""Create a local release inventory; never uploads or approves publication."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

VERSION = re.compile(r"\d+\.\d+\.\d+(?:-[a-z][a-z0-9.-]*)?\Z")
SHA = re.compile(r"[a-f0-9]{64}\Z")
PLACEHOLDER = re.compile(r"(?i)(todo|tbd|placeholder|replace.?me|actual_|full_40_|<|>)")


def required(obj, key):
    value = obj.get(key)
    if not isinstance(value, str) or not value.strip() or PLACEHOLDER.search(value):
        raise ValueError(f"{key}: non-placeholder text required")
    return value


def digest_file(path):
    if any(character in path.name for character in ('\n', '\r', '\t', '\\')):
        raise ValueError('File names must be safe for checksum manifests')
    if not path.is_file() or path.is_symlink():
        raise ValueError(f"Regular, non-symlink file required: {path}")
    size = path.stat().st_size
    if size == 0:
        raise ValueError(f"Empty file: {path}")
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return {'filename': path.name, 'bytes': size, 'sha256': digest.hexdigest()}


def resolve(base, value):
    path = Path(value)
    return path if path.is_absolute() else base / path


def inventory(spec, base):
    version = required(spec, 'version')
    if not VERSION.fullmatch(version) or 'snapshot' in version.lower():
        raise ValueError('version must be a concrete release/prerelease version, not SNAPSHOT')
    commit = required(spec, 'source_commit')
    if not re.fullmatch(r'[a-f0-9]{40}', commit):
        raise ValueError('source_commit must be the full 40-character commit')
    database = spec.get('database', {})
    db = digest_file(resolve(base, required(database, 'path')))
    expected = required(database, 'sha256')
    if not SHA.fullmatch(expected) or expected != db['sha256']:
        raise ValueError('Database SHA256 does not match explicitly supplied expected hash')
    db.update(build_id=required(database, 'build_id'), product=required(database, 'product'))
    if db['product'] != 'wow_classic_beta':
        raise ValueError('This release train requires the Forever wow_classic_beta product')
    evidence = spec.get('evidence')
    if not isinstance(evidence, list) or not evidence:
        raise ValueError('At least one evidence record is required')
    evidence_out = []
    for record in evidence:
        name = required(record, 'name')
        result = required(record, 'result')
        if result not in ('passed', 'failed', 'not-run'):
            raise ValueError('Evidence result must be passed, failed or not-run')
        item = {'name': name, 'result': result}
        if result != 'not-run':
            item.update(digest_file(resolve(base, required(record, 'path'))))
        else:
            item['reason'] = required(record, 'reason')
        evidence_out.append(item)
    artifacts = spec.get('artifacts')
    if not isinstance(artifacts, list) or not artifacts:
        raise ValueError('At least one artifact is required')
    seen, output = set(), []
    for artifact in artifacts:
        os_name, arch = required(artifact, 'os'), required(artifact, 'arch')
        if os_name not in ('linux', 'windows', 'macos') or arch not in ('x64', 'arm64'):
            raise ValueError('Unsupported OS/architecture')
        key = (os_name, arch)
        if key in seen:
            raise ValueError('Duplicate OS/architecture artifact')
        seen.add(key)
        if artifact.get('includes_database') is not True or artifact.get('bundled_java') is not True:
            raise ValueError('End-user artifacts must include gameplay database and Java runtime')
        test = required(artifact, 'launch_test')
        if test not in ('passed', 'failed', 'not-run'):
            raise ValueError('launch_test must be passed, failed or not-run')
        item = digest_file(resolve(base, required(artifact, 'path')))
        item.update(os=os_name, arch=arch, includes_database=True, bundled_java=True,
                    java_version=required(artifact, 'java_version'), launch_test=test)
        if test == 'passed':
            index = artifact.get('launch_evidence_index')
            if type(index) is not int or not 0 <= index < len(evidence_out) or evidence_out[index]['result'] != 'passed':
                raise ValueError('Passed launch requires a passed evidence record index')
            item['launch_evidence_index'] = index
        else:
            item['launch_note'] = required(artifact, 'launch_note')
        if item['filename'] in {row['filename'] for row in output}:
            raise ValueError('Artifact filenames must be unique')
        output.append(item)
    return {'schema_version': 1, 'version': version, 'source_commit': commit,
            'publication_status': 'local-inventory-only',
            'verification_scope': 'Hashes verify supplied files. Contents, platform claims and publication rights require separate review.',
            'database': db, 'evidence': evidence_out, 'artifacts': output}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('spec', type=Path)
    parser.add_argument('--output', type=Path, required=True, help='New output directory (must not exist)')
    args = parser.parse_args()
    try:
        if args.output.exists():
            raise ValueError('Output directory already exists; refusing to overwrite release evidence')
        result = inventory(json.loads(args.spec.read_text()), args.spec.parent)
        args.output.mkdir(parents=True)
        (args.output / 'manifest.json').write_text(json.dumps(result, indent=2) + '\n')
        (args.output / 'SHA256SUMS').write_text(''.join(f"{row['sha256']}  {row['filename']}\n" for row in result['artifacts']))
    except (OSError, ValueError, TypeError, KeyError) as exc:
        print(f'Inventory failed: {exc}', file=sys.stderr)
        return 1
    print(f"Local inventory written: {args.output}; publication is not approved or performed.")
    return 0


if __name__ == '__main__':
    sys.exit(main())
