# Zone — EOC viewer (PWA)

A read-only window onto a snapshot a responder carried out. The web can act as a
Bluetooth *central* only — no advertising, no relay, no background — so this is
not a field role; Citizen and Responder run the native app.

Two builds of the same viewer:

| File | Use |
|---|---|
| `eoc.html` | zero-dependency, works from `file://`. Double-click, drop the JSON. |
| `eoc-hosted.html` | design pass (IBM Plex, sonar console layout). Needs a network font; deployed as a Claude Artifact: **https://claude.ai/code/artifact/3ad51630-bccd-4cb1-8211-c18b7e9df289** |

Both render the same thing from the same JSON.

## Getting a snapshot

On any phone running the app: long-press → Debug → H2 → **Export EOC**.
It writes `thezone-eoc.json` to the app's external files dir; the path is logged
(`adb logcat -s TheZone` → "EOC export -> …"). Pull it:

```
adb pull /sdcard/Android/data/com.thezone.probe.debug/files/thezone-eoc.json
```

Then drop it on the viewer. `sample-eoc.json` here lets you try it without a device
(both viewers also open showing a built-in demo snapshot).

## Deploying your own copy

- **GitHub Pages** — push the repo, Settings → Pages → deploy from `main`,
  branch root; the viewer is then at
  `https://arunodoy18.github.io/TheZone/pwa/eoc.html`.
- **Netlify Drop** — drag the `pwa/` folder onto https://app.netlify.com/drop.

## Schema (v2)

```
{ v, generatedAt,
  reports:[{deviceId, cell:{lat,lon}, severity, status, battery, hops,
            altDelta, altTrend, silence, lastHeardMs}],
  cellLosses:[{cell:{lat,lon}, deviceCount, silentCount, firstSilent, lastSilent}],
  confidence:[{cell:{lat,lon}, severity, confidence, devices, pathDiversity, verified}] }
```

`status`: 0 unknown · 1 safe · 2 trapped · 3 rising water · 4 injured · 5 has supplies · 6 responder
`silence`: ALIVE · OVERDUE · EXPECTED_SILENCE · UNEXPECTED_SILENCE · `altDelta` −128 = no barometer
