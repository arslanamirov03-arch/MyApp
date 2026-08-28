#!/usr/bin/env bash
# Fetch assets, generate the icon and sounds, import, test and export the APK.
#
#   GODOT=/path/to/godot ./tools/build.sh
#
# Requires the Android SDK (build-tools, for apksigner) and a release keystore.
# The keystore is NOT in the repository or in export_presets.cfg — point Godot
# at yours with:
#
#   export GODOT_ANDROID_KEYSTORE_RELEASE_PATH=/path/to/release.keystore
#   export GODOT_ANDROID_KEYSTORE_RELEASE_USER=your-alias
#   export GODOT_ANDROID_KEYSTORE_RELEASE_PASSWORD=your-password
#
# No NDK and no Gradle build: the APK is repacked from the stock export
# template.
set -euo pipefail

GODOT="${GODOT:-godot}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${1:-$HERE/../build/SpiderHouse.apk}"

cd "$HERE"

echo "==> assets"
python3 tools/fetch_assets.py
python3 tools/gen_audio.py
python3 tools/gen_icon.py

echo "==> import"
"$GODOT" --headless --editor --quit --path . >/dev/null 2>&1 || true

echo "==> physics probe"
"$GODOT" --headless --path . tools/probe.tscn

echo "==> export"
mkdir -p "$(dirname "$OUT")"
"$GODOT" --headless --path . --export-release "Android" "$OUT"

ls -lh "$OUT"
