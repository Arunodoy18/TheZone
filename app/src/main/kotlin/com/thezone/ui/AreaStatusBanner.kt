package com.thezone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thezone.config.IncidentConfig
import com.thezone.core.GridCells
import com.thezone.packet.GeoPosition
import com.thezone.sensors.Position
import com.thezone.transport.TransportController

/**
 * The one piece of the Map screen's severity grid a Citizen actually needs:
 * "did the area around me just go dark." No grid, no colour scale, no legend —
 * just the single most useful fact, in the same silent-unless-relevant banner
 * slot as [BluetoothBanner] / [AlertBanner]. Renders nothing unless this
 * phone's own grid cell (not neighbours — see CLAUDE.md's CELL_LOSS rule:
 * no-data must never look like collapse, so this only speaks when it can
 * actually confirm one) has a detected CELL_LOSS.
 */
@Composable
fun AreaStatusBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fix = Position.snapshot() ?: return
    val deltaLat = GeoPosition.encodeDelta(fix.lat, IncidentConfig.originLat(context))
    val deltaLon = GeoPosition.encodeDelta(fix.lon, IncidentConfig.originLon(context))
    val cell = GridCells.of(deltaLat, deltaLon) ?: return
    val loss = TransportController.cellLosses.firstOrNull { it.cell == cell } ?: return

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF7A1206))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "Nearby area went dark",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                "${loss.silentCount} signals stopped at ${clockLabel(loss.lastSilentAtMillis)}",
                color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp,
            )
        }
    }
}

private val hhmm = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
private fun clockLabel(millis: Long): String = hhmm.format(java.util.Date(millis))
