# PRD — Offline Disaster Signal Network

**Target:** working Android APK, demoable on 3 real phones, built in one session.
**Problem statements:** Avinya 2026 PS2 (Ground-Zero Communication Blackout) + PS5 (Post-Disaster Information Fog).

---

## 1. What this is

A phone app that keeps working when every network is gone. Phones talk directly to each other over Bluetooth Low Energy. A trapped person's phone broadcasts a 31-byte signal. Any phone that comes within range copies it and carries it. The signal physically walks out of the disaster zone in someone's pocket.

The same app, in a different mode, assembles those signals into a severity map for responders.

**The thing that makes it different:** every packet declares when the device will next transmit. So when a device goes quiet, the system knows whether that silence was expected. Unexpected silence — a device that promised to speak in 60 seconds and vanished at 12 with 70% battery — means the device was destroyed. Cell-wide simultaneous silence means the area was destroyed. **We infer destruction from the shape of the hole in the data.**

---

## 2. Scope for this session

### In scope (must ship)

| # | Capability | Why it's in |
|---|---|---|
| 1 | BLE advertise + scan, dual PHY (1M + Coded) | The whole system |
| 2 | 31-byte packet codec | The contract everything depends on |
| 3 | Store-carry-forward relay | Makes it a network, not a broadcast |
| 4 | Dead Man's Packet evaluator | **The USP. Non-negotiable.** |
| 5 | Barometric relative altitude | Basement vs rooftop — PS2's literal ask |
| 6 | Three UI modes: Citizen / Responder / Map | Demo surface |
| 7 | Battery-adaptive duty cycle | Makes #4 meaningful |
| 8 | Seeded simulator | Demo insurance + PS5 scale story |

### Explicitly cut (say so in the deck, don't build)

- **Real crypto signatures** → use a truncated hash of a per-install random key. Same anti-spoofing story at demo scale, one hour saved.
- **RSSI trilateration** → a single proximity strength bar is enough for "dig here". Trilateration needs movement tracking you won't finish.
- **CRDT library** → merging content-addressed records by hash *is* a CRDT. Don't import anything.
- **PWA** → P2. Build only if the APK is done and tested.
- **iOS** → impossible (see §7). State the limitation, don't attempt it.
- **On-device ML** → no training data, no time, indefensible under questioning.

---

## 3. The three modes

The same APK, a mode switch on first launch. This matters: **one binary, three roles** is a much better story than three apps, and it's less work.

### Mode A — Citizen (phones 1 and 2 in the demo)

The person using this is trapped, panicking, possibly injured, in the dark, on a phone at 8% battery.

- **No onboarding. No menus. No login.** Opens straight into broadcasting.
- Dark background by default — a bright screen in rubble costs battery and gives away position.
- One enormous status readout, readable at arm's length: **"You are being heard."**
- Beneath it, the only number that matters to a frightened person: **how many devices have carried your signal.** `3 devices carried your signal` is the entire emotional payload of this screen.
- Three optional status buttons, thumb-sized, high contrast: `Trapped` / `Water rising` / `Safe`. Optional because the app broadcasts sensor-derived status with zero input — it works when the person is unconscious or the phone is buried.
- Nothing else. No map, no settings, no stats.

### Mode B — Responder (phone 3 in the demo)

Field use: gloves, rain, sunlight, one hand.

- A live list of heard devices, sorted by triage priority (see §5).
- Each row: status, severity, relative altitude, last heard, battery, and **silence state**.
- Tap a row → **Dig Here**: a large proximity bar driven by RSSI. Walk toward the signal, the bar fills. This is the metal-detector screen, not a map.
- High brightness, large type, works in sunlight.

### Mode C — Map / EOC (projector-facing)

- Grid-cell severity map over the local area.
- Each cell coloured by confidence-scored severity.
- **Silence rendering is the critical design problem.** A cell where everyone stopped transmitting at once must NOT look like a cell with no coverage. No-data = flat neutral. Unexpected mass silence = its own visual language, with a timestamp: *"12 devices, all silent at 04:12."* If those two look the same, the USP is invisible in the demo and the whole build was pointless.
- A time scrubber so you can replay the event.

---

## 4. Dead Man's Packet — the evaluator

This is the core logic. Get it right before anything cosmetic.

Every received packet carries `next_expected_tx` (seconds until the sender intends to transmit again) and `battery`.

For each known device, maintain: `last_heard_at`, `promised_next_tx`, `last_battery`, `consecutive_misses`.

Classify on a timer tick:

```
overdue_by = now - (last_heard_at + promised_next_tx)

if overdue_by < grace:                    → ALIVE
if last_battery <= CRITICAL:              → EXPECTED_SILENCE      (deprioritise)
if overdue_by > (3 * promised_next_tx):   → UNEXPECTED_SILENCE    (escalate)
```

Then the cell-level rule, which is the PS5 payoff:

```
if a grid cell has >= 3 devices AND >= 80% enter UNEXPECTED_SILENCE
   within the same 120-second window
       → CELL_LOSS  ("total collapse vs minor waterlogging", answered)
```

`grace` should be generous — BLE advertisements get missed constantly. Start at 2x the promised interval and tune on real phones.

**Log every state transition with a timestamp.** The demo depends on being able to point at the exact moment a device went dark.

---

## 5. Triage priority

The responder list sorts by a single computed score, not by time. Rank order:

1. `RISING_WATER` **with increasing relative altitude** — person climbing as water rises, drowning imminent
2. `UNEXPECTED_SILENCE` within the last 5 minutes — just lost, most likely still alive
3. `TRAPPED_DEBRIS` with negative relative altitude — below grade, crush injury
4. Any status with battery below critical — about to go silent, get them while they're still reporting
5. Everything else by severity, then recency

Point 4 is worth calling out on stage: **prioritising by who is about to stop transmitting** is a triage signal no other system has, and it falls directly out of the USP.

---

## 6. Barometric altitude

- Read `Sensor.TYPE_PRESSURE`. Convert to relative altitude against a baseline captured at first launch.
- Absolute altitude is worthless. **Relative delta is the whole value** — ±1 m, and it works indoors where GPS is dead.
- Broadcast as a signed 1-byte delta in metres, clamped.
- Track the **trend** across the last N packets. A rising trend under `RISING_WATER` is the automatic drowning escalation. This requires no user input at all.
- Not all phones have a barometer. Handle absence gracefully — flag the device as no-baro rather than reporting a false zero.

---

## 7. Known limitations — state these first, in the deck

Judges respect a limitation you declare. They punish one they discover.

- **iOS cannot participate.** Web Bluetooth gives browsers the GATT central role only — no advertising, no background, and no Web Bluetooth on iOS Safari at all. Two PWA phones are both scanners and will never see each other. Native iOS background advertising is restricted by Apple. A real deployment needs an entitlement or a hardware beacon.
- **2.4 GHz penetrates rubble badly.** Concrete and rebar attenuate hard. The answer is not magic penetration — it's that a pinned phone advertises for days at low duty cycle, and a rescuer walking the pile eventually gets within 10 m. We convert "search this block" into "search this 10 m circle." Coded PHY improves the odds.
- **Coded PHY advertising support is chipset-dependent** and does not track price or release year. Dual-mode is mandatory; see BUILD_PLAN §H0.
- **The last mile is out of scope.** The responder device is the gateway and syncs on any bearer it finds. Critically: the system is useful even if sync never happens — a responder's own phone showing "47 signals in this building, 3 marked rising-water" is already the whole product.

---

## 8. Definition of done

Ship when all of these pass on three real phones with **airplane mode on**:

- [ ] Phone 1 broadcasting from another room. Phone 3 does not see it.
- [ ] Phone 2 walks from phone 1 to phone 3. Phone 3 now shows phone 1's signal, hop count 2. **This is the demo.**
- [ ] Phone 1 powered off. Within 3x its interval, phone 3 shows `UNEXPECTED_SILENCE` with the timestamp.
- [ ] Phone 1 carried upstairs. Relative altitude on phone 3 changes by roughly the real height.
- [ ] Responder list re-sorts when a device changes status.
- [ ] Dig Here proximity bar responds to walking toward and away.
- [ ] Map mode shows a `CELL_LOSS` cell that is visually distinct from an empty cell.
- [ ] Simulator seeds 500 nodes and the map assembles without dropping frames.
