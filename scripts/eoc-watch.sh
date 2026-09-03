#!/usr/bin/env bash
# Live EOC bridge, no network: pull the auto-exported snapshot off a phone on a
# loop into pwa/eoc.json, and serve pwa/ so viewer.html can auto-refresh from it.
#
#   scripts/eoc-watch.sh [serial]
#
# On the phone: run Zone on the mesh (any mode). Debug -> H2 -> "Live EOC" must
# say auto-export: on. Then open  http://localhost:8000/viewer.html?live=eoc.json
set -euo pipefail
cd "$(dirname "$0")/.."

PKG="com.thezone.debug"
REMOTE="/sdcard/Android/data/$PKG/files/thezone-eoc.json"
OUT="pwa/eoc.json"
SERIAL="${1:-}"
ADB=(adb)
[ -n "$SERIAL" ] && ADB=(adb -s "$SERIAL")

command -v adb >/dev/null || { echo "adb not found" >&2; exit 1; }
"${ADB[@]}" get-state >/dev/null 2>&1 || { echo "no device (pass a serial?)" >&2; exit 1; }

# serve pwa/ in the background if nothing is on :8000 yet
if ! curl -s -o /dev/null "http://localhost:8000/viewer.html" 2>/dev/null; then
  ( cd pwa && python3 -m http.server 8000 >/dev/null 2>&1 ) &
  echo "serving pwa/ at http://localhost:8000"
  sleep 1
fi

echo "open:  http://localhost:8000/viewer.html?live=eoc.json"
echo "pulling $REMOTE -> $OUT every 3s (Ctrl-C to stop)"
while true; do
  "${ADB[@]}" pull -a "$REMOTE" "$OUT" >/dev/null 2>&1 \
    && printf '\r%s  %s bytes   ' "$(date +%H:%M:%S)" "$(wc -c < "$OUT" | tr -d ' ')" \
    || printf '\r%s  (no snapshot yet — is auto-export on?)   ' "$(date +%H:%M:%S)"
  sleep 3
done
