#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
exec java -Dfile.encoding=UTF-8 -Xmx2G -jar bcu-pvp.jar
