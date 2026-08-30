# CLAUDE.md

Project instructions. Read `docs/PRD.md` and `docs/PACKET_SPEC.md` before writing code.

---

## What we're building

A native Android app (Kotlin + Jetpack Compose) that forms an offline BLE store-carry-forward network between phones during a disaster. Hackathon build — one session, demoed live on three real phones.

**One APK, three modes:** Citizen, Responder, Map. Mode is chosen on first launch and stored.

---

## Hard rules

1. **Native Kotlin only.** No React Native, no Flutter, no Capacitor. BLE peripheral advertising through a JS bridge is where this project dies. `BluetoothLeAdvertiser` and `BluetoothLeScanner` directly.
2. **Never break the packet contract.** `docs/PACKET_SPEC.md` is authoritative. The core packet is 31 bytes. If you think you need more space, you don't — ask first.
3. **Transport is an interface.** `ReportTransport` with `BleTransport`, `SimulatedTransport`, and `FileTransport` implementations. All logic — Dead Man's Packet, triage, barometry, scoring — must run identically on any of them. If BLE fails at 3am, the demo still works on `SimulatedTransport`. Never let BLE types leak past the transport boundary.
4. **No network calls.** No Firebase, no analytics, no CDN fonts, no map tile server. Everything works in airplane mode or it doesn't ship. Map tiles are drawn primitives, not downloaded.
5. **No crypto library.** Device ID is a truncated hash of a per-install random key. That's the whole identity story for the demo.
6. **Log every Dead Man's Packet state transition** with a wall-clock timestamp, to an in-app log the demo can display. We need to point at the exact moment a device went dark.
7. **`minSdk = 26`, `targetSdk = 34`.** Extended advertising and Coded PHY need API 26+.

---

## Build order — do not reorder

Each step must run on a real phone before starting the next. Details and time budget in `docs/BUILD_PLAN.md`.

```
H0  Capability probe        ← MUST BE FIRST. Prints PHY flags on all 3 phones.
H1  Packet codec + tests    ← pure Kotlin, no Android deps, unit tested
H2  Advertiser + scanner    ← dual PHY
H3  Store + relay
H4  Dead Man's Packet       ← THE USP
H5  Barometer
H6  UI (3 modes)
H7  Simulator + seed data
H8  Three-phone validation
```

Do not start UI before H4 works. The USP is the product; the screens are packaging.

---

## Architecture

```
app/
  transport/
    ReportTransport.kt      interface: advertise(bytes), onPacket(cb), start(), stop()
    BleTransport.kt         BluetoothLeAdvertiser + BluetoothLeScanner, dual PHY
    SimulatedTransport.kt   in-process fake peers, demo insurance
    FileTransport.kt        JSON import/export, emergency fallback
  packet/
    Packet.kt               data class
    PacketCodec.kt          encode/decode, 31 bytes, pure, unit tested
  core/
    ReportStore.kt          content-addressed set, dedup by hash, union merge
    SilenceEvaluator.kt     Dead Man's Packet — see PRD §4
    TriageScorer.kt         see PRD §5
    DutyCycler.kt           battery -> interval + PHY ladder
    Barometry.kt            TYPE_PRESSURE -> relative altitude + trend
  ui/
    CitizenScreen.kt
    ResponderScreen.kt      list + DigHereScreen
    MapScreen.kt            grid cells, silence rendering, time scrubber
    ModePicker.kt
```

`core/` must have **zero Android BLE imports**. It should be unit-testable on the JVM.

---

## BLE specifics — read before touching the radio

- Everything rides in **manufacturer-specific data**. Use a fixed company ID (`0xFFFF` is fine for a demo). No GATT connection needed for the heartbeat — connectionless broadcast is simpler and far more robust than pairing.
- **Dual-mode is mandatory.** Extended advertisements are invisible to legacy scanners. Advertise on both 1M and Coded PHY; scan on both. If you skip this, the network silently partitions by handset model and it looks like "nobody is nearby" rather than "incompatible radio."
- Check both flags separately, they are not the same thing:
  ```kotlin
  adapter.isLeCodedPhySupported()
  adapter.isLeExtendedAdvertisingSupported()
  ```
  A phone can pass one and fail the other. Fall back to legacy advertising when extended is unsupported.
- Permissions: `BLUETOOTH_ADVERTISE`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (API 31+), plus `ACCESS_FINE_LOCATION` for older. Request at runtime. Set `neverForLocation` on the scan permission if you aren't deriving location from it.
- Use a **foreground service** with a persistent notification for the citizen mode, or Android will kill the advertiser.
- Scan mode `SCAN_MODE_LOW_LATENCY` while the screen is on, `SCAN_MODE_LOW_POWER` in survival mode.

**If the other phone can't see you, it is almost always permissions or advertising mode.** Budget a full hour for this. It happens to everyone.

---

## Duty cycle ladder

Battery drives both interval and PHY. This is what makes `next_expected_tx` honest.

| Battery | Interval | PHY |
|---|---|---|
| > 60% | 1 s | Coded (max reach while affordable) |
| 30–60% | 10 s | alternate Coded / 1M |
| 10–30% | 60 s | 1M only |
| < 10% | 300 s | 1M, sparse — **but still broadcasts an accurate `next_expected_tx`** |

Coded PHY S=8 transmits each bit eight times: longer airtime, more current. It fights survival mode directly, which is exactly why it belongs on the ladder rather than being a fixed setting.

---

## UI direction

Not a dashboard. Three different products in one binary, each with a different physical context:

- **Citizen** — dark, enormous type, arm's-length legible, thumb-sized targets. The person is panicking in the dark on a dying phone. One headline (`You are being heard`) and one number (`3 devices carried your signal`). Nothing else on the screen.
- **Responder** — sunlight, gloves, one hand. Large rows, high contrast, no small tap targets. Dig Here is a proximity bar, closer to a metal detector than a map.
- **Map** — projector-facing. The one screen where the USP has to be *visible*.

**Copy rules:** active voice, sentence case, plain verbs. Name things by what the person controls, never by how the system is built. Empty states are an invitation to act, not a mood. Errors say what happened and what to do.

**The single most important visual decision in this project:** a cell where every device went silent at once must not look like a cell that never had coverage. No-data is flat and neutral. `CELL_LOSS` gets its own visual language and carries a timestamp — "12 devices, all silent at 04:12". If those two read the same, the USP is invisible on stage and the build was pointless.

Do not use gradient-on-dark with a single acid accent — it's the default AI look and reads as templated. Derive the palette from the subject: emergency signage, topographic maps, sonar displays.

---

## Demo constraints

The build must survive these, live, in front of judges:

- Airplane mode toggled **on stage**, visibly. Everything keeps working.
- Phone 1 in a bag behind the stage. Phone 3 at the back of the room. Phone 2 physically walked between them by a presenter. Audience watches a message cross the room in someone's pocket.
- Simulator seeds 500 nodes on the projector, replaying the real Rasuwagadhi toll curve (22 → 95 → 469 → 626 over 72 hours) while the map converges in minutes.

If a change would make any of these fragile, don't make it.

---

## When you're stuck

Ask before: changing the packet layout, adding a dependency, introducing a network call, or reordering the build phases. Everything else, proceed.
