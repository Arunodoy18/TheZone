# Zone — web front door

**Live:** <https://zone-thezone.vercel.app/>

Deployed as one Vercel site (output dir = `pwa/`, no build step — `vercel.json`
at the repo root pins it, connected to auto-deploy from GitHub on every push to
`main`).

| Path | What |
|---|---|
| `/` | landing page — what it is, the pain points, the USP, limitations, and the one button |
| `/zone.apk` | the Android app, signed release build (`com.thezone`). Tap to install (allow "install from this source" once). Refresh it with `scripts/release.sh` then commit. |
| `/install.html` | install card (QR) for a demo floor |

## Deploy

Vercel → import `Arunodoy18/TheZone` → auto-detects `vercel.json` (output
directory `pwa`, no build command). Every push to `main` redeploys.

Previously hosted on Netlify (`netlify.toml` is still in the repo but its
auto-deploy became unreliable — stale builds, pages never picking up new
pushes — and wasn't fixable from outside Netlify's own dashboard).
