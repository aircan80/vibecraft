#!/usr/bin/env bash
set -euo pipefail
if (( $# != 4 )); then echo "Usage: $0 REVIEW_JAR DATABASE EMPTY_OUTPUT headless|native" >&2; exit 2; fi
journal_source=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
journal_temp=$(mktemp -d)
trap 'rm -rf -- "$journal_temp"' EXIT
mkdir -p "$journal_temp/classes" "$journal_temp/prefs"
javac -encoding UTF-8 -cp "$1" -d "$journal_temp/classes" "$journal_source/JournalSnapshots.java"
journal_headless=true
if [[ "$4" == native ]]; then journal_headless=false; fi
java --enable-native-access=ALL-UNNAMED -Dwowforever.music.enabled=false -Djava.awt.headless="$journal_headless" -Dflatlaf.uiScale=1 -Djava.util.prefs.userRoot="$journal_temp/prefs" -cp "$journal_temp/classes:$1" JournalSnapshots "$1" "$2" "$3" "$4"
