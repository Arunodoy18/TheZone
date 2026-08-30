package com.thezone.demo

import android.content.Context
import android.os.BatteryManager
import com.thezone.identity.DeviceKeyStore
import com.thezone.packet.BatteryScale
import com.thezone.packet.EventClock
import com.thezone.packet.Packet
import com.thezone.packet.PacketCodec
import com.thezone.packet.Status
import com.thezone.sensors.Altitude

/**
 * H2 PLACEHOLDER. Builds the 31-byte packet this phone broadcasts so the
 * advertiser has something real to send while we prove the radio out.
 *
 * It only wires up what H2 needs: this device's identity, live battery level, and
 * the duty-cycle `next_expected_tx` ladder. Position is no-fix, altitude is
 * no-barometer, status is UNKNOWN. H4 (Dead Man's Packet) and H5 (barometer)
 * replace this with the real heartbeat.
 */
object HeartbeatSource {

    fun current(context: Context, nowMillis: Long = System.currentTimeMillis()): ByteArray {
        val identity = DeviceKeyStore.identity(context)
        val batteryPercent =
            com.thezone.demo.DebugOverrides.batteryPercentOverride ?: readBatteryPercent(context)
        val batteryLevel = BatteryScale.percentToNibble(batteryPercent)

        // Sensor-derived status with zero user input: a rising barometric trend
        // is inferred as RISING_WATER (PACKET_SPEC status enum, PRD §6).
        val status = if (Altitude.rising) Status.RISING_WATER.code else Status.UNKNOWN.code

        val packet = Packet(
            version = Packet.PROTOCOL_VERSION,
            type = Packet.TYPE_STATUS,
            deviceId = identity.deviceId,
            deltaLat = Packet.NO_FIX,
            deltaLon = Packet.NO_FIX,
            status = status,
            severity = 0,
            casualties = 0,
            timestampMinutes = EventClock.stampMinutes(nowMillis),
            batteryLevel = batteryLevel,
            hopCount = 0,
            nextExpectedTxSeconds = ladderSeconds(batteryPercent),
            altDelta = Altitude.deltaByte,   // NO_BAROMETER when absent — never a false zero
            altTrend = Altitude.trendMeters,
        )
        return PacketCodec.encode(packet, identity).also { Altitude.markTransmitted() }
    }

    /** docs/PACKET_SPEC.md "next_expected_tx" table. */
    fun ladderSeconds(batteryPercent: Int): Int = when {
        batteryPercent > 60 -> 1
        batteryPercent >= 30 -> 10
        batteryPercent >= 10 -> 60
        else -> 300
    }

    private fun readBatteryPercent(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        return level.takeIf { it in 0..100 } ?: 100
    }
}
