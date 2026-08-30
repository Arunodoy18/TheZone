package com.thezone.transport

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat

/**
 * The real radio (BUILD_PLAN H2 — "the risky one").
 *
 * Everything rides in manufacturer-specific data under company ID
 * [BaseTransport.COMPANY_ID]. Connectionless broadcast, no GATT.
 *
 * Dual-mode is mandatory. When the adapter supports it we run two extended
 * advertising sets at once — one on 1M, one on Coded PHY — and scan on
 * `PHY_LE_ALL_SUPPORTED` with `setLegacy(false)` so both are reported. If
 * extended advertising is missing we fall back to a single legacy 1M
 * advertisement and flag it: the 31-byte packet does not fit a legacy PDU once
 * manufacturer framing is added, so that path is a known, logged degradation
 * (BUILD_PLAN H2 "cut if over").
 *
 * All `android.bluetooth` types stay inside this file — nothing leaks past
 * [ReportTransport].
 */
@SuppressLint("MissingPermission") // callers gate on runtime permissions; every radio call is also try/caught
class BleTransport(context: Context) : BaseTransport(kind = "BLE") {

    private val appContext = context.applicationContext
    private val btManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter get() = btManager?.adapter

    private val worker = HandlerThread("ble-transport").apply { start() }
    private val handler = Handler(worker.looper)

    @Volatile private var packetBytes: ByteArray? = null

    // Debug switches (BUILD_PLAN H2 troubleshooting order).
    @Volatile var manufacturerFilterEnabled = true
    @Volatile var forceLegacyAdvertising = false
    @Volatile var survivalMode = false
        set(value) {
            field = value
            if (running) handler.post { restartScan() }
        }

    private var set1mCallback: SetCallback? = null
    private var setCodedCallback: SetCallback? = null
    private var legacyCallback: AdvertiseCallback? = null

    // --- lifecycle --------------------------------------------------------

    override fun start() {
        handler.post {
            if (running) return@post
            val a = adapter
            if (a == null) {
                fail("no Bluetooth adapter")
                return@post
            }
            if (!a.isEnabled) {
                fail("Bluetooth is OFF — enable it and press Start again")
                return@post
            }
            if (!hasScanPermission()) {
                fail("BLUETOOTH_SCAN / location not granted")
                return@post
            }
            running = true
            log("start — extAdv=${a.isLeExtendedAdvertisingSupported} coded=${a.isLeCodedPhySupported} multiAdv=${a.isMultipleAdvertisementSupported}")
            startScan()
            packetBytes?.let(::startAdvertising)
            emitDiagnostics()
        }
    }

    override fun stop() {
        handler.post {
            if (!running) return@post
            stopAdvertising()
            stopScan()
            running = false
            log("stop")
            emitDiagnostics()
        }
    }

    override fun advertise(bytes: ByteArray?) {
        requirePacketSize(bytes)
        val copy = bytes?.copyOf()
        handler.post {
            packetBytes = copy
            advertisedHex = copy?.toHex()
            if (!running) {
                emitDiagnostics()
                return@post
            }
            stopAdvertising()
            if (copy != null) startAdvertising(copy)
            emitDiagnostics()
        }
    }

    fun shutdown() {
        stop()
        handler.post { worker.quitSafely() }
    }

    // --- advertising ----------------------------------------------------

    private fun startAdvertising(bytes: ByteArray) {
        val a = adapter ?: return
        val advertiser = a.bluetoothLeAdvertiser
        if (advertiser == null) {
            fail("device cannot advertise (no BluetoothLeAdvertiser)")
            return
        }
        if (!hasAdvertisePermission()) {
            fail("BLUETOOTH_ADVERTISE not granted")
            return
        }

        val data = AdvertiseData.Builder()
            .addManufacturerData(COMPANY_ID, bytes)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        val extended = a.isLeExtendedAdvertisingSupported && !forceLegacyAdvertising
        legacyFallbackActive = !extended
        codedPhyActive = false
        oneMPhyActive = false

        try {
            if (extended) {
                val multi = a.isMultipleAdvertisementSupported
                val wantCoded = a.isLeCodedPhySupported

                if (wantCoded) {
                    val params = paramsFor(BluetoothDevice.PHY_LE_CODED, codedIntervalUnits)
                    log("startAdvertisingSet 'Coded' primary=Coded secondary=Coded interval=$codedIntervalUnits units")
                    setCodedCallback = SetCallback("Coded").also {
                        advertiser.startAdvertisingSet(params, data, null, null, null, it)
                    }
                }
                if (!wantCoded || multi) {
                    val params = paramsFor(BluetoothDevice.PHY_LE_1M, oneMIntervalUnits)
                    log("startAdvertisingSet '1M' primary=1M secondary=1M interval=$oneMIntervalUnits units")
                    set1mCallback = SetCallback("1M").also {
                        advertiser.startAdvertisingSet(params, data, null, null, null, it)
                    }
                }
                if (wantCoded && !multi) {
                    log("multiple advertisement unsupported — Coded PHY only, no concurrent 1M")
                }
            } else {
                startLegacyAdvertising(advertiser, data)
            }
            advertising = true
            countSent()
            log("advertising ${bytes.size}B  hex=${bytes.toHex()}")
        } catch (t: Throwable) {
            fail("startAdvertising failed: ${t.message}")
        }
    }

    // Coded (S=8) has ~8x the airtime of 1M; running both fast can overrun the
    // controller's scheduler and starve the Coded set. Give Coded more headroom.
    @Volatile var oneMIntervalUnits = AdvertisingSetParameters.INTERVAL_LOW      // 400 * 0.625ms = 250ms
    @Volatile var codedIntervalUnits = AdvertisingSetParameters.INTERVAL_MEDIUM  // 1600 -> 1s

    private fun paramsFor(primaryPhy: Int, intervalUnits: Int): AdvertisingSetParameters =
        AdvertisingSetParameters.Builder()
            .setLegacyMode(false)
            .setConnectable(false)
            .setScannable(false)
            .setInterval(intervalUnits)
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
            .setPrimaryPhy(primaryPhy)
            .setSecondaryPhy(primaryPhy)
            .build()

    private fun startLegacyAdvertising(
        advertiser: android.bluetooth.le.BluetoothLeAdvertiser,
        data: AdvertiseData,
    ) {
        log(
            "WARNING: legacy 1M advertising — a 31-byte packet + manufacturer framing " +
                "overflows the 31-byte legacy PDU; expect DATA_TOO_LARGE. This is the " +
                "documented cut, not a bug.",
        )
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        legacyCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                oneMPhyActive = true
                log("legacy advertising started")
                emitDiagnostics()
            }

            override fun onStartFailure(errorCode: Int) {
                fail("legacy advertising failed: ${advertiseError(errorCode)}")
            }
        }
        advertiser.startAdvertising(settings, data, legacyCallback)
    }

    private fun stopAdvertising() {
        val advertiser = adapter?.bluetoothLeAdvertiser
        try {
            set1mCallback?.let { advertiser?.stopAdvertisingSet(it) }
            setCodedCallback?.let { advertiser?.stopAdvertisingSet(it) }
            legacyCallback?.let { advertiser?.stopAdvertising(it) }
        } catch (t: Throwable) {
            log("stopAdvertising: ${t.message}")
        }
        set1mCallback = null
        setCodedCallback = null
        legacyCallback = null
        advertising = false
        codedPhyActive = false
        oneMPhyActive = false
    }

    private inner class SetCallback(private val label: String) : AdvertisingSetCallback() {
        override fun onAdvertisingSetStarted(set: AdvertisingSet?, txPower: Int, status: Int) {
            if (status == ADVERTISE_SUCCESS) {
                if (label == "Coded") codedPhyActive = true else oneMPhyActive = true
                log("advertising set '$label' started (txPower=$txPower dBm)")
            } else {
                fail("advertising set '$label' failed: ${setError(status)}")
            }
            emitDiagnostics()
        }

        override fun onAdvertisingSetStopped(set: AdvertisingSet?) {
            if (label == "Coded") codedPhyActive = false else oneMPhyActive = false
            log("advertising set '$label' stopped")
            emitDiagnostics()
        }
    }

    // --- scanning -----------------------------------------------------

    private fun startScan() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            fail("no BluetoothLeScanner")
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(
                if (survivalMode) ScanSettings.SCAN_MODE_LOW_POWER
                else ScanSettings.SCAN_MODE_LOW_LATENCY,
            )
            .setLegacy(false) // report legacy AND extended advertisements
            .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .build()

        val filters =
            if (manufacturerFilterEnabled) {
                listOf(
                    ScanFilter.Builder()
                        .setManufacturerData(COMPANY_ID, ByteArray(0))
                        .build(),
                )
            } else {
                null // BUILD_PLAN H2 step 4: try a null filter first
            }

        try {
            scanner.startScan(filters, settings, scanCallback)
            scanning = true
            log("scanning — mode=${if (survivalMode) "LOW_POWER" else "LOW_LATENCY"} filter=${if (filters == null) "none" else "company 0x%04X".format(COMPANY_ID)}")
        } catch (t: Throwable) {
            fail("startScan failed: ${t.message}")
        }
        emitDiagnostics()
    }

    private fun stopScan() {
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (t: Throwable) {
            log("stopScan: ${t.message}")
        }
        scanning = false
    }

    private fun restartScan() {
        if (!running) return
        stopScan()
        startScan()
    }

    @Volatile private var lastPhyDump = 0L
    private val rxByPhy = java.util.concurrent.ConcurrentHashMap<PacketPhy, Long>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            val payload = record.getManufacturerSpecificData(COMPANY_ID) ?: return

            // Data on an extended advertisement rides the secondary PHY (the AUX
            // packet), so a Coded-primary set shows secondaryPhy == Coded. Some
            // OEM stacks under-report primaryPhy, so accept either.
            val phy = when {
                result.primaryPhy == BluetoothDevice.PHY_LE_CODED -> PacketPhy.CODED
                result.secondaryPhy == BluetoothDevice.PHY_LE_CODED -> PacketPhy.CODED
                result.primaryPhy == BluetoothDevice.PHY_LE_1M -> PacketPhy.ONE_M
                else -> PacketPhy.UNKNOWN
            }

            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastPhyDump > 3000) {
                lastPhyDump = now
                log(
                    "scan sample dev=${result.device?.address} " +
                        "pri=${phyName(result.primaryPhy)} sec=${phyName(result.secondaryPhy)} " +
                        "legacy=${result.isLegacy} conn=${result.isConnectable} " +
                        "dataStatus=${result.dataStatus} rssi=${result.rssi} rxByPhy=$rxByPhy",
                )
            }

            if (payload.size != PACKET_SIZE_BYTES) {
                log("dropped ${payload.size}B from ${result.device?.address} (not $PACKET_SIZE_BYTES)")
                return
            }
            rxByPhy.merge(phy, 1L) { a, b -> a + b }
            deliver(
                InboundPacket(
                    bytes = payload.copyOf(),
                    rssi = result.rssi,
                    receivedAtMillis = System.currentTimeMillis(),
                    phy = phy,
                ),
            )
        }

        override fun onScanFailed(errorCode: Int) {
            fail("scan failed: ${scanError(errorCode)}")
        }
    }

    private fun phyName(phy: Int): String = when (phy) {
        BluetoothDevice.PHY_LE_1M -> "1M"
        BluetoothDevice.PHY_LE_2M -> "2M"
        BluetoothDevice.PHY_LE_CODED -> "Coded"
        0 -> "unused"
        else -> "phy$phy"
    }

    // --- permission checks -----------------------------------------

    private fun hasScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            granted(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun hasAdvertisePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            true // install-time BLUETOOTH_ADMIN on API <= 30
        }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED

    // --- error decoding -------------------------------------------

    private fun setError(status: Int): String = when (status) {
        AdvertisingSetCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
        AdvertisingSetCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
        AdvertisingSetCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
        AdvertisingSetCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
        AdvertisingSetCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
        else -> "status $status"
    }

    private fun advertiseError(code: Int): String = when (code) {
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
        else -> "code $code"
    }

    private fun scanError(code: Int): String = when (code) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "APP_REGISTRATION_FAILED"
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
        else -> "code $code"
    }
}
