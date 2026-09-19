#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
if [ "$#" -eq 0 ]; then set -- 127.0.0.1 8766; fi
exec java -Dfile.encoding=UTF-8 -cp 'bcu-pvp.jar:lib/*' online.net.RoomServer "$@"
