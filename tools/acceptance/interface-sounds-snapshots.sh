#!/usr/bin/env bash
set -euo pipefail
if (( $# != 3 )); then echo "Usage: $0 REVIEW_JAR DATABASE EMPTY_OUTPUT" >&2; exit 2; fi
sound_source=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
sound_temp=$(mktemp -d)
trap 'rm -rf -- "$sound_temp"' EXIT
mkdir -p "$sound_temp/classes" "$sound_temp/prefs"
javac -encoding UTF-8 -cp "$1" -d "$sound_temp/classes" "$sound_source/JournalSnapshots.java" "$sound_source/ResearchAcceptance.java" "$sound_source/InterfaceSoundsAcceptance.java"
java --enable-native-access=ALL-UNNAMED -Dwowforever.music.enabled=false -Dwowforever.sound.enabled=false -Djava.awt.headless=false -Dflatlaf.uiScale=1 -Djava.util.prefs.userRoot="$sound_temp/prefs" -cp "$sound_temp/classes:$1" InterfaceSoundsAcceptance "$@"
