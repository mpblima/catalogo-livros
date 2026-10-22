#!/usr/bin/env bash
# Dependências de compilação: libtesseract, leptonica, libgomp e modelos eng/por.
set -euo pipefail
cd "$(dirname "$0")/.."
dest=src/main/resources/catalogo/ocr
mkdir -p "$dest/native" "$dest/tessdata"
find_library() {
  local pattern="$1" found
  found=$(ldconfig -p | awk -v pattern="$pattern" '$1 ~ pattern && /x86-64/ && !found {print $NF; found=1}')
  [[ -n "$found" && -f "$found" ]] || { echo "Biblioteca ausente: $pattern" >&2; exit 1; }
  printf '%s' "$found"
}
cp -L "$(find_library '^libtesseract.so.5$')" "$dest/native/libtesseract.so.5"
cp -L "$(find_library '^lib(lept|leptonica).so.[0-9]+$')" "$dest/native/libleptonica.so"
cp -L "$(find_library '^libgomp.so.1$')" "$dest/native/libgomp.so.1"
for lang in eng por; do
  model=""
  for candidate in "/usr/share/tesseract-ocr/5/tessdata/$lang.traineddata" "/usr/share/tessdata/$lang.traineddata"; do
    if [[ -f "$candidate" ]]; then model="$candidate"; break; fi
  done
  if [[ -z "$model" && -s "$dest/tessdata/$lang.traineddata" ]]; then continue; fi
  [[ -n "$model" ]] || { echo "Instale tesseract-ocr-$lang" >&2; exit 1; }
  cp "$model" "$dest/tessdata/"
done
