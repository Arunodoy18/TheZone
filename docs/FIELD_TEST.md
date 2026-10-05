# Field test — signed release, three phones

Why this exists: the mesh was validated on three phones on 2026-08-30 (see
`H8_VALIDATION.md`: T1–T7 passed on a **debug** build). Since then we shipped a
**signed release** and added the siren, Bluetooth-name beacon, SMS status, CAP
import/export (v0.4.1), reboot + Bluetooth-toggle recovery (v0.4.2), and
per-responder ECDSA signatures (v0.4.3). Nothing below has been run across
multiple phones on any signed release build. T9 (soak) and the range survey
were never done.

Phones: **P1** Galaxy S23 (Trapped, barometer) · **P2** Realme 7 (Carrier) ·
**P3** Motorola Edge 50 Pro (Responder).
Bring: a USB cable, a second person, a stopwatch, a tape measure (or count paces).

Record every result in the table at the bottom. A failure is a result. Write it
down and move on.

---

## 0. Install the release build (all three)

1. On each phone open `https://zone-thezone.vercel.app/zone.apk`, install, allow
   "install from this source" once. Remove any debug build first (`Zone Probe`).
   Check Settings → Apps → Zone → "App info" shows **version 0.4.3** on all three
   — if it still says 0.4.1/0.4.2, the install didn't take (stale APK cache is
   the usual cause: uninstall first, then reinstall).
2. Open the app, pick the mode (P1 Citizen, P2 Responder, P3 Responder), grant
   every permission it asks for.
3. Bluetooth on, Location on. Airplane mode on, then Bluetooth back on.
4. Battery: Settings → Apps → Zone → Battery → **Unrestricted** (ColorOS, One UI
   and MIUI all kill the radio otherwise). Note the exact menu path per phone.

## 1. Regression on the release build (15 min)

Repeat these from `H8_VALIDATION.md` unchanged. They must still pass:

| # | Test | Pass looks like |
|---|---|---|
| T1 | Direct link P1 → P3 | P3 shows P1, `1 hop`, LIVE |
| T2 | Carry P2 between P1 and P3 | P3 shows P1 at `2 hop` |
| T3 | Power P1 off at healthy battery | P3 flips to SILENT (red), log has the wall-clock time |
| T4 | P1 at 8% override, power off | P3 shows EXPECTED (grey), no escalation |

## 2. The alert chain across phones

| # | Test | Pass looks like |
|---|---|---|
| A1 | P3 (responder) issues an ALERT | P1 and P2 both show the alert screen, siren sounds, torch strobes |
| A2 | P1 taps acknowledge | Siren and torch stop on P1 only |
| A3 | Phone Bluetooth name during an alert | Nearby phones list a device named `ZONE ALERT: <phrase>`; the name is restored after |
| A4 | ALERT relayed: P1 far from P3, P2 carried between | P1 receives the alert at `2 hop` |
| A5 | Forged alert with the shared key alone | Still succeeds — expected, the shared key is still the sole gate until phase 2, see docs/RESPONDER_PROVISIONING.md |

## 3. Reliability — fixed in v0.4.2, needs confirming on real hardware

| # | Test | Pass looks like |
|---|---|---|
| R1 | Lock the screen on P1 for 10 min | Still shows LIVE on P3 afterwards |
| R2 | Swipe the app away from recents on P1 | Foreground notification stays; P3 still hears P1 |
| R3 | Reboot P1 (while its Zone notification was up before the reboot) | The notification comes back on its own after boot, with no one opening the app — look for "You are being heard" within ~a minute of the phone finishing booting. P3 should show P1 LIVE again shortly after. |
| R4 | Battery saver on, on P1 | Still heard on P3 |
| R5 | Toggle Bluetooth off then on (Quick Settings, not Airplane mode) on P1 | The radio comes back on its own — P3 should see P1 again within the next duty-cycle interval, no app restart needed |

## 4. Responder signing key — new in v0.4.3 (not load-bearing yet, see docs/RESPONDER_PROVISIONING.md)

| # | Test | Pass looks like |
|---|---|---|
| S1 | On P3: Debug → "Responder signing key" → Generate signing key | Shows a `device_id` and a `public key` (starts with `04`), and a "Copy roster entry" button |
| S2 | P3 issues a RESOLVE or ALERT right after S1 | No crash, no change in behaviour visible to the user — signing happens silently alongside the existing shared-key flow |
| S3 | On P1: Debug → "Responder signing key" → watch "verified signatures" after S2 | Expected **0 confirmed** right now — P1 doesn't have P3 in its TrustRoster yet (it's still empty by default). This is the expected, not-yet-provisioned state; it is not a failure. |

## 5. Soak, T9 (30 min, untouched)

All three on BLE, screens off after starting. At the end record: crashes (y/n),
notification still up (y/n), battery % lost per phone.
Pass: no crash, notification up, under 10% drain per 30 min on each phone.

## 6. Range survey

P3 → Dig Here on P1. Walk P1 away and read `≈ N m` until LINK LOST. Three runs
per cell, keep the median.

| Condition | 1M PHY | Coded PHY |
|---|---|---|
| Open line of sight | ___ m | ___ m |
| Through one wall | ___ m | ___ m |
| Phone in a bag | ___ m | ___ m |
| Through two walls | ___ m | ___ m |

---

## Results log

| Test | Phone(s) | Pass / Fail | What you saw |
|---|---|---|---|
| T1 | | | |
| T2 | | | |
| T3 | | | |
| T4 | | | |
| A1 | | | |
| A2 | | | |
| A3 | | | |
| A4 | | | |
| A5 | | | |
| R1 | | | |
| R2 | | | |
| R3 | | | |
| R4 | | | |
| R5 | | | |
| S1 | | | |
| S2 | | | |
| S3 | | | |
| T9 soak | | | |

Send me the filled table (or photos of it) and I will fix whatever failed.
Expected trouble spots, in order: R3 (reboot — fixed in code, never run on real
hardware), R5 (Bluetooth toggle — same), and the per-brand battery settings.
