#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIXTURE="$(mktemp -d)"
trap 'rm -rf "$FIXTURE"' EXIT
mkdir -p "$FIXTURE/clean/META-INF" "$FIXTURE/assets/data" "$FIXTURE/worker/tools"
printf 'licensed dependency notice\n' > "$FIXTURE/clean/META-INF/NOTICE"
printf 'fixture only\n' > "$FIXTURE/assets/data/catalog.sqlite"
printf 'fixture only\n' > "$FIXTURE/worker/tools/preview_worker.py"
jar --create --file "$FIXTURE/clean.jar" -C "$FIXTURE/clean" .
jar --create --file "$FIXTURE/assets.jar" -C "$FIXTURE/assets" .
jar --create --file "$FIXTURE/worker.jar" -C "$FIXTURE/worker" .
java "$SCRIPT_DIR/ArchiveAudit.java" "$FIXTURE/clean.jar"
if java "$SCRIPT_DIR/ArchiveAudit.java" "$FIXTURE/assets.jar" > /dev/null 2>&1; then
  echo 'FAIL: default audit accepted bulk assets' >&2; exit 1
fi
java "$SCRIPT_DIR/ArchiveAudit.java" "$FIXTURE/assets.jar" --private-qa
if java "$SCRIPT_DIR/ArchiveAudit.java" "$FIXTURE/worker.jar" --private-qa > /dev/null 2>&1; then
  echo 'FAIL: private override accepted a worker script' >&2; exit 1
fi
echo 'PASS: clean distribution, default asset rejection, private asset override, unconditional worker rejection'
