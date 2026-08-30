package com.thezone.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thezone.core.SilenceState
import com.thezone.core.TriageEntry
import com.thezone.core.TriageScorer
import com.thezone.packet.Packet
import com.thezone.packet.Status
import com.thezone.transport.TransportController
import com.thezone.ui.theme.Zone
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Responder (PRD §3 B). Gloves, rain, sunlight, one hand. Big rows, high
 * contrast, no small tap targets. Sorted by triage priority, not time.
 */
@Composable
fun ResponderScreen() {
    transportTick()
    var digTarget by remember { mutableStateOf<String?>(null) }

    val target = digTarget
    if (target != null) {
        DigHereScreen(deviceIdHex = target, onBack = { digTarget = null })
        return
    }

    val now = System.currentTimeMillis()
    val rows = TriageScorer.sort(TransportController.triageEntries(), now)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Zone.paper)
            .padding(horizontal = 12.dp),
    ) {
        Text(
            "Heard devices — ${rows.size}",
            color = Zone.paperInk,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 14.dp),
        )
        if (rows.isEmpty()) {
            Text("No signals in range. Move toward the structure.", color = Zone.paperInk, fontSize = 16.sp)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.deviceIdHex }) { e ->
                ResponderRow(e, now) { digTarget = e.deviceIdHex }
            }
        }
    }
}

@Composable
private fun ResponderRow(e: TriageEntry, now: Long, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Zone.paper)
            .clickable(onClick = onTap)
            .height(88.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(10.dp)
                .background(Zone.severity(e.severity)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                statusLabel(e.status),
                color = Zone.paperInk,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${altLabel(e)}   ·   ${agoLabel(now - e.lastHeardAtMillis)}   ·   bat ${e.batteryPercent}%   ·   ${e.hopsFromOrigin} hop",
                color = Zone.paperInk,
                fontSize = 13.sp,
            )
            Text(
                TriageScorer.reason(e, now),
                color = Zone.calm,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.width(10.dp))
        SilencePill(e.silence)
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun SilencePill(state: SilenceState) {
    val (bg, fg, text) = when (state) {
        SilenceState.ALIVE -> Triple(Zone.calm, Zone.paper, "LIVE")
        SilenceState.OVERDUE -> Triple(Zone.amber, Zone.paperInk, "OVERDUE")
        SilenceState.EXPECTED_SILENCE -> Triple(Zone.paperLine, Zone.paperInk, "EXPECTED")
        SilenceState.UNEXPECTED_SILENCE -> Triple(Zone.alarm, Zone.paper, "SILENT")
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Dig Here (PRD §3 B). A proximity bar, closer to a metal detector than a map. */
@Composable
fun DigHereScreen(deviceIdHex: String, onBack: () -> Unit) {
    transportTick()
    val entry = TransportController.triageEntries().firstOrNull { it.deviceIdHex == deviceIdHex }
    val rssi = entry?.lastRssiDbm ?: -100

    // -95 dBm ≈ empty, -45 dBm ≈ full; smoothed by the animation.
    val rawFill = ((rssi + 95f) / 50f).coerceIn(0f, 1f)
    val fill by animateFloatAsState(rawFill, label = "dig-fill")
    val metres = estimateMetres(rssi)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Zone.paper)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("‹ Back", color = Zone.calm, fontSize = 16.sp, modifier = Modifier
            .align(Alignment.Start)
            .clickable(onClick = onBack)
            .padding(vertical = 8.dp))

        Text(
            "dev ${deviceIdHex.take(12)}",
            color = Zone.paperInk, fontFamily = FontFamily.Monospace, fontSize = 14.sp,
        )
        Text(
            when {
                fill > 0.8f -> "VERY CLOSE"
                fill > 0.5f -> "CLOSE"
                fill > 0.25f -> "GETTING WARMER"
                else -> "FAR"
            },
            color = Zone.paperInk, fontSize = 34.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text("≈ $metres m   ·   $rssi dBm", color = Zone.paperInk, fontSize = 16.sp)

        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .weight(1f)
                .width(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Zone.paperLine),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fill)
                    .background(if (fill > 0.5f) Zone.alarm else Zone.amber),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Walk. The bar rises as you close in.", color = Zone.paperInk, fontSize = 14.sp)
    }
}

private fun estimateMetres(rssi: Int): Int {
    // crude log-distance path loss: -40 dBm at 1 m, n = 2.7
    val d = 10.0.pow((-40.0 - rssi) / (10.0 * 2.7))
    return d.roundToInt().coerceIn(1, 200)
}

private fun statusLabel(code: Int): String = when (code) {
    Status.SAFE.code -> "SAFE"
    Status.TRAPPED_DEBRIS.code -> "TRAPPED"
    Status.RISING_WATER.code -> "RISING WATER"
    Status.INJURED.code -> "INJURED"
    Status.HAVE_RESOURCE.code -> "HAS SUPPLIES"
    Status.RESPONDER.code -> "RESPONDER"
    else -> "UNKNOWN"
}

private fun altLabel(e: TriageEntry): String {
    if (e.altDelta == Packet.NO_BAROMETER) return "alt —"
    val arrow = when {
        e.altTrend > 0 -> " ↑"
        e.altTrend < 0 -> " ↓"
        else -> ""
    }
    val sign = if (e.altDelta > 0) "+" else ""
    return "alt $sign${e.altDelta} m$arrow"
}

private fun agoLabel(ms: Long): String {
    val s = ms / 1000
    return if (s < 90) "${s}s ago" else "${s / 60}m ago"
}
