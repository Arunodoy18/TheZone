# Zone

**A phone network that works when every network is gone — and can tell the difference
between a phone that went quiet and a phone that was destroyed.**

Native Android app (Kotlin + Jetpack Compose) that forms an offline Bluetooth
store-carry-forward mesh between phones during a disaster. Built for Avinya 2026,
problem statements **PS2** (Ground-Zero Communication Blackout) and **PS5**
(Post-Disaster Information Fog).

- No servers, no internet, no analytics, no downloaded map tiles. Runs identically
  in airplane mode.
- One APK, three modes chosen on first launch: **Citizen / Responder / Map (EOC)**.
- A trapped person's phone broadcasts a **31-byte** packet over BLE. Any phone in
  range copies it and rebroadcasts it with a hop count; duplicates drop by content
  hash. The signal physically walks out of the zone in a rescuer's pocket.

**Live:**
[site](https://neon-monstera-b28c12.netlify.app/) ·
[download the APK](https://neon-monstera-b28c12.netlify.app/zone.apk) ·
[EOC viewer](https://neon-monstera-b28c12.netlify.app/viewer.html) ·
[GitHub release](https://github.com/Arunodoy18/TheZone/releases/latest)

---

## The USP — the Dead Man's Packet

Every packet declares `next_expected_tx` — how many seconds until the sender
intends to transmit again. So when a phone goes silent, the network can classify
that silence:

| Silence | Meaning |
|---|---|
| **Expected** | Battery was critical; the phone warned us. Deprioritise. |
| **Unexpected** | Promised to speak in 60 s, vanished at 12 s with 70 % battery. The phone was destroyed. Escalate. |
| **Cell loss** | ≥ 3 phones in one ~100 m cell, ≥ 80 % gone within 120 s. The area collapsed — rendered as a hatched, timestamped hole on the map. |

> We infer destruction from the shape of the hole in the data.

It also produces a triage signal no other system has: rank victims by **who is about
to stop transmitting** — reach the phone at 4 % before it goes dark, not after.

---

## Get the app

**Prebuilt APK** (debug build, ~9 MB, `minSdk 26`):

- <https://neon-monstera-b28c12.netlify.app/zone.apk>
- <https://github.com/Arunodoy18/TheZone/releases/latest/download/Zone.apk>
- [`pwa/zone.apk`](pwa/zone.apk) in this repo

```bash
adb install -r pwa/zone.apk
```

On the phone: install, open, pick a mode. Grant Bluetooth + Location when asked
(Android requires Location for BLE scanning — turn it on, then restart the app).
Turn **off** battery optimisation for "Zone Probe (H0)" or the OS kills the
advertiser.

Package id: `com.thezone.probe.debug` · label: **Zone Probe (H0)**.

---

## Build from source

Requires JDK 17 and the Android SDK (API 34). If you have Android Studio, its
bundled JBR works:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew :app:testDebugUnitTest      # 56 unit tests — packet codec, silence, triage
./gradlew :app:assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug           # build + install on every attached device
```

Helper scripts:

| Script | What it does |
|---|---|
| `scripts/deploy.sh [--launch]` | build, install on every `adb` device, grant perms, doze-whitelist, print BT/airplane/location state |
| `scripts/release.sh` | build the APK and stage it at `pwa/zone.apk` for the site |
| `scripts/make-explainer-pdf.sh` | render `docs/EXPLAINER.html` → `docs/Zone-Explainer.pdf` |

---

## The three-phone demo

Full field checklist in [`docs/PHONE_TEST.md`](docs/PHONE_TEST.md); recorded
results in [`docs/H8_VALIDATION.md`](docs/H8_VALIDATION.md).

| Role | Phone in our validation | Why |
|---|---|---|
| **P1 — Trapped** | Samsung Galaxy S23 | only phone with a barometer (vertical triage) |
| **P2 — Carrier** | Realme 7 | has Coded PHY; the phone that covers distance |
| **P3 — Responder** | Motorola Edge 50 Pro | best screen, mirrored to the projector |

The moment: P1 broadcasts from another room with its **Bluetooth off after pickup**;
P2 is walked from P1 to P3; P3 now shows P1's signal at **hop 2**. The message
crossed the room in someone's pocket.

If BLE won't cooperate on stage: any phone → long-press → Debug → H2 → **Simulated**
→ Start. Every non-walk part of the demo still runs — the logic is transport-agnostic.

---

## Project layout

```
app/src/main/kotlin/com/thezone/
  transport/    ReportTransport interface + BleTransport / SimulatedTransport / FileTransport
                BleForegroundService keeps the active transport alive across screen lock
  packet/       Packet, PacketCodec (31 bytes, pure Kotlin, unit tested), DeviceIdentity
  core/         ReportStore (content-addressed, union merge)  ·  SilenceEvaluator (Dead Man's Packet)
                TriageScorer  ·  CorroborationScorer  ·  Barometry  ·  GridCells
  sensors/      PressureReader -> Altitude (relative, EMA-smoothed, trend)
  ui/           CitizenScreen  ·  ResponderScreen + DigHereScreen  ·  MapScreen  ·  theme/
  probe/        MainActivity — mode router, permission gate, H0/H2 debug host
  config/       IncidentConfig (position origin, shared responder key) · LangStore
  persistence/  StatePersistence — crash-safe JSON snapshot of the store + collapses
  diagnostics/  CrashLog — on-device crash file, no analytics
app/src/test/   78 JVM unit tests (no Android deps in core/ or packet/)
pwa/            offline landing page + browser EOC viewer (?live= auto-refresh) + staged zone.apk  (Netlify: publish = pwa)
docs/           specs, build plan, field checklist, explainer
scripts/        deploy / release / eoc-watch / pdf helpers
.github/        CI — unit tests + assembleDebug on push
```

`core/` and `packet/` have zero Android imports and run on the plain JVM.

---

## Docs

| File | |
|---|---|
| [`docs/PRD.md`](docs/PRD.md) | product requirements — §4 Dead Man's Packet, §5 triage, §7 limitations |
| [`docs/PACKET_SPEC.md`](docs/PACKET_SPEC.md) | **authoritative** 31-byte packet contract + test vectors |
| [`docs/BUILD_PLAN.md`](docs/BUILD_PLAN.md) | H0→H8 phased build order, checkpoints, cuts |
| [`docs/PHONE_TEST.md`](docs/PHONE_TEST.md) | on-device field checklist + failure drills |
| [`docs/H8_VALIDATION.md`](docs/H8_VALIDATION.md) | three-phone validation results (2026-08-30) |
| [`docs/EXPLAINER.html`](docs/EXPLAINER.html) / [`docs/Zone-Explainer.pdf`](docs/Zone-Explainer.pdf) | the full field explainer — problem, USP, every feature, proof |
| [`CLAUDE.md`](CLAUDE.md) | build rules and architecture constraints |

---

## Status

**Hackathon build (v0.1)** — validated on three real phones in airplane mode
(2026-08-30): direct link, 2-hop store-carry-forward, expected vs unexpected
silence told apart, barometric floor detection, drowning escalation, Dig Here,
and cell loss in a 500-node simulator replaying the real Rasuwagadhi toll curve
(22 → 95 → 469 → 626 over 72 h).

**v0.3** adds crash-safe persistence, a real GPS position with a configurable
incident origin, inferred `TRAPPED` from sustained immobility, a real app
identity + icon, Sybil-resistant scoring, an on-device crash log, a "Keep Zone
alive" battery-setup screen, a pre-shared responder key (verifiable `RESPONDER`
packets, no crypto library), a `RESOLVE` packet type (mesh-wide "reached"),
hot/cold Dig Here, an optional headcount, continuous live-EOC auto-export + an
auto-refreshing viewer, a sneakernet mesh-state file, the Citizen screen in
Hindi/Nepali, and CI. Single-phone re-check on the Realme 7 (2026-09-04): GPS,
battery ladder, responder key, no crashes. The mesh half re-runs on three phones.
Outstanding: a 30-minute soak run and a range survey.

**Known limits (stated on purpose):** iOS cannot participate (no Web Bluetooth on
Safari; native background advertising is locked down — needs an Apple entitlement
or a hardware beacon). 2.4 GHz penetrates rubble poorly — the answer is a pinned
phone advertising for days at low duty cycle until a rescuer walks within ~10 m.
The `RESPONDER` tier verifies against a pre-shared key; full per-device
signatures would need a crypto library and more than 31 bytes.

---

## License

[MIT](LICENSE).
