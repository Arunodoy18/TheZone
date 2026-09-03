package com.thezone.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Thin Android glue: best-effort device location into [Position]. Mirrors
 * [PressureReader] — start/stop with the transport lifecycle.
 *
 * Uses only the offline providers (GPS + PASSIVE, plus NETWORK if the platform
 * offers a cached one). No Play Services, no network calls. If FINE_LOCATION
 * isn't granted it does nothing and [Position] stays fixless — the heartbeat
 * then sends NO_FIX, exactly as before.
 */
class LocationReader(context: Context) {

    private val appContext = context.applicationContext
    private val lm = appContext.getSystemService(LocationManager::class.java)

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = publish(location)

        // keep these for API < 30 where they are abstract
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        @Deprecated("deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    }

    private fun granted(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // guarded by granted()
    fun start() {
        val lm = lm ?: return
        if (!granted()) {
            Log.d("TheZone", "location: FINE_LOCATION not granted — running fixless")
            return
        }
        // seed from whatever the platform already has
        newestLastKnown(lm)?.let(::publish)

        for (provider in listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        )) {
            runCatching {
                if (lm.isProviderEnabled(provider)) {
                    lm.requestLocationUpdates(
                        provider, MIN_INTERVAL_MS, MIN_DISTANCE_M, listener, Looper.getMainLooper(),
                    )
                }
            }.onFailure { Log.d("TheZone", "location: $provider unavailable (${it.message})") }
        }
    }

    fun stop() {
        runCatching { lm?.removeUpdates(listener) }
    }

    @SuppressLint("MissingPermission")
    private fun newestLastKnown(lm: LocationManager): Location? {
        if (!granted()) return null
        return listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private fun publish(l: Location) {
        Position.onFix(
            lat = l.latitude,
            lon = l.longitude,
            accuracyMeters = if (l.hasAccuracy()) l.accuracy else Float.MAX_VALUE,
            atMillis = if (l.time > 0) l.time else System.currentTimeMillis(),
        )
    }

    private companion object {
        const val MIN_INTERVAL_MS = 15_000L
        const val MIN_DISTANCE_M = 8f
    }
}
