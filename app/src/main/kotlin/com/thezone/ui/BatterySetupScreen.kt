package com.thezone.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.thezone.ui.theme.Zone

/**
 * "Keep Zone alive" — Android's Doze plus every OEM's own battery manager will
 * suspend a background BLE advertiser unless the user exempts the app. This walks
 * them through it: the battery-optimisation exemption, and a best-effort
 * deep-link to the manufacturer's autostart list.
 */
@Composable
fun BatterySetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }

    // re-check the exemption whenever we come back from a settings screen
    val owner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    @Suppress("UNUSED_EXPRESSION") refresh
    val exempt = remember(refresh) { isIgnoringBatteryOptimisations(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Zone.ink)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("‹ back", color = Zone.boneDim, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onBack() }) }.padding(vertical = 6.dp))

        Spacer(Modifier.height(12.dp))
        Text("Keep Zone alive", color = Zone.bone, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Android and your phone's battery manager will stop Zone from broadcasting when the screen is off — unless you allow it to run unrestricted. Do this before a drill.",
            color = Zone.boneDim, fontSize = 14.sp, lineHeight = 20.sp,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(20.dp))
        StatusPill(
            ok = exempt,
            okText = "Battery optimisation is off for Zone",
            badText = "Battery optimisation is ON — Zone will be killed",
        )

        Spacer(Modifier.height(12.dp))
        Action("Allow unrestricted background") { requestBatteryExemption(context) }

        val oem = oemAutostart(context)
        if (oem != null) {
            Spacer(Modifier.height(10.dp))
            Action("Open ${Build.MANUFACTURER} auto-launch settings") { runCatching { context.startActivity(oem) } }
        }

        Spacer(Modifier.height(10.dp))
        Action("Open Zone's app settings") { context.startActivity(appDetails(context)) }

        Spacer(Modifier.height(24.dp))
        Text("Also check, by hand:", color = Zone.bone, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        listOf(
            "Location — ON (Android needs it for Bluetooth scanning)",
            "Battery usage for Zone — Unrestricted / Don't optimise",
            "Auto-launch / Autostart — allowed",
            "Screen timeout — long, or the demo phone on a charger",
        ).forEach {
            Text("•  $it", color = Zone.boneDim, fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun StatusPill(ok: Boolean, okText: String, badText: String) {
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

private fun isIgnoringBatteryOptimisations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@Suppress("BatteryLife")
private fun requestBatteryExemption(context: Context) {
    val pkg = context.packageName
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg"))
    val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    if (context.packageManager.resolveActivity(direct, 0) != null) {
        runCatching { context.startActivity(direct) }.onFailure { runCatching { context.startActivity(list) } }
    } else {
        runCatching { context.startActivity(list) }
    }
}

private fun appDetails(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Best-effort deep-link to the manufacturer's autostart / protected-apps list. */
private fun oemAutostart(context: Context): Intent? {
    val candidates: List<ComponentName> = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" -> listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
        "oppo", "realme" -> listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        )
        "vivo", "iqoo" -> listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        )
        "huawei", "honor" -> listOf(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        )
        "samsung" -> listOf(
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        )
        "oneplus" -> listOf(
            ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        )
        else -> emptyList()
    }
    val pm = context.packageManager
    return candidates.firstNotNullOfOrNull { cn ->
        Intent().setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .takeIf { pm.resolveActivity(it, 0) != null }
    }
}
