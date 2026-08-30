package com.thezone.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.thezone.transport.TransportController
import kotlinx.coroutines.delay

/**
 * Returns a value that changes whenever the transport pings a change, and at
 * least once a second (so "12s ago" style ages stay fresh). Read it inside a
 * composable to make that composable re-read [TransportController] snapshots.
 */
@Composable
fun transportTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val prev = TransportController.onChange
        TransportController.onChange = {
            prev?.invoke()
            tick++
        }
        onDispose { TransportController.onChange = prev }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    return tick
}
