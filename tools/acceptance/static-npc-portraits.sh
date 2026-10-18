#!/usr/bin/env bash
set -euo pipefail
if (( $# != 3 )); then echo "Usage: $0 REVIEW_JAR DATABASE EMPTY_OUTPUT" >&2; exit 2; fi
portrait_source=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
portrait_temp=$(mktemp -d)
trap 'rm -rf -- "$portrait_temp"' EXIT
mkdir -p "$portrait_temp/classes" "$portrait_temp/prefs"
javac -encoding UTF-8 -cp "$1" -d "$portrait_temp/classes" "$portrait_source/JournalSnapshots.java" "$portrait_source/ResearchAcceptance.java" "$portrait_source/StaticNpcPortraitAcceptance.java"
java --enable-native-access=ALL-UNNAMED -Dwowforever.music.enabled=false -Dwowforever.sound.enabled=false -Dwowforever.npcPreview.cache="${NPC_PORTRAIT_ACCEPTANCE_CACHE:?set existing verified cache input}" -Djava.awt.headless=false -Dflatlaf.uiScale=1 -Djava.util.prefs.userRoot="$portrait_temp/prefs" -cp "$portrait_temp/classes:$1" StaticNpcPortraitAcceptance "$@"
