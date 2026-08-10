#!/usr/bin/env bash
set -e

# Printable quick-reference of pediatric protocols (patientType contains "pediatric"),
# with any weight-in-kg found in the protocol name annotated with its pound equivalent
# (e.g. "CT CHEST <5KG" -> "CT CHEST <5KG (11 lb)"). Writes peds-weights.html.
#
# Usage:
#   ./pediatric-weight-sheet.sh
#       Uses the "protocol data" folder in this repo (not tracked by git -
#       put your real exported protocol folders there).
#   ./pediatric-weight-sheet.sh ~/ProtocolData

cd "$(dirname "$0")"

INPUT="${1:-protocol data}"

./gradlew run --args="'$INPUT' --peds-weights peds-weights.html"

echo
echo "Done. Open peds-weights.html in a browser and print it."
