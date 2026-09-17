#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p target/pvp-test-classes
CP="target/classes:target/lib/*:/usr/share/java/java_websocket.jar:/usr/share/java/slf4j-api.jar:/usr/share/java/slf4j-simple.jar"
find src/pvp/java src/main/java/online -name '*.java' > target/pvp-prod.list
printf '%s\n' src/main/java/page/MainPage.java src/main/java/io/BCMusic.java src/main/java/io/BCPlayer.java \
  src/main/java/page/battle/BattleInfoPage.java src/main/java/page/battle/BattleBox.java \
  src/main/java/page/battle/BBCtrl.java src/main/java/page/battle/EntityTable.java \
  src/main/java/page/battle/TotalDamageTable.java src/main/java/page/awt/BattleBoxDef.java >> target/pvp-prod.list
javac --release 8 -Xlint:-options -encoding UTF-8 -cp "$CP" -d target/classes @target/pvp-prod.list
find src/test/java -name '*.java' > target/pvp-tests.list
javac --release 8 -Xlint:-options -encoding UTF-8 -cp "$CP" -d target/pvp-test-classes @target/pvp-tests.list
java -ea -Djava.awt.headless=true -cp "target/pvp-test-classes:$CP" online.tests.AllTests
