#!/usr/bin/env python3
"""Validate portable layout and essential v2 classes, without running downloaded code."""
from pathlib import Path, PurePosixPath
import io
import sys
import zipfile

REQUIRED_FILES = {
    "bcu-pvp.jar", "README_JA.md", "REVISION.txt", "pvp-server.properties",
    "start-pvp.bat", "start-pvp.sh", "start-pvp-server.bat", "start-pvp-server.sh",
}
REQUIRED_CLASSES = {
    "online/net/PvpServerMain.class", "online/net/RoomClient.class",
    "online/net/core/RoomServerCore.class", "online/net/core/LockstepState.class",
    "online/net/realtime/UdpRealtimeClient.class", "online/net/realtime/UdpRealtimeServer.class",
    "online/net/realtime/UdpPacketCodec.class", "online/net/duel/DuelRoster.class",
    "online/ui/FriendServerPanel.class", "common/battle/PvpStageBasis.class",
}


def verify(path: Path) -> None:
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate ZIP entries")
        prefix = "bcu-pvp-portable/"
        for name in names:
            if not name.startswith(prefix) or ".." in PurePosixPath(name).parts or "\\" in name:
                raise ValueError(f"Invalid distribution path: {name}")
            if name.lower().endswith(".zip"):
                raise ValueError("Nested ZIP in distribution")
        relative = {name[len(prefix):] for name in names}
        if not REQUIRED_FILES <= relative:
            raise ValueError(f"Missing required files: {REQUIRED_FILES-relative}")
        if not any(name.startswith("lib/") and name.endswith(".jar") for name in relative):
            raise ValueError("Runtime dependency directory is missing")
        if any(name.startswith(("assets/", "user/", "workspace/", "packs/")) for name in relative):
            raise ValueError("Private/game data must not be packaged")
        if archive.testzip() is not None:
            raise ValueError("ZIP CRC verification failed")
        with zipfile.ZipFile(io.BytesIO(archive.read(prefix+"bcu-pvp.jar"))) as jar:
            if not REQUIRED_CLASSES <= set(jar.namelist()):
                raise ValueError("Portable JAR does not contain the hybrid transport implementation")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: verify-pvp-package.py PACKAGE.zip")
    verify(Path(sys.argv[1]))
    print("Portable package verified")
