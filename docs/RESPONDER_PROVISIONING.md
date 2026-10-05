# Responder provisioning — ECDSA signatures for RESOLVE / ALERT

## The gap this closes

RESOLVE and ALERT packets are authenticated today with one pre-shared
responder key (`IncidentConfig.responderKey`). It works, but it has a single
failure mode: anyone who extracts that one key from any provisioned phone can
forge a "reached" or an evacuation alert, and every phone in the mesh will
honour it, because they all check against the same secret.

Zone now also supports per-responder ECDSA P-256 signatures
(`PacketCodec.TYPE_SIG`, `com.thezone.identity`, `com.thezone.core.SignatureLog`).
A forged alert would need a specific responder's private key — one that never
leaves that phone's Android Keystore — not a secret copied onto every
responder phone.

**This is additive, not yet load-bearing.** Until you provision a real
responder (`TrustRoster.PILOT` is non-empty), RESOLVE/ALERT trust works
exactly as it does today — the shared key alone. Signatures are computed and
verified in the background and exposed as `TransportController.isSignatureVerified(contentIdHex)`,
a stronger signal nothing currently acts on. Flipping the gate to require a
verified signature once real responders are provisioned is "phase 2" —
deliberately left for after a field test, so this never ships blind.

## Phase 1 — provision one responder (do this per responder phone)

1. Install the signed release build on the responder's phone.
2. Open the app → long-press → **Debug** → **Responder signing key (ECDSA)**.
3. Tap **Generate signing key**. This creates a P-256 keypair in the phone's
   Android Keystore (hardware-backed where the device supports it) — the
   private key never leaves the phone and is never displayed.
4. Tap **Copy roster entry**. This copies a line like:
   ```kotlin
   RosterEntry(deviceIdHex = "a1b2c3d4e5f6", publicKeyHex = "04...", label = "Gangtok EOC responder 1"),
   ```
5. Send that line back (paste it into a message, a notes app — it's public
   information, not a secret) to whoever maintains the build.
6. Edit the `label` to say who/where this responder is, for your own records.

## Phase 1 — add the entry to the app (do this once per batch of responders)

In `app/src/main/kotlin/com/thezone/identity/TrustRoster.kt`:

```kotlin
val PILOT: TrustRoster = TrustRoster(
    listOf(
        RosterEntry(deviceIdHex = "a1b2c3d4e5f6", publicKeyHex = "04...", label = "Gangtok EOC responder 1"),
        // add more responders here, one line each
    ),
)
```

Rebuild the signed release (`scripts/release.sh`), redistribute the APK to
every phone in the deployment (not just the new responder's — every phone
needs the updated roster to be able to *verify* the new responder's
signatures), and commit the change.

This is manual and offline by design (CLAUDE.md "no network calls"): adding a
responder means shipping an app update, exactly like rotating the shared
responder key already does. It does not scale past a single-agency pilot —
see "phase 3" below for what would.

## Phase 2 — require it, once you've field-tested it

Once you've run a real test (two phones: one provisioned responder issuing a
RESOLVE/ALERT, one plain phone verifying it — see `docs/FIELD_TEST_V0_4_1.md`
for the harness this slots into), change the trust gate in
`TransportController.ingest()` from "the shared key alone" to:

- If `TrustRoster.PILOT.isProvisioned` is false: keep today's behaviour (shared
  key only) — an unprovisioned deployment must not silently break.
- If it's true: honour a RESOLVE/ALERT only once `isSignatureVerified` is also
  true, *or* allow a grace window (e.g. honour on the shared key immediately,
  but revoke/flag it if the signature never arrives or fails) — the right
  choice depends on how reliable SIG-fragment delivery proves to be on real
  radios, which is exactly what the field test will tell you.

This file will be updated with the real switch once that test has run. Do not
flip it blind.

## Phase 3 — beyond a pilot (not built, noted for later)

A fixed, baked-in roster means every responder change is an app rebuild. For
more than one agency, or for responders who need to be added in the field
without a software update, that stops being practical — the AskUserQuestion
decision this was scoped against was deliberately "fixed list for a pilot,"
not this. An authority-issued, runtime-provisioned roster (an incident
commander's phone minting new responder credentials and handing them out over
the mesh) is real additional design and build work, not a tweak — treat it as
its own future decision when the pilot outgrows a fixed list, not something to
half-build now.
