package com.thezone.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Responder (PRD §3 B). Gloves, rain, sunlight, one hand. Big rank-numbered rows,
 * max contrast, whole-row tap targets, sorted by triage priority not time.
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
    val silent = rows.count { it.silence == SilenceState.UNEXPECTED_SILENCE }
    val rising = rows.count { it.status == Status.RISING_WATER.code && it.altTrend > 0 }

    Column(
        Modifier
            .fillMaxSize()
            .background(Zone.paper),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Zone.paperInk)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("HEARD", color = Zone.paper, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text("${rows.size}", color = Zone.signal, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Zone.mono)
            Spacer(Modifier.weight(1f))
            val reached = TransportController.resolvedCount
            if (rising > 0) Tag("$rising RISING", Zone.alarm, Zone.paper)
            if (silent > 0) { Spacer(Modifier.width(8.dp)); Tag("$silent SILENT", Zone.alarmDeep, Zone.paper) }
            if (reached > 0) { Spacer(Modifier.width(8.dp)); Tag("$reached REACHED", Zone.calm, Zone.paper) }
        }

        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No signals in range.\nMove toward the structure.",
                    color = Zone.paperDim, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(rows.size, key = { rows[it].deviceIdHex }) { i ->
                ResponderRow(
                    rank = i + 1,
                    e = rows[i],
                    now = now,
                    modifier = Modifier.animateItem(
                        placementSpec = tween(420, easing = FastOutSlowInEasing),
                    ),
                ) { digTarget = rows[i].deviceIdHex }
            }
        }
    }
}

@Composable
private fun Tag(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 9.dp, vertical = 5.dp)) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ResponderRow(
    rank: Int,
    e: TriageEntry,
    now: Long,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Zone.paperPanel)
            .clickable(onClick = onTap)
            .height(112.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // rank + severity spine
        Box(
            Modifier.fillMaxHeight().width(52.dp).background(Zone.severity(e.severity)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$rank", color = Zone.paper, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = Zone.mono)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(statusLabel(e.status), color = Zone.paperInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                buildString {
                    append("${altLabel(e)}   ${agoLabel(now - e.lastHeardAtMillis)}   ${e.batteryPercent}%   ${e.hopsFromOrigin} hop")
                    if (e.casualties > 0) append("   " + (if (e.casualties >= 15) "15+" else e.casualties.toString()) + " ppl")
                },
                color = Zone.paperDim, fontSize = 14.sp,
            )
            Text(TriageScorer.reason(e, now).uppercase(), color = Zone.calm, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        SilenceBlock(e.silence)
        Spacer(Modifier.width(10.dp))
    }
}

@Composable
private fun SilenceBlock(state: SilenceState) {
    val (bg, text) = when (state) {
        SilenceState.ALIVE -> Zone.calm to "LIVE"
        SilenceState.OVERDUE -> Zone.amber to "OVERDUE"
        SilenceState.EXPECTED_SILENCE -> Zone.paperLine to "EXPECTED"
        SilenceState.UNEXPECTED_SILENCE -> Zone.alarmDeep to "SILENT"
    }
    val fg = if (state == SilenceState.EXPECTED_SILENCE) Zone.paperInk else Zone.paper
    Box(
        Modifier.width(104.dp).fillMaxHeight(0.78f).clip(RoundedCornerShape(10.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** Dig Here (PRD §3 B). The bar owns the screen — a metal detector, not a map. */
@Composable
fun DigHereScreen(deviceIdHex: String, onBack: () -> Unit) {
    transportTick()
    val entry = TransportController.triageEntries().firstOrNull { it.deviceIdHex == deviceIdHex }
    val rssi = entry?.lastRssiDbm ?: -100
    val heardAgoMs = entry?.let { System.currentTimeMillis() - it.lastHeardAtMillis } ?: Long.MAX_VALUE

    val rawFill = ((rssi + 95f) / 50f).coerceIn(0f, 1f)
    val fill by animateFloatAsState(rawFill, label = "dig-fill")
    val metres = estimateMetres(rssi)
    val (linkText, linkColor) = when {
        heardAgoMs < 3_000 -> "LINK OK" to Zone.calm
        heardAgoMs < 10_000 -> "MARGINAL" to Zone.amber
        else -> "LINK LOST" to Zone.alarmDeep
    }
    val word = when {
        fill > 0.82f -> "VERY CLOSE"
        fill > 0.55f -> "CLOSE"
        fill > 0.28f -> "WARMER"
        else -> "FAR"
    }

    // a physical tick each time you cross a proximity band — eyes can stay on the rubble
    val view = LocalView.current
    var prevWord by remember { mutableStateOf(word) }
    LaunchedEffect(word) {
        if (word != prevWord) {
            view.performHapticFeedback(
                if (word == "VERY CLOSE") HapticFeedbackConstants.LONG_PRESS
                else HapticFeedbackConstants.CONTEXT_CLICK,
            )
            prevWord = word
        }
    }

    val fillColor = if (fill > 0.55f) Zone.alarm else Zone.amber

    Column(
        Modifier.fillMaxSize().background(Zone.paper).padding(20.dp),
    ) {
        Text("‹ BACK", color = Zone.paperInk, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onBack).padding(vertical = 6.dp))

        // readout — always at the top, so the layout is full at any fill level
        Spacer(Modifier.height(10.dp))
        Text(word, color = Zone.paperInk, fontSize = 52.sp, fontWeight = FontWeight.Bold)
        Text("≈ $metres m", color = fillColor, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Zone.mono)
        Text("$linkText  ·  $rssi dBm  ·  heard ${(heardAgoMs / 1000).coerceAtMost(999)}s ago",
            color = linkColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("dev ${deviceIdHex.take(12)}", color = Zone.paperDim, fontFamily = Zone.mono, fontSize = 12.sp)

        Spacer(Modifier.height(16.dp))

        // the channel — a framed track, always visible; fill rises from the bottom
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Zone.paperPanel)
                .border(2.dp, Zone.paperLine, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                repeat(5) { Box(Modifier.fillMaxWidth().height(1.dp).background(Zone.paperLine)) }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fill.coerceAtLeast(0.014f))
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp, topStart = 6.dp, topEnd = 6.dp))
                    .background(fillColor),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(Modifier.fillMaxWidth().height(4.dp).background(Zone.paperInk.copy(alpha = 0.4f)))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Walk toward the signal — the channel fills as you close in.",
            color = Zone.paperDim, fontSize = 13.sp)

        val context = androidx.compose.ui.platform.LocalContext.current
        if (TransportController.canResolve(context)) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Zone.calm)
                    .clickable {
                        if (TransportController.markResolved(context, deviceIdHex)) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onBack()
                        }
                    }
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("MARK REACHED", color = Zone.paper, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun estimateMetres(rssi: Int): Int {
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
    val arrow = when { e.altTrend > 0 -> "↑"; e.altTrend < 0 -> "↓"; else -> "" }
    val sign = if (e.altDelta > 0) "+" else ""
    return "alt $sign${e.altDelta}m$arrow"
}

private fun agoLabel(ms: Long): String {
    val s = ms / 1000
    return if (s < 90) "${s}s" else "${s / 60}m"
}
