#!/usr/bin/env bash
# Builds a World English Bible zip containing only John 1, for the smoke test.
# Usage: make-minimal-web.sh <full engwebu_usfm.zip> <output zip>
set -euo pipefail

src="$1"
out="$(realpath -m "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

unzip -q "$src" -d "$work/full"
john="$(find "$work/full" -type f \( -name '*.usfm' -o -name '*.sfm' \) | grep -E 'JHN|-43-' | head -n 1)"
if [ -z "$john" ]; then
  echo "John USFM file not found in $src" >&2
  exit 1
fi

mkdir -p "$work/minimal"
# Keep the file header and chapter 1 only, dropping everything from chapter 2 onward.
awk '/^\\c 2([[:space:]]|$)/ { exit } { print }' "$john" > "$work/minimal/$(basename "$john")"
grep -q '^\\c 1' "$work/minimal/$(basename "$john")"

(cd "$work/minimal" && zip -q -r "$out" .)
echo "Wrote $out ($(unzip -l "$out" | tail -n 1))"
