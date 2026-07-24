#!/usr/bin/env bash
set -e

# Creates/updates plane-labels.json with one empty entry per scout plane angle
# found (e.g. "0": ""). Leave a value blank to keep the built-in default
# (0=AP, 90=Lateral, 180=PA, 270=Lateral); fill one in only to override it for
# your site. Safe to re-run any time; only adds new codes.
#
# Usage:
#   ./init-plane-labels.sh
#       Uses the "protocol data" folder in this repo (not tracked by git -
#       put your real exported protocol folders there).
#   ./init-plane-labels.sh ~/ProtocolData

cd "$(dirname "$0")"

INPUT="${1:-protocol data}"

./gradlew run --args="'$INPUT' --init-plane-labels"
