#!/usr/bin/env bash
set -euo pipefail
if (( $# != 3 )); then echo "Usage: $0 REVIEW_JAR DATABASE EMPTY_OUTPUT" >&2; exit 2; fi
source_evidence_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
python3 - "$source_evidence_dir/SourceEvidenceSnapshots.java" "$1" "$2" "$3" <<'PY'
import pathlib, shutil, subprocess, sys, tempfile
source, jar, database, output = map(pathlib.Path, sys.argv[1:])
with tempfile.TemporaryDirectory(prefix='source-evidence-') as temporary:
    work = pathlib.Path(temporary)
    classes = work / 'classes'
    classes.mkdir()
    reviewed = work / 'app.jar'
    shutil.copyfile(jar, reviewed)
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(reviewed), '-d', str(classes), str(source)], check=True)
    subprocess.run(['java', '--enable-native-access=ALL-UNNAMED', '-Djava.awt.headless=true', '-Dflatlaf.uiScale=1', '-Djava.util.prefs.userRoot=' + str(work / 'prefs'), '-cp', str(classes) + ':' + str(reviewed), 'SourceEvidenceSnapshots', str(reviewed), str(database), str(output)], check=True)
PY
