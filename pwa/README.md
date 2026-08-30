# Zone — web front door + EOC viewer

**Live:** <https://neon-monstera-b28c12.netlify.app/>

Deployed as one Netlify site (publish dir = `pwa/`, no build step — `netlify.toml`
at the repo root pins it). One link covers everything:

| Path | What |
|---|---|
| `/` | landing page — what it is, the pain points, the USP, limitations, and the two buttons |
| `/zone.apk` | the Android app. Tap to install (allow "install from this source" once). Refresh it with `scripts/release.sh` then commit. |
| `/viewer.html` | the EOC severity-map + triage viewer. Read-only — the web can be a Bluetooth central only, so it can't join the mesh. Opens with a demo snapshot; drop a `thezone-eoc.json` export to load field data. |
| `/eoc.html` | zero-dependency build of the viewer (works from `file://`) |

## Getting a snapshot into the viewer

In the app: long-press → Debug → H2 → **Export EOC** → writes
`thezone-eoc.json` to the app's external files dir (path logged under tag
`TheZone`). Pull it:

```
adb pull /sdcard/Android/data/com.thezone.probe.debug/files/thezone-eoc.json
```

Then drop it on `/viewer.html`. `sample-eoc.json` here lets you try it without a device.

## Deploy

Netlify → import `Arunodoy18/TheZone` → build command empty, publish dir `pwa`
(auto-filled from `netlify.toml`). Every push to `main` redeploys.

## Snapshot schema (v2)

```
{ v, generatedAt,
  reports:[{deviceId, cell:{lat,lon}, severity, status, battery, hops,
            altDelta, altTrend, silence, lastHeardMs}],
  cellLosses:[{cell:{lat,lon}, deviceCount, silentCount, firstSilent, lastSilent}],
  confidence:[{cell:{lat,lon}, severity, confidence, devices, pathDiversity, verified}] }
```
