#!/usr/bin/env bash
set -e

# Creates/updates detector-labels.json with one empty entry per detector row
# count found (e.g. "128": ""). Fill in the "" values from the scanner console
# (e.g. "64 slice", "128 slice/80mm") - there's no way to derive these names
# from the export itself. Safe to re-run any time; only adds new codes.
#
# Usage:
#   ./init-detector-labels.sh
#       Uses the "protocol data" folder in this repo (not tracked by git -
#       put your real exported protocol folders there).
#   ./init-detector-labels.sh ~/ProtocolData

cd "$(dirname "$0")"

INPUT="${1:-protocol data}"

./gradlew run --args="'$INPUT' --init-detector-labels"
