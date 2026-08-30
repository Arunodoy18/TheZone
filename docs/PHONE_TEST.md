# PHONE_TEST

Validation on real hardware, and staging for the live demo.

---

## Phone roles

Pick **three different handsets from different manufacturers**, ideally including one mid-range. "It works on whatever people actually own" is the deployability argument; three identical Pixels invites the question of whether you only ever tested one chipset.

| Role | Requirements | Demo position |
|---|---|---|
| **P1 — Trapped** | barometer required, Coded PHY preferred | in a bag, behind the stage |
| **P2 — Carrier** | Coded PHY preferred | walked across the room by a presenter |
| **P3 — Responder** | best screen, brightest | back of the room, mirrored to projector |

Record from the H0 probe before staging:

```
P1 ____________  coded:___ extAdv:___ baro:___
P2 ____________  coded:___ extAdv:___ baro:___
P3 ____________  coded:___ extAdv:___ baro:___
```

If a phone lacks a barometer, make it P3. If one lacks extended advertising, don't make it P2 — the carrier is the phone that has to cover distance.

### H0 probe results (2026-08-30)

| Phone | manuf / model | Android | codedPhy | extAdv | multiAdv | maxAdvLen | baro | perms granted |
|---|---|---|---|---|---|---|---|---|
| Samsung Galaxy S23 | samsung / SM-S911B | 16 (API 36) | true | true | true | 1650 B | **true** (lps22hh) | ADVERTISE/SCAN/CONNECT/FINE_LOCATION all granted |
| Realme 7 | realme / RMX2151 | 11 (API 30) | true | true | true | 192 B | **false** (no barometer) | FINE_LOCATION granted; ADVERTISE/SCAN/CONNECT are install-time BLUETOOTH/BLUETOOTH_ADMIN on API 30 (auto-granted) |
| Motorola Edge 50 Pro | motorola / motorola edge 50 pro | 16 (API 36) | true | true | true | 1650 B | **false** (no barometer) | ADVERTISE/SCAN/CONNECT/FINE_LOCATION all granted |

Probe APK: `com.thezone.probe.debug` / `MainActivity` (H0 scaffold).

**All three support Coded PHY + extended advertising** → dual-mode works everywhere, no partition by handset.

### H2 transport smoke test (2026-08-30)

Single-phone, via the "H2 Transport" tab → BLE → Start. Follow with `adb logcat -s TheZone`.

| Phone | FGS starts | scan LOW_LATENCY | Coded set | 1M set | notes |
|---|---|---|---|---|---|
| Samsung Galaxy S23 | yes | yes | started (txPower +1 dBm) | started | Simulated mode also decoded 3 peers |
| Realme 7 | yes | yes | started (txPower −2 dBm) | started | API 30 — only FINE_LOCATION gates it |
| Motorola Edge 50 Pro | yes | yes | started (txPower 0 dBm) | started | API 36 |

All three advertise on both PHYs and scan without error. Device ids: S23
`d675c51afb0d`, Realme 7 `ca949dc36761`, Motorola `d7aad5677d8c`.

**Two-phone air path (A → B) CONFIRMED** (Realme scanning, S23 + Motorola
advertising): both peers received, raw hex decodes to a valid heartbeat, both
directions. That is BUILD_PLAN's H2 checkpoint — met.

**Coded PHY caveat:** with two concurrent advertising sets, every reception came
in on 1M (`scan sample: pri=1M sec=1M legacy=false`, `rxByPhy={ONE_M=n}`, zero
Coded). The Coded set starts (status 0) but the controller gives it no airtime.
Fix in commit 69ef9a4: the advertiser now defaults to ALTERNATING — one set
flipped 1M↔Coded every 1.5 s — so Coded is genuinely emitted. The H2 screen
shows an `rx Coded / 1M` split to confirm on hardware. **Re-run the two-phone
test on the alternating build to confirm `rx Coded > 0`** (H8-class check;
1M-only remains the documented acceptable cut).

**Barometer is the constraint:** only the S23 has one. So role assignment is forced:

```
P1  Trapped    Samsung Galaxy S23      coded:Y  extAdv:Y  baro:Y   (only baro phone -> must be P1)
P2  Carrier    Realme 7                coded:Y  extAdv:Y  baro:N
P3  Responder  Motorola Edge 50 Pro    coded:Y  extAdv:Y  baro:N   (best/brightest screen)
```

Trade-off to state in the deck: the S23 is behind the stage in a bag (P1), so the walk-across-the-room phone (P2, Realme 7) is a budget handset — still fine, it has Coded PHY. Vertical-triage demo (T5/T6) runs on the S23 only.

---

## Pre-flight, on every phone

- [ ] Battery optimisation **disabled** for the app (Settings → Apps → Battery → Unrestricted). Android will kill your advertiser mid-demo otherwise.
- [ ] All runtime permissions granted, verified in-app rather than assumed
- [ ] Location services **on** (Android requires it for BLE scanning even when you don't use location)
- [ ] Airplane mode **on**, Bluetooth manually re-enabled
- [ ] Do Not Disturb on — no notification banners over the demo
- [ ] Auto-brightness off, brightness at max on P3
- [ ] Screen timeout 10 minutes or never
- [ ] Charged to 100%
- [ ] Debug battery-override gesture tested (you're demoing a battery-adaptive system on full batteries)

---

## Functional tests

Run all of these with airplane mode on.

### T1 — Direct link
P1 and P2 in the same room. P2 shows P1's packet, hop count 1, within one interval.

### T2 — Store-carry-forward · **this is the demo**
1. P1 and P3 far enough apart that P3 sees nothing. **Confirm P3 is empty first** — this is the setup that makes the payoff land.
2. P2 walks to P1, waits for pickup.
3. P2 walks to P3.
4. P3 now shows P1's packet at hop count 2.

### T3 — Unexpected silence
P1 broadcasting normally at high battery. Power it off. Within 3× its declared interval, P3 classifies it `UNEXPECTED_SILENCE` and records the wall-clock time it went dark.

### T4 — Expected silence · **don't skip this one**
Override P1's battery to critical. Power it off. P3 classifies `EXPECTED_SILENCE` and deprioritises it.

T3 and T4 together are the USP. Either one alone proves nothing — the claim is that the system tells them *apart*.

### T5 — Vertical triage
Carry P1 up one floor. P3's reported altitude delta lands within about 1 m of the real height.

### T6 — Drowning escalation
Set P1 to `RISING_WATER`, walk it upstairs. P3 auto-escalates it to the top of the triage list on rising trend alone.

### T7 — Dig Here
P1 hidden. Walk P3 toward and away. Proximity bar responds monotonically enough to guide someone.

### T8 — Cell loss
Simulator: fire the scripted mass-silence event. The cell renders distinctly from an empty cell. **Check this from three metres back**, not from the laptop.

### T9 — Sustained run
All three running 30 minutes untouched. No crash, no advertiser death, no runaway battery drain.

---

## Range survey — do this in the actual room

Measure before you stage anything.

| Condition | 1M PHY | Coded PHY |
|---|---|---|
| Open line of sight | ___ m | ___ m |
| Through one wall | ___ m | ___ m |
| Phone in a bag | ___ m | ___ m |
| Through two walls | ___ m | ___ m |

**Set the demo walking route to about 60% of measured maximum.** Forty bodies in a room absorb 2.4 GHz noticeably, and the range you measure in an empty hall is not the range you'll get with an audience in it.

---

## Demo run sheet

**Before you start:** hold up P3, show the empty screen, and say what it means. *"This is a responder at the perimeter. Right now it knows nothing."* The payoff only works if the audience saw the emptiness first.

1. **Airplane mode on, visibly, on all three phones.** Hold them up. Half the credibility is in this one gesture.
2. P1 goes into the bag, behind the stage. *"Someone trapped under a collapsed floor."*
3. Show P3 at the back of the room, still empty. *"No network. No cell towers. The responder cannot see them."*
4. Pick up P2 and walk. *"A neighbour walks out of the building."*
5. P2 near P1 — pickup indicator fires.
6. Walk P2 to P3. Handoff fires. **P3 now shows P1.** *"That message just walked across the room in my pocket."*
7. Power off P1. Wait. P3 flips to `UNEXPECTED_SILENCE` with a timestamp. *"That device didn't run out of battery. It said it would speak again in ten seconds, and it didn't. Something happened to it, and we know when."*
8. Switch to the projector. Simulator, 500 nodes, Rasuwa. `CELL_LOSS` fires. *"Real toll: 22, then 95, then 469, then 626, over three days. Two agencies reporting different numbers on the same evening. Our map converges in minutes."*

---

## Failure drills — rehearse these, don't improvise

| Failure | Response |
|---|---|
| BLE won't link on stage | Switch to `SimulatedTransport` from the mode picker. Everything except the physical walk still demos. Say so plainly: "the radio isn't cooperating in this room, here's the same logic on simulated transport." |
| A phone dies | Roles are interchangeable. Any phone can be any role. Know which one you'd drop — it's P2, and the demo becomes T1 plus the simulator. |
| Advertiser killed by the OS mid-demo | Force-stop and relaunch takes about 8 seconds. Keep talking through it. Have the next sentence ready. |
| Projector won't mirror | Run the simulator on P3 and hold it up. Pre-record a 60-second screen capture as final insurance. |

Rehearse the whole run sheet at least twice with the phones in their real positions. The walk takes longer than you think, and the silence while you cross the room is the part people forget to fill.
