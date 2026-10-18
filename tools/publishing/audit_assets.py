#!/usr/bin/env python3
"""Read-only publication gate. Reports candidates, not copyright ownership."""
import argparse, collections, datetime, io, json, pathlib, sqlite3, subprocess, sys, zipfile
ROOT = pathlib.Path(__file__).resolve().parents[2]
EXT = {'.png', '.jpg', '.jpeg', '.webp', '.blp', '.m2', '.skin', '.anim', '.ogg', '.mp3', '.wav', '.flac', '.obj', '.gltf', '.glb', '.fbx', '.sqlite', '.db', '.zip', '.jar', '.db2', '.dbc'}
SIGNATURES = [('png', b'\x89PNG\r\n\x1a\n'), ('jpeg', b'\xff\xd8\xff'), ('ogg', b'OggS'), ('blp', b'BLP'), ('wav', b'RIFF'), ('zip', b'PK\x03\x04')]
def git(*args):
    return subprocess.check_output(['git', '-C', str(ROOT), *args])
def candidate(p):
    return pathlib.PurePosixPath(p).suffix.lower() in EXT or p.startswith(('.local/', 'app/src/main/resources/data/npc-'))
def quote(s):
    return '"' + s.replace('"', '""') + '"'
def database(path):
    result = []
    with sqlite3.connect(path.as_uri() + '?mode=ro', uri=True) as conn:
        conn.execute('PRAGMA query_only=ON')
        tables = conn.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").fetchall()
        for (table,) in tables:
            for _, col, typ, *_ in conn.execute('PRAGMA table_info(' + quote(table) + ')'):
                if 'BLOB' not in typ.upper():
                    continue
                count, size = conn.execute(f'SELECT count(*),coalesce(sum(length({quote(col)})),0) FROM {quote(table)} WHERE typeof({quote(col)})=\'blob\'').fetchone()
                signatures = {}
                for label, magic in SIGNATURES:
                    n = conn.execute(f'SELECT count(*) FROM {quote(table)} WHERE substr({quote(col)},1,?)=?', (len(magic), magic)).fetchone()[0]
                    if n:
                        signatures[label] = n
                entry = dict(table=table, column=col, rows=count, bytes=size, signatures=signatures)
                if signatures.get('zip'):
                    archives = []
                    for (payload,) in conn.execute(f'SELECT {quote(col)} FROM {quote(table)} WHERE substr({quote(col)},1,4)=?', (b'PK\x03\x04',)):
                        with zipfile.ZipFile(io.BytesIO(payload)) as archive:
                            assets = [dict(path=f.filename, bytes=f.file_size) for f in archive.infolist() if pathlib.PurePosixPath(f.filename).suffix.lower() in EXT | {'.tga'}]
                            archives.append(dict(bytes=len(payload), candidates=assets))
                    entry['zip_archives'] = archives
                result.append(entry)
    return result
def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--output', type=pathlib.Path)
    ap.add_argument('--allow-findings', action='store_true', help='Inventory mode; default exits 1 when candidates need review')
    args = ap.parse_args()
    tracked = [p.decode() for p in git('ls-files', '-z').split(b'\0') if p]
    files = [dict(path=p, bytes=(ROOT / p).stat().st_size) for p in tracked if candidate(p) and (ROOT / p).is_file()]
    # Every reachable revision, including deleted files; metadata only, no blob extraction.
    objects = git('rev-list', '--objects', '--all').decode().splitlines()
    candidates = [dict(object_id=line.split(' ', 1)[0], path=line.split(' ', 1)[1]) for line in objects if ' ' in line and candidate(line.split(' ', 1)[1])]
    metadata = subprocess.check_output(['git', '-C', str(ROOT), 'cat-file', '--batch-check=%(objecttype) %(objectsize)'], input=''.join(row['object_id'] + '\n' for row in candidates).encode()).decode().splitlines()
    historical = [dict(row, bytes=int(info.split()[1])) for row, info in zip(candidates, metadata) if info.startswith('blob ')]
    groups = collections.defaultdict(lambda: dict(files=0, bytes=0))
    for row in files:
        suffix = pathlib.Path(row['path']).suffix.lower()
        groups[suffix]['files'] += 1
        groups[suffix]['bytes'] += row['bytes']
    dbs = {p: database(ROOT / p) for p in tracked if pathlib.Path(p).suffix.lower() in {'.sqlite', '.db'} and (ROOT / p).is_file()}
    report = dict(generated_at=datetime.datetime.now(datetime.timezone.utc).isoformat(), commit=git('rev-parse', 'HEAD').decode().strip(), scope='Tracked working-tree files and all locally reachable Git objects; no remote/unreachable objects or legal clearance', categories=dict(groups), tracked_candidates=files, historical_candidates=historical, sqlite_blob_columns=dbs, publication_gate='BLOCKED: assets and embedded data need explicit redistribution review' if files or historical else 'No known binary candidate found; not legal clearance')
    serialized = json.dumps(report, indent=2) + '\n'
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(serialized)
    else:
        print(serialized, end='')
    print(f'{len(files)} tracked candidate files; {len(historical)} reachable historical objects; {len(dbs)} SQLite databases inspected. {report["publication_gate"]}', file=sys.stderr)
    return 0 if args.allow_findings or not (files or historical) else 1
if __name__ == '__main__':
    sys.exit(main())
