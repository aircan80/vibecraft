#!/usr/bin/env python3
"""Build a reproducible exact-build NPC appearance catalog from reviewed native CSVs."""
import argparse,csv,hashlib,json,math
from pathlib import Path
TABLES=('Creature','CreatureDisplayInfo','CreatureModelData','CreatureDisplayInfoExtra')
SCHEMA='npc-appearance-v1'
def sha(data):return hashlib.sha256(data).hexdigest()
def integer(row,key):
 try:return int(row.get(key,''))
 except (ValueError,TypeError):return 0
def decimal(row,key):
 try:
  value=float(row.get(key,''));return value if math.isfinite(value) else None
 except (ValueError,TypeError):return None
def build(directory,attestation):
 source=json.loads(attestation.read_text());product=source['product'];version=source['build'];locale=source['locale']
 tables={};manifests=[]
 for table in TABLES:
  path=directory/(table+'.csv');data=path.read_bytes();digest=sha(data)
  declaration=next(r for r in source['requests'] if r['table']==table)
  if declaration['status']!=200 or declaration['sha256']!=digest:raise ValueError('Unattested source bytes: '+table)
  with path.open(newline='',encoding='utf-8-sig') as f:
   reader=csv.DictReader(f);rows={row['ID']:(line,row) for line,row in enumerate(reader,2)};columns=reader.fieldnames
  if len(rows)!=declaration['row_count'] or columns!=declaration['columns']:raise ValueError('Source shape differs: '+table)
  tables[table]=rows;manifests.append(dict(table=table,path=path.name,sha256=digest,rowCount=len(rows),columns=columns,url=declaration['url']))
 manifest_by_table={m['table']:m for m in manifests}
 catalog_id=sha(json.dumps(dict(schema=SCHEMA,product=product,build=version,locale=locale,sources=manifests),sort_keys=True,separators=(',',':')).encode())
 def evidence(table,key):
  line,row=tables[table][str(key)];m=manifest_by_table[table]
  return dict(table=table,recordId=str(key),sourcePath=m['path'],sourceLine=line,sourceSha256=m['sha256'],payload=row)
 appearances={}
 for npc_id,(_,creature) in tables['Creature'].items():
  variants=[]
  equipment=[integer(creature,'AlwaysItem_'+str(i)) for i in range(3)]
  for slot in range(4):
   display_id=integer(creature,'DisplayID_'+str(slot))
   if display_id<=0:continue
   display=tables['CreatureDisplayInfo'].get(str(display_id),(0,{}))[1]
   model_id=integer(display,'ModelID');model=tables['CreatureModelData'].get(str(model_id),(0,{}))[1]
   model_file=integer(model,'FileDataID');extra_id=integer(display,'ExtendedDisplayInfoID')
   textures=[integer(display,'TextureVariationFileDataID_'+str(i)) for i in range(4)]
   scale=decimal(display,'CreatureModelScale');model_scale=decimal(model,'ModelScale')
   reasons=[];state='MAPPED';e=[evidence('Creature',npc_id)]
   if display:e.append(evidence('CreatureDisplayInfo',display_id))
   if model:e.append(evidence('CreatureModelData',model_id))
   if extra_id>0 and str(extra_id) in tables['CreatureDisplayInfoExtra']:e.append(evidence('CreatureDisplayInfoExtra',extra_id))
   if not display:reasons.append('Display record is absent from this exact build.');state='UNRESOLVED'
   elif not model or model_file<=0:reasons.append('Model file mapping is absent from this exact build.');state='UNRESOLVED'
   elif scale is None or model_scale is None or scale<=0 or model_scale<=0:reasons.append('A positive display/model scale is not established.');state='UNRESOLVED'
   if extra_id>0:reasons.append('Humanoid customization and baked-material mapping are not implemented (extra '+str(extra_id)+').')
   if any(v>0 for v in equipment):reasons.append('Creature equipment mapping is not implemented.')
   if integer(model,'CreatureGeosetDataID')>0:reasons.append('Model geoset overrides are not implemented.')
   if integer(display,'ConditionalCreatureModelID')>0:reasons.append('Conditional model selection is not implemented.')
   if display and integer(display,'CreatureModelAlpha')!=255:reasons.append('Non-opaque display alpha is not supported by the generic preview request.')
   if reasons and state=='MAPPED':state='UNSUPPORTED'
   identity='\n'.join((SCHEMA,product,version,str(display_id),str(model_id),str(model_file),','.join(map(str,textures)),display.get('CreatureModelScale',''),model.get('ModelScale',''),str(extra_id),','.join(map(str,equipment))))
   variants.append(dict(displayId=display_id,displaySlot=slot,modelId=model_id,modelFileDataId=model_file,textures=textures,displayScale=scale,modelScale=model_scale,appearanceKey=sha(identity.encode()),state=state,reason=' '.join(reasons),evidence=e))
  states={v['state'] for v in variants}
  state='UNRESOLVED' if not variants else next(iter(states)) if len(states)==1 else 'PARTIAL'
  appearances[npc_id]=dict(product=product,build=version,npcId=int(npc_id),name=creature.get('Name_lang',''),state=state,variants=variants,evidence=[evidence('Creature',npc_id)],message='Native display variants; mapping does not attest that a preview has rendered.' if variants else 'No positive display IDs are recorded for this creature.',catalogId=catalog_id)
 return dict(schema=SCHEMA,catalogId=catalog_id,product=product,build=version,locale=locale,sources=manifests,appearances=appearances)
def main():
 p=argparse.ArgumentParser();p.add_argument('--source-dir',type=Path,required=True);p.add_argument('--attestation',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 result=build(a.source_dir,a.attestation);a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,ensure_ascii=False,sort_keys=True,separators=(',',':'))+'\n')
 print(json.dumps({'catalogId':result['catalogId'],'creatures':len(result['appearances']),'variants':sum(len(n['variants']) for n in result['appearances'].values())}))
if __name__=='__main__':main()
