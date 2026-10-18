#!/usr/bin/env python3
"""Plan baked skin material dependencies; this is NOT a humanoid render request."""
import argparse,collections,hashlib,json
from pathlib import Path
PRODUCT='wow_classic_beta';BUILD='1.60.1.70450'
def plan(catalog,textures,proof):
 for value in (catalog,textures,proof):
  if (value.get('product'),value.get('build'))!=(PRODUCT,BUILD):raise ValueError('Wrong product/build')
 if textures.get('table')!='TextureFileData':raise ValueError('Wrong table')
 if proof.get('file_data_id')!=982459 or proof.get('exact_root_membership_verified') is not True:raise ValueError('Unverified TextureFileData')
 if proof.get('build_config_md5')!='84bea4233331e390fda8724c6f91ffa5' or proof.get('root_content_md5')!='8a0ca94be2f6ace93991c04ca6e19b57':raise ValueError('Wrong official build/root')
 if textures.get('sourceSha256')!=proof.get('sha256'):raise ValueError('Decoded input differs from root-verified bytes')
 by_material=collections.defaultdict(set)
 for row in textures['rows']:
  if row['UsageType']==0 and row['MaterialResourcesID']>0:by_material[row['MaterialResourcesID']].add(row['FileDataID'])
 result=[]
 for npc in catalog['appearances'].values():
  for variant in npc['variants']:
   extras=[e for e in variant['evidence'] if e['table']=='CreatureDisplayInfoExtra']
   if not extras:continue
   row=extras[0]['payload'];materials=[]
   for field in ('BakeMaterialResourcesID','HDBakeMaterialResourcesID'):
    resource=int(row[field]);candidates=sorted(by_material[resource])
    materials.append(dict(field=field,resourceId=resource,fileDataIds=candidates,state='UNIQUE' if len(candidates)==1 else 'MISSING' if not candidates else 'AMBIGUOUS'))
   result.append(dict(npcId=npc['npcId'],displayId=variant['displayId'],extraId=int(row['ID']),materials=materials,
    state='DEPENDENCY_PLAN_ONLY', blockers=['NPC customization options and geosets are not assembled.','Equipment textures and attachments are not assembled.','Baked skin atlas is not a portrait and must not be displayed as one.']))
 return dict(schema='humanoid-material-plan-v1',product=PRODUCT,build=BUILD,catalogId=catalog['catalogId'],textureSourceSha256=proof['sha256'],appearances=result)
def main():
 p=argparse.ArgumentParser(description=__doc__)
 for name in ('catalog','textures','proof','output'):p.add_argument('--'+name,type=Path,required=True)
 a=p.parse_args();result=plan(*[json.loads(getattr(a,n).read_text()) for n in ('catalog','textures','proof')]);a.output.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({'humanoidVariants':len(result['appearances'])}))
if __name__=='__main__':main()
