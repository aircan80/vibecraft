#!/usr/bin/env python3
"""Audit exact-build NPC→display hotfix links without mutating application data.

Unlike the DB2 on-disk relation section, this table's hotfix payload includes
its trailing CreatureID relation. The supported field order is checked before
that one relation is made inline; other layouts fail closed.
"""
import argparse
import collections
import csv
import hashlib
import json
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'clientdata'))
from clientdata import dbd
from clientdata.hotfix import header,entries,table_hash,VALID,INVALID,REMOVED
TABLE='CreatureXDisplayInfo'
EXPECTED_COLUMNS=('ID','CreatureDisplayInfoID','Probability','Scale','OrderIndex','Field_11_0_0_54210_004','CreatureID')


def audit(cache,definition,build,baseline=None,targets=()):
    if header(cache)[1]!=int(build.split('.')[-1]):raise ValueError('Hotfix header does not match exact requested build')
    fields=dbd.layout(definition,build)
    if tuple(field.name for field in fields)!=EXPECTED_COLUMNS:raise ValueError('Unsupported CreatureXDisplayInfo layout')
    relation=fields[-1]
    if relation.annotations!=('noninline','relation') or relation.size!=32:raise ValueError('Unsupported CreatureID relation layout')
    fields=fields[:-1]+[relation._replace(annotations=('relation',))]
    pending={};counts=collections.Counter();raw_rows=[]
    for sequence,entry in enumerate(entries(cache)):
        if entry.table_hash!=table_hash(TABLE):continue
        counts[entry.status]+=1
        raw_rows.append(dict(record_id=entry.record_id,push=entry.push,status=entry.status,region=entry.region,sequence=sequence,data_hex=entry.data.hex()))
        pushes=pending.setdefault(entry.record_id,{})
        if entry.status==VALID:pushes[entry.push]=(sequence,entry)
        elif entry.status==INVALID:pushes.pop(entry.push,None)
        elif entry.status==REMOVED:pushes.clear() # Tombstones do not become appearance evidence.
    base=None if baseline is None else {(int(row['CreatureID']),int(row['CreatureDisplayInfoID'])) for row in baseline}
    mappings=[]
    for record,pushes in sorted(pending.items()):
        if not pushes:continue
        sequence,entry=max(pushes.values(),key=lambda pair:pair[0]);row=dbd.decode(fields,entry.data,record)
        if row['CreatureID']<=0 or row['CreatureDisplayInfoID']<=0:raise ValueError('Invalid native identity in hotfix')
        mappings.append(dict(row,source_push=entry.push,source_region=entry.region,source_sequence=sequence,
                             new_vs_baseline=None if base is None else (row['CreatureID'],row['CreatureDisplayInfoID']) not in base))
    return dict(build=build,table=TABLE,cache_sha256=hashlib.sha256(cache).hexdigest(),
                definition_sha256=hashlib.sha256(definition.encode()).hexdigest(),definition_reference_commit=dbd.DBD_COMMIT,
                status_counts=dict(counts),mappings=mappings,raw_records=raw_rows,
                targets={str(target):[row for row in mappings if row['CreatureID']==target] for target in targets})


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--cache',type=Path,required=True);p.add_argument('--cache-sha256',required=True)
    p.add_argument('--definition',type=Path,required=True);p.add_argument('--definition-sha256',required=True)
    p.add_argument('--build',required=True);p.add_argument('--baseline',type=Path);p.add_argument('--npc',type=int,action='append',default=[])
    p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    cache=a.cache.read_bytes();raw_definition=a.definition.read_bytes()
    if hashlib.sha256(cache).hexdigest()!=a.cache_sha256:raise ValueError('Cache SHA256 mismatch')
    if hashlib.sha256(raw_definition).hexdigest()!=a.definition_sha256:raise ValueError('Definition SHA256 mismatch')
    baseline=None
    if a.baseline:
        with a.baseline.open(newline='') as source:baseline=list(csv.DictReader(source))
    result=audit(cache,raw_definition.decode(),a.build,baseline,a.npc)
    result['source']=dict(cache_path=str(a.cache),cache_url='https://storage.googleapis.com/raidbots-static/wow/classic_beta/enUS/DBCache.bin',
                          definition_url=dbd.DBD_BASE_URL+dbd.dbd_path(TABLE),baseline_path=str(a.baseline) if a.baseline else None)
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,indent=2,allow_nan=False)+'\n')
    print(json.dumps(dict(mappings=len(result['mappings']),targets=result['targets'])))

if __name__=='__main__':main()
