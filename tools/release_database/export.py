#!/usr/bin/env python3
"""Developer-only, non-destructive export of a compact runtime SQLite dataset."""
import argparse
from contextlib import closing
import datetime
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import tempfile


def identifier(value):
    return '"' + value.replace('"', '""') + '"'


def digest_table(db, table):
    """Order-independent digest, including SQLite value types and duplicate rows."""
    total = 0
    count = 0
    for row in db.execute('SELECT * FROM ' + identifier(table)):
        values = [({'blob': value.hex()} if isinstance(value, bytes) else value) for value in row]
        encoded = json.dumps(values, ensure_ascii=True, separators=(',', ':')).encode()
        total = (total + int.from_bytes(hashlib.sha256(encoded).digest(), 'big')) % (1 << 256)
        count += 1
    return {'rows': count, 'sha256_multiset_sum': f'{total:064x}'}


def export(source, output):
    source, output = Path(source).resolve(), Path(output).absolute()
    if not source.is_file():
        raise ValueError('Source database must exist.')
    if output.exists() or output.is_symlink():
        raise ValueError('Output already exists; refusing to replace it.')
    if not output.parent.is_dir():
        raise ValueError('Output parent directory must exist.')
    # mode=ro and an explicit read transaction hold one consistent input snapshot.
    with closing(sqlite3.connect(source.as_uri() + '?mode=ro', uri=True)) as original:
        original.execute('BEGIN')
        tables = {r[0] for r in original.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        required = {'meta', 'source_record', 'source_archive_blob', 'source_archive_file'}
        if not required <= tables:
            raise ValueError('Not a supported development database: missing ' + ', '.join(sorted(required - tables)))
        if original.execute("SELECT 1 FROM meta WHERE key='release_dataset_profile'").fetchone():
            raise ValueError('Input is already a release export; use the development database.')
        if original.execute('PRAGMA foreign_key_check').fetchone():
            raise ValueError('Source has foreign-key violations; refusing to export.')
        if original.execute('PRAGMA quick_check').fetchall() != [('ok',)]:
            raise ValueError('Source integrity check failed.')
        report = {
            'profile': 'runtime-v1', 'created_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'source_bytes': source.stat().st_size,
            'build_id': (original.execute("SELECT value FROM meta WHERE key='build_id'").fetchone() or [None])[0],
            'omitted_raw_records': original.execute('SELECT count(*) FROM source_record').fetchone()[0],
            'omitted_archive_bytes': original.execute('SELECT coalesce(sum(length(bytes)),0) FROM source_archive_blob').fetchone()[0],
            'archive_blobs': original.execute('SELECT count(*) FROM source_archive_blob').fetchone()[0],
        }
        # Verify every retained table, not merely a few known entity counts. SQLite
        # FTS shadow tables are retained verbatim and included in this comparison.
        retained = sorted(tables - {'meta', 'source_record', 'source_archive_blob', 'sqlite_stat1', 'sqlite_stat4'})
        report['retained_tables'] = {name: digest_table(original, name) for name in retained}
        original_meta = dict(original.execute('SELECT key,value FROM meta'))
        fd, temporary = tempfile.mkstemp(prefix='.' + output.name + '-', dir=output.parent)
        os.close(fd)
        try:
            with closing(sqlite3.connect(temporary)) as release:
                original.backup(release)
                release.execute('PRAGMA foreign_keys=OFF')
                release.execute('DELETE FROM source_record')
                release.execute('DROP INDEX IF EXISTS field_knowledge_assertion_identity')
                # Keep SHA identities so source-file foreign keys and citations stay
                # valid. NULL explicitly means omitted; it never pretends to hash
                # as the original content. This schema is a runtime export only.
                release.execute('CREATE TABLE release_blob_temp (sha256 TEXT PRIMARY KEY, bytes BLOB CHECK(bytes IS NULL), original_size INTEGER NOT NULL, storage_state TEXT NOT NULL CHECK(storage_state=\'omitted-release\'))')
                release.execute("INSERT INTO release_blob_temp SELECT sha256,NULL,length(bytes),'omitted-release' FROM source_archive_blob")
                release.execute('DROP TABLE source_archive_blob')
                release.execute('ALTER TABLE release_blob_temp RENAME TO source_archive_blob')
                release.executemany('INSERT INTO meta(key,value) VALUES (?,?)', [
                    ('release_dataset_profile', 'runtime-v1'),
                    ('release_dataset_created_at', report['created_at']),
                    ('release_raw_records_omitted', str(report['omitted_raw_records'])),
                    ('release_archive_bytes_omitted', str(report['omitted_archive_bytes'])),
                    ('release_rebuild_supported', 'false'),
                ])
                release.commit()
                release.execute('VACUUM')
                release.execute('PRAGMA foreign_keys=ON')
                if release.execute('PRAGMA integrity_check').fetchall() != [('ok',)]:
                    raise ValueError('Export integrity check failed.')
                if release.execute('PRAGMA foreign_key_check').fetchone():
                    raise ValueError('Export foreign-key check failed.')
                if release.execute('SELECT count(*) FROM source_record').fetchone()[0]:
                    raise ValueError('Raw source records remain.')
                if release.execute('SELECT 1 FROM source_archive_blob WHERE bytes IS NOT NULL LIMIT 1').fetchone():
                    raise ValueError('Archived bytes remain.')
                for name, expected in report['retained_tables'].items():
                    if digest_table(release, name) != expected:
                        raise ValueError('Retained table changed: ' + name)
                actual_meta = dict(release.execute('SELECT key,value FROM meta'))
                if any(actual_meta.get(k) != v for k, v in original_meta.items()):
                    raise ValueError('Original build metadata changed.')
            report['release_bytes'] = Path(temporary).stat().st_size
            report['saved_bytes'] = report['source_bytes'] - report['release_bytes']
            report['saved_percent'] = round(100 * report['saved_bytes'] / report['source_bytes'], 2)
            report['validation'] = 'integrity, foreign keys, every retained table multiset digest, original metadata'
            # Atomic non-replacing publication, including protection from an output
            # created while export was running. Temporary/output share a directory.
            os.link(temporary, output)
        finally:
            Path(temporary).unlink(missing_ok=True)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    print(json.dumps(export(args.source, args.output), indent=2))


if __name__ == '__main__':
    main()
