package com.thezone.ui

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
import androidx.compose.ui.input.pointer.pointerInput
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
 * Citizen (PRD §3 A). The person is trapped, in the dark, on a dying phone. One
 * headline, one number, three optional buttons. Nothing else — no map, no
 * settings, no stats. Dark by default: a bright screen in rubble costs battery
 * and gives away position.
 */
@Composable
fun CitizenScreen() {
    transportTick()
    val peers = TransportController.peersHeard
    var status by remember { mutableStateOf(UserStatus.code) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zone.ink),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "You are being heard",
                color = if (peers > 0) Zone.signal else Zone.bone,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 50.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = when (peers) {
                    0 -> "Searching for a nearby phone"
                    1 -> "1 device carried your signal"
                    else -> "$peers devices carried your signal"
                },
                color = Zone.boneDim,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusButton("Trapped", Status.TRAPPED_DEBRIS.code, status) {
                status = toggle(status, it); UserStatus.code = status
            }
            StatusButton("Water rising", Status.RISING_WATER.code, status) {
                status = toggle(status, it); UserStatus.code = status
            }
            StatusButton("Safe", Status.SAFE.code, status) {
                status = toggle(status, it); UserStatus.code = status
            }
        }

        // Hidden: long-press the bottom-left corner to cycle the fake battery
        // level (BUILD_PLAN — demoing a battery-adaptive system on full phones).
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(64.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { cycleBatteryOverride() })
                },
        )
    }
}

private fun toggle(current: Int?, tapped: Int): Int? = if (current == tapped) null else tapped

@Composable
private fun StatusButton(label: String, code: Int, selected: Int?, onTap: (Int) -> Unit) {
    val on = selected == code
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) Zone.signal else Zone.inkSoft)
            .fillMaxWidth(1f / 3f)
            .height(76.dp)
            .pointerInput(code) { detectTapGestures(onTap = { onTap(code) }) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (on) Zone.ink else Zone.bone,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

private val batterySteps = listOf<Int?>(null, 90, 55, 25, 8)

private fun cycleBatteryOverride() {
    val i = batterySteps.indexOf(DebugOverrides.batteryPercentOverride).let { if (it < 0) 0 else it }
    DebugOverrides.batteryPercentOverride = batterySteps[(i + 1) % batterySteps.size]
}
