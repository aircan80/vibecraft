#!/usr/bin/env python3
"""Developer/CI helper only; never included in the Java portable distribution."""
import argparse, hashlib, json, os, pathlib, posixpath, re, stat, subprocess, tarfile, zipfile
import xml.etree.ElementTree as ET
SEMVER = re.compile(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-((?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)(?:\.(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*))*))?(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?')
TARGETS = ('linux-x64', 'windows-x64', 'macos-arm64', 'macos-x64')
def version(value, pom):
    match = SEMVER.fullmatch(value) if len(value)<=128 else None
    if not match: raise ValueError('Expected strict SemVer without v prefix')
    if any(int(match[i])>65535 for i in (1,2,3)): raise ValueError('Version component exceeds native packaging bound')
    core = '.'.join(match.group(i) for i in (1,2,3))
    tree = ET.parse(pom); project = tree.getroot(); ns = {'m':'http://maven.apache.org/POM/4.0.0'}
    actual = project.findtext('m:version', namespaces=ns)
    if actual not in (core, core+'-SNAPSHOT', value): raise ValueError('Tag numeric version does not match root POM')
    # JDK21 CFBundleVersion requires a positive major; product identity is unchanged.
    mac = '.'.join((str(int(match[1])+1), match[2], match[3]))
    return {'version':value, 'native':core, 'mac_native':mac, 'prerelease':str(bool(match[4])).lower()}
def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda:f.read(1024*1024),b''): h.update(chunk)
    return h.hexdigest()
def inspect_image(image):
    jars=list(image.rglob('wow-forever-browser.jar'))
    if len(jars)!=1: raise ValueError('Expected exactly one application JAR')
    for p in image.rglob('*'):
        if p.is_file() and (p.suffix.lower() in ('.sqlite','.db','.blp','.mp3','.ogg','.wav','.m2','.wmo') or p.name.lower() in ('python','python.exe','node','node.exe','blender','blender.exe')):
            raise ValueError('Unexpected game data/tool in app image: '+str(p))
    return jars[0]
def commit(value):
    if not re.fullmatch(r'[0-9a-f]{40}',value): raise ValueError('Expected exact source commit SHA')
    return value

def validate_receipt(receipt,target,product):
    expected_arch=('aarch64','arm64') if target=='macos-arm64' else ('amd64','x86_64')
    os_name=receipt.get('osName','')
    expected_os=(os_name=='Linux' if target.startswith('linux') else os_name.startswith('Windows ') if target.startswith('windows') else os_name=='Mac OS X')
    if receipt.get('formatVersion')!=1 or receipt.get('success') is not True or receipt.get('productVersion')!=product or receipt.get('marker')!='VIBECRAFT_PORTABLE_SELF_TEST_OK' or receipt.get('javaFeature')!=21 or receipt.get('osArch','').lower() not in expected_arch or not expected_os:
        raise ValueError('Native smoke version/platform evidence mismatch')

def extract_archive(archive,destination,image_name):
    def path_allowed(name):
        p=pathlib.PurePosixPath(name)
        return not p.is_absolute() and not any(x in ('..','') or ':' in x or '\\' in x for x in p.parts) and p.parts and p.parts[0]==image_name
    names=set()
    def unique(name):
        if not path_allowed(name) or name in names: raise ValueError('Unsafe/duplicate archive member')
        names.add(name)
    if archive.suffix=='.zip':
        with zipfile.ZipFile(archive) as z:
            for entry in z.infolist():
                unique(entry.filename)
                if stat.S_ISLNK(entry.external_attr>>16): raise ValueError('ZIP symlinks unsupported')
            z.extractall(destination)
    else:
        with tarfile.open(archive,'r:gz') as tar:
            for entry in tar.getmembers():
                unique(entry.name)
                if not (entry.isfile() or entry.isdir() or entry.issym() or entry.islnk()): raise ValueError('Unsupported archive member')
                if entry.issym() or entry.islnk():
                    target=posixpath.normpath(posixpath.join(posixpath.dirname(entry.name),entry.linkname) if entry.issym() else entry.linkname)
                    if not path_allowed(target): raise ValueError('Archive link escapes application image')
            tar.extractall(destination,filter='data')

def package(args):
    commit(args.source_commit)
    work=pathlib.Path(args.work).resolve(); dest=work/'package'; out=work/'upload'
    out.mkdir(parents=True,exist_ok=True)
    image=dest/('Vibecraft.app' if args.target.startswith('macos') else 'Vibecraft')
    jar=inspect_image(image)
    label=f'vibecraft-{args.version}-{args.target}'
    archive=out/(label+('.zip' if args.target=='windows-x64' else '.tar.gz'))
    if args.target=='windows-x64':
        with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
            for p in sorted(image.rglob('*')):
                if p.is_file(): z.write(p,pathlib.Path(image.name)/p.relative_to(image))
    else:
        with tarfile.open(archive,'w:gz') as tar: tar.add(image,arcname=image.name)
    # Smoke the final archive's extracted bytes, never a pre-archive directory copy.
    extracted=work/'relocated portable'; extracted.mkdir()
    extract_archive(archive,extracted,image.name)
    relocated=extracted/image.name
    if sha(inspect_image(relocated))!=sha(jar): raise ValueError('Extracted JAR differs from staged application')
    launcher=relocated/({'linux-x64':'bin/Vibecraft','windows-x64':'Vibecraft.exe'}.get(args.target,'Contents/MacOS/Vibecraft'))
    home=work/'fresh user'; home.mkdir(exist_ok=True)
    report_dir=work/'vibecraft-portable-self-test-native'; report_dir.mkdir()
    report=report_dir/'report.json'
    env=os.environ.copy()
    for key in ('JAVA_HOME','JDK_HOME','JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','NPC_PREVIEW_ASSET_CACHE','WOWFOREVER_DATABASE'):
        env.pop(key,None)
    env.update(PATH=str(work/'no-tools'),HOME=str(home),USERPROFILE=str(home),XDG_DATA_HOME=str(home/'data'),XDG_CACHE_HOME=str(home/'cache'),XDG_CONFIG_HOME=str(home/'config'),APPDATA=str(home/'roaming'),LOCALAPPDATA=str(home/'local'))
    proc=subprocess.run([str(launcher),'--portable-self-test',str(report)],env=env,cwd=home,timeout=120,capture_output=True,text=True)
    (work/'native-smoke.log').write_text(proc.stdout+proc.stderr,encoding='utf-8')
    if proc.returncode or not report.is_file(): raise ValueError('Native data-free smoke failed or report missing')
    receipt=json.loads(report.read_text(encoding='utf-8'))
    if receipt.get('marker')!='VIBECRAFT_PORTABLE_SELF_TEST_OK' or receipt.get('success') is not True or receipt.get('javaFeature')!=21 or receipt.get('productVersion')!=args.version:
        raise ValueError('Native data-free smoke identity/version failed')
    validate_receipt(receipt,args.target,args.version)
    java_home=pathlib.Path(receipt.get('javaHome','')).resolve()
    if not java_home.is_relative_to(relocated.resolve()): raise ValueError('Smoke did not use relocated bundled runtime')
    digest=sha(archive)
    (out/(label+'.sha256')).write_text(f'{digest}  {archive.name}\n',encoding='utf-8')
    evidence={'version':args.version,'source_commit':commit(args.source_commit),'target':args.target,'archive':archive.name,'bytes':archive.stat().st_size,'sha256':digest,'jar_sha256':sha(jar),'native_smoke':receipt,'game_data_bundled':False,'signing':'unsigned; not notarized'}
    (out/(label+'.json')).write_text(json.dumps(evidence,indent=2)+'\n',encoding='utf-8')
def verify_set(directory, product, source_commit):
    source_commit=commit(source_commit)
    root=pathlib.Path(directory); expected=set()
    for target in TARGETS:
        label=f'vibecraft-{product}-{target}'
        archive=label+('.zip' if target=='windows-x64' else '.tar.gz')
        expected.update((archive,label+'.sha256',label+'.json'))
        metadata=json.loads((root/(label+'.json')).read_text(encoding='utf-8'))
        digest=sha(root/archive)
        if metadata.get('source_commit')!=source_commit or metadata.get('version')!=product or metadata.get('target')!=target or metadata.get('archive')!=archive or metadata.get('sha256')!=digest or metadata.get('game_data_bundled') is not False:
            raise ValueError('Platform release metadata mismatch')
        if (root/(label+'.sha256')).read_text(encoding='utf-8')!=f'{digest}  {archive}\n': raise ValueError('Checksum mismatch')
        validate_receipt(metadata.get('native_smoke',{}),target,product)
    if {p.name for p in root.iterdir()}!=expected: raise ValueError('Missing or unexpected release artifact')

def main():
    p=argparse.ArgumentParser(); sub=p.add_subparsers(dest='command',required=True)
    v=sub.add_parser('version');v.add_argument('--version',required=True);v.add_argument('--pom',default='pom.xml');v.add_argument('--output')
    b=sub.add_parser('package');b.add_argument('--work',required=True);b.add_argument('--target',choices=TARGETS,required=True);b.add_argument('--version',required=True);b.add_argument('--source-commit',required=True)
    vs=sub.add_parser('verify-set');vs.add_argument('--directory',required=True);vs.add_argument('--version',required=True);vs.add_argument('--source-commit',required=True)
    a=p.parse_args()
    if a.command=='version':
        result=version(a.version,a.pom)
        if a.output:
            with open(a.output,'a',encoding='utf-8') as f:
                for k,value in result.items(): f.write(f'{k}={value}\n')
        else: print(json.dumps(result))
    elif a.command=='verify-set': verify_set(a.directory,a.version,a.source_commit)
    else: package(a)
if __name__=='__main__':main()
