package com.thezone.transport

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared bookkeeping for the concrete transports: the single packet callback, the
 * diagnostics callback, a capped wall-clock log, and counters. Not part of the
 * public boundary — [ReportTransport] is.
 */
abstract class BaseTransport(final override val kind: String) : ReportTransport {

    private val lock = Any()
    private val logLines = ArrayDeque<String>()
    private var packetCallback: ((InboundPacket) -> Unit)? = null
    private var diagnosticsCallback: ((TransportDiagnostics) -> Unit)? = null

    @Volatile
    protected var running = false
    @Volatile
    protected var advertising = false
    @Volatile
    protected var scanning = false
    @Volatile
    protected var advertisedHex: String? = null
    @Volatile
    protected var legacyFallbackActive = false
    @Volatile
    protected var codedPhyActive = false
    @Volatile
    protected var oneMPhyActive = false
    @Volatile
    protected var lastError: String? = null

    private var sent = 0L
    private var received = 0L

    override fun onPacket(callback: (InboundPacket) -> Unit) {
        synchronized(lock) { packetCallback = callback }
    }

    override fun onDiagnostics(callback: (TransportDiagnostics) -> Unit) {
        synchronized(lock) { diagnosticsCallback = callback }
        emitDiagnostics()
    }

    override val diagnostics: TransportDiagnostics
        get() = snapshot()

    /** Deliver an inbound packet to the sink and bump the counter. */
    protected fun deliver(packet: InboundPacket) {
        val cb = synchronized(lock) {
            received++
            packetCallback
        }
        cb?.invoke(packet)
        emitDiagnostics()
    }

    protected fun countSent() {
        synchronized(lock) { sent++ }
        emitDiagnostics()
    }

    /** Append a timestamped line, mirror it to logcat, and emit fresh diagnostics. */
    protected fun log(line: String) {
        synchronized(lock) {
            logLines.addLast("${timestamp()}  $line")
            while (logLines.size > MAX_LOG_LINES) logLines.removeFirst()
        }
        Log.d(LOG_TAG, "[$kind] $line")
        emitDiagnostics()
    }

    protected fun fail(message: String) {
        lastError = message
        Log.w(LOG_TAG, "[$kind] ERROR: $message")
        log("ERROR: $message")
    }

    protected fun emitDiagnostics() {
        val (snapshot, cb) = synchronized(lock) { snapshot() to diagnosticsCallback }
        cb?.invoke(snapshot)
    }

    private fun snapshot(): TransportDiagnostics = synchronized(lock) {
        TransportDiagnostics(
            kind = kind,
            running = running,
            advertising = advertising,
            scanning = scanning,
            advertisedHex = advertisedHex,
            legacyFallbackActive = legacyFallbackActive,
            codedPhyActive = codedPhyActive,
            oneMPhyActive = oneMPhyActive,
            packetsSent = sent,
            packetsReceived = received,
            lastError = lastError,
            log = logLines.toList(),
        )
    }

    protected fun requirePacketSize(bytes: ByteArray?) {
        require(bytes == null || bytes.size == PACKET_SIZE_BYTES) {
            "advertise() needs a $PACKET_SIZE_BYTES-byte packet, got ${bytes?.size}"
        }
    }

    private fun timestamp(): String = TIME_FORMAT.get()!!.format(Date())

    companion object {
        const val PACKET_SIZE_BYTES = 31

        /** Company ID for manufacturer-specific data (0xFFFF is demo-reserved). */
        const val COMPANY_ID = 0xFFFF

        /** `adb logcat -s TheZone` to follow every transport line on-device. */
        const val LOG_TAG = "TheZone"

        private const val MAX_LOG_LINES = 40

        private val TIME_FORMAT =
            object : ThreadLocal<SimpleDateFormat>() {
                override fun initialValue() =
                    SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
            }
    }
}

/** Lowercase hex, no separators. */
fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** Parse lowercase/uppercase hex, ignoring whitespace. */
fun String.hexToBytes(): ByteArray {
    val clean = filter { !it.isWhitespace() }
    require(clean.length % 2 == 0) { "odd hex length: ${clean.length}" }
    return ByteArray(clean.length / 2) {
        clean.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}
