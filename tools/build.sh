#!/usr/bin/env bash
# Build and sign Unlock FPS without Gradle (works on a GitHub Actions Ubuntu runner).
# Needs: JDK 17+, Android SDK with platform android-35 and build-tools 35.0.0.
#
# Signing (release key, same keystore you use for your other app):
#   KEYSTORE_FILE   path to the .jks / .keystore file
#   KEYSTORE_PASS   keystore password
#   KEY_ALIAS       key alias
#   KEY_PASS        key password (defaults to KEYSTORE_PASS)
# Without a keystore the build FAILS, unless ALLOW_DEBUG_KEY=1 (test builds only: those APKs
# cannot be updated by a release-signed build).
set -euo pipefail
cd "$(dirname "$0")/.."

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
BT="$SDK/build-tools/${BUILD_TOOLS:-35.0.0}"
JAR="$SDK/platforms/android-${API:-35}/android.jar"
OUT=build

# Keep the bundled game list in sync with data/games.json
mkdir -p res/raw
python3 -c "import json;d=json.load(open('data/games.json',encoding='utf-8'));open('res/raw/games.json','w',encoding='utf-8').write(json.dumps(d,ensure_ascii=False,separators=(',',':')))"

rm -rf "$OUT" && mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex"
"$BT/aapt2" compile --dir res -o "$OUT/res/res.zip"
"$BT/aapt2" link -o "$OUT/unsigned.apk" -I "$JAR" --manifest AndroidManifest.xml \
  --java "$OUT/gen" "$OUT/res/res.zip" --auto-add-overlay
# Any compile error stops the build (set -e); nothing is hidden.
javac -nowarn -source 11 -target 11 -classpath "$JAR" -d "$OUT/classes" \
  $(find src "$OUT/gen" -name '*.java')
"$BT/d8" --min-api 26 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
cp "$OUT/unsigned.apk" "$OUT/unaligned.apk"
(cd "$OUT/dex" && jar uf ../unaligned.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

if [[ -n "${KEYSTORE_FILE:-}" && -f "${KEYSTORE_FILE:-}" ]]; then
  "$BT/apksigner" sign --ks "$KEYSTORE_FILE" --ks-pass "pass:$KEYSTORE_PASS" \
    --ks-key-alias "$KEY_ALIAS" --key-pass "pass:${KEY_PASS:-$KEYSTORE_PASS}" \
    --out "$OUT/UnlockFPS.apk" "$OUT/aligned.apk"
  echo "signed with your release key"
else
  if [[ "${ALLOW_DEBUG_KEY:-}" != "1" ]]; then
    echo "ERROR: no release keystore found. Set KEYSTORE_FILE/KEYSTORE_PASS/KEY_ALIAS," >&2
    echo "or set ALLOW_DEBUG_KEY=1 for a throw-away test build." >&2
    exit 1
  fi
  keytool -genkeypair -keystore "$OUT/debug.jks" -storepass android -keypass android \
    -alias debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=debug" >/dev/null 2>&1
  "$BT/apksigner" sign --ks "$OUT/debug.jks" --ks-pass pass:android --key-pass pass:android \
    --out "$OUT/UnlockFPS.apk" "$OUT/aligned.apk"
  echo "signed with a temporary debug key"
fi
echo "built $OUT/UnlockFPS.apk"
