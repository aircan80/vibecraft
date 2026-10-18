#!/usr/bin/env bash
set -euo pipefail
if (( $# != 4 )); then echo 'Usage: REVIEW_JAR COMPACT_DB REVIEWED_DATA_DIR EMPTY_OUTPUT' >&2; exit 2; fi
header_source=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
header_temp=$(mktemp -d)
trap 'rm -rf -- "$header_temp"' EXIT
mkdir -p "$header_temp/classes" "$header_temp/prefs"
javac -encoding UTF-8 -cp "$1" -d "$header_temp/classes" "$header_source/JournalSnapshots.java" "$header_source/ResearchAcceptance.java" "$header_source/JournalHeaderAcceptance.java"
java --enable-native-access=ALL-UNNAMED -Dwowforever.database="$2" -Dwowforever.data.dir="$3" -Dwowforever.music.enabled=false -Dwowforever.sound.enabled=false -Djava.awt.headless=false -Dflatlaf.uiScale=1 -Djava.util.prefs.userRoot="$header_temp/prefs" -cp "$header_temp/classes:$1" JournalHeaderAcceptance "$1" "$4"
