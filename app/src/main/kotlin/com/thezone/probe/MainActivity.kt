package com.thezone.probe

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.thezone.ui.TransportDebugScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Debug host for the pre-UI build phases (BUILD_PLAN forbids real UI before H4).
 * Two screens behind a plain switcher:
 *
 *  - H0 capability probe — PHY flags, barometer, actual permission grant state.
 *  - H2 transport debug  — advertise / scan, received-packet log, mode picker.
 *
 * Replaced wholesale by the mode picker + Citizen/Responder/Map in H6.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var screen by remember { mutableStateOf(DebugScreen.PROBE) }
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DebugScreen.entries.forEach { target ->
                                if (screen == target) {
                                    Button(onClick = {}) { Text(target.label) }
                                } else {
                                    OutlinedButton(onClick = { screen = target }) { Text(target.label) }
                                }
                            }
                        }
                        HorizontalDivider()
                        when (screen) {
                            DebugScreen.PROBE -> ProbeScreen()
                            DebugScreen.TRANSPORT -> TransportDebugScreen()
                        }
                    }
                }
            }
        }
    }
}

private enum class DebugScreen(val label: String) {
    PROBE("H0 Probe"),
    TRANSPORT("H2 Transport"),
}

/** The four permissions H2 (advertiser + scanner) will require at runtime. */
private val PROBED_PERMISSIONS: List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    add(Manifest.permission.ACCESS_FINE_LOCATION)
}

private data class CapabilityReport(
    val build: String,
    val androidVersion: String,
    val bluetoothPresent: Boolean,
    val bluetoothEnabled: Boolean,
    val leCodedPhySupported: String,
    val leExtendedAdvertisingSupported: String,
    val le2MPhySupported: String,
    val multipleAdvertisementSupported: String,
    val leMaximumAdvertisingDataLength: String,
    val pressureSensorPresent: Boolean,
)

private fun buildCapabilityReport(context: Context): CapabilityReport {
    val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = btManager?.adapter

    // The BLE PHY / advertising queries need the adapter to exist; several OEMs
    // only return truthful values once Bluetooth is actually ON. If it is off,
    // re-run the probe after enabling Bluetooth.
    fun flag(block: () -> Boolean): String =
        if (adapter == null) "n/a (no BT adapter)" else block().toString()

    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val hasPressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

    return CapabilityReport(
        build = "${Build.MANUFACTURER} ${Build.MODEL}",
        androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        bluetoothPresent = adapter != null,
        bluetoothEnabled = adapter?.isEnabled == true,
        leCodedPhySupported = flag { adapter!!.isLeCodedPhySupported },
        leExtendedAdvertisingSupported = flag { adapter!!.isLeExtendedAdvertisingSupported },
        le2MPhySupported = flag { adapter!!.isLe2MPhySupported },
        multipleAdvertisementSupported = flag { adapter!!.isMultipleAdvertisementSupported },
        leMaximumAdvertisingDataLength =
            if (adapter == null) "n/a (no BT adapter)"
            else adapter.leMaximumAdvertisingDataLength.toString() + " bytes",
        pressureSensorPresent = hasPressure,
    )
}

@Composable
private fun ProbeScreen() {
    val context = LocalContext.current
    val report = remember { buildCapabilityReport(context) }

    // Live permission grant state, re-read on demand and whenever the activity resumes
    // (so returning from the system settings screen refreshes it).
    var permissionState by remember {
        mutableStateOf(readPermissionState(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionState = readPermissionState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionState = readPermissionState(context)
    }

    Scaffold { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(inner)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionTitle("Build")
            KeyValue("Device", report.build)
            KeyValue("OS", report.androidVersion)

            SectionDivider()
            SectionTitle("BLE capability")
            KeyValue("BT adapter present", report.bluetoothPresent.toString())
            KeyValue("BT enabled", report.bluetoothEnabled.toString())
            KeyValue("isLeCodedPhySupported()", report.leCodedPhySupported)
            KeyValue("isLeExtendedAdvertisingSupported()", report.leExtendedAdvertisingSupported)
            KeyValue("isLe2MPhySupported()", report.le2MPhySupported)
            KeyValue("isMultipleAdvertisementSupported()", report.multipleAdvertisementSupported)
            KeyValue("leMaximumAdvertisingDataLength()", report.leMaximumAdvertisingDataLength)
            if (!report.bluetoothEnabled) {
                Text(
                    "Bluetooth is OFF — some OEMs only report PHY flags truthfully once " +
                        "BT is enabled. Turn Bluetooth on and reopen this screen.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            SectionDivider()
            SectionTitle("Sensors")
            KeyValue("Sensor.TYPE_PRESSURE present", report.pressureSensorPresent.toString())

            SectionDivider()
            SectionTitle("Runtime permissions (actual grant state)")
            permissionState.forEach { (permission, granted) ->
                KeyValue(permission.substringAfterLast('.'), if (granted) "GRANTED" else "DENIED")
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Text(
                    "BLUETOOTH_ADVERTISE / SCAN / CONNECT are not runtime permissions " +
                        "below Android 12 (API 31); on this device only FINE_LOCATION gates BLE.",
                    fontSize = 12.sp,
                )
            }

            Button(
                onClick = { permissionLauncher.launch(PROBED_PERMISSIONS.toTypedArray()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text("Request permissions")
            }
            Text(
                "\"GRANTED\" above reflects PackageManager.checkSelfPermission at this moment, " +
                    "not the manifest. Deny-with-don't-ask-again will keep showing DENIED until " +
                    "you grant it in system settings.",
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun readPermissionState(context: Context): List<Pair<String, Boolean>> =
    PROBED_PERMISSIONS.map { permission ->
        permission to (
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
            )
    }

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = key,
            modifier = Modifier.padding(end = 12.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
        )
    }
}
