#!/usr/bin/env bash
set -e

# Creates/updates kernel-labels.json with one empty entry per recon kernel
# number found (e.g. "8": ""). Fill in the "" values from the scanner console
# (e.g. "STD", "DTL", "BN", "BN+") - there's no way to derive these names from
# the export itself. Safe to re-run any time; only adds new codes.
#
# Usage:
#   ./init-kernel-labels.sh
#       Uses the "protocol data" folder in this repo (not tracked by git -
#       put your real exported protocol folders there).
#   ./init-kernel-labels.sh ~/ProtocolData

cd "$(dirname "$0")"

INPUT="${1:-protocol data}"

./gradlew run --args="'$INPUT' --init-kernel-labels"
