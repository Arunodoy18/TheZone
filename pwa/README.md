# Zone — web front door + EOC viewer + command dashboard

**Live:** <https://zone-thezone.vercel.app/>

Deployed as one Vercel site (output dir = `pwa/`, no build step — `vercel.json`
at the repo root pins it, connected to auto-deploy from GitHub on every push to
`main`). One link covers everything:

| Path | What |
|---|---|
| `/` | landing page — what it is, the pain points, the USP, limitations, and the two buttons |
| `/zone.apk` | the Android app, signed release build (`com.thezone`). Tap to install (allow "install from this source" once). Refresh it with `scripts/release.sh` then commit. |
| `/viewer.html` | the EOC severity-map + triage viewer. Read-only — the web can be a Bluetooth central only, so it can't join the mesh. Opens with a demo snapshot; drop a `thezone-eoc.json` export to load field data. |
| `/dashboard.html` | **online-only** command dashboard — real 3D map, satellite imagery, Zone's own mesh layer, plus open hazard feeds (USGS earthquakes, NASA EONET). Not the offline app; a separate companion for a command centre with a connection. |
| `/eoc.html` | zero-dependency build of the viewer (works from `file://`) |

## Getting a snapshot into the viewer

In the app: long-press → Debug → H2 → **Export EOC** → writes
`thezone-eoc.json` to the app's external files dir (path logged under tag
`TheZone`). Pull it (debug build; drop `.debug` for the signed release build):

```
adb pull /sdcard/Android/data/com.thezone.debug/files/thezone-eoc.json
```

Then drop it on `/viewer.html` or `/dashboard.html`. `sample-eoc.json` here
lets you try it without a device.

## Deploy

Vercel → import `Arunodoy18/TheZone` → auto-detects `vercel.json` (output
directory `pwa`, no build command). Every push to `main` redeploys.

Previously hosted on Netlify (`netlify.toml` is still in the repo but its
auto-deploy became unreliable — stale builds, pages never picking up new
pushes — and wasn't fixable from outside Netlify's own dashboard).

## Snapshot schema (v2)

```
{ v, generatedAt,
  reports:[{deviceId, cell:{lat,lon}, severity, status, battery, hops,
            altDelta, altTrend, silence, lastHeardMs}],
  cellLosses:[{cell:{lat,lon}, deviceCount, silentCount, firstSilent, lastSilent}],
  confidence:[{cell:{lat,lon}, severity, confidence, devices, pathDiversity, verified}] }
```
