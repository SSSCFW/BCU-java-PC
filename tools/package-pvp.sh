#!/usr/bin/env bash
# Run after: mvn clean verify dependency:copy-dependencies -DoutputDirectory=target/lib
set -euo pipefail
cd "$(dirname "$0")/.."
test -f target/bcu-pvp.jar
test -d target/lib
rm -rf target/bcu-pvp-portable
mkdir -p target/bcu-pvp-portable/lib
cp target/bcu-pvp.jar target/bcu-pvp-portable/
cp target/lib/*.jar target/bcu-pvp-portable/lib/
cp distribution/* target/bcu-pvp-portable/
cp docs/ONLINE_PVP_JA.md target/bcu-pvp-portable/README_JA.md
printf 'Desktop: %s\nCommon: %s\n' "$(git rev-parse HEAD)" "8920447e73bea56289a2da5a2a9294e24ff08c67" > target/bcu-pvp-portable/REVISION.txt
python3 tools/zip-pvp-directory.py target/bcu-pvp-portable target/bcu-pvp-portable.zip
python3 tools/verify-pvp-package.py target/bcu-pvp-portable.zip
(cd target && sha256sum -c bcu-pvp-portable.zip.sha256)
