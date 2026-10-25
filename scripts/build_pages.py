#!/usr/bin/env python3
"""Gera o site e um retrato autenticado das releases, sem expor tokens no navegador."""
import json
import os
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORMATS = {"linux": (".run", ".deb", ".tar.gz", ".rpm"), "windows": (".msi", ".exe"), "macos": (".dmg", ".pkg")}

def snapshot(releases):
    stable = sorted((r for r in releases if not r.get("draft") and not r.get("prerelease")), key=lambda r: r.get("published_at") or "", reverse=True)
    platforms = {}
    total = 0
    for platform, extensions in FORMATS.items():
        downloads = sum(a.get("download_count", 0) for r in stable for a in r.get("assets", []) if a["name"].lower().endswith(extensions))
        total += downloads
        for release in stable:
            assets = [a for a in release.get("assets", []) if a["name"].lower().endswith(extensions)]
            assets.sort(key=lambda a: next(i for i, ext in enumerate(extensions) if a["name"].lower().endswith(ext)))
            if assets:
                platforms[platform] = {"version": release["tag_name"], "published": release["published_at"], "downloads": downloads, "assets": [{"name": a["name"], "url": a["browser_download_url"], "size": a.get("size", 0)} for a in assets]}
                break
        else:
            platforms[platform] = {"version": None, "downloads": downloads, "assets": []}
    return {"updated": datetime.now(timezone.utc).isoformat(), "downloads": total, "platforms": platforms}

def main():
    repo = os.environ.get("GITHUB_REPOSITORY", "mpblima/catalogo-livros")
    releases = []
    page = 1
    while True:
        batch = json.loads(subprocess.check_output(["gh", "api", f"repos/{repo}/releases?per_page=100&page={page}"], text=True))
        releases.extend(batch)
        if len(batch) < 100:
            break
        page += 1
    data = snapshot(releases)
    dest = ROOT / "_site"
    if dest.exists():
        shutil.rmtree(dest)
    shutil.copytree(ROOT / "docs", dest)
    (dest / "downloads.json").write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    print(f"Site gerado: {data['downloads']} downloads; " + ", ".join(f"{p}: {v['version']}" for p, v in data['platforms'].items()))

if __name__ == "__main__":
    main()
