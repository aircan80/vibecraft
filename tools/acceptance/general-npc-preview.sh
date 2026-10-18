#!/usr/bin/env bash
set -euo pipefail
if (( $# != 6 )); then echo "Usage: $0 REVIEW_JAR DATABASE EMPTY_OUTPUT NPC_ID generate|cached|unresolved|variants CACHE_DIRECTORY" >&2; exit 2; fi
preview_source=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
preview_temp=$(mktemp -d)
trap 'rm -rf -- "$preview_temp"' EXIT
mkdir -p "$preview_temp/classes" "$preview_temp/prefs"
javac -encoding UTF-8 -cp "$1" -d "$preview_temp/classes" "$preview_source/JournalSnapshots.java" "$preview_source/GeneralNpcPreviewAcceptance.java"
java --enable-native-access=ALL-UNNAMED -Dwowforever.music.enabled=false -Djava.awt.headless=false -Dflatlaf.uiScale=1 -Djava.util.prefs.userRoot="$preview_temp/prefs" -cp "$preview_temp/classes:$1" GeneralNpcPreviewAcceptance "$@"
