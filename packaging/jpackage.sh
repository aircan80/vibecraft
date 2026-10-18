#!/usr/bin/env bash
# Builds a portable runtime bundle by default; native installers remain optional.
# Usage: packaging/jpackage.sh        (portable app-image + archive)
#        PKG_TYPE=rpm packaging/jpackage.sh   (override the package type)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR_NAME="wow-forever-browser.jar"
JAR="${APP_JAR:-$ROOT/app/target/$JAR_NAME}"
DEST="${PACKAGE_DEST:-$ROOT/packaging/dist}"
INPUT="$DEST/input"

if [ ! -f "$JAR" ]; then
  echo "Missing $JAR" >&2
  echo "Build it first: ./mvnw -B -Psource-only -pl app -am clean package -DskipTests" >&2
  exit 1
fi
command -v jpackage >/dev/null 2>&1 || { echo "jpackage not found; use a JDK 21 and put it on PATH." >&2; exit 1; }

OS="$(uname -s)"
EXTRA=()
APP_VERSION="${APP_VERSION:-0.1.0}"
case "$OS" in
  Darwin)
    TYPE="${PKG_TYPE:-app-image}"
    # macOS rejects an app version whose first number is 0 (the bundle version must start at 1 or higher).
    APP_VERSION="1.0.0"
    EXTRA+=(--mac-package-identifier dev.wowforever.browser)
    ;;
  Linux)
    TYPE="${PKG_TYPE:-app-image}"
    if [ "$TYPE" != app-image ]; then EXTRA+=(--linux-shortcut); fi
    ;;
  *)
    echo "Unsupported OS '$OS'; use packaging/jpackage.ps1 on Windows." >&2
    exit 1
    ;;
esac

AUDIT_ARGS=()
if [ "${ALLOW_PRIVATE_ASSETS:-0}" = 1 ]; then
  AUDIT_ARGS+=(--private-qa)
  echo "PRIVATE QA ONLY: this output contains local assets and must not be published." >&2
fi
java "$SCRIPT_DIR/ArchiveAudit.java" "$JAR" "${AUDIT_ARGS[@]}"

# Stage only the shaded jar so test jars and class dirs are not bundled.
rm -rf "$INPUT"
mkdir -p "$INPUT"
cp "$JAR" "$INPUT/$JAR_NAME"

echo "Running jpackage (type: $TYPE)..."
jpackage \
  --input "$INPUT" \
  --main-jar "$JAR_NAME" \
  --main-class dev.wowforever.app.Main \
  --name "WoW Forever Browser" \
  --app-version "$APP_VERSION" \
  --dest "$DEST" \
  --java-options "-Djava.awt.headless=false" \
  --vendor "wow-forever-browser contributors" \
  --description "Browse World of Warcraft: Forever items and their sources" \
  --type "$TYPE" \
  "${EXTRA[@]}"

rm -rf "$INPUT"
if [ "$TYPE" = app-image ]; then
  IMAGE="WoW Forever Browser"
  if [ "$OS" = Darwin ]; then IMAGE="$IMAGE.app"; fi
  LABEL="wow-forever-browser-${APP_VERSION}-$(uname -s)-$(uname -m)"
  if [ "${ALLOW_PRIVATE_ASSETS:-0}" = 1 ]; then LABEL="$LABEL-PRIVATE-QA"; fi
  tar -czf "$DEST/$LABEL.tar.gz" -C "$DEST" "$IMAGE"
  echo "Portable archive: $DEST/$LABEL.tar.gz"
fi
echo "Done. Output in $DEST"
