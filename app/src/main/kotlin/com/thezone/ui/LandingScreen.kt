package com.thezone.ui

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thezone.ui.theme.Zone

/**
 * The front door, shown on every cold start before the mode router. The website
 * landing page has no equivalent inside the app otherwise — this is it: the
 * wordmark, the one line, and a way in. Tap anywhere or the button to continue.
 */
@Composable
fun LandingScreen(onEnter: () -> Unit) {
    val t = rememberInfiniteTransition(label = "landing")
    val ping by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "ping",
    )
    val sweep by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Zone.ink)
            .pointerInput(Unit) { detectTapGestures(onTap = { onEnter() }) },
    ) {
        // sonar: a slow rotating wedge + expanding range rings
        Canvas(
            Modifier
                .fillMaxSize()
                .rotate(sweep),
        ) {
            val c = Offset(size.width / 2f, size.height * 0.42f)
            drawCircle(
                brush = Brush.sweepGradient(
                    0f to Zone.signal.copy(alpha = 0f),
                    0.06f to Zone.signal.copy(alpha = 0.10f),
                    0.14f to Zone.signal.copy(alpha = 0f),
                    1f to Zone.signal.copy(alpha = 0f),
                    center = c,
                ),
                radius = size.maxDimension,
                center = c,
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * 0.42f)
            val maxR = size.minDimension * 0.62f
            for (k in 0..2) {
                val p = ((ping + k / 3f) % 1f)
                drawCircle(
                    color = Zone.signal.copy(alpha = (1f - p) * 0.13f),
                    radius = maxR * p,
                    center = c,
                    style = Stroke(width = 2f),
                )
            }
            for (r in listOf(0.30f, 0.55f, 0.82f)) {
                drawCircle(Zone.inkLine, maxR * r, c, style = Stroke(width = 1f))
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "GROUND-ZERO BLACKOUT · INFORMATION FOG",
                color = Zone.boneDim, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp, fontFamily = Zone.mono, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Row {
                Text("Zone", color = Zone.bone, fontSize = 72.sp, fontWeight = FontWeight.Bold)
                Text(".", color = Zone.signal, fontSize = 72.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "The network that works when every network is gone.",
                color = Zone.bone, fontSize = 20.sp, fontWeight = FontWeight.Medium,
                lineHeight = 27.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Phones relay a 31-byte distress signal hand to hand, out of the disaster zone. No towers, no internet.",
                color = Zone.boneDim, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LangPicker()
            Spacer(Modifier.height(12.dp))
            Text(
                "OFFLINE · NO SERVERS · 31 BYTES · AIRPLANE-MODE NATIVE",
                color = Zone.boneFaint, fontSize = 9.sp, fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp, fontFamily = Zone.mono, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Zone.signal)
                    .pointerInput(Unit) { detectTapGestures(onTap = { onEnter() }) }
                    .padding(vertical = 17.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Enter", color = Zone.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Language for the Citizen screen — the one a local victim reads. */
@Composable
private fun LangPicker() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var current by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(com.thezone.config.LangStore.tag(context) ?: "system")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        com.thezone.config.LangStore.options.forEach { (tag, label) ->
            val on = tag == current
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) Zone.signal else Zone.inkSoft)
                    .pointerInput(tag) {
                        detectTapGestures(onTap = {
                            com.thezone.config.LangStore.set(context, tag)
                            current = tag
                        })
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(label, color = if (on) Zone.ink else Zone.boneDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
