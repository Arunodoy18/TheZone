# H8 — three-phone validation

Field checklist. Run it in the actual room if you can, **airplane mode on**, on
the three phones from the H0 probe:

| Role | Phone | Why |
|---|---|---|
| **P1 — Trapped** | Samsung Galaxy S23 | only phone with a barometer (T5/T6) |
| **P2 — Carrier** | Realme 7 | has Coded PHY; the phone that covers distance |
| **P3 — Responder** | Motorola Edge 50 Pro | best screen, mirrored to the projector |

Build + push to all three: `scripts/deploy.sh --launch`.
Follow every phone's log with: `adb -s <serial> logcat -s TheZone`.

---

## Pre-flight — every phone

- [ ] Battery optimisation **Unrestricted** (Settings → Apps → Zone Probe (H0) → Battery). `deploy.sh` doze-whitelists via adb, but OEM battery managers still need this by hand.
- [ ] Airplane mode **ON**, then Bluetooth manually back **ON**.
- [ ] Location services **ON** (Android requires it for BLE scanning).
- [ ] All four runtime permissions granted — open the app, long-press → **Debug** → **H0 Probe**, check every row reads `GRANTED`.
- [ ] Do Not Disturb **ON**. Auto-brightness **OFF**; brightness max on P3.
- [ ] Screen timeout 10 min or never.
- [ ] Charged to 100%. Then remember: to show the duty-cycle ladder, use the
      **battery override** — Citizen screen, long-press the bottom-left corner
      cycles 90 → 55 → 25 → 8 → auto; or Debug → H2 → the battery chips.

Pick each phone's mode on first launch (Citizen / Responder / Map). Change later
with a long-press → mode switcher.

---

## Functional tests (airplane mode on)

### T1 — direct link
P1 (Citizen) and P3 (Responder) in the same room, both started.
- **Watch:** P3's list.
- **Pass:** P3 shows P1's row within one interval; `1 hop`; silence pill `LIVE`.
  On P3 log: `NEW … dev=<P1 id>` — no, per-packet logging is off; instead the
  row simply appears. P1's Citizen screen shows `1 device carried your signal`.

### T2 — store-carry-forward · **the demo**
1. P1 and P3 far enough apart that P3's list stays **empty**. Confirm the empty
   screen first — the payoff needs the audience to see it.
2. Carry P2 to P1, wait ~10 s for pickup (P2 in any mode; Responder shows it best).
3. Carry P2 to P3.
- **Pass:** P3 now shows P1's row at **`2 hop`**. P1's Citizen count went up.

### T3 — unexpected silence
P1 broadcasting at healthy battery (auto, or override ≥ 55%). Power P1 **off**.
- **Watch:** P3 log — `SILENCE <P1 id> …->UNEXPECTED_SILENCE @<ms> since=…`.
- **Pass:** within ~3–4× P1's declared interval P3's row flips to `SILENT`
  (red pill) and the log line carries the wall-clock time it went dark.

### T4 — expected silence · **don't skip**
On P1 set the battery override to **8%** (Citizen corner long-press, or Debug
chips). Confirm P1's heartbeat now declares a 300 s interval. Power P1 **off**.
- **Watch:** P3 log — `…->EXPECTED_SILENCE`.
- **Pass:** P3 classifies P1 `EXPECTED` (grey pill), does **not** escalate it.
  T3 + T4 together are the USP — the claim is that the system tells them apart.

### T5 — vertical triage (P1 = S23, the barometer phone)
Open the app on P1 → long-press → Debug → H2 → **Reset baseline** at ground
level. Carry P1 up one floor.
- **Watch:** P3's row for P1 → `alt +N m`.
- **Pass:** reported delta within ~1 m of the real floor height.

### T6 — drowning escalation
On P1 tap **Water rising** (Citizen). Walk P1 upstairs.
- **Pass:** P1's `alt_trend` goes positive; P3 re-sorts P1 toward the **top** of
  the list (triage reason: "rising water, climbing").

### T7 — Dig Here
P1 hidden. On P3 tap P1's row → Dig Here. Walk toward / away.
- **Pass:** the bar rises/falls monotonically enough to guide; `LINK OK` when
  close, `MARGINAL` / `LINK LOST` as you back off.

### T8 — cell loss (simulator, on P3 for the projector)
On P3: long-press → Debug → H2 → mode **Simulated** → **Start** → Close debug →
switch to **Map**. Let the ~90 s scenario run.
- **Pass:** header reaches `toll ≈ 500 · 100%`; **one cell** flips to `1
  collapsed` and renders as a hatched, bone-framed hole labelled `12 dark
  HH:mm`. **Check it from three metres back** — it must not read like an empty
  cell. Log: `CELL_LOSS cell=… 12/12 silent`.

### T9 — sustained run
All three running 30 min untouched, on BLE.
- **Pass:** no crash, the FGS notification ("You are being heard") stays up, no
  runaway battery drain.

---

## Coded-PHY confirmation (carry-over from H2)

With P2 and P3 both on BLE, on P3: Debug → H2 → Status → **`rx Coded / 1M`**.
- **Pass:** `rx Coded` climbs above 0 within a minute or two (the advertiser
  alternates 1M ↔ Coded every 1.5 s). If it stays 0 on a given pair, that
  chipset can't receive Coded — fall back to 1M-only and note it (BUILD_PLAN's
  documented cut). Toggle `PHY: alternate/concurrent` and `company filter` to
  probe.

---

## Range survey — do this in the real room

Use P3 → Dig Here on P1, read `≈ N m` and the `LINK OK/MARGINAL/LOST` line while
you walk P1 away.

| Condition | 1M PHY | Coded PHY |
|---|---|---|
| Open line of sight | ___ m | ___ m |
| Through one wall | ___ m | ___ m |
| Phone in a bag | ___ m | ___ m |
| Through two walls | ___ m | ___ m |

**Set the demo walk to ~60% of the measured maximum** — 40 bodies in the room
absorb 2.4 GHz and the empty-hall range is not the range you'll get on stage.

---

## Demo failure drills (rehearse)

| Failure | Response |
|---|---|
| BLE won't link on stage | Any phone: long-press → Debug → H2 → **Simulated** → Start → close. The FGS keeps it running; every non-walk part of the demo still works. |
| A phone dies | Roles are interchangeable. The one to drop is P2 — the demo becomes T1 + the simulator. |
| Advertiser killed by the OS | Force-stop + relaunch ≈ 8 s. Keep talking; have the next sentence ready. |
| Projector won't mirror | Run the simulator on P3, hold it up. Keep a 60 s screen recording as insurance. |
