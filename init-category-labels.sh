#!/usr/bin/env bash
set -e

# Creates/updates category-labels.json with one empty entry per body part found
# (e.g. "lower Extremities": ""). Leave a value blank to keep the guessed
# reading category (keyword-based: Neuro/Body/MSK/Other); fill one in only to
# override it for your site (e.g. force "spine" into "Spine" instead of "Neuro").
# Safe to re-run any time; only adds new body parts.
#
# Usage:
#   ./init-category-labels.sh
#       Uses the "protocol data" folder in this repo (not tracked by git -
#       put your real exported protocol folders there).
#   ./init-category-labels.sh ~/ProtocolData

cd "$(dirname "$0")"

INPUT="${1:-protocol data}"

./gradlew run --args="'$INPUT' --init-category-labels"
