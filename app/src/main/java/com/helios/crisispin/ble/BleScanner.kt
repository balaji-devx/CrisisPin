package com.helios.crisispin.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.helios.crisispin.utils.CrisisMessage
import com.helios.crisispin.utils.DeviceIdentity
import java.util.UUID

class BleScanner(
    context: Context,
    private val onMessageReceived: (CrisisMessage) -> Unit
) {
    private val appContext = context.applicationContext
    private val bluetoothAdapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val serviceUUID: UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
    private var isScanning = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    // Hardened limits for scalability
    private val seenMsgIds = mutableMapOf<String, Long>()
    private val seenCancelIds = mutableMapOf<String, Long>()
    private val seenDevices = mutableMapOf<String, Long>() // originId -> timestamp
    
    private val MSG_TTL_MS = 5 * 60 * 1000L
    private val CANCEL_TTL_MS = 60 * 1000L
    private val DEVICE_TTL_MS = 30 * 1000L
    private val MAX_TRACKED_ITEMS = 500

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            val pUuid = ParcelUuid(serviceUUID)
            val rawBytes = record.getServiceData(pUuid) ?: return
            if (rawBytes.isEmpty()) return

            // Hardened decode with V1/V2 support
            val msg = CrisisMessage.decodeFromBle(rawBytes) ?: return
            val myId = DeviceIdentity.getDeviceId(appContext)

            // FIX 4: Prevent origin device from processing its own message (Loopback protection)
            if (msg.originId == myId) return

            // Track nearby devices based on originId
            trackDevice(msg.originId)

            if (myId in msg.visited) return
            
            // GATE 3: Max hops — mesh propagation ceiling
            if (msg.hop >= CrisisMessage.MAX_HOPS) return

            val now = System.currentTimeMillis()
            cleanOldData(now)
            
            // Always process CANCEL messages even if we've seen the msgId before
            val isCancel = (msg.flags and CrisisMessage.FLAG_CANCEL) != 0

            if (isCancel) {
                val lastCancel = seenCancelIds[msg.msgId]
                if (lastCancel != null && now - lastCancel <= CANCEL_TTL_MS) return
                if (seenCancelIds.size < MAX_TRACKED_ITEMS) {
                    seenCancelIds[msg.msgId] = now
                }
            }
            
            if (!isCancel && seenMsgIds.containsKey(msg.msgId)) return
            
            if (seenMsgIds.size < MAX_TRACKED_ITEMS) {
                seenMsgIds[msg.msgId] = now
            }

            Log.d("BLE", "✅ Alert: ${msg.type} origin=${msg.originId} hop=${msg.hop} cancel=$isCancel")
            mainHandler.post { onMessageReceived(msg) }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            Log.e("BLE", "Scan failed: $errorCode")
        }
    }

    private fun trackDevice(originId: String) {
        val now = System.currentTimeMillis()
        val isNew = !seenDevices.containsKey(originId)
        if (seenDevices.size < MAX_TRACKED_ITEMS) {
            seenDevices[originId] = now
        }
        
        if (isNew || (now % 5000 < 500)) { // Throttled broadcast
            broadcastMetrics()
        }
    }

    private fun broadcastMetrics() {
        val now = System.currentTimeMillis()
        cleanOldData(now)
        val intent = Intent("com.helios.crisispin.METRICS_UPDATED")
        intent.putExtra("nearby_count", seenDevices.size)
        LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent)
    }

    private fun cleanOldData(now: Long) {
        seenMsgIds.entries.removeIf { now - it.value > MSG_TTL_MS }
        seenCancelIds.entries.removeIf { now - it.value > CANCEL_TTL_MS }
        seenDevices.entries.removeIf { now - it.value > DEVICE_TTL_MS }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScanning() {
        if (isScanning) return
        val activeScanner = scanner ?: run {
            Log.e("BLE", "Scanner unavailable"); return
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            activeScanner.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
            Log.d("BLE", "Scanner started")
        } catch (e: Exception) {
            isScanning = false
            Log.e("BLE", "Scanner failed: ${e.message}")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScanning() {
        if (!isScanning) return
        try { scanner?.stopScan(scanCallback) } catch (e: Exception) { Log.e("BLE", "Stop failed: ${e.message}") }
        finally { isScanning = false }
    }

    fun isActive(): Boolean = isScanning
    fun isScanning(): Boolean = isScanning

    fun getNearbyCount(): Int {
        cleanOldData(System.currentTimeMillis())
        return seenDevices.size
    }
}
