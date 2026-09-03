package com.thezone.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thezone.core.CellLoss
import com.thezone.core.GridCell
import com.thezone.core.SilenceState
import com.thezone.transport.TransportController
import com.thezone.ui.theme.Zone
import kotlin.math.ln

/**
 * Map / EOC (PRD §3 C). Projector-facing, scanned not read. Surface the summary
 * before the detail: a glanceable count band and a "send here first" strip sit
 * above the grid. On the grid, empty is a recessive dot, a confirmed cell is
 * solid colour, and CELL_LOSS is a bright hatched hole with an expanding ripple
 * and a timestamp — it must never read like a cell that never had coverage.
 */
private class CellState(var devices: Int = 0, var severitySum: Int = 0, var silent: Int = 0) {
    var confidence: Double = 1.0
    val avgSeverity: Int get() = if (devices == 0) 0 else severitySum / devices
}

private const val SEVERE = 11
private const val MODERATE = 6

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapScreen() {
    transportTick()

    val cellByDevice = TransportController.silenceDevices.associate { it.deviceIdHex to it.cell }
    val allLosses: List<CellLoss> = TransportController.cellLosses
    // a report a responder has marked reached, and RESOLVE packets themselves, drop off the map
    val reports = TransportController.reports.filterNot {
        TransportController.isResolved(it.contentId) ||
            it.packet.type == com.thezone.packet.PacketCodec.TYPE_RESOLVE
    }
    val scenario = TransportController.simScenario

    val tMin = reports.minOfOrNull { it.firstHeardAtMillis } ?: 0L
    val tMax = System.currentTimeMillis()
    var scrub by remember { mutableFloatStateOf(1f) }
    val live = scrub >= 0.999f || tMax <= tMin
    val tAt = if (live) tMax else (tMin + (scrub.toDouble() * (tMax - tMin)).toLong())

    val confByCell = TransportController.cellConfidence.associateBy { it.cell }

    val cells = HashMap<GridCell, CellState>()
    reports.filterNot { it.isOwn }.forEach { r ->
        if (!live && r.firstHeardAtMillis > tAt) return@forEach
        val dev = r.packet.deviceId.joinToString("") { "%02x".format(it) }
        val cell = cellByDevice[dev] ?: fallbackCell(dev)
        val st = cells.getOrPut(cell) { CellState() }
        st.devices++
        st.severitySum += r.packet.severity
        st.confidence = confByCell[cell]?.confidence ?: 1.0
    }
    if (live) {
        TransportController.silenceDevices.forEach { d ->
            val cell = d.cell ?: fallbackCell(d.deviceIdHex)
            if (d.state == SilenceState.UNEXPECTED_SILENCE) cells[cell]?.let { it.silent++ }
        }
    }
    val losses: Map<GridCell, CellLoss> = allLosses
        .filter { live || it.firstSilentAtMillis <= tAt }
        .associateBy { it.cell }

    val severe = cells.count { it.value.avgSeverity >= SEVERE }
    val moderate = cells.count { it.value.avgSeverity in MODERATE until SEVERE }
    val gaps = coverageGaps(cells.keys, losses.keys)
    val priorities = priorityList(cells, losses)

    val pulse by rememberInfiniteTransition(label = "cl").animateFloat(
        0.30f, 1f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "p",
    )
    val ripple by rememberInfiniteTransition(label = "rp").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1700, easing = LinearEasing)), label = "r",
    )

    Column(Modifier.fillMaxSize().background(Zone.ink).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SEVERITY MAP", color = Zone.bone, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                if (live) "LIVE" else "REPLAY ${clock(tAt)}",
                color = if (live) Zone.signal else Zone.amber,
                fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Zone.mono,
            )
        }
        if (scenario != null) {
            Text(
                "Rasuwa replay · toll ${scenario.second} · ${(scenario.first * 100).toInt()}%",
                color = Zone.boneFaint, fontSize = 11.sp, fontFamily = Zone.mono,
            )
        } else if (live) {
            val age = TransportController.newestReportAgeMillis
            Text(
                "${TransportController.reporterCount} reporters · " +
                    (if (age == Long.MAX_VALUE) "nothing heard yet" else "newest ${age / 1000}s ago"),
                color = Zone.boneFaint, fontSize = 11.sp, fontFamily = Zone.mono,
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("COLLAPSED", losses.size, if (losses.isNotEmpty()) Zone.alarm else Zone.boneDim)
            Stat("SEVERE", severe, if (severe > 0) Zone.amber else Zone.boneDim)
            Stat("MODERATE", moderate, Zone.boneDim)
            Stat("NO EYES", gaps, Zone.boneDim)
        }
        if (TransportController.nearDamage) {
            Spacer(Modifier.height(6.dp))
            Text(
                "▲ NETWORK ALERT — nodes near the collapse pinned to max reach",
                color = Zone.alarm, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Zone.inkSoft),
        ) {
            if (cells.isEmpty() && losses.isEmpty()) {
                Text("No signals yet.", color = Zone.boneDim, fontSize = 18.sp, modifier = Modifier.align(Alignment.Center))
            } else {
                MapGrid(cells, losses, pulse, ripple)
                Text(
                    "N ↑", color = Zone.boneDim, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    fontFamily = Zone.mono,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                )
            }
        }

        if (priorities.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("SEND HERE FIRST", color = Zone.boneDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 16.dp),
            ) {
                items(priorities) { p -> PriorityCard(p) }
            }
        }

        Spacer(Modifier.height(10.dp))
        Legend()

        if (tMax - tMin > 30_000L) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (live) "replay ‹" else "‹ live", color = Zone.boneFaint, fontSize = 11.sp, fontFamily = Zone.mono)
                Slider(
                    value = scrub,
                    onValueChange = { scrub = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Zone.signal,
                        activeTrackColor = Zone.boneFaint,
                        inactiveTrackColor = Zone.inkLine,
                    ),
                )
                Text(if (live) "now" else clock(tAt), color = Zone.boneFaint, fontSize = 11.sp, fontFamily = Zone.mono)
            }
        }
    }
}

// --- summary band -------------------------------------------------------------

@Composable
private fun Stat(label: String, value: Int, color: Color) {
    Column {
        Text("$value", color = color, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = Zone.mono)
        Text(label, color = Zone.boneDim, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

// --- "send here first" -------------------------------------------------------

private data class Priority(
    val cell: GridCell,
    val eyebrow: String,
    val eyebrowColor: Color,
    val line: String,
)

private fun priorityList(cells: Map<GridCell, CellState>, losses: Map<GridCell, CellLoss>): List<Priority> {
    val fromLoss = losses.values
        .sortedByDescending { it.lastSilentAtMillis }
        .map {
            Priority(it.cell, "COLLAPSED", Zone.alarm,
                "${it.deviceCount} devices dark · ${clock(it.lastSilentAtMillis)}")
        }
    val fromActive = cells.entries
        .filter { it.key !in losses && it.value.avgSeverity >= MODERATE }
        .sortedByDescending { (it.value.avgSeverity / 15.0) * it.value.confidence * (1 + ln(1.0 + it.value.devices)) }
        .map { (cell, st) ->
            val sev = if (st.avgSeverity >= SEVERE) "SEVERE" else "MODERATE"
            val col = if (st.avgSeverity >= SEVERE) Zone.amber else Zone.shoal
            Priority(cell, sev, col,
                "${st.devices} devices · ${(st.confidence * 100).toInt()}% conf")
        }
    return (fromLoss + fromActive).take(6)
}

@Composable
private fun PriorityCard(p: Priority) {
    Column(
        Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Zone.inkSoft)
            .padding(12.dp),
    ) {
        Text(p.eyebrow, color = p.eyebrowColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("cell ${p.cell.latIndex}, ${p.cell.lonIndex}", color = Zone.bone, fontSize = 15.sp,
            fontWeight = FontWeight.Bold, fontFamily = Zone.mono,
            maxLines = 1)
        Text(p.line, color = Zone.boneDim, fontSize = 12.sp, maxLines = 1)
    }
}

/** Empty cells *enclosed* by coverage (>=3 of 4 neighbours have data) — real holes, not fringe. */
private fun coverageGaps(cells: Set<GridCell>, losses: Set<GridCell>): Int {
    val covered = cells + losses
    if (covered.size < 4) return 0
    val minLat = covered.minOf { it.latIndex }; val maxLat = covered.maxOf { it.latIndex }
    val minLon = covered.minOf { it.lonIndex }; val maxLon = covered.maxOf { it.lonIndex }
    var gaps = 0
    for (la in minLat..maxLat) for (lo in minLon..maxLon) {
        val here = GridCell(la, lo)
        if (here in covered) continue
        val around = listOf(GridCell(la + 1, lo), GridCell(la - 1, lo), GridCell(la, lo + 1), GridCell(la, lo - 1))
            .count { it in covered }
        if (around >= 3) gaps++
    }
    return gaps
}

// --- grid ------------------------------------------------------------------

@Composable
private fun MapGrid(
    cells: Map<GridCell, CellState>,
    losses: Map<GridCell, CellLoss>,
    pulse: Float,
    ripple: Float,
) {
    val keys = cells.keys + losses.keys
    val minLat = keys.minOf { it.latIndex } - 1
    val maxLat = keys.maxOf { it.latIndex } + 1
    val minLon = keys.minOf { it.lonIndex } - 1
    val maxLon = keys.maxOf { it.lonIndex } + 1
    val rows = (maxLat - minLat + 1).coerceIn(1, 40)
    val cols = (maxLon - minLon + 1).coerceIn(1, 40)

    Canvas(Modifier.fillMaxSize().padding(10.dp)) {
        val cw = size.width / cols
        val ch = size.height / rows
        val gap = minOf(cw, ch) * 0.10f

        for (r in 0 until rows) for (c in 0 until cols) {
            val cell = GridCell(minLat + r, minLon + c)
            val x = c * cw
            val y = (rows - 1 - r) * ch // north up
            val tl = Offset(x + gap, y + gap)
            val sz = Size(cw - gap * 2, ch - gap * 2)
            val loss = losses[cell]
            val st = cells[cell]
            when {
                loss != null -> drawCellLoss(tl, sz, pulse, ripple)
                st != null -> drawActiveCell(tl, sz, st)
                else -> drawEmptyCell(tl, sz)
            }
            if (loss != null) label("${loss.deviceCount} dark", clock(loss.lastSilentAtMillis), tl, sz, Zone.bone)
            else if (st != null && st.devices > 0) label("${st.devices}", null, tl, sz, Zone.ink)
        }
    }
}

private fun DrawScope.drawEmptyCell(tl: Offset, sz: Size) {
    // recessive: just a faint dot. No fill, no outline — must not compete.
    drawCircle(Zone.boneFaint.copy(alpha = 0.35f), radius = 1.6f, center = Offset(tl.x + sz.width / 2, tl.y + sz.height / 2))
}

private fun DrawScope.drawActiveCell(tl: Offset, sz: Size, st: CellState) {
    val a = (0.55f + 0.4f * st.confidence.toFloat()).coerceIn(0.55f, 0.95f)
    drawRoundRect(Zone.severity(st.avgSeverity).copy(alpha = a), tl, sz, CornerRadius(6f))
    if (st.confidence < 0.4) {
        var d = 0f
        while (d < sz.width) {
            drawLine(Zone.boneDim, Offset(tl.x + d, tl.y), Offset(tl.x + minOf(d + 6f, sz.width), tl.y), strokeWidth = 1.5f)
            drawLine(Zone.boneDim, Offset(tl.x + d, tl.y + sz.height), Offset(tl.x + minOf(d + 6f, sz.width), tl.y + sz.height), strokeWidth = 1.5f)
            d += 12f
        }
    }
    if (st.silent > 0) drawRoundRect(Zone.alarm, tl, Size(sz.width, 5f))
}

private fun DrawScope.drawCellLoss(tl: Offset, sz: Size, pulse: Float, ripple: Float) {
    // expanding ripple from the hole — drawn first so the frame sits on top
    val cx = tl.x + sz.width / 2
    val cy = tl.y + sz.height / 2
    val r0 = maxOf(sz.width, sz.height) / 2f
    drawCircle(
        Zone.alarm.copy(alpha = (1f - ripple) * 0.5f),
        radius = r0 + ripple * r0 * 3f,
        center = Offset(cx, cy),
        style = Stroke(width = 2.5f),
    )

    drawRoundRect(Zone.ink, tl, sz, CornerRadius(4f))
    clipRect(tl.x, tl.y, tl.x + sz.width, tl.y + sz.height) {
        var d = -sz.height
        while (d < sz.width) {
            drawLine(Zone.alarm.copy(alpha = 0.6f),
                Offset(tl.x + d, tl.y + sz.height), Offset(tl.x + d + sz.height, tl.y), strokeWidth = 2f)
            d += 14f
        }
    }
    drawRoundRect(Zone.bone.copy(alpha = pulse), tl, sz, CornerRadius(4f), style = Stroke(width = 3f))
}

private fun DrawScope.label(text: String, sub: String?, tl: Offset, sz: Size, color: Color) {
    drawContext.canvas.nativeCanvas.apply {
        val ts = minOf(sz.width, sz.height) * (if (sub == null) 0.30f else 0.19f)
        val p = android.graphics.Paint().apply {
            this.color = color.toArgb(); textSize = ts; isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val cx = tl.x + sz.width / 2; val cy = tl.y + sz.height / 2
        if (sub == null) drawText(text, cx, cy + ts / 3, p)
        else { drawText(text, cx, cy - ts * 0.1f, p); drawText(sub, cx, cy + ts * 1.15f, p) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LegendItem(Zone.boneFaint, "no eyes")
        LegendItem(Zone.severity(3), "light")
        LegendItem(Zone.severity(9), "moderate")
        LegendItem(Zone.severity(14), "severe")
        LegendItem(Zone.severity(14).copy(alpha = 0.35f), "unconfirmed")
        LegendItem(Zone.bone, "confirmed collapse")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(13.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.size(6.dp))
        Text(label, color = Zone.boneDim, fontSize = 12.sp)
    }
}

/** Devices with no GPS fix still land on the grid so a live demo isn't blank. */
private fun fallbackCell(deviceIdHex: String): GridCell {
    val h = deviceIdHex.hashCode()
    return GridCell(((h ushr 8) and 0x7) - 3, (h and 0x7) - 3)
}

private val hhmm = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
private fun clock(millis: Long): String = hhmm.format(java.util.Date(millis))
