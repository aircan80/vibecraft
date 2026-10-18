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
NATIVE_VERSION="${NATIVE_APP_VERSION:-$APP_VERSION}"
if [[ ! "$APP_VERSION" =~ ^[A-Za-z0-9][A-Za-z0-9.+_-]*$ ]]; then
  echo "Invalid APP_VERSION; use a release identifier without spaces or paths." >&2; exit 1
fi
case "$OS" in
  Darwin)
    TYPE="${PKG_TYPE:-app-image}"
    # JDK21 validates native CFBundleVersion separately from our product release identity.
    if [[ ! "$NATIVE_VERSION" =~ ^[1-9][0-9]*(\.[0-9]+){0,2}$ ]]; then
      echo "macOS needs an explicit numeric NATIVE_APP_VERSION with positive major version." >&2
      echo "APP_VERSION remains the actual product release; it will not be silently rewritten." >&2
      exit 1
    fi
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

RELEASE_DATA=0
if [ -n "${RELEASE_DATA_DIR:-}" ] || [ -n "${RELEASE_DATA_MANIFEST:-}" ]; then
  : "${RELEASE_DATA_DIR:?Set RELEASE_DATA_DIR and RELEASE_DATA_MANIFEST together}"
  : "${RELEASE_DATA_MANIFEST:?Set RELEASE_DATA_DIR and RELEASE_DATA_MANIFEST together}"
  if [ "${ALLOW_PRIVATE_ASSETS:-0}" = 1 ]; then
    echo "Reviewed release-data and private-QA modes cannot be combined." >&2; exit 1
  fi
  RELEASE_DATA=1
fi
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
printf 'product-version=%s\nnative-package-version=%s\n' "$APP_VERSION" "$NATIVE_VERSION" > "$INPUT/release-version.txt"
EXTRA+=(--java-options "-Dwowforever.release.version=$APP_VERSION")
if [ "$RELEASE_DATA" = 1 ]; then
  java "$SCRIPT_DIR/ReleaseData.java" "$RELEASE_DATA_DIR" "$RELEASE_DATA_MANIFEST" "$INPUT"
  EXTRA+=(--java-options '-Dwowforever.database=$APPDIR/data/wowforever.sqlite'
    --java-options '-Dwowforever.data.dir=$APPDIR/data')
fi

echo "Running jpackage (type: $TYPE)..."
jpackage \
  --input "$INPUT" \
  --main-jar "$JAR_NAME" \
  --main-class dev.wowforever.app.Main \
  --name "Vibecraft" \
  --app-version "$NATIVE_VERSION" \
  --dest "$DEST" \
  --java-options "-Djava.awt.headless=false" \
  --vendor "Vibecraft contributors" \
  --description "A World of Warcraft companion for exploration, loot, crafting, and lore" \
  --type "$TYPE" \
  "${EXTRA[@]}"

rm -rf "$INPUT"
if [ "$TYPE" = app-image ]; then
  IMAGE="Vibecraft"
  if [ "$OS" = Darwin ]; then IMAGE="$IMAGE.app"; fi
  LABEL="vibecraft-${APP_VERSION}-$(uname -s)-$(uname -m)"
  if [ "$RELEASE_DATA" = 1 ]; then LABEL="$LABEL-with-data"; fi
  if [ "${ALLOW_PRIVATE_ASSETS:-0}" = 1 ]; then LABEL="$LABEL-PRIVATE-QA"; fi
  tar -czf "$DEST/$LABEL.tar.gz" -C "$DEST" "$IMAGE"
  echo "Portable archive: $DEST/$LABEL.tar.gz"
fi
echo "Done. Output in $DEST"
