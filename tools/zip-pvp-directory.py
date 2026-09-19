#!/usr/bin/env python3
"""Deterministic portable archive, stable across Actions artifact extraction."""
from pathlib import Path
import hashlib
import os
import stat
import sys
import tempfile
import zipfile


def package(directory: Path, output: Path) -> None:
    directory, output = Path(directory), Path(output)
    if not directory.is_dir() or directory.is_symlink():
        raise ValueError("Expected a real distribution directory")
    paths = sorted(directory.rglob("*"), key=lambda p: p.relative_to(directory).as_posix())
    for path in paths:
        if path.is_symlink() or not (path.is_dir() or path.is_file()):
            raise ValueError(f"Unsafe distribution entry: {path}")
    output.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(prefix="pvp-package-", suffix=".tmp", dir=output.parent)
    os.close(fd)
    try:
        with zipfile.ZipFile(tmp, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for path in paths:
                if not path.is_file():
                    continue
                name = "bcu-pvp-portable/" + path.relative_to(directory).as_posix()
                info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                info.create_system = 3
                info.external_attr = (stat.S_IFREG | (0o755 if path.suffix == ".sh" else 0o644)) << 16
                info.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(info, path.read_bytes(), compresslevel=9)
        os.replace(tmp, output)
    finally:
        Path(tmp).unlink(missing_ok=True)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    Path(str(output)+".sha256").write_text(f"{digest}  {output.name}\n", encoding="ascii")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: zip-pvp-directory.py DISTRIBUTION_DIRECTORY OUTPUT.zip")
    package(Path(sys.argv[1]), Path(sys.argv[2]))
