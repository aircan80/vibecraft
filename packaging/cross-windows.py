#!/usr/bin/env python3
"""Developer-only Windows portable assembly; Python is never copied into the product."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import urllib.request
import zipfile


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def fetch(url, path):
    if not url.startswith('https://github.com/adoptium/temurin21-binaries/releases/download/'):
        raise ValueError('Runtime downloads must use the pinned official Temurin release URL')
    with urllib.request.urlopen(url, timeout=120) as response, path.open('wb') as out:
        shutil.copyfileobj(response, out)


def safe_runtime_extract(archive, target):
    with zipfile.ZipFile(archive) as source:
        names = [item for item in source.infolist() if not item.is_dir()]
        roots = {PurePosixPath(item.filename).parts[0] for item in names}
        if len(roots) != 1:
            raise ValueError('Expected one runtime ZIP root directory')
        for item in names:
            parts = PurePosixPath(item.filename).parts
            if item.filename.startswith('/') or any(part in ('', '.', '..') for part in parts) or '\\' in item.filename or ':' in item.filename:
                raise ValueError('Unsafe runtime archive path')
            if (item.external_attr >> 16) & 0o170000 == 0o120000:
                raise ValueError('Runtime symlinks are not supported in Windows ZIP')
            dest = target.joinpath(*parts[1:])
            dest.parent.mkdir(parents=True, exist_ok=True)
            with source.open(item) as inp, dest.open('wb') as out:
                shutil.copyfileobj(inp, out)
    for required in ('bin/javaw.exe', 'bin/java.exe', 'release', 'legal/java.base/LICENSE'):
        if not (target / required).is_file():
            raise ValueError('Required runtime file missing: ' + required)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--jar-sha256', required=True)
    parser.add_argument('--data-root', type=Path, required=True)
    parser.add_argument('--data-manifest', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True, help='External developer output directory')
    parser.add_argument('--version', required=True)
    parser.add_argument('--runtime-pin', type=Path, default=Path(__file__).with_name('windows-runtime.json'))
    parser.add_argument('--launcher-exe', type=Path, help='Optional separately reviewed Windows launcher')
    parser.add_argument('--launcher-sha256', help='Required SHA-256 pin when a launcher EXE is supplied')
    parser.add_argument('--launcher-license', type=Path, action='append', default=[], help='Launcher license/notice files (repeatable)')
    parser.add_argument('--launcher-provenance', type=Path, help='Reviewed launcher provenance JSON')
    args = parser.parse_args()
    if not args.version or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.+_-' for c in args.version):
        parser.error('Invalid release version')
    jar = args.jar.resolve(strict=True)
    if sha256(jar) != args.jar_sha256:
        raise ValueError('Frozen application JAR SHA-256 mismatch')
    pin = json.loads(args.runtime_pin.read_text())
    if (pin['os'], pin['architecture'], pin['imageType']) != ('windows', 'x64', 'jre'):
        raise ValueError('Expected Windows x64 JRE pin')
    output = args.output.resolve()
    if output.is_relative_to(Path(__file__).resolve().parents[1]):
        raise ValueError('Build outputs must be outside the application source checkout')
    output.mkdir(parents=True, exist_ok=True)
    app = output / 'Vibecraft'
    if app.exists():
        raise ValueError('Refusing to replace an existing application folder: ' + str(app))
    cache = output / 'runtime-download'
    cache.mkdir(exist_ok=True)
    runtime_zip = cache / PurePosixPath(pin['url']).name
    if not runtime_zip.exists():
        fetch(pin['url'], runtime_zip)
    checksum_file = cache / 'official-runtime.sha256.txt'
    fetch(pin['checksumUrl'], checksum_file)
    if checksum_file.read_text().split()[0].lower() != pin['sha256']:
        raise ValueError('Official checksum sidecar disagrees with pinned metadata')
    if runtime_zip.stat().st_size != pin['bytes'] or sha256(runtime_zip) != pin['sha256']:
        raise ValueError('Downloaded runtime SHA-256/size mismatch')
    # Invoke the same Java audit/staging gates as native jpackage; these are build-time only.
    scripts = Path(__file__).resolve().parent
    subprocess.run(['java', str(scripts / 'ArchiveAudit.java'), str(jar)], check=True)
    stage = app / 'app'
    stage.mkdir(parents=True)
    subprocess.run(['java', str(scripts / 'ReleaseData.java'), str(args.data_root.resolve()),
                    str(args.data_manifest.resolve()), str(stage)], check=True)
    shutil.copyfile(jar, stage / 'wow-forever-browser.jar')
    safe_runtime_extract(runtime_zip, app / 'runtime')
    shutil.copyfile(args.runtime_pin, app / 'runtime-source.json')
    (app / 'release-version.txt').write_text('product-version=' + args.version + '\nplatform=Windows-x64\nlaunch-tested=false\n')
    # %~dp0 is independent of current working directory; quote all paths for spaces.
    command = ('@echo off\r\nsetlocal DisableDelayedExpansion\r\nset "VIBECRAFT_DIR=%~dp0"\r\n'
               'if not exist "%VIBECRAFT_DIR%runtime\\bin\\javaw.exe" (\r\n'
               '  echo Keep the complete extracted Vibecraft folder together.\r\n  pause\r\n  exit /b 1\r\n)\r\n'
               'start "Vibecraft" "%VIBECRAFT_DIR%runtime\\bin\\javaw.exe" '
               '"-Dwowforever.database=%VIBECRAFT_DIR%app\\data\\wowforever.sqlite" '
               '"-Dwowforever.data.dir=%VIBECRAFT_DIR%app\\data" '
               '"-Dwowforever.release.version=' + args.version + '" '
               '-jar "%VIBECRAFT_DIR%app\\wow-forever-browser.jar" %*\r\n')
    (app / 'Vibecraft.cmd').write_bytes(command.encode('ascii'))
    if args.launcher_exe:
        launcher = args.launcher_exe.resolve(strict=True)
        if not args.launcher_sha256 or sha256(launcher) != args.launcher_sha256:
            raise ValueError('Launcher SHA-256 pin missing or mismatched')
        if not args.launcher_license or not args.launcher_provenance:
            raise ValueError('Launcher license and provenance records are required')
        if launcher.read_bytes()[:2] != b'MZ':
            raise ValueError('Optional launcher is not a Windows PE executable')
        shutil.copyfile(launcher, app / 'Vibecraft.exe')
        notices = app / 'launcher-notices'
        notices.mkdir()
        for notice in args.launcher_license:
            shutil.copyfile(notice.resolve(strict=True), notices / notice.name)
        shutil.copyfile(args.launcher_provenance.resolve(strict=True), notices / 'provenance.json')
    readme = ('Vibecraft ' + args.version + '\nEvery place. Every drop. Every adventure.\n\n'
              'A World of Warcraft companion for exploration, loot, crafting, and lore.\n\n'
              'WINDOWS BUILD: ASSEMBLED ON LINUX; NOT LAUNCH-TESTED ON WINDOWS.\n'
              'Extract the complete ZIP, then double-click '
              + ('Vibecraft.exe (Vibecraft.cmd is also available).' if args.launcher_exe else 'Vibecraft.cmd.')
              + '\nJava is included. No separate Java, Python, Node or Blender installation is required.\n'
              'Keep all folders together. Move the entire Vibecraft folder to relocate the app.\n'
              'User preferences/caches remain in per-user application folders.\n'
              'Runtime licenses are preserved under runtime/legal. Runtime source/checksum metadata\n'
              'is recorded in runtime-source.json. Optional EXE notices are under launcher-notices.\n'
              'Exact gameplay asset inventory is under\n'
              'app/data/release-data-inventory.tsv.\n')
    (app / 'README.txt').write_text(readme, encoding='utf-8')
    inventory = []
    for path in sorted(app.rglob('*')):
        if path.is_file():
            inventory.append({'path': path.relative_to(app).as_posix(), 'bytes': path.stat().st_size, 'sha256': sha256(path)})
    report = {'product': 'Vibecraft', 'version': args.version, 'platform': 'Windows', 'architecture': 'x64',
              'assembledOn': os.uname().sysname if hasattr(os, 'uname') else os.name,
              'launchTested': False, 'status': 'NOT LAUNCH-TESTED ON WINDOWS', 'runtime': pin,
              'jarSha256': args.jar_sha256, 'dataManifestSha256': sha256(args.data_manifest),
              'files': inventory, 'publication': 'none'}
    (app / 'assembly-inventory.json').write_text(json.dumps(report, indent=2) + '\n')
    archive = output / ('vibecraft-' + args.version + '-Windows-x64-with-data-NOT-LAUNCH-TESTED.zip')
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6, allowZip64=True) as bundle:
        for path in sorted(app.rglob('*')):
            if path.is_file():
                bundle.write(path, path.relative_to(output))
    with zipfile.ZipFile(archive) as bundle:
        bad = bundle.testzip()
        if bad:
            raise ValueError('ZIP integrity check failed: ' + bad)
    report['archive'] = {'name': archive.name, 'bytes': archive.stat().st_size, 'sha256': sha256(archive)}
    (output / 'windows-packaging-evidence.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'archive': report['archive'], 'launchTested': False, 'files': len(inventory)}, indent=2))


if __name__ == '__main__':
    main()
