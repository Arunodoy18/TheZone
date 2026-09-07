package com.thezone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thezone.packet.AlertText
import com.thezone.transport.TransportController
import com.thezone.ui.theme.Zone

/**
 * Compose sheet for a provisioned authority phone to issue a mesh emergency
 * alert: pick a category, a predefined phrase, a radius and how long it stays
 * valid, then flood it. Only shown when the phone holds the shared key.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IssueAlertSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var category by remember { mutableIntStateOf(3) }   // WARNING
    var phrase by remember { mutableIntStateOf(1) }      // EVACUATE NOW
    var radiusM by remember { mutableIntStateOf(1000) }
    var validMin by remember { mutableIntStateOf(120) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Zone.ink.copy(alpha = 0.97f))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Issue alert", color = Zone.bone, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("‹ close", color = Zone.boneDim, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }.padding(6.dp))
        }
        Spacer(Modifier.height(8.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Label("Category")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertText.categories.forEachIndexed { i, name ->
                    Chip(name, i == category) { category = i }
                }
            }
            Spacer(Modifier.height(14.dp))
            Label("Message")
            AlertText.phrases.forEachIndexed { i, (labelText, full) ->
                if (i == 0) return@forEachIndexed
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (i == phrase) Zone.signal else Zone.inkSoft)
                        .pointerInput(i) { detectTapGestures(onTap = { phrase = i }) }
                        .padding(12.dp),
                ) {
                    Column {
                        Text(labelText, color = if (i == phrase) Zone.ink else Zone.bone,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(full, color = if (i == phrase) Zone.ink.copy(alpha = 0.8f) else Zone.boneDim,
                            fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Label("Radius")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(250, 500, 1000, 2000, 5000).forEach { m ->
                    Chip(if (m >= 1000) "${m / 1000} km" else "$m m", m == radiusM) { radiusM = m }
                }
            }
            Spacer(Modifier.height(14.dp))
            Label("Valid for")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30 to "30 min", 120 to "2 h", 360 to "6 h", 1440 to "24 h").forEach { (min, name) ->
                    Chip(name, min == validMin) { validMin = min }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Zone.alarm)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        TransportController.issueAlert(context, category, phrase, radiusM, validMin)
                        onDismiss()
                    })
                }
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("SEND ALERT TO THE MESH", color = Zone.bone, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Label(t: String) {
    Text(t, color = Zone.boneDim, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Chip(text: String, on: Boolean, onTap: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (on) Zone.signal else Zone.inkSoft)
            .pointerInput(text) { detectTapGestures(onTap = { onTap() }) }
            .padding(horizontal = 13.dp, vertical = 8.dp),
    ) {
        Text(text, color = if (on) Zone.ink else Zone.boneDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
