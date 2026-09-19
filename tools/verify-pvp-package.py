#!/usr/bin/env python3
"""Validate portable layout and essential v4 classes, without running downloaded code."""
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
    "online/ui/OnlineBattleField.class", "page/battle/BattleInfoPage.class",
    "page/battle/BattleBox$PlayerView.class", "page/battle/BBCtrl.class",
    "online/ui/RoomLobbyPage.class", "online/ui/AudioSettingsPanel.class",
    "online/net/lobby/RoomRules.class", "common/battle/PvpAudio.class", "common/battle/ELineUp.class",
}
REQUIRED_ADD_OPENS = {
    "java.base/java.lang", "java.desktop/sun.java2d",
    "java.desktop/sun.awt", "java.desktop/sun.awt.windows",
}


def manifest_entries(data: bytes) -> dict[str, str]:
    logical = []
    for line in data.decode("utf-8").replace("\r\n", "\n").split("\n"):
        if line.startswith(" ") and logical:
            logical[-1] += line[1:]
        else:
            logical.append(line)
    return dict(line.split(": ", 1) for line in logical if ": " in line)



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
            jar_names = set(jar.namelist())
            if not REQUIRED_CLASSES <= jar_names:
                raise ValueError("Portable JAR is missing the hybrid transport or native battle UI")
            if "online/ui/PvpCanvas.class" in jar_names:
                raise ValueError("Obsolete custom PvP battlefield must not be distributed")
            if "META-INF/MANIFEST.MF" not in jar_names:
                raise ValueError("Portable JAR manifest is missing")
            manifest = manifest_entries(jar.read("META-INF/MANIFEST.MF"))
            if manifest.get("Main-Class") != "main.MainBCU":
                raise ValueError("Portable JAR main class is missing")
            opens = set(manifest.get("Add-Opens", "").split())
            if not REQUIRED_ADD_OPENS <= opens:
                raise ValueError(f"Portable JAR is missing JDK 21 Add-Opens: {REQUIRED_ADD_OPENS-opens}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: verify-pvp-package.py PACKAGE.zip")
    verify(Path(sys.argv[1]))
    print("Portable package verified")
