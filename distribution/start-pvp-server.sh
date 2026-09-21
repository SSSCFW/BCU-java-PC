#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
exec java -Djava.awt.headless=true -Dfile.encoding=UTF-8 -cp 'bcu-pvp.jar:lib/*' online.net.PvpServerMain "$@"
