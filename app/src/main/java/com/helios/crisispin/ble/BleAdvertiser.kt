package com.helios.crisispin.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

class BleAdvertiser(context: Context) {

    private val bluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val serviceUUID: UUID = UUID.fromString(BleScanner.SERVICE_UUID_STRING)
    private var currentCallback: AdvertiseCallback? = null
    private var retryCount = 0
    private val maxRetries = 3
    private val handler = Handler(Looper.getMainLooper())

    // ── MIUI 10s cap fix ──────────────────────────────────────────────────────
    // Xiaomi/MIUI hardware enforces ~10s advertising timeout ignoring setTimeout(0).
    // Keep-alive runnable restarts advertising every 8s to maintain continuous broadcast.
    // User sees "30 second alert" but it's actually sustained indefinitely until stopped.
    private val KEEP_ALIVE_MS = 8_000L
    private var currentMessage: String? = null
    private var isAdvertisingIntentional = false

    private val keepAliveRunnable = Runnable {
        val msg = currentMessage
        if (isAdvertisingIntentional && msg != null) {
            Log.d("BleAdvertiser", "Keep-alive: restarting '$msg'")
            executeStart(msg)
            scheduleKeepAlive()
        }
    }

    private fun scheduleKeepAlive() {
        handler.removeCallbacks(keepAliveRunnable)
        handler.postDelayed(keepAliveRunnable, KEEP_ALIVE_MS)
    }

    private val advertiser: BluetoothLeAdvertiser?
        get() = bluetoothAdapter.bluetoothLeAdvertiser

    fun startAdvertising(message: String = "SOS") {
        retryCount = 0
        currentMessage = message
        isAdvertisingIntentional = true
        stopCurrentCallback()
        handler.postDelayed({ executeStart(message) }, 500)
    }

    private fun executeStart(message: String) {
        val active = advertiser ?: run {
            Log.e("BleAdvertiser", "Advertiser null — BT off?")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val pUuid = ParcelUuid(serviceUUID)
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(pUuid)
            .addServiceData(pUuid, message.toByteArray(Charsets.UTF_8))
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                retryCount = 0
                Log.d("BleAdvertiser", "Advertising '$message' ✓")
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e("BleAdvertiser", "Advertise FAILED errorCode=$errorCode")
                if (retryCount < maxRetries && isAdvertisingIntentional) {
                    retryCount++
                    try { active.stopAdvertising(this) } catch (e: Exception) { }
                    handler.postDelayed({ executeStart(message) }, 2000)
                } else { retryCount = 0 }
            }
        }

        stopCurrentCallback()
        currentCallback = callback
        try {
            active.startAdvertising(settings, data, callback)
        } catch (e: Exception) {
            Log.e("BleAdvertiser", "startAdvertising exception: ${e.message}")
        }
    }

    private fun stopCurrentCallback() {
        currentCallback?.let { try { advertiser?.stopAdvertising(it) } catch (e: Exception) { } }
        currentCallback = null
    }

    fun stopAdvertising() {
        isAdvertisingIntentional = false
        currentMessage = null
        handler.removeCallbacks(keepAliveRunnable)
        stopCurrentCallback()
        retryCount = 0
        Log.d("BleAdvertiser", "Stopped")
    }

    fun isAdvertising() = isAdvertisingIntentional
}