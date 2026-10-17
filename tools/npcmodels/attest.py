#!/usr/bin/env python3
"""Small, fail-closed official CDN content proof for selected WoW file IDs.

Only handles unencrypted N/Z BLTE, EN encoding and TSFM root versions 1/2.
Expected metadata lives in .local/npc-render-poc/attestation; exact build and
config hash are checked against Blizzard's archived versions response.
Based on formats also implemented by pinned wow.export's CASC parsers.
"""
from __future__ import annotations
import hashlib,json,struct,zlib
from pathlib import Path
import requests

BASE='https://us.cdn.blizzard.com/tpr/wow'
P=Path('.local/npc-render-poc/attestation')


def blte(raw:bytes)->bytes:
    assert raw[:4]==b'BLTE','not BLTE'
    header=struct.unpack_from('>I',raw,4)[0]
    def decode(block):
        if block[:1]==b'N':return block[1:]
        if block[:1]==b'Z':return zlib.decompress(block[1:])
        raise ValueError('unsupported BLTE mode '+repr(block[:1]))
    if not header:return decode(raw[8:])
    assert raw[8]==15
    count=int.from_bytes(raw[9:12],'big');offset=header;out=[]
    for i in range(count):
        size,unpacked=struct.unpack_from('>II',raw,12+24*i);md5=raw[20+24*i:36+24*i]
        block=raw[offset:offset+size];assert hashlib.md5(block).digest()==md5,'BLTE chunk hash mismatch'
        data=decode(block);assert len(data)==unpacked
        out.append(data);offset+=size
    assert offset==len(raw)
    return b''.join(out)


def encoding_find(raw:bytes, wanted:set[str])->dict:
    assert raw[:2]==b'EN'
    csize,esize=raw[3],raw[4];page=struct.unpack_from('>H',raw,5)[0]*1024
    count=struct.unpack_from('>I',raw,9)[0];spec=struct.unpack_from('>I',raw,18)[0]
    start=22+spec+count*(csize+16);found={}
    for i in range(count):
        o=start+page*i;end=o+page
        while o<end:
            n=raw[o];o+=1
            if not n:break
            size=int.from_bytes(raw[o:o+5],'big');o+=5
            ck=raw[o:o+csize].hex();o+=csize
            keys=[raw[o+j*esize:o+(j+1)*esize].hex() for j in range(n)];o+=n*esize
            if ck in wanted:found[ck]={'size':size,'encoding_keys':keys}
    return found


def fetch(key:str)->bytes:
    p=P/(key+'.blte')
    if not p.exists():
        u=f'{BASE}/data/{key[:2]}/{key[2:4]}/{key}'
        r=requests.get(u,timeout=120);r.raise_for_status();p.write_bytes(r.content)
    return p.read_bytes()


def root_find(raw:bytes,wanted:set[int])->list[dict]:
    assert raw[:4]==b'TSFM','unsupported legacy root'
    header,version=struct.unpack_from('<II',raw,4);assert header==24 and version in [1,2],(header,version)
    total,named=struct.unpack_from('<II',raw,12);o=header;found=[]
    while o<len(raw):
        count=struct.unpack_from('<I',raw,o)[0];o+=4
        if version==1:flags,locale=struct.unpack_from('<II',raw,o);o+=8
        else:
            locale,f1,f2,f3=struct.unpack_from('<IIIB',raw,o);o+=13;flags=f1|f2|(f3<<17)
        ids=[];last=0
        for i in range(count):
            v=last+struct.unpack_from('<i',raw,o)[0];o+=4;ids.append(v);last=v+1
        for fd in ids:
            ck=raw[o:o+16].hex();o+=16
            if fd in wanted:found.append({'file_data_id':fd,'content_key':ck,'content_flags':flags,'locale_flags':locale})
        if not(total!=named and flags&0x10000000):o+=8*count
    assert o==len(raw),(o,len(raw))
    return found


def main():
    P.mkdir(exist_ok=True,parents=True)
    for name,url in [('versions','https://us.version.battle.net/wow_classic_beta/versions'),('build-config',BASE+'/config/84/be/84bea4233331e390fda8724c6f91ffa5')]:
        if not (P/name).exists():
            response=requests.get(url,timeout=120);response.raise_for_status();(P/name).write_bytes(response.content)
    versions=(P/'versions').read_text()
    assert '|84bea4233331e390fda8724c6f91ffa5|' in versions and '|70450|1.60.1.70450|' in versions
    assert hashlib.md5((P/'build-config').read_bytes()).hexdigest()=='84bea4233331e390fda8724c6f91ffa5'
    config=dict(line.split(' = ',1) for line in (P/'build-config').read_text().splitlines() if ' = ' in line)
    if not (P/'encoding.blte').exists():
        encoding_key=config['encoding'].split()[1]
        (P/'encoding.blte').write_bytes(fetch(encoding_key))
    encoding=blte((P/'encoding.blte').read_bytes());assert hashlib.md5(encoding).hexdigest()==config['encoding'].split()[0]
    root_key=config['root'];match=encoding_find(encoding,{root_key})[root_key];print('root encoding',match,flush=True)
    root=blte(fetch(match['encoding_keys'][0]));assert hashlib.md5(root).hexdigest()==root_key
    (P/'root.decoded').write_bytes(root)
    rows=root_find(root,{126061,126062,479933});print('root matches',rows,flush=True)
    needed=encoding_find(encoding,{r['content_key'] for r in rows})
    for row in rows:
        row['encoding']=needed[row['content_key']]
        current=Path(f".local/npc-render-poc/{row['file_data_id']}.bin").read_bytes()
        row['existing_poc_md5']=hashlib.md5(current).hexdigest();row['existing_poc_matches']=row['existing_poc_md5']==row['content_key']
    assert all(any(r['file_data_id']==fd and r['existing_poc_matches'] for r in rows) for fd in [126061,126062,479933]),'POC asset missing from exact root'
    report={'proof_status':'all_three_poc_assets_match_official_build_root', 'official_versions_url':'https://us.version.battle.net/wow_classic_beta/versions', 'official_cdn_base':BASE,'encoding_content_key':config['encoding'].split()[0],'encoding_content_md5_verified':True,'root_content_md5_verified':True,'build':'1.60.1.70450','build_config_md5':hashlib.md5((P/'build-config').read_bytes()).hexdigest(),'build_name':config['build-name'],'root_content_key':root_key,'root_decoded_bytes':len(root),'encoding_decoded_bytes':len(encoding),'files':rows}
    (P/'content-proof.json').write_text(json.dumps(report,indent=2)+'\n')
    manifest_path=P.parent/'assets-manifest.json'
    if manifest_path.exists():
        manifest=json.loads(manifest_path.read_text())
        manifest['official_build_proof']=report
        manifest['official_build_proof_sha256']=hashlib.sha256((P/'content-proof.json').read_bytes()).hexdigest()
        manifest['build_attestation']='Verified: every selected raw asset hash matches its file ID in the official Blizzard CDN root for Forever build 1.60.1.70450. Config, decoded encoding and decoded root content hashes verified.'
        for asset in manifest['files']:
            asset['verified_build_membership']=[r for r in rows if r['file_data_id']==asset['file_data_id'] and r['existing_poc_matches']]
        manifest_path.write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps(report,indent=2))


if __name__=='__main__':main()
