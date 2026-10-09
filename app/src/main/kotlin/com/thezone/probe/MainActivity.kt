package com.thezone.probe

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.withTimeoutOrNull
import com.thezone.mode.AppMode
import com.thezone.mode.FirstRunStore
import com.thezone.mode.ModeStore
import com.thezone.transport.BleForegroundService
import com.thezone.transport.TransportController
import com.thezone.ui.BatterySetupScreen
import com.thezone.ui.CitizenScreen
import com.thezone.ui.EmergencyContactsScreen
import com.thezone.ui.LandingScreen
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
    var showBattery by remember { mutableStateOf(false) }
    var showContacts by remember { mutableStateOf(false) }
    var showIssueAlert by remember { mutableStateOf(false) }
    var showBatteryNudge by rememberSaveable { mutableStateOf(!FirstRunStore.batteryPromptSeen(context)) }
    // shown on every cold start; survives rotation but not the task being cleared
    var showLanding by rememberSaveable { mutableStateOf(true) }

    if (showLanding) {
        LandingScreen(onEnter = { showLanding = false })
        return
    }

    if (showBattery) {
        BatterySetupScreen(onBack = { showBattery = false })
        return
    }

    if (showContacts) {
        EmergencyContactsScreen(onBack = { showContacts = false })
        return
    }

    if (showIssueAlert) {
        com.thezone.ui.IssueAlertSheet(onDismiss = { showIssueAlert = false })
        return
    }

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
        if (showBatteryNudge) {
            FirstRunBatteryNudge(
                onSetUp = {
                    FirstRunStore.markBatteryPromptSeen(context)
                    showBatteryNudge = false
                    showBattery = true
                },
                onSkip = {
                    FirstRunStore.markBatteryPromptSeen(context)
                    showBatteryNudge = false
                },
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectLongPressBeforeChildren { showSwitcher = true } },
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
                        onBattery = {
                            FirstRunStore.markBatteryPromptSeen(context)
                            showSwitcher = false
                            showBattery = true
                        },
                        onContacts = { showSwitcher = false; showContacts = true },
                        onIssueAlert = { showSwitcher = false; showIssueAlert = true },
                        onDismiss = { showSwitcher = false },
                    )
                }
            }
        }
    }
}

/** Ensures BLE permissions, then keeps the foreground service running. */
@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    var granted by remember { mutableStateOf(missingPermissions(context).isEmpty()) }
    // Set once the system dialog has actually been shown and answered — before
    // that, shouldShowRequestPermissionRationale is meaningless (it's also false
    // pre-first-ask), so "permanently denied" can't be judged from one check.
    var attempted by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        attempted = true
        granted = missingPermissions(context).isEmpty()
    }

    // Re-check on return from Settings — a user who left this screen to fix the
    // permission by hand must not come back to find it still stuck showing the
    // same blocked state.
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) granted = missingPermissions(context).isEmpty()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    if (!granted) {
        // Once the OS has stopped offering a rationale for every still-missing
        // permission, it has also stopped showing the request dialog at all (the
        // user picked "Don't ask again", or denied twice on older Android) — the
        // "Allow" button would silently relaunch a dialog that never appears,
        // leaving a real user stuck on this screen with no visible way out.
        val permanentlyDenied = attempted && activity != null &&
            missingPermissions(context).none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }

        Column(
            Modifier.fillMaxSize().background(Zone.ink).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (permanentlyDenied)
                    "Bluetooth and location are blocked for Zone. Turn them on in Settings so this phone can be heard."
                else
                    "Allow Bluetooth and location so this phone can be heard.",
                color = Zone.bone, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            if (permanentlyDenied) {
                ZoneButton("Open Settings", filled = true) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            } else {
                ZoneButton("Allow", filled = true) { launcher.launch(transportPermissions().toTypedArray()) }
            }
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

/**
 * Shown once, right after the first permission grant — "Keep Zone alive" used
 * to be reachable only through an undiscoverable long-press, so a real user
 * could have BLE working correctly and then watch it get silently killed in
 * the background by Doze/an OEM battery manager with no idea why. Skippable:
 * this is a nudge, not a gate — the mesh already works without it.
 */
@Composable
private fun FirstRunBatteryNudge(onSetUp: () -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Zone.ink).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("One more thing", color = Zone.bone, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Your phone's battery manager will stop Zone from broadcasting once the screen is off, unless you tell it not to. Takes a few seconds.",
            color = Zone.boneDim, fontSize = 15.sp, lineHeight = 21.sp,
        )
        Spacer(Modifier.height(24.dp))
        ZoneButton("Set it up", filled = true) { onSetUp() }
        Spacer(Modifier.height(10.dp))
        ZoneButton("Skip for now", filled = false) { onSkip() }
    }
}

@Composable
private fun ModeSwitcher(
    current: AppMode,
    onPick: (AppMode) -> Unit,
    onDebug: () -> Unit,
    onBattery: () -> Unit,
    onContacts: () -> Unit,
    onIssueAlert: () -> Unit,
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
            ZoneButton("⚠ Issue alert", filled = false) { onIssueAlert() }
            Spacer(Modifier.height(6.dp))
            ZoneButton("Keep Zone alive (battery)", filled = false) { onBattery() }
            Spacer(Modifier.height(6.dp))
            ZoneButton("Text my status (SMS)", filled = false) { onContacts() }
            Spacer(Modifier.height(6.dp))
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

/**
 * Long-press-to-open-switcher, without stealing an ordinary tap from whatever's
 * underneath (a scrollable [ResponderScreen] row, in particular). A plain
 * `detectTapGestures(onLongPress = ...)` here competed with that row's own
 * `clickable` on [PointerEventPass.Main] — same-pass gesture detectors race, and
 * the row usually won, so long-pressing anywhere on a populated Responder list
 * just opened Dig Here instead of the switcher (same class of bug as the
 * PhraseRow tap-loss fix). Watching [PointerEventPass.Initial] instead lets this
 * Box see the pointer before any child's Main-pass detector does: a short tap is
 * never touched, so it reaches the child exactly as before, but once the hold
 * crosses the long-press threshold this consumes the pointer — which aborts the
 * child's in-flight `clickable` — and fires [onLongPress].
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectLongPressBeforeChildren(
    onLongPress: () -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // Race the release against the threshold rather than polling for
        // intermediate events — a stationary hold isn't guaranteed to deliver
        // any pointer event between Down and Up (synthetic input in particular
        // routinely doesn't), so a loop that only checks elapsed time when a
        // new event arrives can miss the window entirely.
        val releasedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            waitForUpOrCancellation(pass = PointerEventPass.Initial)
        } != null
        if (!releasedEarly) {
            onLongPress()
            // Still down past the threshold: consume the eventual release so
            // the child's own (not-yet-resolved) Main-pass tap detector sees
            // this pointer as already consumed and aborts its click.
            waitForUpOrCancellation(pass = PointerEventPass.Initial)?.consume()
        }
    }
}
