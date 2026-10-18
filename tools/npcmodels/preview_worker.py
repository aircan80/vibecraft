#!/usr/bin/env python3
"""Exact-build native assets -> offline portrait/turntable. Invoked by the Java preview queue."""
from __future__ import annotations
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

VERSION = 'native-preview-v1'
EXPORT_COMMIT = 'c2fd7bde36a712be78a5da896c995b84fbfa2545'
HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def validate(request):
    for field in ('product', 'build', 'appearanceKey'):
        if not isinstance(request.get(field), str) or not request[field] or len(request[field]) > 512:
            raise ValueError('Missing or invalid ' + field)
    for field in ('npcId', 'displayId', 'modelId', 'modelFileDataId'):
        if type(request.get(field)) is not int or not 0 < request[field] <= 0xffffffff:
            raise ValueError('Invalid ' + field)
    textures = request.get('textures')
    if not isinstance(textures, list) or len(textures) > 4 or any(type(i) is not int or not 0 <= i <= 0xffffffff for i in textures):
        raise ValueError('Invalid display texture slots')
    for name in ('displayScale', 'modelScale'):
        value = request.get(name, 1)
        if type(value) not in (int, float) or not math.isfinite(value) or not 0 < value < 100:
            raise ValueError('Invalid ' + name)
    return request


def tool(variable, fallback):
    candidate = os.environ.get(variable)
    path = Path(candidate).expanduser() if candidate else fallback
    if path and path.exists():
        return str(path.resolve())
    raise RuntimeError(f'Render tool unavailable: configure {variable}')


def run(command, log, timeout=180):
    with log.open('ab') as stream:
        result = subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, timeout=timeout, check=False)
    if result.returncode:
        tail = log.read_text(errors='replace')[-2500:]
        raise RuntimeError(f'Render stage failed (exit {result.returncode}): {tail}')


def render(request, output):
    from native_assets import NativeAssets
    from fetch_assets import dependencies
    started = time.monotonic()
    local = ROOT / '.local/npc-render-poc'
    node = tool('NPC_PREVIEW_NODE', local / 'toolchain/node-v22.15.0-linux-x64/bin/node')
    blender = tool('NPC_PREVIEW_BLENDER', local / 'toolchain/blender-4.5.3-linux-x64/blender')
    exporter = Path(os.environ.get('NPC_PREVIEW_WOW_EXPORT', str(local / 'wow-export'))).resolve()
    commit = subprocess.check_output(['git', '-C', str(exporter), 'rev-parse', 'HEAD'], text=True, timeout=10).strip()
    if commit != EXPORT_COMMIT:
        raise RuntimeError('Unsupported wow.export revision; expected ' + EXPORT_COMMIT)
    cache = Path(os.environ.get('NPC_PREVIEW_ASSET_CACHE', str(Path.home() / '.cache/wowforever-browser/native-assets'))).expanduser()
    seed = os.environ.get('NPC_PREVIEW_METADATA') or (str(local / 'attestation') if (local / 'attestation').is_dir() else None)
    assets = NativeAssets(cache, request['product'], request['build'], metadata_dir=seed)
    raw, converted = output / 'native', output / 'converted'
    raw.mkdir(parents=True, exist_ok=True)
    converted.mkdir(exist_ok=True)
    pending = [request['modelFileDataId']] + [i for i in request['textures'] if i]
    seen = set()
    proof = []
    while pending:
        fdid = pending.pop(0)
        if fdid in seen:
            continue
        if len(seen) >= 64:
            raise RuntimeError('Unsupported appearance: native dependency limit exceeded')
        path = assets.get(fdid)
        data = path.read_bytes()
        if len(data) > 32 * 1024 * 1024:
            raise RuntimeError('Unsupported appearance: native asset size limit exceeded')
        shutil.copyfile(path, raw / f'{fdid}.bin')
        seen.add(fdid)
        asset_proof = assets.cache_root / 'proof' / assets.product / assets.build / f'{fdid}.json'
        proof.append(json.loads(asset_proof.read_text()))
        deps = dependencies(data)
        # Equipped humanoids, skeleton/animation assembly are deliberately not guessed.
        if any(reason == 'SKID' for _, reason in deps):
            raise RuntimeError('Unsupported appearance: external skeleton assembly is not implemented')
        pending.extend(i for i, reason in deps if reason in ('SFID', 'TXID'))
    request_path = output / 'request.json'
    request_path.write_text(json.dumps(request, indent=2) + '\n')
    log = output / 'worker.log'
    run([node, str(HERE / 'export-m2.cjs'), str(exporter), str(raw), str(converted), str(request_path)], log, 60)
    run([blender, '--background', '--factory-startup', '--disable-autoexec', '--python', str(HERE / 'render_portrait.py'), '--', str(converted / 'preview.glb'), str(output), '--turntable', '--request', str(request_path)], log, 210)
    frames = [{'file': f'turntable-{i:02d}.png', 'angle': i * 30, 'sha256': sha(output / f'turntable-{i:02d}.png')} for i in range(12)]
    manifest = dict(product=request['product'], build=request['build'], appearanceKey=request['appearanceKey'], rendererVersion=VERSION,
        portrait={'file': 'portrait.png', 'sha256': sha(output / 'portrait.png')}, frames=frames,
        evidence={'mapping': request, 'assets': proof, 'exporterCommit': commit, 'renderer': 'Blender Cycles CPU',
            'pose': 'Native bind pose; twelve rendered views, not live 3D animation',
            'officialBuildIdentity': assets.identity,
            'buildVerification': 'Every raw asset was verified against its content key in the exact product/build CASC root'},
        durationSeconds=round(time.monotonic() - started, 3))
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    # Cache contains compact outputs and audit records; raw blobs are shared separately by content.
    shutil.rmtree(raw)
    shutil.rmtree(converted)
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--request', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    request = validate(json.loads(args.request.read_text()))
    args.output.mkdir(parents=True, exist_ok=True)
    if (args.output / 'manifest.json').exists():
        raise RuntimeError('Worker output already contains a preview; use an unpublished staging directory')
    result = render(request, args.output)
    print(json.dumps({'status': 'ready', 'appearanceKey': result['appearanceKey'], 'durationSeconds': result['durationSeconds']}))


if __name__ == '__main__':
    try:
        main()
    except Exception as failure:
        print('NPC preview unavailable: ' + str(failure), file=sys.stderr)
        sys.exit(1)
