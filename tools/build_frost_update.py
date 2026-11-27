#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2015 - 2026 Rime community
# SPDX-License-Identifier: GPL-3.0-or-later

"""Build the in-app White Frost resource update bundle.

The bundle is intentionally built from the curated assets already checked into
this repository, so local adaptations are preserved. CI publishes the result
to the gh-pages branch; the app downloads it from raw.githubusercontent.com
with a jsDelivr fallback.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

REPO_ROOT = Path(__file__).resolve().parents[1]

TREE_SOURCES = [
    ("app/src/main/assets/shared/cn_dicts", "cn_dicts"),
    ("app/src/main/assets/shared/lua", "lua"),
    ("app/src/main/assets/shared/rime_frost", "rime_frost"),
    ("app/data/rime-frost/opencc", "opencc"),
]

FILE_SOURCES = [
    ("app/src/main/assets/shared/rime_frost.dict.yaml", "rime_frost.dict.yaml"),
    ("app/src/main/assets/shared/rime_frost.schema.yaml", "rime_frost.schema.yaml"),
    ("app/src/main/assets/shared/rime_frost_aux.dict.yaml", "rime_frost_aux.dict.yaml"),
    ("app/src/main/assets/shared/rime_frost_aux.schema.yaml", "rime_frost_aux.schema.yaml"),
    ("app/src/main/assets/shared/symbols_v.yaml", "symbols_v.yaml"),
    ("app/src/main/assets/shared/zh-moqi.gram", "zh-moqi.gram"),
]


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def collect_entries() -> list[tuple[Path, str]]:
    entries: list[tuple[Path, str]] = []
    for source, prefix in TREE_SOURCES:
        root = REPO_ROOT / source
        if not root.is_dir():
            raise SystemExit(f"Missing source directory: {source}")
        for path in sorted(root.rglob("*")):
            if not path.is_file() or path.name.startswith("."):
                continue
            entries.append((path, f"{prefix}/{path.relative_to(root).as_posix()}"))
    for source, target in FILE_SOURCES:
        path = REPO_ROOT / source
        if not path.is_file():
            raise SystemExit(f"Missing source file: {source}")
        entries.append((path, target))
    entries.sort(key=lambda item: item[1])
    return entries


def git_commit() -> str:
    env_sha = os.environ.get("GITHUB_SHA", "").strip()
    if env_sha:
        return env_sha
    try:
        return subprocess.check_output(
            ["git", "-C", str(REPO_ROOT), "rev-parse", "HEAD"],
            text=True,
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return "local"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="build/frost-update")
    args = parser.parse_args()

    out_dir = Path(args.out)
    if not out_dir.is_absolute():
        out_dir = REPO_ROOT / out_dir
    out_dir.mkdir(parents=True, exist_ok=True)

    entries = collect_entries()
    zip_path = out_dir / "frost-update.zip"
    manifest_entries = []
    with ZipFile(zip_path, "w", compression=ZIP_DEFLATED, compresslevel=9) as archive:
        for source, target in entries:
            info = ZipInfo(target, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            archive.writestr(info, source.read_bytes())
            manifest_entries.append({"path": target, "sha256": sha256_file(source)})

    version = f"{git_commit()[:12]}-{datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S')}"
    manifest = {
        "version": version,
        "zipSha256": sha256_file(zip_path),
        "fileCount": len(entries),
        "files": manifest_entries,
    }
    manifest_path = out_dir / "manifest.json"
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    print(f"Built White Frost update bundle: {version}")
    print(f"  files: {len(entries)}")
    print(f"  zip:   {zip_path}")
    print(f"  sha256:{manifest['zipSha256']}")


if __name__ == "__main__":
    main()
