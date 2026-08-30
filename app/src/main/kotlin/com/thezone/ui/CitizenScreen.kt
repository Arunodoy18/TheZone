package com.thezone.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thezone.demo.DebugOverrides
import com.thezone.demo.UserStatus
import com.thezone.packet.Status
import com.thezone.transport.TransportController
import com.thezone.ui.theme.Zone

/**
 * Citizen (PRD §3 A). Trapped, in the dark, on a dying phone. One line of proof,
 * one number, three optional buttons — nothing else. Dark by default. A slow
 * sonar ping behind the number is the only motion: it says the app is alive even
 * when the person can't touch it.
 */
@Composable
fun CitizenScreen() {
    transportTick()
    val peers = TransportController.peersHeard
    var status by remember { mutableStateOf(UserStatus.code) }

    val ping by rememberInfiniteTransition(label = "ping").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200), RepeatMode.Restart),
        label = "ping",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Zone.ink),
    ) {
        // sonar rings, very faint, expanding out
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * 0.40f)
            val maxR = size.minDimension * 0.62f
            for (k in 0..2) {
                val p = ((ping + k / 3f) % 1f)
                drawCircle(
                    color = Zone.signal.copy(alpha = (1f - p) * 0.16f),
                    radius = maxR * p,
                    center = c,
                    style = Stroke(width = 2f),
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "You are being heard",
                color = if (peers > 0) Zone.signal else Zone.bone.copy(alpha = 0.55f),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 40.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "$peers",
                color = if (peers > 0) Zone.signal else Zone.boneFaint,
                fontSize = 132.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = when (peers) {
                    0 -> "reaching for a nearby phone"
                    1 -> "device is carrying your signal"
                    else -> "devices are carrying your signal"
                },
                color = Zone.boneDim,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
            )
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusButton("Trapped", Status.TRAPPED_DEBRIS.code, status) { s -> status = toggle(status, s); UserStatus.code = status }
            StatusButton("Water\nrising", Status.RISING_WATER.code, status) { s -> status = toggle(status, s); UserStatus.code = status }
            StatusButton("Safe", Status.SAFE.code, status) { s -> status = toggle(status, s); UserStatus.code = status }
        }

        // hidden: long-press the bottom-left corner to cycle the fake battery level
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .size(72.dp)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { cycleBatteryOverride() }) },
        )
    }
}

private fun toggle(current: Int?, tapped: Int): Int? = if (current == tapped) null else tapped

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatusButton(
    label: String,
    code: Int,
    selected: Int?,
    onTap: (Int) -> Unit,
) {
    val on = selected == code
    Box(
        Modifier
            .weight(1f)
            .height(84.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) Zone.signal else Zone.inkSoft)
            .pointerInput(code) { detectTapGestures(onTap = { onTap(code) }) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (on) Zone.ink else Zone.bone,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )
    }
}

private val batterySteps = listOf<Int?>(null, 90, 55, 25, 8)

private fun cycleBatteryOverride() {
    val i = batterySteps.indexOf(DebugOverrides.batteryPercentOverride).let { if (it < 0) 0 else it }
    DebugOverrides.batteryPercentOverride = batterySteps[(i + 1) % batterySteps.size]
}
