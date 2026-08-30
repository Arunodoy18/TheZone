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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Returns a value that changes at most ~4x/second when the transport reports
 * activity, and at least once a second regardless (so "12s ago" ages stay
 * fresh). The coalescing matters at H7 scale — 500 sim nodes push dozens of
 * packets a second and we must not recompose per packet.
 */
@Composable
fun transportTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val dirty = remember { AtomicBoolean(true) }

    DisposableEffect(Unit) {
        val prev = TransportController.onChange
        TransportController.onChange = {
            prev?.invoke()
            dirty.set(true)
        }
        onDispose { TransportController.onChange = prev }
    }

    LaunchedEffect(Unit) {
        var sinceForcedRefresh = 0
        while (true) {
            delay(250)
            sinceForcedRefresh += 250
            if (dirty.getAndSet(false) || sinceForcedRefresh >= 1000) {
                sinceForcedRefresh = 0
                tick++
            }
        }
    }
    return tick
}
