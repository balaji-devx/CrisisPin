package com.helios.crisispin.ble

import android.annotation.SuppressLint
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
    private val bluetoothAdapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val serviceUUID: UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
    private var currentCallback: AdvertiseCallback? = null
    private var retryCount = 0
    private val maxRetries = 3

    private val advertiser: BluetoothLeAdvertiser?
        get() = bluetoothAdapter?.bluetoothLeAdvertiser

    fun startAdvertising(alertType: String) {
        val myId = DeviceIdentity.getDeviceId(appContext)
        val msg  = CrisisMessage.newAlert(alertType, myId)
        scheduleStart(msg)
    }

    fun startRelaying(receivedMsg: CrisisMessage) {
        scheduleStart(receivedMsg)
    }

    private fun scheduleStart(msg: CrisisMessage) {
        retryCount = 0
        stopAdvertising()
        Handler(Looper.getMainLooper()).postDelayed({ executeStart(msg) }, 300)
    }

    @SuppressLint("MissingPermission")
    private fun executeStart(msg: CrisisMessage) {
        val activeAdvertiser = advertiser ?: run {
            Log.e("BLE", "Advertiser unavailable"); return
        }

        // STRATEGY: v2 binary in ServiceData (primary, always ≤20 bytes)
        // Fallback: v1 string if v2 encoding fails
        // If ManufacturerData fits, also include v2 there for redundancy
        val myId = (msg.visited.firstOrNull { it != msg.originId } ?: msg.originId)

        // Try v2 first (guaranteed ≤ 20 bytes)
        val v2Bytes = CrisisMessage.encodeForBle(msg) ?: run {
            Log.e("BLE", "V2 encoding failed"); return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false).setTimeout(0).build()

        val pUuid = ParcelUuid(serviceUUID)
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false).setIncludeTxPowerLevel(false)
            .addServiceUuid(pUuid)
            .addServiceData(pUuid, v2Bytes)  // Primary: v2 binary in ServiceData
            .build()

        // Advertise (no scan response needed; this is simpler and more compatible)
        @SuppressLint("MissingPermission")
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                retryCount = 0
                Log.d("BLE", "Advertising: ${v2Bytes.size} bytes (v2 binary format)")
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e("BLE", "Advertise failed: $errorCode")
                if ((errorCode == ADVERTISE_FAILED_ALREADY_STARTED ||
                            errorCode == ADVERTISE_FAILED_TOO_MANY_ADVERTISERS) && retryCount < maxRetries) {
                    retryCount++
                    @Suppress("MissingPermission")
                    try { activeAdvertiser.stopAdvertising(this) } catch (se: SecurityException) { 
                        Log.e("BLE", "Missing permission to stop: ${se.message}")
                    } catch (e: Exception) { }
                    Handler(Looper.getMainLooper()).postDelayed({ executeStart(msg) }, 2500)
                } else retryCount = 0
            }
        }
        currentCallback = callback
        try {
            activeAdvertiser.startAdvertising(settings, data, callback)
        } catch (se: SecurityException) {
            Log.e("BLE", "Missing BLUETOOTH_ADVERTISE permission: ${se.message}")
        } catch (e: Exception) {
            Log.e("BLE", "startAdvertising exception: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        val cb = currentCallback ?: return
        try { advertiser?.stopAdvertising(cb); Log.d("BLE", "Advertising stopped") }
        catch (se: SecurityException) { Log.e("BLE", "Missing permission to stop: ${se.message}") }
        catch (e: Exception) { Log.e("BLE", "Stop error: ${e.message}") }
        finally { currentCallback = null; retryCount = 0 }
    }
}
