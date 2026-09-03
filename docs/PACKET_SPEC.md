# PACKET_SPEC

**Authoritative.** Every component encodes and decodes to this. Do not change without changing this file first.

---

## Why 31 bytes

BLE legacy advertising payload is 31 bytes. Extended advertising allows up to 255, but Coded PHY advertising support is chipset-dependent and extended advertisements are invisible to legacy scanners. If we design for 255 and later need a legacy fallback, we write two encoders and maintain two code paths under time pressure.

**The 31-byte core is mandatory. Extra bytes, when available, are an optional appendix that carries nothing essential.**

Also: "a life in 31 bytes" is a good line on stage.

---

## Layout

Manufacturer-specific data field. Company ID `0xFFFF` (demo-reserved). All multi-byte values big-endian.

| Off | Len | Field | Encoding |
|---|---|---|---|
| 0 | 1 | `version_type` | high nibble = protocol version (start at 1), low nibble = packet type (0 = STATUS, 1 = RESOLVE) |
| 1 | 6 | `device_id` | first 6 bytes of SHA-256 of a per-install random 32-byte key |
| 7 | 4 | `position` | lat/lon delta from a hardcoded local origin, 2 × int16, ~2 m precision (see below) |
| 11 | 1 | `status` | enum, see below |
| 12 | 1 | `severity_casualties` | high nibble severity 0–15, low nibble casualty count 0–15 (15 = "15 or more") |
| 13 | 2 | `timestamp` | uint16, minutes since event epoch (~45 day range) |
| 15 | 1 | `battery_hops` | high nibble battery in 16 steps (0–15 → 0–100%), low nibble hop count 0–15 |
| 16 | 2 | `next_expected_tx` | uint16 seconds until the sender intends to transmit again. **The USP.** |
| 18 | 1 | `alt_delta` | int8, relative altitude in metres vs this device's baseline, clamped ±127. `0x80` = no barometer |
| 19 | 4 | `auth` | first 4 bytes of SHA-256(device_key ‖ payload[0..18]). Not real crypto — anti-spoofing at demo scale |
| 23 | 1 | `alt_trend` | int8, metres change across the last 3 transmissions. Positive = climbing |
| 24 | 7 | `reserved` | zero-filled in a STATUS packet. In a RESOLVE packet, the first 7 bytes of the resolved report's `content_id` |

Total: **31 bytes.**

### Packet type 1 — RESOLVE

Same 31-byte layout, `version_type` low nibble = 1. A responder declares a heard
report handled so the network's picture converges on who still needs help.

- `device_id` — the resolving responder.
- `status` — always `RESPONDER` (6).
- `reserved[24,31)` — first 7 bytes of the target report's `content_id` (56 bits;
  collision-safe at a disaster's report volume).
- `auth` — MAC'd with the **pre-shared responder key**, not this device's key, so
  any phone holding the key can verify it and a forged RESOLVE from a non-responder
  is rejected. Other fields carry the responder's own live state, so a RESOLVE also
  proves the responder is alive.

Receivers keep a set-union log of resolved `content_id` prefixes (carried and
merged like everything else). A report whose `content_id` starts with any stored
prefix is hidden from the triage list and dropped from the severity map.

---

## Position encoding

A full lat/lon pair costs 8 bytes. We have 4.

Pick a **local origin** per deployment — for the demo, hardcode the venue's coordinates. Then:

```
delta_lat = round((lat - origin_lat) * 100_000)   // int16, ±327.67 → ~±36 km N/S
delta_lon = round((lon - origin_lon) * 100_000)   // int16
```

Precision is roughly 1–2 m, range roughly ±36 km. Both are correct for a district-scale disaster.

If GPS is unavailable, send `0x8000, 0x8000` (no fix). Position is then inferred from the relay path — a device with no fix that was heard by a device with a fix is localised to that device's neighbourhood. Say this in Q&A; it's a good answer to "what if GPS is dead indoors."

---

## Status enum

| Value | Name | Notes |
|---|---|---|
| 0 | `UNKNOWN` | default before any sensor or input |
| 1 | `SAFE` | user-asserted |
| 2 | `TRAPPED_DEBRIS` | user-asserted, or inferred from immobility + negative alt |
| 3 | `RISING_WATER` | user-asserted, or inferred from positive `alt_trend` |
| 4 | `INJURED` | user-asserted |
| 5 | `HAVE_RESOURCE` | has water, medicine, shelter — routes supply, not rescue |
| 6 | `RESPONDER` | verified tier, higher trust weight in scoring |
| 7–15 | reserved | |

The app broadcasts a sensor-derived status with **zero user input**. Buttons refine it. This is what makes "works when the person is unconscious or the phone is buried" a true claim.

---

## Relay rules

1. On receiving a packet, verify `auth` shape (length and non-zero), then hash the full 31 bytes.
2. If the hash is already in the store, **drop it** — that's the dedup, and it's why the merge is conflict-free.
3. If new, store it and increment `hop_count` in the copy that gets rebroadcast. Never mutate the stored original.
4. Drop at `hop_count == 15` (nibble ceiling).
5. Rebroadcast own packets at the duty-cycle interval; rebroadcast carried packets at a slower rate, round-robin, so relaying never starves your own signal.

**Merge is set-union over content-addressed records.** That is a CRDT by construction. Do not import a CRDT library.

---

## `next_expected_tx` — the field the USP runs on

The sender writes what it genuinely intends. It is computed from the current duty-cycle rung, not a constant:

| Battery | `next_expected_tx` |
|---|---|
| > 60% | 1 |
| 30–60% | 10 |
| 10–30% | 60 |
| < 10% | 300 |

**The value must stay honest at every battery level.** A device at 3% that keeps accurately declaring a 300-second interval is still fully participating in the USP — the receiver can still tell expected silence from destruction. A device that lies here breaks the entire system.

Receivers use it exactly as specified in `PRD.md` §4.

---

## Test vectors

Write these as unit tests in H1, before any Android code exists.

```
1. Round-trip: encode(p) → decode → equals(p) for 1000 randomised packets
2. Every encode produces exactly 31 bytes, always
3. Position: (origin_lat + 0.001, origin_lon) → delta_lat == 100
4. No GPS fix survives round-trip as no-fix, not as 0,0
5. alt_delta of 0x80 decodes to "no barometer", not to -128 metres
6. Battery nibble: 100% → 15, 0% → 0, 50% → 7 or 8
7. Hop increment on relay does not mutate the stored original
8. Two packets differing only in hop_count hash differently (so relays don't dedup each other away incorrectly — decide and test this deliberately)
9. auth changes when any payload byte changes
10. next_expected_tx round-trips at all four ladder values
```

Test 8 is a real design decision, not a formality: if hop count is inside the hash, the same message re-enters the store at every hop. Recommended fix — **hash over bytes 0..18 and 23..30, excluding the hop nibble and `auth`** — so identity is the message, not the journey. Implement it that way and make the test assert it.
