#!/usr/bin/env python3
"""Disable OpenMP in the pinned vcpkg Tesseract port for portable app bundles."""
from pathlib import Path
import sys

portfile = Path(sys.argv[1])
source = portfile.read_text()
needle = "    OPTIONS\n        ${FEATURE_OPTIONS}\n"
if source.count(needle) != 1:
    raise SystemExit("Unexpected vcpkg Tesseract port layout")
portfile.write_text(source.replace(needle, needle + "        -DOPENMP_BUILD=OFF\n"))
