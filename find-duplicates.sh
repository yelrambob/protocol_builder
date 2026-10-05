#!/usr/bin/env bash
set -e

# Lists protocols that are effectively duplicates - identical settings filed under two
# numbers, or the same name with different settings (and what differs) - plus the
# protocol-overrides.json lines that would hide the extra copies from the book.
# Writes duplicates.html. Nothing on the scanner is changed.
#
# Usage:
#   ./find-duplicates.sh
#       Uses the "protocol data" folder in this repo (not tracked by git -
#       put your real exported protocol folders there).
#   ./find-duplicates.sh ~/ProtocolData

cd "$(dirname "$0")"

INPUT="${1:-protocol data}"

./gradlew run --args="'$INPUT' --duplicates duplicates.html"

echo
echo "Done. Open duplicates.html in a browser."
