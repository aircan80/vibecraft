#!/usr/bin/env bash
# Builds a native installer for WoW Forever Browser with jpackage.
# Usage: packaging/jpackage.sh        (type: dmg on macOS, deb on Linux)
#        PKG_TYPE=rpm packaging/jpackage.sh   (override the package type)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR_NAME="wow-forever-browser.jar"
JAR="$ROOT/app/target/$JAR_NAME"
DEST="$ROOT/packaging/dist"
INPUT="$DEST/input"

if [ ! -f "$JAR" ]; then
  echo "Missing $JAR" >&2
  echo "Build it first: ./mvnw -B -pl app -am package -DskipTests" >&2
  exit 1
fi
command -v jpackage >/dev/null 2>&1 || { echo "jpackage not found; use a JDK 21 and put it on PATH." >&2; exit 1; }

OS="$(uname -s)"
EXTRA=()
case "$OS" in
  Darwin)
    TYPE="${PKG_TYPE:-dmg}"
    EXTRA+=(--mac-package-identifier dev.wowforever.browser)
    ;;
  Linux)
    TYPE="${PKG_TYPE:-deb}"
    EXTRA+=(--linux-shortcut)
    ;;
  *)
    echo "Unsupported OS '$OS'; use packaging/jpackage.ps1 on Windows." >&2
    exit 1
    ;;
esac

# Stage only the shaded jar so test jars and class dirs are not bundled.
rm -rf "$INPUT"
mkdir -p "$INPUT"
cp "$JAR" "$INPUT/"

echo "Running jpackage (type: $TYPE)..."
jpackage \
  --input "$INPUT" \
  --main-jar "$JAR_NAME" \
  --main-class dev.wowforever.app.Main \
  --name "WoW Forever Browser" \
  --app-version 0.1.0 \
  --dest "$DEST" \
  --java-options "-Djava.awt.headless=false" \
  --vendor "wow-forever-browser contributors" \
  --description "Browse World of Warcraft: Forever items and their sources" \
  --type "$TYPE" \
  "${EXTRA[@]}"

rm -rf "$INPUT"
echo "Done. Output in $DEST"
