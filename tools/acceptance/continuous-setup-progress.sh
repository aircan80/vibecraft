#!/usr/bin/env bash
set -euo pipefail
if (( $# != 2 )); then echo 'Usage: SOURCE_JAR EMPTY_OUTPUT' >&2; exit 2; fi
setup_source=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
setup_temp=$(mktemp -d)
trap 'rm -rf -- "$setup_temp"' EXIT
mkdir -p "$setup_temp/classes" "$setup_temp/prefs" "$setup_temp/data" "$setup_temp/cache" "$setup_temp/config" "$setup_temp/home"
javac -encoding UTF-8 -cp "$1" -d "$setup_temp/classes" "$setup_source/JournalSnapshots.java" "$setup_source/ResearchAcceptance.java" "$setup_source/FirstRunSetupAcceptance.java" "$setup_source/AuthoredSetupJobAcceptance.java" "$setup_source/ContinuousSetupProgressAcceptance.java"
env -u _JAVA_AWT_WM_NONREPARENTING -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS -u _JAVA_OPTIONS -u CLASSPATH XDG_DATA_HOME="$setup_temp/data" XDG_CACHE_HOME="$setup_temp/cache" XDG_CONFIG_HOME="$setup_temp/config" java --enable-native-access=ALL-UNNAMED -Dwowforever.music.enabled=false -Dwowforever.sound.enabled=false -Djava.awt.headless=false -Dflatlaf.uiScale=1 -Duser.home="$setup_temp/home" -Djava.util.prefs.userRoot="$setup_temp/prefs" -cp "$setup_temp/classes:$1" ContinuousSetupProgressAcceptance "$@"
