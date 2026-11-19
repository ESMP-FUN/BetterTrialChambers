#!/usr/bin/env bash
# Builds the test server the docs pictures are taken on (see SHOTS.md).
#   tools/docs-guide/install.sh          keep the world if there is one
#   tools/docs-guide/install.sh --fresh  start over with a new world
# Then start it with:
#   ../test-servers/start.sh profiles/btc-docs-26.3 ../BetterTrialChambers/build/libs/BetterTrialChambers-<version>.jar
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
SERVER="$REPO/../test-servers/profiles/btc-docs-26.3"
JAR="$(ls "$REPO"/build/libs/BetterTrialChambers-*.jar | grep -v -- '-mc26' | sort -V | tail -1)"

if [ "${1:-}" = "--fresh" ]; then
    rm -rf "$SERVER/world" "$SERVER/world_nether" "$SERVER/world_the_end" "$SERVER/plugins/BetterTrialChambers"
fi
mkdir -p "$SERVER/plugins/BetterTrialChambers"
echo "paper-26.3-248.jar" > "$SERVER/server-jar"

cat > "$SERVER/server.properties" <<PROPS
server-port=25599
online-mode=false
spawn-protection=0
view-distance=8
simulation-distance=6
level-seed=emerald-smp
difficulty=normal
gamemode=creative
motd=Emerald SMP
PROPS

# Defaults from the jar, with one change: the first chamber found is called Bastion.
CONFIG="$SERVER/plugins/BetterTrialChambers/config.yml"
if [ ! -f "$CONFIG" ]; then
    unzip -p "$JAR" config.yml | python3 -c '
import re, sys
s = sys.stdin.read()
s = re.sub(r"(  name-pool:\n)(    - .*\n)+", r"\1    - Bastion\n", s)
sys.stdout.write(s)' > "$CONFIG"
fi
echo "Server ready in $SERVER, plugin $JAR"
