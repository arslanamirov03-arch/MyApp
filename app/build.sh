#!/usr/bin/env bash
# Manual APK build (no Gradle): aapt2 -> javac -> d8 -> zip -> zipalign -> apksigner
# Usage: SDK=/path/to/android-sdk ./build.sh
set -euo pipefail
cd "$(dirname "$0")"

SDK="${SDK:?Set SDK to the Android SDK root}"
BT="$SDK/build-tools/35.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
OUT=build
rm -rf "$OUT"
mkdir -p "$OUT/compiled" "$OUT/classes" "$OUT/apk"

# 1. Compile & link resources
"$BT/aapt2" compile --dir res -o "$OUT/compiled"
"$BT/aapt2" link \
  -I "$PLATFORM" \
  --manifest AndroidManifest.xml \
  -A assets \
  --java "$OUT/gen" \
  -o "$OUT/apk/base.apk" \
  "$OUT"/compiled/*.flat

# 2. Compile Java
find java "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac --release 11 -classpath "$PLATFORM" -nowarn \
  -d "$OUT/classes" @"$OUT/sources.txt"

# 3. Dex
"$BT/d8" --release --lib "$PLATFORM" --min-api 24 \
  --output "$OUT" $(find "$OUT/classes" -name '*.class')

# 4. Add dex into APK
(cd "$OUT" && cp apk/base.apk app-unsigned.apk && zip -qj app-unsigned.apk classes.dex)

# 5. Align + sign (debug key, auto-generated once)
KEYSTORE="${KEYSTORE:-$HOME/.gk-debug.keystore}"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -alias gk -storepass gkdebug1 -keypass gkdebug1 \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Grammatik Kompass"
fi
"$BT/zipalign" -f 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:gkdebug1 --key-pass pass:gkdebug1 \
  --out "$OUT/GrammatikKompass.apk" "$OUT/app-aligned.apk"
"$BT/apksigner" verify "$OUT/GrammatikKompass.apk"
echo "OK: $OUT/GrammatikKompass.apk"
