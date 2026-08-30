#!/usr/bin/env bash
# Build the APK and stage it for the Netlify site so `git push` publishes it.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"

echo "== building debug APK =="
./gradlew -q :app:assembleDebug

SRC="app/build/outputs/apk/debug/app-debug.apk"
DST="pwa/zone.apk"
cp "$SRC" "$DST"
SIZE=$(du -h "$DST" | cut -f1)
VER=$(git rev-parse --short HEAD)
echo "staged $DST  ($SIZE, built at $VER)"
echo "commit + push, then Netlify serves it at  <site>/zone.apk"
