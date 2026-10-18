#!/usr/bin/env python3
"""Acquire and decode exact Forever portrait composition inputs; missing data stays explicit."""
import argparse,hashlib,importlib,json,subprocess,sys,urllib.request
from pathlib import Path
TABLES={'TextureFileData':982459,'NPCModelItemSlotDisplayInfo':1340661,'CreatureDisplayInfoOption':3692043,'ChrCustomizationChoice':3450554,'ChrCustomizationElement':3512765,'ChrCustomizationGeoset':3456171,'ChrCustomizationOption':3384247,'ItemDisplayInfo':1266429,'ChrModel':3384313,'ChrModelMaterial':3566562,'ChrCustomizationMaterial':3459652,'ChrModelTextureLayer':3548976,'ModelFileData':1337833,'ComponentModelFileData':1349053,'HelmetGeosetData':2821752}
DBD_COMMIT='b9a63096cdd99b26967f17f9515377ae62cadfd7'
def main():
 p=argparse.ArgumentParser(description=__doc__)
 for name in ('native-tools','metadata','cache','wow-export','node','output'):p.add_argument('--'+name,type=Path,required=True)
 p.add_argument('--tables',nargs='+',choices=TABLES,default=list(TABLES));a=p.parse_args()
 sys.path.insert(0,str(a.native_tools.resolve()));NativeAssets=importlib.import_module('native_assets').NativeAssets
 store=NativeAssets(a.cache,'wow_classic_beta','1.60.1.70450',metadata_dir=str(a.metadata));a.output.mkdir(parents=True,exist_ok=True);records=[]
 for table in a.tables:
  row={'table':table,'fileDataId':TABLES[table]}
  try:
   binary=store.get(TABLES[table]);definition=a.output/(table+'.dbd')
   url=f'https://raw.githubusercontent.com/wowdev/WoWDBDefs/{DBD_COMMIT}/definitions/{table}.dbd'
   definition.write_bytes(urllib.request.urlopen(url,timeout=45).read())
   target=a.output/(table+'.json')
   subprocess.run([str(a.node.resolve()),str(Path(__file__).with_name('decode-native-table.cjs')),str(a.wow_export.resolve()),table,str(binary.resolve()),str(definition.resolve()),str(target.resolve())],check=True)
   decoded=json.loads(target.read_text());row.update(status='VERIFIED_DECODED',inputSha256=decoded['sourceSha256'],definitionSha256=decoded['definitionSha256'],definitionUrl=url,rowCount=len(decoded['rows']),skippedEncryptedSections=decoded['skippedEncryptedSections'],decodedSha256=hashlib.sha256(target.read_bytes()).hexdigest())
  except Exception as failure:row.update(status='UNAVAILABLE',error=str(failure))
  records.append(row);(a.output/'acquisition.json').write_text(json.dumps({'product':'wow_classic_beta','build':'1.60.1.70450','tables':records},indent=2)+'\n');print(json.dumps(row),flush=True)
 return 0 if all(r['status']=='VERIFIED_DECODED' for r in records) else 1
if __name__=='__main__':sys.exit(main())
