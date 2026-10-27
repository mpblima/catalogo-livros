#!/usr/bin/env bash
set -euo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
vcpkg_ref=2026.10.16
tool_root="$repo/target/vcpkg-macos"
overlay="$repo/target/vcpkg-overlay/tesseract"
triplet=arm64-osx-dynamic
installed="$tool_root/installed/$triplet"
native="$repo/src/main/resources/catalogo/ocr/native/macos-aarch64"
licenses="$repo/src/main/resources/catalogo/ocr/licencas/macos"

[[ "$(uname -m)" == arm64 ]] || { echo 'O pacote macOS de OCR exige runner arm64.' >&2; exit 1; }
rm -rf "$tool_root" "$native" "$licenses"
mkdir -p "$(dirname "$tool_root")" "$native" "$licenses"
python3 "$repo/packaging/download_tessdata.py" "$repo/src/main/resources/catalogo/ocr"
git clone --depth 1 --branch "$vcpkg_ref" https://github.com/microsoft/vcpkg.git "$tool_root"
(cd "$tool_root" && ./bootstrap-vcpkg.sh -disableMetrics)
mkdir -p "$overlay"
cp -R "$tool_root/ports/tesseract/." "$overlay/"
python3 "$repo/packaging/disable_tesseract_openmp.py" "$overlay/portfile.cmake"
"$tool_root/vcpkg" install "tesseract:$triplet" \
  --overlay-triplets "$repo/packaging/vcpkg-triplets" \
  --overlay-ports "$repo/target/vcpkg-overlay"
python3 "$repo/packaging/bundle_macos_ocr.py" "$installed/lib" "$native" "$licenses"
