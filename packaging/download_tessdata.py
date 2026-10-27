#!/usr/bin/env python3
"""Fetch the pinned English and Portuguese Tesseract models for native builds."""
from pathlib import Path
import os
import sys
import urllib.request

REVISION = "87416418657359cb625c412a48b6e1d6d41c29bd"
BASE = f"https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/{REVISION}"


def download(url: str, destination: Path, minimum_size: int = 1):
    request = urllib.request.Request(url, headers={"User-Agent": "catalogo-livros-build"})
    with urllib.request.urlopen(request, timeout=120) as response:
        data = response.read()
    if len(data) < minimum_size:
        raise SystemExit(f"Download incompleto para {destination.name}.")
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    try:
        temporary.write_bytes(data)
        os.replace(temporary, destination)
    finally:
        temporary.unlink(missing_ok=True)


root = Path(sys.argv[1])
tessdata = root / "tessdata"
licenses = root / "licencas"
for language in ("eng", "por"):
    download(f"{BASE}/{language}.traineddata", tessdata / f"{language}.traineddata", 1_000_000)
download(f"{BASE}/LICENSE", licenses / "tessdata_fast.txt", 100)
print(f"Tesseract models eng+por included from {REVISION}.")
