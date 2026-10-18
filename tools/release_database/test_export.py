import importlib.util
from contextlib import closing
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('release_export', Path(__file__).with_name('export.py'))
EXPORT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(EXPORT)


class ReleaseExportTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.source, self.output = self.root / 'dev.sqlite', self.root / 'runtime.sqlite'
        with closing(sqlite3.connect(self.source)) as db:
            db.executescript('''
                CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT);
                INSERT INTO meta VALUES ('schema_version','4'),('build_id','test-build');
                CREATE TABLE item(id INTEGER PRIMARY KEY,name TEXT);
                INSERT INTO item VALUES (1,'Keep item');
                CREATE TABLE source_record(id INTEGER PRIMARY KEY,payload TEXT);
                INSERT INTO source_record VALUES (1,'Remove raw source');
                CREATE TABLE source_archive_blob(sha256 TEXT PRIMARY KEY,bytes BLOB NOT NULL);
                INSERT INTO source_archive_blob VALUES ('hash',x'01020304');
                CREATE TABLE source_archive_file(path TEXT PRIMARY KEY,sha256 TEXT REFERENCES source_archive_blob(sha256));
                INSERT INTO source_archive_file VALUES ('original.json','hash');
                CREATE TABLE field_knowledge_assertion(assertion_id INTEGER PRIMARY KEY,value TEXT);
                CREATE UNIQUE INDEX field_knowledge_assertion_identity ON field_knowledge_assertion(value);
                INSERT INTO field_knowledge_assertion VALUES (1,'selected'),(2,'conflicting');
                CREATE TABLE field_knowledge(assertion_id INTEGER REFERENCES field_knowledge_assertion(assertion_id));
                INSERT INTO field_knowledge VALUES (1);
                CREATE TABLE client_table_row(payload TEXT);
                INSERT INTO client_table_row VALUES ('{"Name":"Needed at runtime"}');
                CREATE VIRTUAL TABLE search_index USING fts5(name);
                INSERT INTO search_index VALUES ('Keep item');
            ''')
        self.before = self.source.read_bytes()

    def tearDown(self):
        self.temp.cleanup()

    def test_export_preserves_runtime_and_conflicts_and_search(self):
        report = EXPORT.export(self.source, self.output)
        self.assertEqual(self.before, self.source.read_bytes())
        with closing(sqlite3.connect(self.output)) as db:
            self.assertEqual([(None, 4, 'omitted-release')], db.execute('SELECT bytes,original_size,storage_state FROM source_archive_blob').fetchall())
            self.assertEqual(0, db.execute('SELECT count(*) FROM source_record').fetchone()[0])
            self.assertEqual(2, db.execute('SELECT count(*) FROM field_knowledge_assertion').fetchone()[0])
            self.assertEqual([], db.execute('PRAGMA foreign_key_check').fetchall())
            self.assertEqual([('Keep item',)], db.execute("SELECT name FROM search_index WHERE search_index MATCH 'Keep'").fetchall())
            self.assertEqual(('runtime-v1',), db.execute("SELECT value FROM meta WHERE key='release_dataset_profile'").fetchone())
        self.assertEqual(4, report['omitted_archive_bytes'])
        self.assertEqual(1, report['omitted_raw_records'])
        json.dumps(report)

    def test_existing_output_is_never_overwritten(self):
        self.output.write_bytes(b'keep')
        with self.assertRaisesRegex(ValueError, 'already exists'):
            EXPORT.export(self.source, self.output)
        self.assertEqual(b'keep', self.output.read_bytes())

    def test_reexport_is_rejected(self):
        EXPORT.export(self.source, self.output)
        with self.assertRaisesRegex(ValueError, 'already a release'):
            EXPORT.export(self.output, self.root / 'again.sqlite')
        self.assertFalse((self.root / 'again.sqlite').exists())

    def test_invalid_foreign_keys_do_not_publish(self):
        with closing(sqlite3.connect(self.source)) as db:
            db.execute("UPDATE source_archive_file SET sha256='missing'")
            db.commit()
        with self.assertRaisesRegex(ValueError, 'foreign-key'):
            EXPORT.export(self.source, self.output)
        self.assertFalse(self.output.exists())

    def test_retained_row_changes_detected(self):
        original_digest = EXPORT.digest_table
        calls = {}
        def corrupted_digest(db, table):
            value = original_digest(db, table)
            calls[table] = calls.get(table, 0) + 1
            if table == 'item' and calls[table] == 2:
                return {'rows': 999, 'sha256_multiset_sum': 'bad'}
            return value
        EXPORT.digest_table = corrupted_digest
        try:
            with self.assertRaisesRegex(ValueError, 'Retained table changed: item'):
                EXPORT.export(self.source, self.output)
        finally:
            EXPORT.digest_table = original_digest
        self.assertFalse(self.output.exists())
        self.assertEqual(self.before, self.source.read_bytes())
        self.assertEqual([], list(self.root.glob('.runtime.sqlite-*')))


if __name__ == '__main__':
    unittest.main()
