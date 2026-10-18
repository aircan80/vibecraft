#!/usr/bin/env python3
"""Build exact-client city soundtrack links; does not guess absent assignments."""
import argparse,csv,gzip,hashlib,json,sqlite3
from pathlib import Path
BUILD='1.60.1.70450'
PRODUCT='wow_classic_beta'

def generate(database, source, output):
    source,output=Path(source),Path(output);output.mkdir(parents=True,exist_ok=True)
    tables={};provenance={}
    for table in ('ZoneMusic','SoundKit','SoundKitEntry','WMOAreaTable'):
        raw=(source/(table+'.csv')).read_bytes()
        tables[table]=list(csv.DictReader(raw.decode('utf-8-sig').splitlines()))
        provenance[table]=dict(url=f'https://wago.tools/db2/{table}/csv?build={BUILD}',sha256=hashlib.sha256(raw).hexdigest(),rows=len(tables[table]))
        (output/(table+'.csv.gz')).write_bytes(gzip.compress(raw,mtime=0))
    music={int(r['ID']):r for r in tables['ZoneMusic']};kits={int(r['ID']):r for r in tables['SoundKit']}
    entries={}
    for r in tables['SoundKitEntry']:entries.setdefault(int(r['SoundKitID']),[]).append(r)
    conn=sqlite3.connect(f'file:{Path(database).resolve()}?mode=ro',uri=True)
    cities=[]
    for city,name in conn.execute("SELECT id,name FROM zone WHERE kind='city' ORDER BY id"):
        row=conn.execute("SELECT r.payload,r.source_path,r.source_line,r.snapshot_id FROM client_table_row r JOIN client_table_dataset d USING(snapshot_id) WHERE r.table_name='AreaTable' AND r.row_id=? AND d.build=?",(str(city),BUILD)).fetchone()
        if row is None:continue
        area=json.loads(row[0]);music_id=int(area['ZoneMusic'])
        wmo=[]
        if not music_id:
            wmo=[r for r in tables['WMOAreaTable'] if int(r['AreaTableID'])==city and r['WMOGroupID']=='-1' and int(r['ZoneMusic'])>0]
            assignments={int(r['ZoneMusic']) for r in wmo}
            if len(assignments)==1:music_id=assignments.pop()
        assignment=music.get(music_id);variants={};selected_kits={}
        if assignment:
            for phase,field in [('day','Sounds_0'),('night','Sounds_1')]:
                kit=int(assignment[field]);selected_kits[str(kit)]=kits.get(kit)
                variants[phase]=[dict(fileDataId=int(r['FileDataID']),entryId=int(r['ID']),soundKitId=kit,frequency=int(r['Frequency']),volume=float(r['Volume']),playerConditionId=int(r['PlayerConditionID'])) for r in entries.get(kit,[]) if int(r['FileDataID'])>0]
        cities.append(dict(id=city,name=name,zoneMusicId=music_id,variants=variants,area=area,areaEvidence=dict(path=row[1],line=row[2],snapshot=row[3]),wmoDefaults=wmo,zoneMusic=assignment,soundKits=selected_kits))
    conn.close()
    result=dict(product=PRODUCT,build=BUILD,tables=provenance,cities=cities)
    (output/'catalog.json').write_text(json.dumps(result,indent=2)+'\n')
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--database',required=True);p.add_argument('--source',required=True);p.add_argument('--output',required=True);a=p.parse_args()
    report=generate(a.database,a.source,a.output)
    print([(r['name'],r['zoneMusicId'],{p:len(v) for p,v in r['variants'].items()}) for r in report['cities']])
