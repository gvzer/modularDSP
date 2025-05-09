#!/usr/bin/env bash
set -e

# 1) Find the latest Ableton Live.app in /Applications
APP_ROOT="/Applications"
LIVE_APP=$(ls -1d "${APP_ROOT}/Ableton Live "*.app 2>/dev/null \
           | sort -V \
           | tail -n1)

if [[ ! -d "$LIVE_APP" ]]; then
  echo "❌  No Ableton Live.app found in $APP_ROOT"
  exit 1
fi
echo "Using Ableton: $(basename "$LIVE_APP")"

# 2) Derive Max Java paths inside Live
MAX_BASE="$LIVE_APP/Contents/App-Resources/Max/Max.app/Contents/Resources/C74/packages/max-mxj/java-classes"
DEST="$MAX_BASE/classes"
CP="$MAX_BASE/lib/max.jar"

mkdir -p "$DEST"

# 3) Compile ALL .java in this script’s dir (including your five files)
SRC_DIR="$(cd "$(dirname "$0")" && pwd)"
echo "Compiling → $DEST"
for src in "$SRC_DIR"/*.java; do
  echo "  ↳ $(basename "$src")"
  javac -d "$DEST" -cp "$CP" "$src"
done

echo
echo "✅  Done! Classes installed to:"
echo "   $DEST"
