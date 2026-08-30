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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

/**
 * Map / EOC (PRD §3 C). Projector-facing. The one screen where the USP has to be
 * visible: a cell where every device went silent at once must NOT look like a
 * cell that never had coverage. No-data is flat and recessive; CELL_LOSS gets its
 * own visual language — a bright, hatched, labelled hole — and carries a time.
 */
private class CellState(
    var devices: Int = 0,
    var severitySum: Int = 0,
    var silent: Int = 0,
) {
    val avgSeverity: Int get() = if (devices == 0) 0 else severitySum / devices
}

@Composable
fun MapScreen() {
    transportTick()

    val cellByDevice = TransportController.silenceDevices.associate { it.deviceIdHex to it.cell }
    val losses: Map<GridCell, CellLoss> = TransportController.cellLosses.associateBy { it.cell }

    val cells = HashMap<GridCell, CellState>()
    TransportController.triageEntries().forEach { e ->
        val cell = cellByDevice[e.deviceIdHex] ?: fallbackCell(e.deviceIdHex)
        val st = cells.getOrPut(cell) { CellState() }
        st.devices++
        st.severitySum += e.severity
        if (e.silence == SilenceState.UNEXPECTED_SILENCE) st.silent++
    }

    val pulse by rememberInfiniteTransition(label = "cellloss").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Zone.ink)
            .padding(16.dp),
    ) {
        Text("Severity map", color = Zone.bone, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        val scenario = TransportController.simScenario
        Text(
            buildString {
                append("${cells.size} cells · ${losses.size} collapsed")
                if (scenario != null) {
                    append("  ·  toll ${scenario.second}")
                    append("  ·  ${(scenario.first * 100).toInt()}%")
                }
            },
            color = Zone.boneDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Zone.inkSoft),
        ) {
            if (cells.isEmpty()) {
                Text(
                    "No signals yet.",
                    color = Zone.boneDim,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                MapGrid(cells, losses, pulse)
            }
        }

        Spacer(Modifier.height(12.dp))
        Legend()
    }
}

@Composable
private fun MapGrid(
    cells: Map<GridCell, CellState>,
    losses: Map<GridCell, CellLoss>,
    pulse: Float,
) {
    val keys = cells.keys + losses.keys
    val minLat = keys.minOf { it.latIndex } - 1
    val maxLat = keys.maxOf { it.latIndex } + 1
    val minLon = keys.minOf { it.lonIndex } - 1
    val maxLon = keys.maxOf { it.lonIndex } + 1
    val rows = (maxLat - minLat + 1).coerceAtLeast(1)
    val cols = (maxLon - minLon + 1).coerceAtLeast(1)

    Canvas(Modifier.fillMaxSize().padding(8.dp)) {
        val cw = size.width / cols
        val ch = size.height / rows
        val pad = minOf(cw, ch) * 0.06f

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cell = GridCell(minLat + r, minLon + c)
                val x = c * cw
                // draw north (higher lat) at the top
                val y = (rows - 1 - r) * ch
                val topLeft = Offset(x + pad, y + pad)
                val cellSize = Size(cw - pad * 2, ch - pad * 2)

                val loss = losses[cell]
                val state = cells[cell]

                when {
                    loss != null -> drawCellLoss(topLeft, cellSize, pulse)
                    state != null -> drawActiveCell(topLeft, cellSize, state)
                    else -> drawEmptyCell(topLeft, cellSize)
                }

                if (loss != null) {
                    drawLabel(
                        "${loss.deviceCount} dark",
                        clock(loss.lastSilentAtMillis),
                        topLeft, cellSize, Zone.bone,
                    )
                } else if (state != null) {
                    drawLabel("${state.devices}", null, topLeft, cellSize, Zone.ink)
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEmptyCell(
    topLeft: Offset,
    size: Size,
) {
    // recessive: barely-there. Must not compete with a collapse.
    drawRoundRect(
        color = Zone.ink,
        topLeft = topLeft,
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f),
    )
    drawRoundRect(
        color = Zone.inkLine,
        topLeft = topLeft,
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f),
        style = Stroke(width = 1f),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawActiveCell(
    topLeft: Offset,
    size: Size,
    state: CellState,
) {
    val base = Zone.severity(state.avgSeverity)
    drawRoundRect(
        color = base.copy(alpha = 0.85f),
        topLeft = topLeft,
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f),
    )
    if (state.silent > 0) {
        // some silent, not a full collapse — a thin alarm rule along the top
        drawRoundRect(
            color = Zone.alarm,
            topLeft = topLeft,
            size = Size(size.width, 4f),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCellLoss(
    topLeft: Offset,
    size: Size,
    pulse: Float,
) {
    // a hole punched in the map: near-black fill, bright pulsing frame,
    // diagonal hazard hatch. Nothing else on the screen looks like this.
    drawRoundRect(
        color = Zone.ink,
        topLeft = topLeft,
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
    )
    clipRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height) {
        val step = 14f
        var d = -size.height
        while (d < size.width) {
            drawLine(
                color = Zone.alarm.copy(alpha = 0.55f),
                start = Offset(topLeft.x + d, topLeft.y + size.height),
                end = Offset(topLeft.x + d + size.height, topLeft.y),
                strokeWidth = 2f,
            )
            d += step
        }
    }
    drawRoundRect(
        color = Zone.bone.copy(alpha = pulse),
        topLeft = topLeft,
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
        style = Stroke(width = 3f),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(
    text: String,
    subText: String?,
    topLeft: Offset,
    size: Size,
    color: Color,
) {
    drawContext.canvas.nativeCanvas.apply {
        val ts = minOf(size.width, size.height) * (if (subText == null) 0.26f else 0.18f)
        val paint = android.graphics.Paint().apply {
            this.color = color.toArgb()
            textSize = ts
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val cx = topLeft.x + size.width / 2
        val cy = topLeft.y + size.height / 2
        if (subText == null) {
            drawText(text, cx, cy + ts / 3, paint)
        } else {
            drawText(text, cx, cy - ts * 0.15f, paint)
            drawText(subText, cx, cy + ts * 1.1f, paint)
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendItem(Zone.inkLine, "no data")
        LegendItem(Zone.severity(4), "low")
        LegendItem(Zone.severity(14), "high")
        LegendItem(Zone.bone, "CELL_LOSS")
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

/** Devices with no GPS fix still land on the grid so a live 3-phone demo isn't blank. */
private fun fallbackCell(deviceIdHex: String): GridCell {
    val h = deviceIdHex.hashCode()
    return GridCell(((h ushr 8) and 0x3) - 1, (h and 0x3) - 1)
}

private val hhmm = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)

private fun clock(millis: Long): String = hhmm.format(java.util.Date(millis))
