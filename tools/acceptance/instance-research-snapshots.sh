#!/usr/bin/env bash
set -euo pipefail
if (( $# != 3 )); then echo "Usage: $0 REVIEW_JAR DATABASE EMPTY_OUTPUT" >&2; exit 2; fi
instance_source=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
instance_temp=$(mktemp -d)
trap 'rm -rf -- "$instance_temp"' EXIT
mkdir -p "$instance_temp/classes" "$instance_temp/prefs"
javac -encoding UTF-8 -cp "$1" -d "$instance_temp/classes" "$instance_source/JournalSnapshots.java" "$instance_source/ResearchAcceptance.java" "$instance_source/InstanceResearchAcceptance.java"
java --enable-native-access=ALL-UNNAMED -Dwowforever.music.enabled=false -Dwowforever.sound.enabled=false -Djava.awt.headless=false -Dflatlaf.uiScale=1 -Djava.util.prefs.userRoot="$instance_temp/prefs" -cp "$instance_temp/classes:$1" InstanceResearchAcceptance "$@"
