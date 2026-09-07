package com.thezone.ui

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.thezone.notify.BluetoothNudge
import com.thezone.ui.theme.Zone

/** True while the Bluetooth adapter is on; recomposes on state changes and on resume. */
@Composable
fun rememberBluetoothOn(): Boolean {
    val context = LocalContext.current
    var on by remember { mutableStateOf(BluetoothNudge.isBluetoothOn(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { on = BluetoothNudge.isBluetoothOn(context) }
        }
        ContextCompat.registerReceiver(
            context, rx, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) on = BluetoothNudge.isBluetoothOn(context)
        }
        owner.lifecycle.addObserver(obs)
        onDispose {
            runCatching { context.unregisterReceiver(rx) }
            owner.lifecycle.removeObserver(obs)
        }
    }
    return on
}

/** Red "turn Bluetooth on" strip. Renders nothing while Bluetooth is on. */
@Composable
fun BluetoothBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (rememberBluetoothOn()) return
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Zone.alarmDeep)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    runCatching {
                        context.startActivity(
                            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.onFailure {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                })
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("Bluetooth is OFF", color = Zone.bone, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("You are not connected. Tap to turn it on.", color = Zone.bone.copy(alpha = 0.85f), fontSize = 12.sp)
        }
        Text("TURN ON", color = Zone.bone, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
