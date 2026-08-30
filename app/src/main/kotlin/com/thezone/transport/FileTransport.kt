package com.thezone.transport

/**
 * Emergency fallback (CLAUDE.md architecture): move packets between phones as a
 * JSON file when neither BLE nor the simulator is an option — sneakernet.
 *
 * Wire format, deliberately trivial so it can be hand-inspected and needs no
 * parser dependency:
 *
 *   { "v": 1, "packets": [ "<62 hex chars>", ... ] }
 *
 * As a [ReportTransport], [start] replays every imported packet once through the
 * packet sink; [advertise] appends to an outgoing buffer that [exportJson] reads.
 */
class FileTransport : BaseTransport(kind = "File") {

    private val outgoing = ArrayList<ByteArray>()
    private var imported: List<ByteArray> = emptyList()

    override fun start() {
        running = true
        scanning = true
        log("file transport start — replaying ${imported.size} imported packet(s)")
        imported.forEach { bytes ->
            deliver(
                InboundPacket(
                    bytes = bytes,
                    rssi = 0,
                    receivedAtMillis = System.currentTimeMillis(),
                    phy = PacketPhy.UNKNOWN,
                ),
            )
        }
        emitDiagnostics()
    }

    override fun stop() {
        running = false
        scanning = false
        advertising = false
        log("file transport stop")
        emitDiagnostics()
    }

    override fun advertise(bytes: ByteArray?) {
        requirePacketSize(bytes)
        advertisedHex = bytes?.toHex()
        advertising = bytes != null
        if (bytes != null) {
            outgoing.add(bytes.copyOf())
            countSent()
            log("buffered ${bytes.size}B for export (${outgoing.size} total)")
        }
        emitDiagnostics()
    }

    /** Load packets to be replayed on [start]. */
    fun importJson(text: String) {
        imported = parse(text)
        log("imported ${imported.size} packet(s)")
        emitDiagnostics()
    }

    /** Serialise everything buffered via [advertise] (plus any [extra]) to JSON. */
    fun exportJson(extra: List<ByteArray> = emptyList()): String {
        val all = outgoing + extra
        val body = all.joinToString(",") { "\"${it.toHex()}\"" }
        return """{"v":1,"packets":[$body]}"""
    }

    companion object {
        fun parse(text: String): List<ByteArray> =
            HEX_TOKEN.findAll(text)
                .map { it.groupValues[1].hexToBytes() }
                .filter { it.size == PACKET_SIZE_BYTES }
                .toList()

        private val HEX_TOKEN = Regex("\"([0-9a-fA-F]{${PACKET_SIZE_BYTES * 2}})\"")
    }
}
