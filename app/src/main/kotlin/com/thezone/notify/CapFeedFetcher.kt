package com.thezone.notify

import android.content.Context
import android.util.Log
import com.thezone.cap.CapAlert
import com.thezone.config.CapFeedConfig
import com.thezone.config.IncidentConfig
import com.thezone.transport.TransportController
import java.net.HttpURLConnection
import java.net.URL

/**
 * The scoped, honest version of "live feed for someone in the blackout":
 * a provisioned authority/responder phone that briefly catches any signal —
 * walking to the edge of the disaster zone, a flicker of coverage — pulls
 * whatever official CAP alert is configured and carries it back into the
 * mesh via [TransportController.issueFromCap], same pipeline as a manual
 * file import. This is the one deliberately isolated place in the app that
 * makes a live network call (CLAUDE.md rule 4): off by default, needs an
 * explicit URL the user typed in, and gated behind the same provisioned-key
 * trust boundary every other ALERT-issuing path already uses — a phone
 * without the key can enable this and it will simply never broadcast
 * anything, because [TransportController.issueFromCap] itself requires it.
 *
 * Deliberately does NOT reach every citizen phone with a live feed — that
 * would mean either an unauthenticated alert path (a real trust problem) or
 * pretending offline phones can fetch a URL they have no signal for. This
 * reaches exactly as far as the existing, already-vetted ALERT trust model
 * already reaches, just automated at the moment signal returns instead of
 * needing a manual file import.
 */
object CapFeedFetcher {

    private const val TAG = "TheZone"
    private const val RETRY_INTERVAL_MS = 2 * 60_000L   // don't hammer a flaky link
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000
    private const val MAX_BODY_BYTES = 64 * 1024          // a CAP alert is a few KB; refuse anything absurd

    @Volatile private var lastAttemptAtMillis = 0L
    @Volatile private var lastCarriedIdentifier: String? = null

    /** One-line human status for the settings screen. */
    @Volatile var lastResult: String = "not tried yet"
        private set

    /** Called from the relay pump; cheap and rate-limited internally when disabled or waiting. */
    fun maybeAttempt(context: Context) {
        val app = context.applicationContext
        if (!CapFeedConfig.enabled(app)) { lastResult = "off"; return }
        val url = CapFeedConfig.feedUrl(app)
        if (url.isBlank()) { lastResult = "no feed URL configured"; return }
        if (IncidentConfig.responderKey(app) == null) {
            lastResult = "no responder key provisioned — nothing to carry it with"
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastAttemptAtMillis < RETRY_INTERVAL_MS) return
        lastAttemptAtMillis = now
        attempt(app, url)
    }

    private fun attempt(context: Context, urlStr: String) {
        val xml = runCatching { fetch(urlStr) }.getOrElse {
            lastResult = "fetch failed: ${it.message}"
            Log.w(TAG, "CAP_FEED fetch failed: ${it.message}")
            return
        }
        val fields = CapAlert.parse(xml)
        if (fields == null) {
            lastResult = "fetched, but not a parseable CAP alert"
            return
        }
        if (fields.identifier == lastCarriedIdentifier) {
            lastResult = "up to date (${fields.identifier})"
            return
        }
        val ok = TransportController.issueFromCap(context, xml)
        if (ok) {
            lastCarriedIdentifier = fields.identifier
            lastResult = "carried ${fields.identifier} into the mesh"
            Log.w(TAG, "CAP_FEED carried id=${fields.identifier} from $urlStr")
        } else {
            lastResult = "fetched ${fields.identifier} but could not carry it (missing key?)"
        }
    }

    private fun fetch(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.requestMethod = "GET"
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            // Bounded read: kotlin's InputStream.readBytes(n) treats n only as
            // a buffer-size hint, not a cap — it reads to EOF regardless, so a
            // huge/hostile response would already be fully in memory before
            // any post-hoc size check. Bail out mid-stream instead.
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            conn.inputStream.use { input ->
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BODY_BYTES) throw java.io.IOException("response too large")
                    out.write(buf, 0, n)
                }
            }
            return out.toString("UTF-8")
        } finally {
            conn.disconnect()
        }
    }
}
