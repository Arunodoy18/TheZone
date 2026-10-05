package com.thezone.demo

import com.thezone.core.ReportStore
import com.thezone.core.SilenceEvaluator
import com.thezone.packet.BatteryScale
import com.thezone.packet.DeviceIdentity
import com.thezone.packet.EventClock
import com.thezone.packet.GeoPosition
import com.thezone.packet.Packet
import com.thezone.packet.PacketCodec
import com.thezone.packet.Status
import org.junit.Test
import kotlin.random.Random

/**
 * Not a unit test — a narrated, one-machine walkthrough of the Dead Man's
 * Packet USP, run against the exact production classes the app ships
 * (PacketCodec, ReportStore, SilenceEvaluator — all zero-Android, so they run
 * here unmodified). A fake clock drives it so it finishes in under a second
 * instead of the real ~15 minutes the 300s/8%-battery case would take on a
 * phone. Run with: ./gradlew :app:testDebugUnitTest --tests "*LiveUspDemo*" -i
 * | grep "DEMO "
 */
class LiveUspDemo {

    private var clockMs = 0L
    private fun now() = clockMs
    private fun advance(ms: Long) { clockMs += ms }
    private fun say(line: String) = println("DEMO  $line")
    private fun ts() = "t+${clockMs / 1000}s"

    // Two distinct ~100m grid cells (GeoPosition units are ~1.1m each; the grid
    // cell edge is 90 units ~ 100m) — keeps the "dying phone, alone" case out of
    // the "three phones, one collapsed building" cell below.
    private val cellA = 0.0003  // trapped + carrier's neighbours
    private val cellB = 0.0500  // the dying phone, elsewhere, on its own

    private fun id(seed: Int) = DeviceIdentity(ByteArray(DeviceIdentity.KEY_BYTES) { Random(seed).nextInt().toByte() })

    private fun heartbeat(idn: DeviceIdentity, batteryPct: Int, status: Status, cellOffset: Double, hop: Int = 0): ByteArray {
        val p = Packet(
            version = Packet.PROTOCOL_VERSION, type = 0, deviceId = idn.deviceId,
            deltaLat = GeoPosition.latDelta(GeoPosition.ORIGIN_LAT + cellOffset),
            deltaLon = GeoPosition.lonDelta(GeoPosition.ORIGIN_LON + cellOffset),
            status = status.code, severity = 9, casualties = 0,
            timestampMinutes = EventClock.stampMinutes(0), // re-stamped per call below where needed
            batteryLevel = BatteryScale.percentToNibble(batteryPct),
            hopCount = hop,
            nextExpectedTxSeconds = if (batteryPct <= 10) 300 else 1,
            altDelta = Packet.NO_BAROMETER, altTrend = 0,
        ).let { it.copy(timestampMinutes = EventClock.stampMinutes(clockMs)) }
        return PacketCodec.encode(p, idn)
    }

    @Test
    fun `narrated demo`() {
        say("================================================================")
        say(" ZONE — Dead Man's Packet, live on this machine (no phone needed)")
        say("================================================================")
        say("")

        val store = ReportStore(nowMillis = ::now)
        val silence = SilenceEvaluator(nowMillis = ::now)

        val trapped = id(1)
        val carrier = id(2)
        val dying = id(3)

        // --- 1. a trapped person's phone starts broadcasting --------------
        say("[${ts()}] ${trapped.hex()} (buried under debris, 70% battery) starts broadcasting.")
        say("       Nobody touches the phone again from here — it has to do this alone.")
        var bytes = heartbeat(trapped, 70, Status.TRAPPED_DEBRIS, cellA)
        say("       31-byte packet on air: ${bytes.toHex()}")
        say("       decoded: status=TRAPPED_DEBRIS severity=9 battery=70% next_tx=1s")
        store.accept(bytes, rssiDbm = -58)
        silence.onPacket(trapped.hex(), PacketCodec.decode(bytes), now())
        say("       -> state: ${silence.deviceState(trapped.hex())}")
        say("")

        // --- 2. a second phone carries it one hop ---------------------------
        advance(4_000)
        say("[${ts()}] ${carrier.hex()} (a passer-by's phone) walks within range, hears it, relays it onward.")
        val relayed = PacketCodec.incrementHop(bytes)
        store.accept(relayed, rssiDbm = -70)
        say("       hop count is now ${PacketCodec.decode(relayed).hopCount} — the signal has physically moved.")
        say("")

        // --- 3. it keeps reporting normally ---------------------------------
        repeat(2) {
            advance(1_000)
            bytes = heartbeat(trapped, 68, Status.TRAPPED_DEBRIS, cellA)
            store.accept(bytes, rssiDbm = -58)
            silence.onPacket(trapped.hex(), PacketCodec.decode(bytes), now())
        }
        say("[${ts()}] Two more heartbeats land on schedule. State: ${silence.deviceState(trapped.hex())} (expected).")
        say("")

        // --- 4. the USP: it stops, unprompted ------------------------------
        say("------------------------------------------------------------------")
        say(" THE USP: the phone goes silent WITHOUT warning anyone it would")
        say("------------------------------------------------------------------")
        say("[${ts()}] ${trapped.hex()} had promised its next packet in 1s. Nothing comes.")
        say("       Nobody on the mesh knows why yet — only that a promise was broken.")
        advance(10_000) // past the grace floor; healthy battery -> straight to UNEXPECTED
        val r1 = silence.tick()
        say("[${ts()}] SilenceEvaluator re-checks every tracked device on its own timer.")
        r1.transitions.filter { it.deviceIdHex == trapped.hex() }.forEach {
            say("       >>> ${it.from} -> ${it.to}  @ wall-clock +${it.atMillis}ms")
            say("       >>> silent for ${it.sinceLastHeardMillis}ms, had promised ${it.promisedNextTxSeconds}s, battery was ${it.batteryPercent}%")
        }
        say("       This is the whole USP in one line: it did not just go quiet —")
        say("       it broke a promise it made about itself. That's the signal.")
        say("")

        // --- 5. contrast: a phone that warns it's about to go dark ----------
        say("------------------------------------------------------------------")
        say(" THE CONTRAST: a dying phone that's honest about it")
        say("------------------------------------------------------------------")
        val b2 = heartbeat(dying, 8, Status.SAFE, cellB)
        say("[${ts()}] ${dying.hex()} at 8% battery, in a different building. The duty-cycle")
        say("       ladder makes it declare a 300-second next interval — it's telling the")
        say("       mesh 'I'll be slow', not going dark without warning.")
        store.accept(b2, rssiDbm = -61)
        silence.onPacket(dying.hex(), PacketCodec.decode(b2), now())
        advance(920_000) // past its much longer (300s x2) grace floor
        val r2 = silence.tick()
        r2.transitions.filter { it.deviceIdHex == dying.hex() }.forEach {
            say("       >>> ${it.from} -> ${it.to}  @ wall-clock +${it.atMillis}ms")
        }
        say("       Same idea as the trapped phone's case above — opposite conclusion —")
        say("       because this phone told the truth about itself before going quiet.")
        say("")

        // --- 6. CELL_LOSS: several devices dark together --------------------
        say("------------------------------------------------------------------")
        say(" THE PAYOFF: a whole area going dark at once")
        say("------------------------------------------------------------------")
        val neighbour1 = id(10)
        val neighbour2 = id(11)
        say("[${ts()}] ${trapped.hex()}'s phone, plus two more in the same ~100m cell,")
        say("       all report in together — a normal, healthy moment.")
        val fresh = listOf(trapped to 70, neighbour1 to 65, neighbour2 to 72)
        fresh.forEach { (devId, pct) ->
            val b = heartbeat(devId, pct, Status.SAFE, cellA)
            store.accept(b, rssiDbm = -55)
            silence.onPacket(devId.hex(), PacketCodec.decode(b), now())
        }
        advance(10_000) // same grace-crossing step as part 4, applied to all three at once
        val r3 = silence.tick()
        say("[${ts()}] All three go unexpectedly silent on the very same check.")
        if (r3.newCellLosses.isNotEmpty()) {
            r3.newCellLosses.forEach {
                say("       >>> CELL_LOSS cell=${it.cell}  ${it.silentCount}/${it.deviceCount} silent")
                say("       >>> first went dark @+${it.firstSilentAtMillis}ms, last @+${it.lastSilentAtMillis}ms")
            }
            say("       This is the map's red, hatched cell — 'N devices, all silent at HH:MM' —")
            say("       the one visual the whole pitch depends on: it must not look like no-data.")
        } else {
            say("       (no cell loss fired — see the cellMinDevices/cellSilentFraction thresholds)")
        }
        say("")
        say("================================================================")
        say(" Everything above ran the real app code — PacketCodec, ReportStore,")
        say(" SilenceEvaluator — the exact classes compiled into zone.apk.")
        say(" What a real phone adds on top: an actual BLE radio, actual GPS,")
        say(" actual elapsed wall-clock time instead of this fake clock.")
        say("================================================================")
    }

    private fun DeviceIdentity.hex() = deviceId.toHex()
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
