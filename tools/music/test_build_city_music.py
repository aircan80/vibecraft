import csv
import gzip
import hashlib
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
from build_city_music import generate, BUILD

class CityMusicCatalogTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);self.source=self.root/'source';self.source.mkdir();self.output=self.root/'output'
        self.db=self.root/'db.sqlite'
        with sqlite3.connect(self.db) as c:
            c.executescript("CREATE TABLE zone(id INTEGER,name TEXT,kind TEXT);CREATE TABLE client_table_dataset(snapshot_id TEXT,build TEXT);CREATE TABLE client_table_row(snapshot_id TEXT,table_name TEXT,row_id TEXT,payload TEXT,source_path TEXT,source_line INTEGER);INSERT INTO zone VALUES(1497,'Undercity','city');")
            c.execute('INSERT INTO client_table_dataset VALUES(?,?)',('native',BUILD))
            c.execute('INSERT INTO client_table_row VALUES(?,?,?,?,?,?)',('native','AreaTable','1497',json.dumps(dict(ID='1497',ZoneMusic='0')),'AreaTable.csv',77))
        self.write('ZoneMusic',['ID','Sounds_0','Sounds_1'],[['182','1','2']])
        self.write('SoundKit',['ID','VolumeFloat'],[['1','0.7'],['2','0.8']])
        self.write('SoundKitEntry',['ID','SoundKitID','FileDataID','Frequency','Volume','PlayerConditionID'],[['9','1','100','1','1','0'],['10','2','200','1','0.5','0'],['11','2','201','1','1','99']])
        self.write('WMOAreaTable',['ID','AreaTableID','WMOGroupID','ZoneMusic'],[['5','1497','-1','182'],['6','1497','22','777']])
    def write(self,table,fields,rows):
        with (self.source/(table+'.csv')).open('w',newline='') as out:
            writer=csv.writer(out);writer.writerow(fields);writer.writerows(rows)
    def test_native_wmo_assignment_and_day_night_variants_keep_evidence(self):
        result=generate(self.db,self.source,self.output);city=result['cities'][0]
        self.assertEqual(city['zoneMusicId'],182)
        self.assertEqual([t['fileDataId'] for t in city['variants']['day']],[100])
        self.assertEqual([t['fileDataId'] for t in city['variants']['night']],[200,201])
        self.assertEqual(city['variants']['night'][1]['playerConditionId'],99)
        self.assertEqual(city['areaEvidence']['line'],77)
        original=(self.source/'SoundKitEntry.csv').read_bytes()
        self.assertEqual(gzip.decompress((self.output/'SoundKitEntry.csv.gz').read_bytes()),original)
        self.assertEqual(result['tables']['SoundKitEntry']['sha256'],hashlib.sha256(original).hexdigest())
    def test_ambiguous_indoor_assignment_stays_unavailable(self):
        self.write('WMOAreaTable',['ID','AreaTableID','WMOGroupID','ZoneMusic'],[['5','1497','-1','182'],['6','1497','-1','999']])
        city=generate(self.db,self.source,self.output)['cities'][0]
        self.assertEqual(city['zoneMusicId'],0)
        self.assertEqual(city['variants'],{})

if __name__=='__main__':unittest.main()
