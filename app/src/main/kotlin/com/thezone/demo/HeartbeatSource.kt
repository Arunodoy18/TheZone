package com.thezone.demo

import android.content.Context
import android.os.BatteryManager
import com.thezone.config.IncidentConfig
import com.thezone.identity.DeviceKeyStore
import com.thezone.packet.BatteryScale
import com.thezone.packet.EventClock
import com.thezone.packet.GeoPosition
import com.thezone.packet.Packet
import com.thezone.packet.PacketCodec
import com.thezone.packet.Status
import com.thezone.sensors.Altitude
import com.thezone.sensors.Position

/**
 * Builds the 31-byte heartbeat this phone broadcasts (docs/PACKET_SPEC.md).
 *
 * Wires the live inputs: identity, battery + the duty-cycle `next_expected_tx`
 * ladder, barometric altitude ([Altitude]), and position — the best-effort GPS
 * fix ([Position]) as a delta from the incident origin ([IncidentConfig]), or
 * NO_FIX when there's no fresh fix (a no-fix device is localised from its relay
 * path). Status is a user assertion if there is one, else sensor-derived.
 */
object HeartbeatSource {

    /** A fix older than this is dropped — send NO_FIX and let relay-path localisation take over. */
    private const val POSITION_MAX_AGE_MS = 10L * 60 * 1000

    fun current(context: Context, nowMillis: Long = System.currentTimeMillis()): ByteArray {
        val identity = DeviceKeyStore.identity(context)
        val batteryPercent =
            com.thezone.demo.DebugOverrides.batteryPercentOverride ?: readBatteryPercent(context)
        val batteryLevel = BatteryScale.percentToNibble(batteryPercent)

        // position: a fresh fix -> delta from the incident origin, else NO_FIX
        val fix = Position.snapshot()
        val (deltaLat, deltaLon) = if (fix != null && fix.ageMillis(nowMillis) <= POSITION_MAX_AGE_MS) {
            GeoPosition.encodeDelta(fix.lat, IncidentConfig.originLat(context)) to
                GeoPosition.encodeDelta(fix.lon, IncidentConfig.originLon(context))
        } else {
            Packet.NO_FIX to Packet.NO_FIX
        }

        // A user assertion (Citizen buttons) wins; otherwise status is
        // sensor-derived — a rising barometric trend infers RISING_WATER
        // (PACKET_SPEC status enum, PRD §6) with zero input.
        val status = com.thezone.demo.UserStatus.code
            ?: if (Altitude.rising) Status.RISING_WATER.code else Status.UNKNOWN.code

        val packet = Packet(
            version = Packet.PROTOCOL_VERSION,
            type = Packet.TYPE_STATUS,
            deviceId = identity.deviceId,
            deltaLat = deltaLat,
            deltaLon = deltaLon,
            status = status,
            severity = 0,
            casualties = 0,
            timestampMinutes = EventClock.stampMinutes(nowMillis),
            batteryLevel = batteryLevel,
            hopCount = 0,
            nextExpectedTxSeconds =
                if (NetworkAlert.nearDamage)
                    minOf(ladderSeconds(batteryPercent), NetworkAlert.ALERT_INTERVAL_FLOOR_S)
                else ladderSeconds(batteryPercent),
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

    /** The effective battery % (debug override wins), for the interval + PHY ladders. */
    fun effectiveBatteryPercent(context: Context): Int =
        com.thezone.demo.DebugOverrides.batteryPercentOverride ?: readBatteryPercent(context)

    private fun readBatteryPercent(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        return level.takeIf { it in 0..100 } ?: 100
    }
}
