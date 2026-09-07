package com.thezone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thezone.notify.AlertActivity
import com.thezone.packet.AlertText
import com.thezone.packet.PacketCodec
import com.thezone.transport.TransportController

/** Shows the most severe active mesh alert. Renders nothing when there are none. */
@Composable
fun AlertBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val alert = TransportController.activeAlerts().firstOrNull() ?: return
    val bg = when {
        alert.category >= PacketCodec.ALERT_EXTREME -> Color(0xFF7A1206)
        alert.category >= PacketCodec.ALERT_WARNING -> Color(0xFFB5311B)
        alert.category >= PacketCodec.ALERT_WATCH -> Color(0xFFC77A16)
        else -> Color(0xFF2E7D8A)
    }
    val leftMin = ((alert.expiresAtMillis - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .pointerInput(alert.contentIdHex) {
                detectTapGestures(onTap = {
                    runCatching { context.startActivity(AlertActivity.intent(context, alert)) }
                })
            }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${AlertText.categoryName(alert.category)} · ${AlertText.label(alert.phraseCode)}",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                AlertText.full(alert.phraseCode),
                color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, maxLines = 2,
            )
        }
        Text("${leftMin}m", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
