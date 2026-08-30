# BUILD_PLAN

Ten hours with a two-hour buffer. Each phase has a **checkpoint that must pass on a real phone** before the next begins. If a checkpoint fails, use the cut listed for that phase rather than pushing the schedule.

---

## H0 — Capability probe (30 min) · DO THIS FIRST

A single-screen app that prints, on each of the three phones:

```
Build:                 <manufacturer> <model> <Android version>
isLeCodedPhySupported()               : true/false
isLeExtendedAdvertisingSupported()    : true/false
isMultipleAdvertisementSupported()    : true/false
leMaximumAdvertisingDataLength()      : <n>
Sensor.TYPE_PRESSURE present          : true/false
```

**Why first:** Coded PHY advertising support is chipset-dependent and does not track price or release year. A flagship can fail where a mid-ranger works. You need to know now, not at 3am.

**Checkpoint:** all three phones report their flags and you've written them down.

**If a phone lacks extended advertising:** fine — it uses legacy 1M advertising. Dual-mode handles it. But that phone can no longer demo the Coded PHY range claim, so pick which phone walks across the room accordingly.

**If a phone lacks a barometer:** it can't demo vertical triage. Make it the responder.

---

## H1 — Packet codec (1 h)

Pure Kotlin, zero Android dependencies, in `packet/`. Implement `PACKET_SPEC.md` exactly. Write all ten test vectors first.

**Checkpoint:** `./gradlew test` green, including the hop-count hashing decision in test 8.

**Cut if over:** none. This phase cannot be cut. Everything downstream depends on the contract.

---

## H2 — Advertiser + scanner (2 h) · the risky one

Implement `BleTransport` behind the `ReportTransport` interface. Advertise on both PHYs, scan on both. Foreground service with a persistent notification.

**Checkpoint:** phone A advertises, phone B logs the received bytes, and `decode()` reproduces phone A's packet exactly.

**Budget the full two hours.** If the other phone can't see you it is almost always permissions or advertising mode. This happens to every team.

Order of things to check when it doesn't work:
1. Runtime permissions actually granted (not just declared in the manifest)
2. `ADVERTISE_MODE_LOW_LATENCY` and `ADVERTISE_TX_POWER_HIGH` while debugging
3. Advertising on legacy 1M only, to isolate whether extended is the problem
4. Scanning with a null filter first, then add the manufacturer-ID filter
5. Bluetooth off/on on both phones — clears a surprising number of stuck states
6. A generic BLE scanner app on a third device to confirm you're advertising at all

**Cut if over:** ship with legacy 1M advertising only. You lose the Coded PHY range claim and the demo distance shortens, but everything else survives. Note it as a known limitation rather than hiding it.

---

## H3 — Store and relay (1 h)

`ReportStore` as a content-addressed set. Dedup by hash. Relay with hop increment. Round-robin rebroadcast of carried packets so relaying never starves your own signal.

**Checkpoint — this is the demo, so treat it seriously:** phone A out of range of phone C. Phone B walks A → C. Phone C now holds A's packet at hop count 2.

**Cut if over:** none. This is the product.

---

## H4 — Dead Man's Packet (1 h) · the USP

`SilenceEvaluator` per `PRD.md` §4. Device-level classification plus the cell-level `CELL_LOSS` rule. Log every state transition with a wall-clock timestamp to an in-app log the demo can display.

**Checkpoint:** power off phone A. Within 3× its declared interval, phone C shows `UNEXPECTED_SILENCE` with the time it went dark. Then set phone A's battery model to critical, power it off again, and confirm it classifies as `EXPECTED_SILENCE` instead.

That second half is the one people skip and it's the half that proves the idea. Both branches must demo.

**Cut if over:** none. Without this you have a worse Bridgefy.

---

## H5 — Barometer (45 min)

`TYPE_PRESSURE` → relative altitude against a launch baseline. Trend across the last three transmissions. Handle absence as a flag, never as a false zero.

**Checkpoint:** carry a phone up one floor. The reported delta is within about 1 m of the real height. Trigger the rising-water escalation by walking upstairs with status set to `RISING_WATER`.

**Cut if over:** broadcast `alt_delta` but skip the trend logic and the auto-escalation. You keep the basement/rooftop separation, which is PS2's literal ask, and lose only the automatic drowning escalation.

---

## H6 — UI, three modes (2.5 h)

Mode picker on first launch, then Citizen / Responder / Map per `PRD.md` §3. Follow the UI direction in `CLAUDE.md`.

Order within the phase, so a time overrun costs you the least important thing:
1. Citizen (simplest, and it's what two of three demo phones show)
2. Responder list + triage sort
3. Dig Here proximity bar
4. Map with silence rendering
5. Time scrubber

**Checkpoint:** all three modes run, and a `CELL_LOSS` cell is visually distinguishable from an empty cell at projector distance. Stand back three metres and check. If you can't tell them apart from there, the USP is invisible on stage.

**Cut if over:** drop the time scrubber first, then Dig Here. Never drop the map's silence rendering — that's where the USP becomes visible.

---

## H7 — Simulator and seed data (1 h)

`SimulatedTransport` with 500 synthetic nodes over Rasuwa geography, replaying the real toll curve (22 → 95 → 469 → 626 across 72 hours), compressed to about 90 seconds of stage time.

Script in a mass-silence event so `CELL_LOSS` fires visibly during the demo. Don't rely on it happening naturally.

**Checkpoint:** runs on the projector without dropping frames, and `CELL_LOSS` fires on cue.

**Cut if over:** reduce to 100 nodes. The scale story weakens slightly; nothing else changes.

---

## H8 — Three-phone validation (1 h)

Run the full `PRD.md` §8 checklist, in the actual room if you can get into it, with airplane mode on.

Measure the real working distance between phones through whatever walls are actually there. **Know your range before you stage the demo** — set the walking route to about 60% of measured maximum so you have margin when forty bodies in the room add attenuation.

---

## H9–H10 — Buffer

Do not schedule anything here. It will be consumed by H2.

If it somehow isn't: the PWA fallback viewer, then the deck.

---

## Standing rules

- **Commit at every checkpoint.** A working H4 you can roll back to is worth more than an ambitious H6 that doesn't build.
- **Keep `SimulatedTransport` working the entire time.** It is the demo insurance. If BLE dies at 3am, everything except the walk-across-the-room moment still runs, because Dead Man's Packet, triage and barometry are transport-agnostic by design.
- **Never debug BLE on an emulator.** It doesn't have a radio.
- Turn off battery optimisation for the app on all three phones, or Android will kill the advertiser mid-demo.
- Charge all three phones to full the night before, then note that you're demoing a battery-adaptive system on full batteries — you'll need to fake the battery level to show the ladder. Add a hidden debug gesture to override reported battery. **Do this in H4, not on stage.**
