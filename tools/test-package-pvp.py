#!/usr/bin/env python3
"""Packaging regression checks; no game assets or external services required."""
from pathlib import Path
import hashlib
import importlib.util
import os
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parent

def load(name, filename):
    path = ROOT / filename
    if not path.is_file():
        raise AssertionError(f"Missing packaging implementation: {filename}")
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

class PackageTests(unittest.TestCase):
    def test_reproducible_verified_zip(self):
        pack = load("pvp_zip", "zip-pvp-directory.py")
        verify = load("pvp_verify", "verify-pvp-package.py")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "bcu-pvp-portable"
            root.mkdir()
            for name in verify.REQUIRED_FILES:
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("fixture", encoding="utf-8")
            with zipfile.ZipFile(root / "bcu-pvp.jar", "w") as jar:
                manifest = (
                    "Manifest-Version: 1.0\n"
                    "Main-Class: main.MainBCU\n"
                    "Add-Opens: java.base/java.lang java.desktop/sun.java2d\n"
                    "  java.desktop/sun.awt java.desktop/sun.awt.windows\n\n"
                )
                jar.writestr("META-INF/MANIFEST.MF", manifest.encode("utf-8"))
                for name in verify.REQUIRED_CLASSES:
                    jar.writestr(name, b"test-class")
            (root / "lib").mkdir(exist_ok=True)
            (root / "lib" / "dependency.jar").write_bytes(b"fixture")
            output = Path(directory) / "bcu-pvp-portable.zip"
            pack.package(root, output)
            first = output.read_bytes()
            for path in root.rglob("*"):
                if path.is_file():
                    os.utime(path, (12345678, 12345678))
                    path.chmod(0o600)
            pack.package(root, output)
            self.assertEqual(first, output.read_bytes(), "artifact round-trip preserves ZIP bytes")
            verify.verify(output)
            digest = hashlib.sha256(first).hexdigest()
            self.assertEqual(f"{digest}  {output.name}\n", Path(str(output)+".sha256").read_text())
            with zipfile.ZipFile(output) as archive:
                self.assertFalse(any(n.endswith(".zip") for n in archive.namelist()), "no nested archive")
                mode = archive.getinfo("bcu-pvp-portable/start-pvp-server.sh").external_attr >> 16
                self.assertTrue(mode & 0o111, "shell executables remain executable")
            (root / "old-extra.txt").write_text("stale")
            pack.package(root, output)
            (root / "old-extra.txt").unlink()
            pack.package(root, output)
            self.assertEqual(first, output.read_bytes(), "rebuild does not retain deleted files")
            (root / "nested.zip").write_bytes(b"bad")
            pack.package(root, output)
            with self.assertRaises(ValueError):
                verify.verify(output)

    def test_symlinks_rejected(self):
        pack = load("pvp_zip", "zip-pvp-directory.py")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "bcu-pvp-portable"
            root.mkdir()
            (root / "unsafe").symlink_to(Path(directory))
            with self.assertRaises(ValueError):
                pack.package(root, Path(directory) / "out.zip")

    def test_release_is_gated_and_artifact_is_not_zip(self):
        workflow = (ROOT.parent / ".github/workflows/pvp-ci.yml").read_text()
        self.assertIn("needs: [build, headless, swing, presentation, gradle]", workflow)
        self.assertIn("path: target/bcu-pvp-portable/", workflow)
        self.assertNotIn("path: target/bcu-pvp-portable.zip", workflow)
        self.assertIn('"$EXPECTED_SHA256"', workflow)

if __name__ == "__main__":
    unittest.main()
