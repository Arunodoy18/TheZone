package com.thezone.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.thezone.core.SilenceState
import com.thezone.demo.DebugOverrides
import com.thezone.identity.DeviceKeyStore
import com.thezone.packet.Packet
import com.thezone.sensors.Altitude
import com.thezone.transport.BleForegroundService
import com.thezone.transport.TransportController
import com.thezone.transport.toHex
import kotlinx.coroutines.delay

/**
 * H2 checkpoint surface: advertise a real 31-byte heartbeat, scan, and show every
 * inbound packet with its decode so a second phone can confirm the bytes survive
 * the air. Also the mode picker used in the demo's failure drills.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransportDebugScreen() {
    val context = LocalContext.current

    // The controller talks in plain callbacks; bump a counter to recompose.
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        TransportController.onChange = { revision++ }
        onDispose { TransportController.onChange = null }
    }
    // Keep "last heard" ages fresh even when nothing new arrives.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            revision++
        }
    }

    @Suppress("UNUSED_EXPRESSION")
    revision // read so recomposition tracks it

    val diagnostics = TransportController.diagnostics
    val rows = TransportController.received
    val deviceIdHex = remember { DeviceKeyStore.identity(context).deviceId.toHex() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { revision++ }

    val missing = missingPermissions(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Header("This device")
        KeyVal("device_id", deviceIdHex)
        KeyVal("advertising", diagnostics.advertisedHex ?: "—")

        Header("Mode")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeButton("BLE", TransportController.kind == "BLE") {
                TransportController.useBle(context)
            }
            ModeButton("Simulated", TransportController.kind == "Simulated") {
                TransportController.useSimulated()
            }
            ModeButton("File", TransportController.kind == "File") {
                TransportController.useFile()
            }
        }

        if (missing.isNotEmpty()) {
            Text(
                "Missing permissions: ${missing.joinToString { it.substringAfterLast('.') }}",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
            )
            Button(onClick = { permissionLauncher.launch(missing.toTypedArray()) }) {
                Text("Grant permissions")
            }
        }

        Header("Control")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = missing.isEmpty() || TransportController.kind != "BLE",
                onClick = {
                    if (TransportController.kind == "BLE") {
                        BleForegroundService.start(context)
                    } else {
                        TransportController.start(context)
                    }
                },
            ) { Text("Start") }

            OutlinedButton(onClick = {
                if (TransportController.kind == "BLE") BleForegroundService.stop(context)
                else TransportController.stop()
            }) { Text("Stop") }

            OutlinedButton(onClick = { TransportController.refreshHeartbeat(context) }) {
                Text("Refresh heartbeat")
            }
            OutlinedButton(onClick = { TransportController.clearReceived() }) {
                Text("Clear")
            }
        }

        Header("Battery override (fake the ladder)")
        var battOverride by remember { mutableStateOf(DebugOverrides.batteryPercentOverride) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf<Int?>(90, 55, 25, 8, null).forEach { pct ->
                FilterChip(
                    selected = battOverride == pct,
                    onClick = {
                        battOverride = pct
                        DebugOverrides.batteryPercentOverride = pct
                        TransportController.refreshHeartbeat(context)
                    },
                    label = { Text(pct?.let { "$it%" } ?: "auto") },
                )
            }
        }

        Header("Altitude (barometer)")
        KeyVal("has barometer", Altitude.hasBarometer.toString())
        KeyVal(
            "Δ metres",
            if (Altitude.deltaByte == Packet.NO_BAROMETER) "n/a (no baro)" else "${Altitude.deltaByte} m",
        )
        KeyVal("trend / rising", "${Altitude.trendMeters} m  /  ${Altitude.rising}")
        KeyVal("baseline alt", Altitude.baselineMeters?.let { "%.1f m".format(it) } ?: "—")
        OutlinedButton(onClick = { Altitude.resetBaseline() }) { Text("Reset baseline (I'm at ground)") }

        val ble = TransportController.bleTransport()
        if (ble != null) {
            Header("BLE debug switches")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var filter by remember { mutableStateOf(ble.manufacturerFilterEnabled) }
                var forceLegacy by remember { mutableStateOf(ble.forceLegacyAdvertising) }
                var survival by remember { mutableStateOf(ble.survivalMode) }
                var alternate by remember {
                    mutableStateOf(ble.advertiseStrategy == com.thezone.transport.BleTransport.AdvertiseStrategy.ALTERNATING)
                }
                FilterChip(
                    selected = alternate,
                    onClick = {
                        alternate = !alternate
                        ble.advertiseStrategy =
                            if (alternate) com.thezone.transport.BleTransport.AdvertiseStrategy.ALTERNATING
                            else com.thezone.transport.BleTransport.AdvertiseStrategy.CONCURRENT
                    },
                    label = { Text(if (alternate) "PHY: alternate" else "PHY: concurrent") },
                )
                FilterChip(
                    selected = filter,
                    onClick = { filter = !filter; ble.manufacturerFilterEnabled = filter },
                    label = { Text("company filter") },
                )
                FilterChip(
                    selected = forceLegacy,
                    onClick = { forceLegacy = !forceLegacy; ble.forceLegacyAdvertising = forceLegacy },
                    label = { Text("force legacy 1M") },
                )
                FilterChip(
                    selected = survival,
                    onClick = { survival = !survival; ble.survivalMode = survival },
                    label = { Text("survival scan") },
                )
            }
            Text(
                "Toggle a switch, then Stop and Start to apply advertising changes.",
                fontSize = 11.sp,
            )
        }

        Header("Status")
        KeyVal("kind", diagnostics.kind)
        KeyVal("running / adv / scan", "${diagnostics.running} / ${diagnostics.advertising} / ${diagnostics.scanning}")
        KeyVal("PHY active", phyLabel(diagnostics.codedPhyActive, diagnostics.oneMPhyActive, diagnostics.legacyFallbackActive))
        KeyVal("sent / received", "${diagnostics.packetsSent} / ${diagnostics.packetsReceived}")
        KeyVal("rx Coded / 1M", "${diagnostics.rxCoded} / ${diagnostics.rxOneM}")
        val relayable = rows.count { !it.isOwn }
        KeyVal("store: reports / relayable", "${rows.size} / $relayable")
        diagnostics.lastError?.let {
            Text("last error: $it", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        Header("Store (${rows.size})")
        if (rows.isEmpty()) {
            Text("Nothing yet. On the other phone: Start on the same mode.", fontSize = 12.sp)
        }
        rows.forEach { row ->
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            Text(
                row.summary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = if (row.isOwn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "rssi ${row.lastRssi}dBm · x${row.count} · ${ageSeconds(row.lastSeenMillis)}s ago",
                fontSize = 11.sp,
            )
            Text(row.rawHex, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }

        val cellLosses = TransportController.cellLosses
        val silenceDevices = TransportController.silenceDevices
        val transitions = TransportController.silenceTransitions

        Header("Dead Man's Packet")
        cellLosses.forEach { loss ->
            Text(
                "CELL_LOSS  cell(${loss.cell.latIndex},${loss.cell.lonIndex})  " +
                    "${loss.silentCount}/${loss.deviceCount} devices, all silent by ${clockOf(loss.lastSilentAtMillis)}",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
        if (silenceDevices.isEmpty()) {
            Text("No peers tracked yet.", fontSize = 12.sp)
        }
        silenceDevices.sortedBy { it.state.ordinal }.forEach { d ->
            Text(
                "dev ${d.deviceIdHex.take(12)}  ${d.state}  " +
                    "heard ${ageSeconds(d.lastHeardAtMillis)}s ago  promised ${d.promisedNextTxSeconds}s  " +
                    "bat ${d.lastBatteryPercent}%  misses ${d.consecutiveMisses}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = when (d.state) {
                    SilenceState.UNEXPECTED_SILENCE -> MaterialTheme.colorScheme.error
                    SilenceState.ALIVE -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (transitions.isNotEmpty()) {
            Text("transitions:", fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
            transitions.takeLast(12).asReversed().forEach {
                Text(
                    "${clockOf(it.atMillis)}  ${it.deviceIdHex.take(12)}  ${it.from}→${it.to}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
            }
        }

        Header("Transport log")
        diagnostics.log.asReversed().forEach {
            Text(it, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
    }
}

private fun clockOf(millis: Long): String {
    val s = millis / 1000
    return "%02d:%02d:%02d".format((s / 3600) % 24, (s / 60) % 60, s % 60)
}

private fun phyLabel(coded: Boolean, oneM: Boolean, legacy: Boolean): String = buildList {
    if (coded) add("Coded")
    if (oneM) add("1M")
    if (legacy) add("legacy-fallback")
}.joinToString("+").ifEmpty { "—" }

private fun ageSeconds(millis: Long): Long = ((System.currentTimeMillis() - millis) / 1000).coerceAtLeast(0)

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

@Composable
private fun ModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(label) }
    else OutlinedButton(onClick = onClick) { Text(label) }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun KeyVal(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(key, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.padding(end = 10.dp))
        Text(value, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}
