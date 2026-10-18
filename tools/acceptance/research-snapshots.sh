#!/usr/bin/env bash
set -euo pipefail
if (( $# != 3 )); then echo "Usage: $0 REVIEW_JAR DATABASE EMPTY_OUTPUT" >&2; exit 2; fi
research_source=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
research_temp=$(mktemp -d)
trap 'rm -rf -- "$research_temp"' EXIT
mkdir -p "$research_temp/classes" "$research_temp/prefs"
javac -encoding UTF-8 -cp "$1" -d "$research_temp/classes" "$research_source/JournalSnapshots.java" "$research_source/ResearchAcceptance.java"
java --enable-native-access=ALL-UNNAMED -Dwowforever.music.enabled=false -Dwowforever.sound.enabled=false -Djava.awt.headless=false -Dflatlaf.uiScale=1 -Djava.util.prefs.userRoot="$research_temp/prefs" -cp "$research_temp/classes:$1" ResearchAcceptance "$@"
