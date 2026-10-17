#!/usr/bin/env bash
# Compile against a supplied shaded app jar; all runtime settings and DB extraction stay temporary.
set -euo pipefail
if (( $# < 2 || $# > 3 )); then
  echo "Usage: $0 APP_JAR EMPTY_OUTPUT_DIRECTORY [REPEATS>=20]" >&2
  exit 2
fi
acceptance_source_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
acceptance_temp=$(mktemp -d "${TMPDIR:-/tmp}/wowforever-acceptance.XXXXXX")
trap 'rm -rf -- "$acceptance_temp"' EXIT
mkdir -p "$acceptance_temp/classes" "$acceptance_temp/prefs-user" "$acceptance_temp/prefs-system" "$acceptance_temp/runtime"
# Freeze the reviewed input so a later Maven package cannot replace it while captures are running.
cp -- "$1" "$acceptance_temp/app.jar"
javac -encoding UTF-8 -cp "$acceptance_temp/app.jar" -d "$acceptance_temp/classes" \
  "$acceptance_source_dir/RenderSnapshots.java"
java --enable-native-access=ALL-UNNAMED -Djava.awt.headless=true -Dflatlaf.uiScale=1 \
  -Djava.util.prefs.userRoot="$acceptance_temp/prefs-user" \
  -Djava.util.prefs.systemRoot="$acceptance_temp/prefs-system" \
  -Djava.io.tmpdir="$acceptance_temp/runtime" \
  -cp "$acceptance_temp/classes:$acceptance_temp/app.jar" RenderSnapshots \
  "$acceptance_temp/app.jar" "$2" "$acceptance_temp/database" "${3:-30}"
