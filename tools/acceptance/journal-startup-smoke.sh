#!/usr/bin/env bash
set -euo pipefail
if (( $# != 3 )); then echo "Usage: $0 REVIEW_JAR DATABASE EMPTY_OUTPUT" >&2; exit 2; fi
startup_source=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
startup_temp=$(mktemp -d)
trap 'rm -rf -- "$startup_temp"' EXIT
mkdir -p "$startup_temp/classes" "$startup_temp/prefs" "$startup_temp/data/wowforever-browser"
ln -s -- "$(realpath -- "$2")" "$startup_temp/data/wowforever-browser/wowforever.sqlite"
javac -encoding UTF-8 -cp "$1" -d "$startup_temp/classes" "$startup_source/JournalSnapshots.java" "$startup_source/JournalStartupSmoke.java"
env XDG_DATA_HOME="$startup_temp/data" XDG_CACHE_HOME="$startup_temp/cache" java --enable-native-access=ALL-UNNAMED -Dwowforever.music.enabled=false -Dwowforever.sound.enabled=false -Djava.awt.headless=false -Dflatlaf.uiScale=1 -Djava.util.prefs.userRoot="$startup_temp/prefs" -cp "$startup_temp/classes:$1" JournalStartupSmoke "$1" "$3"
