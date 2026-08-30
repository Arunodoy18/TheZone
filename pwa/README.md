# Zone — EOC viewer (PWA)

`eoc.html` is a single, dependency-free, offline page. It renders the same
severity map + triage list + CELL_LOSS holes the native Map screen shows, from a
JSON snapshot exported by a responder's phone. The web can't hold a BLE radio,
so this is read-only — the EOC's window, not a field role.

## Use

1. On any phone running the app: long-press → Debug → H2 → **Export EOC**.
   It writes `thezone-eoc.json` to the app's external files dir; the path is
   logged (`adb logcat -s TheZone` → "EOC export -> …"). Pull it:
   `adb pull /sdcard/Android/data/com.thezone.probe.debug/files/thezone-eoc.json`
2. Open `eoc.html` in any browser (double-click / `file://` is fine).
3. Drop the JSON onto the page.

`sample-eoc.json` is here to try it without a device.

## Schema (v2)

```
{ v, generatedAt,
  reports:[{deviceId, cell:{lat,lon}, severity, status, battery, hops,
            altDelta, altTrend, silence, lastHeardMs}],
  cellLosses:[{cell:{lat,lon}, deviceCount, silentCount, firstSilent, lastSilent}],
  confidence:[{cell:{lat,lon}, severity, confidence, devices, pathDiversity, verified}] }
```
