#!/usr/bin/env bash
# Builds a signed release APK without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
set -euo pipefail

cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then
  echo "ANDROID_HOME or ANDROID_SDK_ROOT must point to an Android SDK" >&2
  exit 1
fi
BT="$SDK/build-tools/${BUILD_TOOLS_VERSION:-35.0.0}"
PLATFORM="$SDK/platforms/android-34/android.jar"

VERSION_CODE="${VERSION_CODE:-13}"
VERSION_NAME="${VERSION_NAME:-2.2}"

rm -rf build
mkdir -p build/gen build/obj

"$BT/aapt2" compile --dir res -o build/res.zip

"$BT/aapt2" link \
  -o build/unsigned.apk \
  -I "$PLATFORM" \
  --manifest AndroidManifest.xml \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  -A assets \
  --java build/gen \
  build/res.zip

javac -classpath "$PLATFORM" -source 8 -target 8 -nowarn \
  -d build/obj \
  $(find src build/gen -name '*.java')

"$BT/d8" --release --lib "$PLATFORM" --min-api 26 \
  --output build $(find build/obj -name '*.class')

(cd build && zip -q -u unsigned.apk classes.dex)

"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk

"$BT/apksigner" sign \
  --ks release.keystore \
  --ks-pass pass:perfectaudio \
  --ks-key-alias perfectaudio \
  --key-pass pass:perfectaudio \
  --out build/PerfectAudio.apk \
  build/aligned.apk

echo "OK: $(pwd)/build/PerfectAudio.apk"
