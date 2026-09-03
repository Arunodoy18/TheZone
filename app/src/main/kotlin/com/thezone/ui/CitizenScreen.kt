package com.thezone.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
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
 * one number, three optional buttons — nothing else. Dark by default.
 *
 * Motion here is not decoration: a slow sonar ping says the app is still alive
 * when the person can't touch it, and the count kicks + the rings flash the
 * instant another phone picks up the signal — the one moment that matters.
 */
@Composable
fun CitizenScreen() {
    transportTick()
    val peers = TransportController.peersHeard
    val view = LocalView.current
    val s = citizenStrings()
    var status by remember { mutableStateOf(UserStatus.code) }

    // --- the "someone just heard you" moment -------------------------------
    var prevPeers by remember { mutableIntStateOf(peers) }
    val kick = remember { Animatable(1f) }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(peers) {
        if (peers > prevPeers) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            flash.snapTo(1f)
            kick.snapTo(0.86f)
            kick.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 320f))
        }
        prevPeers = peers
        flash.animateTo(0f, tween(1600))
    }

    val ping by rememberInfiniteTransition(label = "ping").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200), RepeatMode.Restart),
        label = "ping",
    )
    // headline colour eases between states instead of snapping
    val headline by animateFloatAsState(if (peers > 0) 1f else 0f, tween(600), label = "headline")

    Box(
        Modifier
            .fillMaxSize()
            .background(Zone.ink),
    ) {
        // sonar rings, very faint, expanding out — brighter for a beat on a new peer
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * 0.40f)
            val maxR = size.minDimension * 0.62f
            val boost = 1f + flash.value * 2.2f
            for (k in 0..2) {
                val p = ((ping + k / 3f) % 1f)
                drawCircle(
                    color = Zone.signal.copy(alpha = ((1f - p) * 0.16f * boost).coerceAtMost(0.6f)),
                    radius = maxR * p,
                    center = c,
                    style = Stroke(width = 2f + flash.value * 2f),
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
                s.heard,
                color = androidx.compose.ui.graphics.lerp(Zone.bone.copy(alpha = 0.55f), Zone.signal, headline),
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
                fontFamily = Zone.mono,
                modifier = Modifier.scale(kick.value),
            )
            Text(
                text = when (peers) {
                    0 -> s.reaching
                    1 -> s.carryingOne
                    else -> s.carryingMany
                },
                color = Zone.boneDim,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            HeadcountRow(s)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusButton(s.trapped, Status.TRAPPED_DEBRIS.code, status) { c -> status = toggle(status, c); UserStatus.code = status }
                StatusButton(s.waterRising, Status.RISING_WATER.code, status) { c -> status = toggle(status, c); UserStatus.code = status }
                StatusButton(s.safe, Status.SAFE.code, status) { c -> status = toggle(status, c); UserStatus.code = status }
            }
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

/** Optional "how many people are with you" — feeds the packet's casualty count. */
@Composable
private fun HeadcountRow(s: CitizenStrings) {
    val view = LocalView.current
    var n by remember { mutableIntStateOf(com.thezone.demo.SelfReport.headcount) }
    fun set(v: Int) {
        n = v.coerceIn(0, 15)
        com.thezone.demo.SelfReport.headcount = n
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Zone.inkSoft)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            if (n == 0) s.peopleNone else if (n >= 15) s.peopleMax else s.peopleN(n),
            color = Zone.boneDim, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepBtn("–") { set(n - 1) }
            StepBtn("+") { set(n + 1) }
        }
    }
}

@Composable
private fun StepBtn(label: String, onTap: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Zone.inkLine)
            .pointerInput(label) { detectTapGestures(onTap = { onTap() }) },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Zone.bone, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatusButton(
    label: String,
    code: Int,
    selected: Int?,
    onTap: (Int) -> Unit,
) {
    val on = selected == code
    val view = LocalView.current
    val press = remember { Animatable(1f) }
    Box(
        Modifier
            .weight(1f)
            .height(84.dp)
            .scale(press.value)
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) Zone.signal else Zone.inkSoft)
            .pointerInput(code) {
                detectTapGestures(
                    onPress = {
                        press.animateTo(0.94f, tween(80))
                        tryAwaitRelease()
                        press.animateTo(1f, spring(stiffness = 420f))
                    },
                    onTap = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        onTap(code)
                    },
                )
            },
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
