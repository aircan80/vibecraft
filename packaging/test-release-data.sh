#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIXTURE="$(mktemp -d)"
trap 'rm -rf "$FIXTURE"' EXIT
mkdir -p "$FIXTURE/root/data"
printf 'dataset fixture\n' > "$FIXTURE/root/data/wowforever.sqlite"
printf 'not selected\n' > "$FIXTURE/root/data/unreviewed.png"
DATA_SHA="$(sha256sum "$FIXTURE/root/data/wowforever.sqlite")"
DATA_SHA="${DATA_SHA%% *}"
DATA_BYTES="$(wc -c < "$FIXTURE/root/data/wowforever.sqlite")"
printf '%s\t%s\tdata/wowforever.sqlite\n' "$DATA_SHA" "$DATA_BYTES" > "$FIXTURE/manifest.tsv"
java "$SCRIPT_DIR/ReleaseData.java" "$FIXTURE/root" "$FIXTURE/manifest.tsv" "$FIXTURE/staged"
test -f "$FIXTURE/staged/data/wowforever.sqlite"
test -f "$FIXTURE/staged/data/release-data-inventory.tsv"
test ! -e "$FIXTURE/staged/data/unreviewed.png"
printf 'modified\n' >> "$FIXTURE/root/data/wowforever.sqlite"
if java "$SCRIPT_DIR/ReleaseData.java" "$FIXTURE/root" "$FIXTURE/manifest.tsv" "$FIXTURE/tampered" >/dev/null 2>&1; then
  echo 'FAIL: accepted changed dataset' >&2; exit 1
fi
for bad in data/../wowforever.sqlite data/worker.py; do
  printf '%s\t%s\t%s\n' "$DATA_SHA" "$DATA_BYTES" "$bad" > "$FIXTURE/bad.tsv"
  if java "$SCRIPT_DIR/ReleaseData.java" "$FIXTURE/root" "$FIXTURE/bad.tsv" "$FIXTURE/invalid" >/dev/null 2>&1; then
    echo "FAIL: accepted $bad" >&2; exit 1
  fi
done
echo 'PASS: exact file staging/inventory; unlisted files omitted; tamper, traversal and worker rejection'
