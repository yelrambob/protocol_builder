#!/usr/bin/env bash
set -e

# Creates/updates protocol-overrides.json with one empty entry per protocol
# found, ready for you to fill in scanning notes or set "excluded": true.
# Safe to re-run any time (e.g. after new protocols show up on the scanner) -
# it only adds new protocol numbers and never touches existing notes.
#
# Usage:
#   ./init-protocol-overrides.sh
#       Uses the "protocol data" folder in this repo (not tracked by git -
#       put your real exported protocol folders there).
#   ./init-protocol-overrides.sh ~/ProtocolData

cd "$(dirname "$0")"

INPUT="${1:-protocol data}"

./gradlew run --args="'$INPUT' --init-overrides"
