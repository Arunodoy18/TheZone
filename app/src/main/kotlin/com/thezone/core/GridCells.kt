package com.thezone.core

import com.thezone.packet.Packet

/** Quantising a position delta to a severity-map cell. ~1.1 m per unit; 90 ≈ 100 m. */
object GridCells {

    const val SIZE_UNITS = 90

    fun of(deltaLat: Int, deltaLon: Int, sizeUnits: Int = SIZE_UNITS): GridCell? {
        if (deltaLat == Packet.NO_FIX || deltaLon == Packet.NO_FIX) return null
        return GridCell(Math.floorDiv(deltaLat, sizeUnits), Math.floorDiv(deltaLon, sizeUnits))
    }

    /** Deterministic stand-in cell for a no-fix device, so a live demo isn't blank. */
    fun fallback(deviceIdHex: String): GridCell {
        val h = deviceIdHex.hashCode()
        return GridCell(((h ushr 8) and 0x7) - 3, (h and 0x7) - 3)
    }
}
