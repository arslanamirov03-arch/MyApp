#!/usr/bin/env bash
# Сборка APK напрямую через aapt2/javac/d8/apksigner — без Gradle и без
# каких-либо внешних зависимостей, поэтому сборка работает офлайн.
#
#   ANDROID_SDK_ROOT=/path/to/sdk ./build.sh
#
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP="$HERE/app"
OUT="$HERE/build"

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[ -n "$SDK" ] || { echo "Задай ANDROID_SDK_ROOT"; exit 1; }

BT="${BUILD_TOOLS:-34.0.0}"
PLATFORM="${PLATFORM:-android-34}"
TOOLS="$SDK/build-tools/$BT"
JAR="$SDK/platforms/$PLATFORM/android.jar"

for f in "$TOOLS/aapt2" "$TOOLS/d8" "$TOOLS/zipalign" "$TOOLS/apksigner" "$JAR"; do
  [ -e "$f" ] || { echo "Не найдено: $f"; exit 1; }
done

rm -rf "$OUT"
mkdir -p "$OUT/flat" "$OUT/gen" "$OUT/classes"

echo "==> aapt2 compile"
"$TOOLS/aapt2" compile --dir "$APP/res" -o "$OUT/flat/res.zip"

echo "==> aapt2 link"
"$TOOLS/aapt2" link \
  -I "$JAR" \
  --manifest "$APP/AndroidManifest.xml" \
  -A "$APP/assets" \
  --java "$OUT/gen" \
  --min-sdk-version 21 \
  --target-sdk-version 34 \
  --version-code 1 \
  --version-name 1.0 \
  -o "$OUT/base.apk" \
  "$OUT/flat/res.zip"

echo "==> javac"
find "$APP/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -nowarn -encoding UTF-8 \
  -bootclasspath "$JAR" -classpath "$JAR" \
  -d "$OUT/classes" @"$OUT/sources.txt" 2>&1 | grep -v 'bootstrap class path' || true

echo "==> d8"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$TOOLS/d8" --lib "$JAR" --min-api 21 --output "$OUT" @"$OUT/classes.txt"

echo "==> упаковка dex"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
( cd "$OUT" && zip -q -X "unsigned.apk" classes.dex )

echo "==> zipalign"
"$TOOLS/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KS="$HERE/release.keystore"
if [ ! -f "$KS" ]; then
  echo "==> создаю ключ подписи"
  keytool -genkeypair -v -keystore "$KS" -storepass wortliste -keypass wortliste \
    -alias wortliste -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=Wortliste B2, OU=App, O=Personal, L=-, S=-, C=DE" >/dev/null 2>&1
fi

echo "==> apksigner"
"$TOOLS/apksigner" sign \
  --ks "$KS" --ks-pass pass:wortliste --key-pass pass:wortliste \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$OUT/WortlisteB2.apk" "$OUT/aligned.apk"

"$TOOLS/apksigner" verify --print-certs "$OUT/WortlisteB2.apk" | head -4

echo
echo "Готово: $OUT/WortlisteB2.apk ($(du -h "$OUT/WortlisteB2.apk" | cut -f1))"
