package com.helios.crisispin.ble

import android.bluetooth.BluetoothAdapter
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

    private val bluetoothAdapter: BluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    // Replace your UUID with a 16-bit one
    // Must be identical in BOTH files
    private val serviceUUID: UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
    private var currentCallback: AdvertiseCallback? = null
    private var retryCount = 0
    private val maxRetries = 3

    // Get advertiser lazily — always fetched fresh so it's valid after BT toggles
    private val advertiser: BluetoothLeAdvertiser?
        get() = bluetoothAdapter.bluetoothLeAdvertiser

    fun startAdvertising(message: String = "ALERT") {
        retryCount = 0
        stopAdvertising()
        // Short delay to let the BT stack clear any previous slot
        Handler(Looper.getMainLooper()).postDelayed({
            executeStart(message)
        }, 1000)
    }

    private fun executeStart(message: String) {
        val activeAdvertiser = advertiser
        if (activeAdvertiser == null) {
            Log.e("BLE", "Advertiser unavailable — Bluetooth may be off or device unsupported")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // change from LOW_POWER
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)     // add this
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val pUuid = ParcelUuid(serviceUUID)
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(pUuid)       // PUT THIS BACK
            .addServiceData(pUuid, message.toByteArray())
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                retryCount = 0
                Log.d("BLE", "Advertising started successfully")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e("BLE", "Advertising failed with error code: $errorCode")
                // Error code 1 = ADVERTISE_FAILED_ALREADY_STARTED or hardware busy
                if (errorCode == ADVERTISE_FAILED_ALREADY_STARTED ||
                    errorCode == ADVERTISE_FAILED_TOO_MANY_ADVERTISERS
                ) {
                    if (retryCount < maxRetries) {
                        retryCount++
                        Log.d("BLE", "Retrying advertising (attempt $retryCount of $maxRetries)...")
                        // Stop this specific callback before retrying
                        try { activeAdvertiser.stopAdvertising(this) } catch (e: Exception) { /* ignore */ }
                        Handler(Looper.getMainLooper()).postDelayed({ executeStart(message) }, 2500)
                    } else {
                        Log.e("BLE", "Max retries reached. Could not start advertising.")
                        retryCount = 0
                    }
                }
            }
        }

        currentCallback = callback

        try {
            activeAdvertiser.startAdvertising(settings, data, callback)
        } catch (e: Exception) {
            Log.e("BLE", "Hardware exception during startAdvertising: ${e.message}")
        }
    }

    fun stopAdvertising() {
        val cb = currentCallback ?: return
        try {
            advertiser?.stopAdvertising(cb)
            Log.d("BLE", "Advertising stopped")
        } catch (e: Exception) {
            Log.e("BLE", "Error stopping advertiser: ${e.message}")
        } finally {
            currentCallback = null
            retryCount = 0
        }
    }

    fun isAdvertising(): Boolean = currentCallback != null
}