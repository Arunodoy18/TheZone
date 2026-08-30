package com.thezone.transport

import com.thezone.packet.DeviceIdentity
import com.thezone.packet.Packet
import com.thezone.packet.PacketCodec
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Demo insurance (CLAUDE.md standing rules). A pure in-process transport: no
 * radio, no Android. A handful of synthetic peers each emit a well-formed 31-byte
 * packet on an interval, so Dead Man's Packet, triage and barometry can all be
 * exercised and demoed when BLE will not cooperate.
 *
 * `advertise()` is accepted and logged but not echoed back — a real peer never
 * hears its own broadcast either.
 */
class SimulatedTransport(
    private val peerCount: Int = 3,
    private val periodMillis: Long = 2_000L,
    private val seed: Long = 0x5EED,
) : BaseTransport(kind = "Simulated") {

    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "sim-transport").apply { isDaemon = true }
    }
    private var task: ScheduledFuture<*>? = null

    private val peers: List<SimPeer> = buildPeers()
    private var tick = 0L

    override fun start() {
        if (running) return
        running = true
        scanning = true
        log("simulated transport start — $peerCount peers every ${periodMillis}ms")
        task = executor.scheduleAtFixedRate(
            ::emitRound,
            0L,
            periodMillis,
            TimeUnit.MILLISECONDS,
        )
        emitDiagnostics()
    }

    override fun stop() {
        if (!running) return
        task?.cancel(false)
        task = null
        running = false
        scanning = false
        advertising = false
        log("simulated transport stop")
        emitDiagnostics()
    }

    override fun advertise(bytes: ByteArray?) {
        requirePacketSize(bytes)
        advertisedHex = bytes?.toHex()
        advertising = bytes != null
        log(if (bytes == null) "advertisement cleared" else "advertising ${bytes.size}B (simulated, not echoed)")
        emitDiagnostics()
    }

    fun shutdown() {
        stop()
        executor.shutdownNow()
    }

    private fun emitRound() {
        try {
            tick++
            for (peer in peers) {
                val bytes = peer.nextPacket(tick)
                deliver(
                    InboundPacket(
                        bytes = bytes,
                        rssi = peer.rssi(tick),
                        receivedAtMillis = System.currentTimeMillis(),
                        phy = if (tick % 2 == 0L) PacketPhy.CODED else PacketPhy.ONE_M,
                    ),
                )
            }
        } catch (t: Throwable) {
            fail("sim round failed: ${t.message}")
        }
    }

    private fun buildPeers(): List<SimPeer> {
        val rng = Random(seed)
        return (0 until peerCount).map { index ->
            val key = ByteArray(DeviceIdentity.KEY_BYTES) { rng.nextInt().toByte() }
            SimPeer(
                identity = DeviceIdentity(key),
                baseRssi = -55 - index * 12,
                startBatteryLevel = 12 - index * 3,
                status = 1 + index % 6,
                rng = Random(seed xor (index.toLong() + 1)),
            )
        }
    }

    /** One synthetic device. Battery drains slowly; altitude drifts; status is fixed. */
    private class SimPeer(
        private val identity: DeviceIdentity,
        private val baseRssi: Int,
        startBatteryLevel: Int,
        private val status: Int,
        private val rng: Random,
    ) {
        private var batteryLevel = startBatteryLevel.coerceIn(0, 15)
        private var altDelta = 0
        private var epochMinute = 0

        /**
         * A real device re-broadcasts the *same* heartbeat until something
         * changes, so the store dedups it. Mirror that: hold the packet steady
         * and only mint a genuinely new identity when battery drains or altitude
         * shifts (every ~10 ticks).
         */
        fun nextPacket(tick: Long): ByteArray {
            if (tick % 10 == 0L) {
                if (batteryLevel > 0 && tick % 40 == 0L) batteryLevel--
                altDelta = (altDelta + rng.nextInt(-1, 2)).coerceIn(-20, 20)
                epochMinute = (epochMinute + 1) % 65_536
            }

            val packet = Packet(
                version = Packet.PROTOCOL_VERSION,
                type = Packet.TYPE_STATUS,
                deviceId = identity.deviceId,
                deltaLat = 200 + (identity.deviceId[0].toInt() and 0x3F),
                deltaLon = -150 + (identity.deviceId[1].toInt() and 0x3F),
                status = status,
                severity = 4 + (identity.deviceId[2].toInt() and 0x07),
                casualties = identity.deviceId[3].toInt() and 0x03,
                timestampMinutes = epochMinute,
                batteryLevel = batteryLevel,
                hopCount = 0,
                nextExpectedTxSeconds = ladder(batteryLevel),
                altDelta = altDelta,
                altTrend = 0,
            )
            return PacketCodec.encode(packet, identity)
        }

        fun rssi(tick: Long): Int = baseRssi + ((tick % 7) - 3).toInt()

        private fun ladder(level: Int): Int = when {
            level > 9 -> 1
            level > 4 -> 10
            level > 1 -> 60
            else -> 300
        }
    }
}
