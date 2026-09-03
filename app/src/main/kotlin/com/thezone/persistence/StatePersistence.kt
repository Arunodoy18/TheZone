package com.thezone.persistence

import android.content.Context
import android.util.Log
import com.thezone.core.CellLoss
import com.thezone.core.GridCell
import com.thezone.core.StoredReport
import com.thezone.packet.Packet
import com.thezone.packet.PacketCodec
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Tier 0 crash safety: the report store and the confirmed collapses are
 * in-memory only, so a reboot or an OS kill mid-incident starts a carrier phone
 * blank. This writes a compact JSON snapshot to the app's private files dir and
 * reloads it on start.
 *
 * Only the raw 31-byte record plus reception metadata is stored per report;
 * everything else (the decoded [Packet], the cell) is derived on load. Writes are
 * atomic (temp file + rename). Not in `core/` — it touches Android + org.json.
 */
object StatePersistence {

    private const val FILE = "zone-state.json"
    private const val VERSION = 1
    private const val TAG = "TheZone"

    data class Loaded(
        val reports: List<StoredReport>,
        val cellLosses: List<CellLoss>,
        val resolvedPrefixes: List<String>,
    )

    private fun file(context: Context) = File(context.filesDir, FILE)

    /** Overwrite the snapshot. Cheap enough to call on a debounce from the pump. */
    fun save(
        context: Context,
        reports: List<StoredReport>,
        cellLosses: List<CellLoss>,
        resolvedPrefixes: Collection<String> = emptyList(),
    ) {
        val root = JSONObject()
        root.put("v", VERSION)
        root.put("savedAt", System.currentTimeMillis())

        val rs = JSONArray()
        for (r in reports) {
            rs.put(
                JSONObject()
                    .put("id", r.contentId)
                    .put("bytes", r.bytes.toHex())
                    .put("first", r.firstHeardAtMillis)
                    .put("last", r.lastHeardAtMillis)
                    .put("bestRssi", r.bestRssiDbm)
                    .put("lastRssi", r.lastRssiDbm)
                    .put("hop", r.receivedHopCount)
                    .put("hopsSeen", JSONArray(r.hopsSeen.toList()))
                    .put("times", r.timesHeard)
                    .put("own", r.isOwn),
            )
        }
        root.put("reports", rs)

        val cl = JSONArray()
        for (l in cellLosses) {
            cl.put(
                JSONObject()
                    .put("lat", l.cell.latIndex)
                    .put("lon", l.cell.lonIndex)
                    .put("devs", l.deviceCount)
                    .put("silent", l.silentCount)
                    .put("first", l.firstSilentAtMillis)
                    .put("last", l.lastSilentAtMillis)
                    .put("detected", l.detectedAtMillis),
            )
        }
        root.put("cellLosses", cl)
        root.put("resolved", JSONArray(resolvedPrefixes.toList()))

        runCatching {
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file(context))) {
                tmp.copyTo(file(context), overwrite = true)
                tmp.delete()
            }
        }.onFailure { Log.w(TAG, "state save failed: ${it.message}") }
    }

    /**
     * Reload the snapshot. Reports (and collapses) whose last activity is older
     * than [maxAgeMillis] are dropped — a phone that was off for days shouldn't
     * resurrect a stale picture. Returns null when there's nothing usable.
     */
    fun load(context: Context, maxAgeMillis: Long): Loaded? {
        val f = file(context)
        if (!f.exists()) return null
        val now = System.currentTimeMillis()

        val root = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return null
        if (root.optInt("v", -1) != VERSION) return null

        val reports = ArrayList<StoredReport>()
        val rs = root.optJSONArray("reports") ?: JSONArray()
        for (i in 0 until rs.length()) {
            val o = rs.optJSONObject(i) ?: continue
            val last = o.optLong("last", 0L)
            if (now - last > maxAgeMillis) continue
            val bytes = runCatching { o.getString("bytes").hexToBytes() }.getOrNull() ?: continue
            if (bytes.size != Packet.SIZE_BYTES) continue
            val packet = runCatching { PacketCodec.decode(bytes) }.getOrNull() ?: continue
            val hopsSeen = o.optJSONArray("hopsSeen")?.let { arr ->
                (0 until arr.length()).map { arr.getInt(it) }.toSet()
            } ?: setOf(packet.hopCount)
            reports.add(
                StoredReport(
                    contentId = o.optString("id", PacketCodec.contentId(bytes).toHex()),
                    bytes = bytes,
                    packet = packet,
                    firstHeardAtMillis = o.optLong("first", last),
                    lastHeardAtMillis = last,
                    bestRssiDbm = o.optInt("bestRssi", 0),
                    lastRssiDbm = o.optInt("lastRssi", 0),
                    receivedHopCount = o.optInt("hop", packet.hopCount),
                    hopsSeen = hopsSeen,
                    timesHeard = o.optInt("times", 1),
                    isOwn = o.optBoolean("own", false),
                ),
            )
        }

        val losses = ArrayList<CellLoss>()
        val cl = root.optJSONArray("cellLosses") ?: JSONArray()
        for (i in 0 until cl.length()) {
            val o = cl.optJSONObject(i) ?: continue
            if (now - o.optLong("last", 0L) > maxAgeMillis) continue
            losses.add(
                CellLoss(
                    cell = GridCell(o.optInt("lat"), o.optInt("lon")),
                    deviceCount = o.optInt("devs"),
                    silentCount = o.optInt("silent"),
                    firstSilentAtMillis = o.optLong("first"),
                    lastSilentAtMillis = o.optLong("last"),
                    detectedAtMillis = o.optLong("detected", o.optLong("last")),
                ),
            )
        }

        val resolved = ArrayList<String>()
        root.optJSONArray("resolved")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i, null)?.let { resolved.add(it) }
        }

        if (reports.isEmpty() && losses.isEmpty() && resolved.isEmpty()) return null
        Log.d(TAG, "state loaded: ${reports.size} reports, ${losses.size} collapses, ${resolved.size} resolved")
        return Loaded(reports, losses, resolved)
    }

    fun delete(context: Context) {
        runCatching { file(context).delete() }
        runCatching { File(context.filesDir, "$FILE.tmp").delete() }
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) sb.append("%02x".format(b.toInt() and 0xFF))
        return sb.toString()
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "odd hex length" }
        return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
