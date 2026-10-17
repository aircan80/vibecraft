#!/usr/bin/env bash
# Capture the exact reviewed jar against a separately supplied candidate database.
set -euo pipefail
if (( $# != 3 )); then
  echo "Usage: $0 APP_JAR DATABASE EMPTY_OUTPUT_DIRECTORY" >&2
  exit 2
fi
world_map_source=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
world_map_temp=$(mktemp -d "${TMPDIR:-/tmp}/wowforever-world-map.XXXXXX")
trap 'rm -rf -- "$world_map_temp"' EXIT
mkdir -p "$world_map_temp/classes" "$world_map_temp/prefs"
cp -- "$1" "$world_map_temp/app.jar"
javac -encoding UTF-8 -cp "$world_map_temp/app.jar" -d "$world_map_temp/classes" "$world_map_source/WorldMapSnapshots.java"
java --enable-native-access=ALL-UNNAMED -Djava.awt.headless=true -Dflatlaf.uiScale=1 \
  -Djava.util.prefs.userRoot="$world_map_temp/prefs" \
  -cp "$world_map_temp/classes:$world_map_temp/app.jar" WorldMapSnapshots "$2" "$3"
