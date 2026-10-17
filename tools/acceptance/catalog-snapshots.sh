#!/usr/bin/env bash
set -euo pipefail
if (( $# != 3 )); then echo "Usage: $0 REVIEW_JAR DATABASE EMPTY_OUTPUT" >&2; exit 2; fi
catalog_source=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
catalog_temp=$(mktemp -d)
trap 'rm -rf -- "$catalog_temp"' EXIT
mkdir -p "$catalog_temp/classes" "$catalog_temp/prefs"
cp -- "$1" "$catalog_temp/app.jar"
javac -encoding UTF-8 -cp "$catalog_temp/app.jar" -d "$catalog_temp/classes" "$catalog_source/CatalogSnapshots.java"
java --enable-native-access=ALL-UNNAMED -Djava.awt.headless=true -Dflatlaf.uiScale=1 -Djava.util.prefs.userRoot="$catalog_temp/prefs" -cp "$catalog_temp/classes:$catalog_temp/app.jar" CatalogSnapshots "$catalog_temp/app.jar" "$2" "$3"
