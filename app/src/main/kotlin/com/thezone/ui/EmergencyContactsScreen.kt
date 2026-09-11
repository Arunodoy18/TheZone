package com.thezone.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.thezone.config.EmergencyContacts
import com.thezone.notify.StatusTexter
import com.thezone.ui.theme.Zone

/**
 * Opt-in "text my status to my own contacts" — a different reach model from
 * the mesh (see PRD / CLAUDE.md rule 4): this is the one screen in the app
 * that talks to a live network (SMS), so it's off by default, needs an
 * explicit toggle, and numbers are hand-typed here — never read from the
 * phone's own contact list.
 */
@Composable
fun EmergencyContactsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(EmergencyContacts.enabled(context)) }
    var numbersField by remember { mutableStateOf(EmergencyContacts.rawNumbers(context)) }
    var refresh by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh++ }

    val tick = transportTick()
    @Suppress("UNUSED_EXPRESSION") refresh
    @Suppress("UNUSED_EXPRESSION") tick
    val hasPermission = remember(refresh) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Zone.ink)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            "‹ back", color = Zone.boneDim, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onBack() }) }.padding(vertical = 6.dp),
        )

        Spacer(Modifier.height(12.dp))
        Text("Text my status", color = Zone.bone, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "A different channel from the mesh — not a broadcast to strangers nearby, " +
                "just your status reaching the people who already have your number, the moment " +
                "your phone catches any signal at all. Off by default.",
            color = Zone.boneDim, fontSize = 14.sp, lineHeight = 20.sp,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(20.dp))
        Pill(
            ok = hasPermission,
            okText = "SMS permission granted",
            badText = "SMS permission needed",
        )
        if (!hasPermission) {
            Spacer(Modifier.height(10.dp))
            Action("Allow SMS") { launcher.launch(Manifest.permission.SEND_SMS) }
        }

        Spacer(Modifier.height(20.dp))
        Text("Contact numbers — one per line", color = Zone.bone, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = numbersField,
            onValueChange = { numbersField = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("+91 98xxxxxxxx", color = Zone.boneDim) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            colors = TextFieldDefaults.colors(
                focusedTextColor = Zone.bone, unfocusedTextColor = Zone.bone,
                focusedContainerColor = Zone.inkSoft, unfocusedContainerColor = Zone.inkSoft,
                focusedIndicatorColor = Zone.signal, unfocusedIndicatorColor = Zone.inkLine,
                cursorColor = Zone.signal,
            ),
        )
        Spacer(Modifier.height(10.dp))
        Action("Save numbers") { EmergencyContacts.setNumbers(context, numbersField) }

        Spacer(Modifier.height(20.dp))
        ToggleRow(
            label = if (enabled) "Auto-text when signal returns  ·  on" else "Auto-text when signal returns",
            on = enabled,
        ) {
            enabled = !enabled
            EmergencyContacts.setEnabled(context, enabled)
        }

        Spacer(Modifier.height(20.dp))
        Action("Send now (test)") { StatusTexter.sendNow(context) }
        Spacer(Modifier.height(10.dp))
        Text("last attempt: ${StatusTexter.lastResult}", color = Zone.boneDim, fontSize = 12.sp)
    }
}

@Composable
private fun Pill(ok: Boolean, okText: String, badText: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (ok) Zone.calm else Zone.alarmDeep).padding(14.dp),
    ) {
        Text(if (ok) okText else badText, color = Zone.bone, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Zone.inkSoft)
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) }
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Zone.signal, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (on) Zone.signal else Zone.inkSoft)
            .pointerInput(label) { detectTapGestures(onTap = { onToggle() }) }
            .padding(vertical = 15.dp, horizontal = 16.dp),
    ) {
        Text(label, color = if (on) Zone.ink else Zone.bone, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
