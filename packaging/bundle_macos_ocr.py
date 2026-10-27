#!/usr/bin/env python3
"""Relocate the vcpkg Tesseract dylib closure into app resources."""
from pathlib import Path
import re
import shutil
import subprocess
import sys

source = Path(sys.argv[1]).resolve()
destination = Path(sys.argv[2]).resolve()
licenses = Path(sys.argv[3]).resolve()
destination.mkdir(parents=True, exist_ok=True)
licenses.mkdir(parents=True, exist_ok=True)

for library in sorted(source.glob("*.dylib")):
    shutil.copyfile(library.resolve(), destination / library.name)

libraries = sorted(destination.glob("*.dylib"))
tesseracts = [path for path in libraries if path.name.startswith("libtesseract")]
leptonicas = [path for path in libraries if path.name.startswith(("liblept", "libleptonica"))]
if not tesseracts or not leptonicas:
    raise SystemExit("vcpkg Tesseract/Leptonica dylibs were not found")
tesseract = min(tesseracts, key=lambda path: (len(path.name), path.name))
leptonica = min(leptonicas, key=lambda path: (len(path.name), path.name))

for library in libraries:
    dependencies = subprocess.check_output(["otool", "-L", str(library)], text=True).splitlines()[1:]
    for row in dependencies:
        match = re.match(r"\s*(.*?)\s+\(compatibility version", row)
        if not match:
            continue
        dependency = match.group(1)
        if dependency.startswith(("/System/Library/", "/usr/lib/")):
            continue
        bundled = destination / Path(dependency).name
        if not bundled.is_file():
            raise SystemExit(f"Unbundled dylib dependency {dependency} required by {library.name}")
        subprocess.run(
            ["install_name_tool", "-change", dependency, f"@loader_path/{bundled.name}", str(library)],
            check=True,
        )
    subprocess.run(["install_name_tool", "-id", f"@rpath/{library.name}", str(library)], check=True)

for library in (tesseract, leptonica):
    architectures = subprocess.check_output(["lipo", "-archs", str(library)], text=True).split()
    if "arm64" not in architectures:
        raise SystemExit(f"Wrong architecture for {library.name}: {architectures}")

for package in sorted((source.parent / "share").iterdir()):
    notice = package / "copyright"
    if package.is_dir() and notice.is_file():
        shutil.copyfile(notice, licenses / f"{package.name}.txt")
if not (licenses / "tesseract.txt").is_file():
    raise SystemExit("Tesseract copyright notice is missing")

files = sorted(path.name for path in destination.glob("*.dylib"))
manifest = destination / "manifest.properties"
manifest.write_text(
    f"tesseract={tesseract.name}\nleptonica={leptonica.name}\nfiles={','.join(files)}\n",
    encoding="ascii",
)
print(f"Bundled {len(files)} macOS dylibs; Tesseract: {tesseract.name}")
