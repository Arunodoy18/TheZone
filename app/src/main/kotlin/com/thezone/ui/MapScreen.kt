package com.thezone.ui

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thezone.core.CellLoss
import com.thezone.core.GridCell
import com.thezone.core.SilenceState
import com.thezone.transport.TransportController
import com.thezone.ui.theme.Zone

/**
 * Map / EOC (PRD §3 C). Projector-facing. The one screen where the USP must be
 * visible: a cell where every device went silent at once must NOT look like a
 * cell that never had coverage. Empty is flat and recessive; CELL_LOSS is a
 * bright, hatched, labelled hole with a timestamp. A time scrubber replays the
 * event so you can watch the hole open.
 */
private class CellState(var devices: Int = 0, var severitySum: Int = 0, var silent: Int = 0) {
    var confidence: Double = 1.0
    val avgSeverity: Int get() = if (devices == 0) 0 else severitySum / devices
}

@Composable
fun MapScreen() {
    transportTick()

    val cellByDevice = TransportController.silenceDevices.associate { it.deviceIdHex to it.cell }
    val allLosses: List<CellLoss> = TransportController.cellLosses
    val reports = TransportController.reports
    val scenario = TransportController.simScenario

    // time window for the scrubber
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
    // fold silence state in (live only — historical per-device state isn't reconstructed)
    if (live) {
        TransportController.silenceDevices.forEach { d ->
            val cell = d.cell ?: fallbackCell(d.deviceIdHex)
            if (d.state == SilenceState.UNEXPECTED_SILENCE) cells[cell]?.let { it.silent++ }
        }
    }
    val losses: Map<GridCell, CellLoss> = allLosses
        .filter { live || it.firstSilentAtMillis <= tAt }
        .associateBy { it.cell }

    val pulse by rememberInfiniteTransition(label = "cl").animateFloat(
        0.30f, 1f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "p",
    )

    Column(Modifier.fillMaxSize().background(Zone.ink).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SEVERITY MAP", color = Zone.bone, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                if (live) "LIVE" else "REPLAY ${clock(tAt)}",
                color = if (live) Zone.signal else Zone.amber,
                fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            buildString {
                append("${cells.size} cells · ${losses.size} collapsed")
                if (scenario != null) append("  ·  toll ${scenario.second}  ·  ${(scenario.first * 100).toInt()}%")
            },
            color = Zone.boneDim, fontSize = 14.sp,
        )
        if (TransportController.nearDamage) {
            Text(
                "NETWORK ALERT — nearby nodes pinned to max reach",
                color = Zone.alarm, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Zone.inkSoft),
        ) {
            if (cells.isEmpty()) {
                Text("No signals yet.", color = Zone.boneDim, fontSize = 18.sp, modifier = Modifier.align(Alignment.Center))
            } else {
                MapGrid(cells, losses, pulse)
            }
        }

        Spacer(Modifier.height(10.dp))
        Legend()

        // scrubber only once there's a meaningful stretch of history to replay
        if (tMax - tMin > 30_000L) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (live) "replay ‹" else "‹ live", color = Zone.boneFaint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
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
            }
            Row(Modifier.fillMaxWidth()) {
                Text(clock(tMin), color = Zone.boneFaint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                Text("now", color = Zone.boneFaint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun MapGrid(cells: Map<GridCell, CellState>, losses: Map<GridCell, CellLoss>, pulse: Float) {
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
                loss != null -> drawCellLoss(tl, sz, pulse)
                st != null -> drawActiveCell(tl, sz, st)
                else -> drawEmptyCell(tl, sz)
            }
            if (loss != null) label("${loss.deviceCount} dark", clock(loss.lastSilentAtMillis), tl, sz, Zone.bone)
            else if (st != null && st.devices > 0) label("${st.devices}", null, tl, sz, Zone.ink)
        }
    }
}

private fun DrawScope.drawEmptyCell(tl: Offset, sz: Size) {
    drawRoundRect(Zone.ink, tl, sz, CornerRadius(6f))
    drawRoundRect(Zone.inkLine, tl, sz, CornerRadius(6f), style = Stroke(width = 1f))
}

private fun DrawScope.drawActiveCell(tl: Offset, sz: Size, st: CellState) {
    // confidence drives opacity — but a cell with data is always clearly visible;
    // low confidence reads through the dashed outline, not near-invisibility.
    val a = (0.55f + 0.4f * st.confidence.toFloat()).coerceIn(0.55f, 0.95f)
    drawRoundRect(Zone.severity(st.avgSeverity).copy(alpha = a), tl, sz, CornerRadius(6f))
    if (st.confidence < 0.4) {
        // dashed outline = "unconfirmed"
        var d = 0f
        while (d < sz.width) {
            drawLine(Zone.boneDim, Offset(tl.x + d, tl.y), Offset(tl.x + minOf(d + 6f, sz.width), tl.y), strokeWidth = 1.5f)
            drawLine(Zone.boneDim, Offset(tl.x + d, tl.y + sz.height), Offset(tl.x + minOf(d + 6f, sz.width), tl.y + sz.height), strokeWidth = 1.5f)
            d += 12f
        }
    }
    if (st.silent > 0) drawRoundRect(Zone.alarm, tl, Size(sz.width, 5f))
}

private fun DrawScope.drawCellLoss(tl: Offset, sz: Size, pulse: Float) {
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
        LegendItem(Zone.inkLine, "no data")
        LegendItem(Zone.severity(4), "low")
        LegendItem(Zone.severity(14), "high")
        LegendItem(Zone.severity(14).copy(alpha = 0.35f), "unconfirmed")
        LegendItem(Zone.bone, "collapsed")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(color))
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
