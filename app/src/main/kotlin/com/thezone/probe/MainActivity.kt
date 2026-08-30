package com.thezone.probe

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.thezone.mode.AppMode
import com.thezone.mode.ModeStore
import com.thezone.transport.BleForegroundService
import com.thezone.transport.TransportController
import com.thezone.ui.CitizenScreen
import com.thezone.ui.MapScreen
import com.thezone.ui.ProbeScreen
import com.thezone.ui.ResponderScreen
import com.thezone.ui.TransportDebugScreen
import com.thezone.ui.theme.Zone

/**
 * One APK, three modes (PRD §3). First launch shows the mode picker; after that
 * the chosen mode opens straight up. A long-press anywhere on a mode screen opens
 * a small switcher that also reaches the H0/H2 debug host.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(typography = com.thezone.ui.theme.zoneTypography()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Root()
                }
            }
        }
    }
}

@Composable
private fun Root() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(ModeStore.get(context)) }
    var showSwitcher by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(false) }

    if (showDebug) {
        Box(Modifier.fillMaxSize()) {
            DebugHost()
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Zone.signal)
                    .pointerInput(Unit) { detectTapGestures(onTap = { showDebug = false }) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) { Text("Close debug", color = Zone.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
        return
    }

    val current = mode
    if (current == null) {
        ModePicker(onPick = {
            ModeStore.set(context, it)
            mode = it
        })
        return
    }

    PermissionGate {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { showSwitcher = true })
                },
        ) {
            Crossfade(targetState = current, animationSpec = tween(360), label = "mode") { m ->
                when (m) {
                    AppMode.CITIZEN -> CitizenScreen()
                    AppMode.RESPONDER -> ResponderScreen()
                    AppMode.MAP -> MapScreen()
                }
            }
            if (showSwitcher) {
                ModeSwitcher(
                    current = current,
                    onPick = {
                        ModeStore.set(context, it)
                        mode = it
                        showSwitcher = false
                    },
                    onDebug = { showSwitcher = false; showDebug = true },
                    onDismiss = { showSwitcher = false },
                )
            }
        }
    }
}

/** Ensures BLE permissions, then keeps the foreground service running. */
@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(missingPermissions(context).isEmpty()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted = missingPermissions(context).isEmpty() }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().background(Zone.ink).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Allow Bluetooth and location so this phone can be heard.",
                color = Zone.bone, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            ZoneButton("Allow", filled = true) { launcher.launch(transportPermissions().toTypedArray()) }
        }
        return
    }

    // permissions in hand — start the foreground service, which keeps whatever
    // transport is active alive (BLE by default; a Simulated/File one picked via
    // debug is left in place — the demo's failure drill).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        BleForegroundService.start(context)
    }
    content()
}

@Composable
private fun ModePicker(onPick: (AppMode) -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Zone.ink).padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Pick a role", color = Zone.bone, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("One app. It stays on this role until you change it.", color = Zone.boneDim, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        AppMode.entries.forEach { m ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Zone.inkSoft)
                    .pointerInput(m) { detectTapGestures(onTap = { onPick(m) }) }
                    .padding(18.dp),
            ) {
                Column {
                    Text(m.label, color = Zone.signal, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(m.blurb, color = Zone.boneDim, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ModeSwitcher(
    current: AppMode,
    onPick: (AppMode) -> Unit,
    onDebug: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Zone.ink.copy(alpha = 0.86f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.74f),
        ) {
            Text(
                "SWITCH MODE",
                color = Zone.boneDim, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 0.18.sp,
            )
            Spacer(Modifier.height(14.dp))
            AppMode.entries.forEach { m ->
                ZoneButton(
                    if (m == current) "${m.label}  ·  on" else m.label,
                    filled = m == current,
                    modifier = Modifier.padding(vertical = 5.dp),
                ) { onPick(m) }
            }
            Spacer(Modifier.height(10.dp))
            ZoneButton("Debug (H0 / H2)", filled = false) { onDebug() }
        }
    }
}

@Composable
private fun ZoneButton(
    label: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) Zone.signal else Zone.inkSoft)
            .then(
                if (filled) Modifier
                else Modifier.border(1.dp, Zone.inkLine, RoundedCornerShape(12.dp)),
            )
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) }
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (filled) Zone.ink else Zone.bone,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// --- the pre-UI debug host, still reachable ---------------------------------

private enum class DebugScreen(val label: String) { PROBE("H0 Probe"), TRANSPORT("H2 Transport") }

@Composable
private fun DebugHost() {
    var screen by remember { mutableStateOf(DebugScreen.PROBE) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DebugScreen.entries.forEach { target ->
                if (screen == target) Button(onClick = {}) { Text(target.label) }
                else OutlinedButton(onClick = { screen = target }) { Text(target.label) }
            }
        }
        when (screen) {
            DebugScreen.PROBE -> ProbeScreen()
            DebugScreen.TRANSPORT -> TransportDebugScreen()
        }
    }
}

private fun transportPermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private fun missingPermissions(context: android.content.Context): List<String> =
    transportPermissions().filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
