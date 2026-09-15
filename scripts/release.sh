#!/usr/bin/env bash
# Build the APK and stage it for the Netlify site so `git push` publishes it.
# Uses the signed release build when keystore.properties exists (real
# distribution identity, com.thezone); falls back to an unsigned debug build
# otherwise (com.thezone.debug) — e.g. a fresh clone without the keystore.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"

if [ -f keystore.properties ]; then
  echo "== building signed release APK =="
  ./gradlew -q :app:testDebugUnitTest :app:assembleRelease
  SRC="app/build/outputs/apk/release/app-release.apk"
else
  echo "== no keystore.properties — building unsigned debug APK =="
  ./gradlew -q :app:testDebugUnitTest :app:assembleDebug
  SRC="app/build/outputs/apk/debug/app-debug.apk"
fi

DST="pwa/zone.apk"
cp "$SRC" "$DST"
SIZE=$(du -h "$DST" | cut -f1)
VER=$(git rev-parse --short HEAD)
echo "staged $DST  ($SIZE, built at $VER)"
echo "commit + push, then Netlify serves it at  <site>/zone.apk"
