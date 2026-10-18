#!/usr/bin/env python3
"""Inventory native direct portrait fields; never infer an NPC/display relation."""
import argparse,csv,hashlib,json
from pathlib import Path
FIELDS=('PortraitTextureFileDataID','PortraitCreatureDisplayInfoID')
def inventory(catalog,records):
 mapped={v['displayId'] for n in catalog['appearances'].values() for v in n['variants']}
 fields={}
 for field in FIELDS:
  positive=[{'displayId':int(r['ID']),'value':int(r[field])} for r in records if int(r[field])>0]
  fields[field]=dict(positiveRecords=len(positive),mappedDisplayRecords=sum(r['displayId'] in mapped for r in positive),records=positive)
 return dict(schema='native-portrait-field-inventory-v1',product=catalog['product'],build=catalog['build'],catalogId=catalog['catalogId'],catalogNpcCount=len(catalog['appearances']),catalogDisplaySlots=sum(len(n['variants']) for n in catalog['appearances'].values()),displayTableRows=len(records),fields=fields,
  conclusion='Native portrait fields do not establish missing NPC-to-display relationships. A portrait texture also needs exact-root asset validation before use.')
def main():
 p=argparse.ArgumentParser(description=__doc__)
 for n in ('catalog','display-csv','attestation','output'):p.add_argument('--'+n,type=Path,required=True)
 a=p.parse_args();c=json.loads(a.catalog.read_text());att=json.loads(a.attestation.read_text())
 if (c['product'],c['build'])!=(att['product'],att['build']):raise ValueError('Mixed source builds')
 record=next(r for r in att['requests'] if r['table']=='CreatureDisplayInfo');sha=hashlib.sha256(a.display_csv.read_bytes()).hexdigest()
 if record['status']!=200 or record['sha256']!=sha:raise ValueError('Unattested display table bytes')
 with a.display_csv.open(newline='',encoding='utf-8-sig') as f:r=list(csv.DictReader(f))
 if len(r)!=record['row_count']:raise ValueError('Source shape differs')
 result=inventory(c,r);result['displayCsvSha256']=sha;result['displayCsvSourceUrl']=record['url'];a.output.write_text(json.dumps(result,indent=2)+'\n')
if __name__=='__main__':main()
