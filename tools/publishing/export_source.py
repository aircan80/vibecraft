#!/usr/bin/env python3
"""Export only individually approved paths from one commit. Never copies Git history."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import subprocess

DENIED_PARTS = {'.git', '.local', 'target', '__pycache__', 'node_modules', 'fixtures'}
DENIED_SUFFIXES = {'.sqlite', '.db', '.png', '.jpg', '.jpeg', '.webp', '.ogg', '.mp3', '.wav', '.m2', '.blp', '.db2', '.wdc', '.zip', '.gz', '.xz', '.jar', '.glb', '.bin', '.ttf'}

def git(repo, *args):
    return subprocess.check_output(['git', '-C', str(repo), *args])

def export(repo, revision, manifest, destination):
    commit = git(repo, 'rev-parse', '--verify', revision + '^{commit}').decode().strip()
    if manifest.get('revision') != commit:
        raise ValueError('Manifest must pin the exact source commit')
    entries = manifest.get('files', [])
    if not entries:
        raise ValueError('Empty manifest')
    payloads, seen = [], set()
    for entry in entries:
        name = entry['path']
        path = PurePosixPath(name)
        if (path.is_absolute() or '..' in path.parts or str(path) != name or name in seen
                or set(path.parts) & DENIED_PARTS or path.suffix.lower() in DENIED_SUFFIXES):
            raise ValueError('Disallowed or duplicate export path: ' + name)
        if entry.get('approved') is not True or not entry.get('review_note', '').strip():
            raise ValueError('Every exported file needs explicit approval and review note: ' + name)
        seen.add(name)
        tree = git(repo, 'ls-tree', '-z', commit, '--', name).split(b'\0')
        record = [row for row in tree if row]
        if len(record) != 1 or record[0].split(b'\t')[0].split()[0] not in (b'100644', b'100755'):
            raise ValueError('Only regular committed files may be exported: ' + name)
        content = git(repo, 'show', commit + ':' + name)
        digest = hashlib.sha256(content).hexdigest()
        if digest != entry.get('sha256'):
            raise ValueError('Reviewed content hash differs: ' + name)
        if b'\0' in content:
            raise ValueError('Binary data needs a separately designed explicit exception: ' + name)
        payloads.append((name, content, record[0].startswith(b'100755')))
    destination = Path(destination)
    if destination.exists():
        raise ValueError('Destination must not already exist')
    destination.mkdir(parents=True)
    for name, content, executable in payloads:
        output = destination / name
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_bytes(content)
        output.chmod(0o755 if executable else 0o644)
    (destination / 'SOURCE-EXPORT-MANIFEST.json').write_text(json.dumps(manifest, indent=2) + '\n')
    return {'commit': commit, 'files': len(payloads), 'history_included': False}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, required=True)
    parser.add_argument('--revision', required=True)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        result = export(args.repo, args.revision, json.loads(args.manifest.read_text()), args.output)
    except (ValueError, KeyError, subprocess.CalledProcessError) as failure:
        parser.exit(2, str(failure) + '\n')
    print(json.dumps(result))

if __name__ == '__main__':
    main()
