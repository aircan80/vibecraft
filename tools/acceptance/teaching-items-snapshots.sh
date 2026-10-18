#!/usr/bin/env bash
set -euo pipefail
if (( $# != 3 )); then echo "Usage: $0 REVIEW_JAR DATABASE EMPTY_OUTPUT" >&2; exit 2; fi
teaching_source=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
teaching_temp=$(mktemp -d)
trap 'rm -rf -- "$teaching_temp"' EXIT
mkdir -p "$teaching_temp/classes" "$teaching_temp/prefs"
javac -encoding UTF-8 -cp "$1" -d "$teaching_temp/classes" "$teaching_source/JournalSnapshots.java" "$teaching_source/ResearchAcceptance.java" "$teaching_source/TeachingItemsAcceptance.java"
java --enable-native-access=ALL-UNNAMED -Dwowforever.music.enabled=false -Dwowforever.sound.enabled=false -Djava.awt.headless=false -Dflatlaf.uiScale=1 -Djava.util.prefs.userRoot="$teaching_temp/prefs" -cp "$teaching_temp/classes:$1" TeachingItemsAcceptance "$@"
