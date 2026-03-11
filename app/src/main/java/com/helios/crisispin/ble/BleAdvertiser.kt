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
import com.helios.crisispin.utils.CrisisMessage
import com.helios.crisispin.utils.DeviceIdentity
import java.util.UUID

class BleAdvertiser(context: Context) {

    private val appContext = context.applicationContext
    private val bluetoothAdapter: BluetoothAdapter =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val serviceUUID: UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
    private var currentCallback: AdvertiseCallback? = null
    private var retryCount = 0
    private val maxRetries = 3

    private val advertiser: BluetoothLeAdvertiser?
        get() = bluetoothAdapter.bluetoothLeAdvertiser

    /** Send a fresh alert originating from this device */
    fun startAdvertising(alertType: String) {
        val myId = DeviceIdentity.getDeviceId(appContext)
        val msg  = CrisisMessage.newAlert(alertType, myId)
        scheduleStart(msg)
    }

    /** Relay a received message — adds this device to visited, increments hop */
    fun startRelaying(receivedMsg: CrisisMessage) {
        val myId  = DeviceIdentity.getDeviceId(appContext)
        val relay = CrisisMessage.relay(receivedMsg, myId)
        scheduleStart(relay)
    }

    private fun scheduleStart(msg: CrisisMessage) {
        retryCount = 0
        stopAdvertising()
        Handler(Looper.getMainLooper()).postDelayed({ executeStart(msg) }, 300)
    }

    private fun executeStart(msg: CrisisMessage) {
        val activeAdvertiser = advertiser ?: run {
            Log.e("BLE", "Advertiser unavailable"); return
        }

        val payload = CrisisMessage.encode(msg)
        // BLE ServiceData limit ~20 bytes — trim visited list if needed
        var payloadBytes = payload.toByteArray(Charsets.UTF_8)
        if (payloadBytes.size > 20) {
            val trimmed = CrisisMessage.encode(msg.copy(visited = msg.visited.take(2).toSet()))
            payloadBytes = trimmed.toByteArray(Charsets.UTF_8).copyOf(minOf(trimmed.length, 20))
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false).setTimeout(0).build()

        val pUuid = ParcelUuid(serviceUUID)
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false).setIncludeTxPowerLevel(false)
            .addServiceUuid(pUuid).addServiceData(pUuid, payloadBytes).build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                retryCount = 0
                Log.d("BLE", "Advertising: $payload")
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e("BLE", "Advertise failed: $errorCode")
                if ((errorCode == ADVERTISE_FAILED_ALREADY_STARTED ||
                            errorCode == ADVERTISE_FAILED_TOO_MANY_ADVERTISERS) && retryCount < maxRetries) {
                    retryCount++
                    try { activeAdvertiser.stopAdvertising(this) } catch (e: Exception) { }
                    Handler(Looper.getMainLooper()).postDelayed({ executeStart(msg) }, 2500)
                } else retryCount = 0
            }
        }
        currentCallback = callback
        try {
            activeAdvertiser.startAdvertising(settings, data, callback)
        } catch (e: Exception) {
            Log.e("BLE", "startAdvertising exception: ${e.message}")
        }
    }

    fun stopAdvertising() {
        val cb = currentCallback ?: return
        try { advertiser?.stopAdvertising(cb); Log.d("BLE", "Advertising stopped") }
        catch (e: Exception) { Log.e("BLE", "Stop error: ${e.message}") }
        finally { currentCallback = null; retryCount = 0 }
    }

    fun isAdvertising(): Boolean = currentCallback != null
}