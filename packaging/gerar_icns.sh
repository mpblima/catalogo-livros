#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
iconset="$root/target/catalogo.iconset"
mkdir -p "$iconset"
trap 'rm -rf "$iconset"' EXIT
for size in 16 32 128 256 512; do
  sips -z "$size" "$size" "$root/packaging/catalogo.png" --out "$iconset/icon_${size}x${size}.png" >/dev/null
  doubled=$((size * 2))
  sips -z "$doubled" "$doubled" "$root/packaging/catalogo.png" --out "$iconset/icon_${size}x${size}@2x.png" >/dev/null
done
iconutil -c icns "$iconset" -o "$root/target/catalogo.icns"
