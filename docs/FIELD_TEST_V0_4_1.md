# Field test — signed release v0.4.1, three phones

Why this exists: the mesh was validated on three phones on 2026-08-30 (see
`H8_VALIDATION.md`: T1–T7 passed on a **debug** build). Since then we added the
siren, Bluetooth-name beacon, SMS status, CAP import/export and shipped a
**signed release**. Nothing below has been run across multiple phones on the
release build. T9 (soak) and the range survey were never done.

Phones: **P1** Galaxy S23 (Trapped, barometer) · **P2** Realme 7 (Carrier) ·
**P3** Motorola Edge 50 Pro (Responder).
Bring: a USB cable, a second person, a stopwatch, a tape measure (or count paces).

Record every result in the table at the bottom. A failure is a result. Write it
down and move on.

---

## 0. Install the release build (all three)

1. On each phone open `https://zone-thezone.vercel.app/zone.apk`, install, allow
   "install from this source" once. Remove any debug build first (`Zone Probe`).
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

## 2. New since H8: the alert chain across phones

| # | Test | Pass looks like |
|---|---|---|
| A1 | P3 (responder) issues an ALERT | P1 and P2 both show the alert screen, siren sounds, torch strobes |
| A2 | P1 taps acknowledge | Siren and torch stop on P1 only |
| A3 | Phone Bluetooth name during an alert | Nearby phones list a device named `ZONE ALERT: <phrase>`; the name is restored after |
| A4 | ALERT relayed: P1 far from P3, P2 carried between | P1 receives the alert at `2 hop` |
| A5 | Forged alert | Skip. Known weakness (shared key). It is the reason the signature work is next |

## 3. Reliability (the part a customer will hit first)

| # | Test | Pass looks like |
|---|---|---|
| R1 | Lock the screen on P1 for 10 min | Still shows LIVE on P3 afterwards |
| R2 | Swipe the app away from recents on P1 | Foreground notification stays; P3 still hears P1 |
| R3 | Reboot P1 | Note what happens. Does it resume? (We expect it does not. Record it.) |
| R4 | Battery saver on, on P1 | Still heard on P3 |
| R5 | Bluetooth toggled off and on on P1 | Does the app recover by itself, or need a restart? |

## 4. Soak, T9 (30 min, untouched)

All three on BLE, screens off after starting. At the end record: crashes (y/n),
notification still up (y/n), battery % lost per phone.
Pass: no crash, notification up, under 10% drain per 30 min on each phone.

## 5. Range survey

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
| R1 | | | |
| R2 | | | |
| R3 | | | |
| R4 | | | |
| R5 | | | |
| T9 soak | | | |

Send me the filled table (or photos of it) and I will fix whatever failed.
Expected trouble spots, in order: R3 (reboot), R5 (Bluetooth toggle), and the
per-brand battery settings.
