#!/usr/bin/env python3
"""Acquire one exact-build native asset and return authenticated cache metadata."""
import argparse
import json
import os
from pathlib import Path
import sys
import tempfile
from native_assets import NativeAssets


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--product',required=True)
    p.add_argument('--build',required=True)
    p.add_argument('--file-data-id',type=int,required=True)
    p.add_argument('--cache',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    a=p.parse_args()
    local=Path(__file__).resolve().parents[2]/'.local/npc-render-poc/attestation'
    seed=os.environ.get('NPC_PREVIEW_METADATA') or (str(local) if local.is_dir() else None)
    store=NativeAssets(a.cache,a.product,a.build,metadata_dir=seed)
    asset=store.get(a.file_data_id)
    proof=json.loads((store.cache_root/'proof'/store.product/store.build/f'{a.file_data_id}.json').read_text())
    result=dict(path=str(asset.resolve()),product=a.product,build=a.build,fileDataID=a.file_data_id,
        contentMD5=proof['content_md5'],sha256=proof['sha256'],proof=proof)
    a.output.parent.mkdir(parents=True,exist_ok=True)
    fd,tmp=tempfile.mkstemp(prefix='.asset-',dir=a.output.parent)
    try:
        with os.fdopen(fd,'w') as out:json.dump(result,out,indent=2);out.write('\n')
        os.replace(tmp,a.output)
    finally:
        if os.path.exists(tmp):os.unlink(tmp)
    print(json.dumps({'status':'ready','fileDataID':a.file_data_id,'build':a.build}))


if __name__=='__main__':
    try:main()
    except Exception as failure:
        print('Native asset unavailable: '+str(failure),file=sys.stderr)
        sys.exit(1)
