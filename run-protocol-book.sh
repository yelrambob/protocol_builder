#!/usr/bin/env bash
set -e

# Usage:
#   ./run-protocol-book.sh
#       Uses the "protocol data" folder in this repo (not tracked by git -
#       put your real exported protocol folders there).
#   ./run-protocol-book.sh ~/ProtocolData
#   ./run-protocol-book.sh ~/ProtocolData my-overrides.json
#
# Optional second argument: an overrides file other than protocol-overrides.json.

cd "$(dirname "$0")"

INPUT="${1:-protocol data}"
OVERRIDES="${2:-protocol-overrides.json}"

./gradlew run --args="'$INPUT' --html book.html --overrides '$OVERRIDES'"

echo
echo "Done. Open book.html in a browser to see the result."
