#!/usr/bin/env bash
# Build the debug APK and push it to every connected phone, with the preflight
# steps from BUILD_PLAN / PHONE_TEST applied where adb is allowed to.
#
#   scripts/deploy.sh            build + install + preflight on all devices
#   scripts/deploy.sh --launch   also cold-launch the app on each
set -euo pipefail

cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
PKG="com.thezone.probe.debug"
APK="app/build/outputs/apk/debug/app-debug.apk"
LAUNCH="${1:-}"

echo "== building =="
./gradlew -q :app:assembleDebug

DEVICES=$(adb devices | awk 'NR>1 && $2=="device"{print $1}')
if [ -z "$DEVICES" ]; then
  echo "no authorised devices connected"; exit 1
fi

for D in $DEVICES; do
  MODEL=$(adb -s "$D" shell getprop ro.product.model | tr -d '\r')
  echo
  echo "== $D  ($MODEL) =="

  adb -s "$D" install -r -d "$APK" >/dev/null && echo "  installed"

  # runtime permissions (silently skipped on OEMs that block adb grant, e.g. ColorOS)
  for P in BLUETOOTH_ADVERTISE BLUETOOTH_SCAN BLUETOOTH_CONNECT ACCESS_FINE_LOCATION POST_NOTIFICATIONS; do
    adb -s "$D" shell pm grant "$PKG" "android.permission.$P" 2>/dev/null || true
  done

  # exempt from Doze / background limits so the advertiser is not killed mid-demo
  adb -s "$D" shell dumpsys deviceidle whitelist "+$PKG" >/dev/null 2>&1 || true
  adb -s "$D" shell cmd appops set "$PKG" RUN_ANY_IN_BACKGROUND allow 2>/dev/null || true

  BT=$(adb -s "$D" shell settings get global bluetooth_on | tr -d '\r')
  AIR=$(adb -s "$D" shell settings get global airplane_mode_on | tr -d '\r')
  LOC=$(adb -s "$D" shell settings get secure location_mode | tr -d '\r')
  echo "  bluetooth_on=$BT  airplane_mode_on=$AIR  location_mode=$LOC"
  [ "$BT" = "1" ]  || echo "  ! enable Bluetooth"
  [ "$LOC" != "0" ] || echo "  ! turn Location services ON (Android needs it for BLE scan)"

  GRANTED=$(adb -s "$D" shell dumpsys package "$PKG" \
    | grep -E "BLUETOOTH_(ADVERTISE|SCAN|CONNECT)|ACCESS_FINE_LOCATION" \
    | grep -c "granted=true" || true)
  echo "  runtime permissions granted: $GRANTED (grant the rest by hand in Settings if low)"

  if [ "$LAUNCH" = "--launch" ]; then
    adb -s "$D" shell am start -n "$PKG/com.thezone.probe.MainActivity" >/dev/null
    echo "  launched"
  fi
done

echo
echo "manual, per phone (see docs/H8_VALIDATION.md):"
echo "  - Settings -> Apps -> Zone Probe (H0) -> Battery -> Unrestricted"
echo "  - Airplane mode ON, then Bluetooth back ON"
echo "  - Do Not Disturb ON;  auto-brightness OFF (max on the responder)"
