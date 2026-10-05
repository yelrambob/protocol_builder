#!/usr/bin/env bash
# Opens the Protocol Builder window (see run-gui.bat).
set -e
cd "$(dirname "$0")"
exec ./gradlew -q gui
