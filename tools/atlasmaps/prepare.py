#!/usr/bin/env python3
"""Prepare pinned Atlas Forever floor maps without executing addon Lua or modifying a database."""
import argparse, hashlib, io, json, re, urllib.request, zipfile
from pathlib import Path
from PIL import Image
MODULE='19c74eae268360cabdab7269b161b4f08b7f7426'
CORE='7787b5b2adfedf3e3e3c62838b81c0db6bd5d7ec'
SOURCES=[('module','Atlas-Kalimdor-and-Eastern-Kingdoms',MODULE,'e3c01071d1116b78b967ec948c97467e468f839087722bf0b8f4b3e061f81549'),('core','Atlas',CORE,'6862abb1158773e6c76d6b7a2b970e1a0e33e56fd94e462818a911d82ad25da4')]
MAPS=[(2959,'CF_CityofDalaranA','Map A'),(2959,'CF_CityofDalaranB','Map B'),(2998,'CF_ExcavationSiteWetlands','Floor map'),(2999,'CF_RuinsofLordaeron','Floor map'),(3065,'CF_HallofThanes','Floor map'),(36,'CL_TheDeadminesEnt','Entrance'),(36,'CL_TheDeadmines','Floor map')]
def prepare(out):
 out.mkdir(parents=True,exist_ok=True); archives={}; evidence=[]
 for key,repo,commit,expected in SOURCES:
  url=f'https://codeload.github.com/arithmandar/{repo}/zip/{commit}'; target=out/f'{key}.zip'
  if not target.exists():
   with urllib.request.urlopen(url,timeout=120) as response: target.write_bytes(response.read())
  raw=target.read_bytes(); assert hashlib.sha256(raw).hexdigest()==expected, f'{key}: archive hash mismatch'
  archives[key]=zipfile.ZipFile(io.BytesIO(raw)); evidence.append(dict(path=target.name,url=url,sha256=expected,commit=commit))
 def read(key,suffix):
  z=archives[key]; matches=[n for n in z.namelist() if n.endswith('/'+suffix)]; assert len(matches)==1,(key,suffix); return z.read(matches[0])
 source=read('module','Data/Data-ClassicForever.lua'); (out/'Data-ClassicForever.lua').write_bytes(source)
 licence=read('core','Docs/gpl-v2-enUS.txt'); (out/'GPL-2.0-Atlas.txt').write_bytes(licence)
 (out/'Readme-enUS.txt').write_bytes(read('module','Docs/Readme-enUS.txt'))
 maps=[]; lines=source.decode('utf-8-sig').splitlines()
 for mapid,key,label in MAPS:
  start=next(i for i,l in enumerate(lines) if re.match(r'\s*'+key+r'\s*=\s*\{',l)); end=next(i for i in range(start+1,len(lines)) if re.match(r'\t},',lines[i])); block=lines[start:end]
  image=read('module',f'Images/{key}.blp'); (out/f'{key}.blp').write_bytes(image)
  im=Image.open(io.BytesIO(image)).convert('RGB'); im.thumbnail((1024,1024)); im.save(out/f'{key}.jpg','JPEG',quality=90)
  legend=[]; number=''
  for offset,line in enumerate(block):
   if not re.match(r'\s*\{',line): continue
   n=re.search(r'"\s*([A-Z]|\d+\x27?)\)\s*"',line)
   number=n.group(1) if n else ''
   name=re.search(r'(?:getCreatureName|getBossName)\("([^"]+)"',line)
   if not name: name=re.search(r'(?:L|ALC|BZ)\["([^"]+)"\]',line)
   text=name.group(1) if name else ('Entrance' if 'ENTRANCE' in line else None)
   if text and 'QUESTS_COLON' in line: text='Quest: '+text
   if text: legend.append(dict(number=number,name=text,line=start+offset+1))
  maps.append(dict(map_id=mapid,key=key,label=label,forever=key.startswith('CF_'),legend=legend,jpeg_sha256=hashlib.sha256((out/f'{key}.jpg').read_bytes()).hexdigest()))
 with (out/'maps.tsv').open('w') as f:
  for m in maps: f.write(f'{m["map_id"]}\t{m["key"]}\t{m["label"]}\t{int(m["forever"])}\n')
 with (out/'legend.tsv').open('w') as f:
  for m in maps:
   for row in m['legend']: f.write(f'{m["key"]}\t{row["number"]}\t{row["name"]}\t{row["line"]}\n')
 manifest=dict(module_commit=MODULE,core_commit=CORE,sources=evidence,maps=maps)
 (out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n'); return manifest
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('output',type=Path);a=p.parse_args();m=prepare(a.output);print(f'Prepared {len(m["maps"])} genuine Atlas maps')
