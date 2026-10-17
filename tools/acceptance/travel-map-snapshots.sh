#!/usr/bin/env bash
# Capture the exact reviewed jar against a separately supplied candidate database.
set -euo pipefail
if (( $# != 3 )); then
  echo "Usage: $0 APP_JAR DATABASE EMPTY_OUTPUT_DIRECTORY" >&2
  exit 2
fi
travel_map_source=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
travel_map_temp=$(mktemp -d "${TMPDIR:-/tmp}/wowforever-travel-map.XXXXXX")
trap 'rm -rf -- "$travel_map_temp"' EXIT
mkdir -p "$travel_map_temp/classes" "$travel_map_temp/prefs"
cp -- "$1" "$travel_map_temp/app.jar"
javac -encoding UTF-8 -cp "$travel_map_temp/app.jar" -d "$travel_map_temp/classes" "$travel_map_source/TravelMapSnapshots.java"
java --enable-native-access=ALL-UNNAMED -Djava.awt.headless=true -Dflatlaf.uiScale=1 \
  -Djava.util.prefs.userRoot="$travel_map_temp/prefs" \
  -cp "$travel_map_temp/classes:$travel_map_temp/app.jar" TravelMapSnapshots "$2" "$3"
