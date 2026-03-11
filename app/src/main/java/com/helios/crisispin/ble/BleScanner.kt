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
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import com.helios.crisispin.utils.CrisisMessage
import com.helios.crisispin.utils.DeviceIdentity
import java.util.UUID

class BleScanner(
    context: Context,
    private val onMessageReceived: (CrisisMessage) -> Unit
) {
    private val appContext = context.applicationContext
    private val bluetoothAdapter: BluetoothAdapter =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val serviceUUID: UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
    private var isScanning = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter.bluetoothLeScanner

    // Seen message IDs → first-seen timestamp. Deduplicates multi-hop arrivals.
    private val seenMsgIds = mutableMapOf<String, Long>()
    private val MSG_TTL_MS = 5 * 60 * 1000L

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record    = result.scanRecord ?: return
            val pUuid     = ParcelUuid(serviceUUID)
            val rawBytes  = record.getServiceData(pUuid) ?: return
            val raw       = String(rawBytes, Charsets.UTF_8).trim()
            if (raw.isEmpty()) return

            val msg = CrisisMessage.decode(raw)
            if (msg == null) {
                Log.w("BLE", "Undecodable packet: '$raw'")
                return
            }

            val myId = DeviceIdentity.getDeviceId(appContext)

            // GATE 1: Own message — sender never receives its own alert
            if (msg.originId == myId) return

            // GATE 2: This device is already in the visited set — already processed this exact message
            if (myId in msg.visited) return

            // GATE 3: Max hops — mesh propagation ceiling
            if (msg.hop >= CrisisMessage.MAX_HOPS) return

            // GATE 4: Already seen this msgId recently (multi-hop dedup)
            val now = System.currentTimeMillis()
            cleanOldMsgIds(now)
            if (seenMsgIds.containsKey(msg.msgId)) return
            seenMsgIds[msg.msgId] = now

            Log.d("BLE", "✅ Alert: ${msg.type} origin=${msg.originId} hop=${msg.hop} msg=${msg.msgId}")
            mainHandler.post { onMessageReceived(msg) }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            Log.e("BLE", "Scan failed: $errorCode")
        }
    }

    private fun cleanOldMsgIds(now: Long) {
        seenMsgIds.entries.filter { now - it.value > MSG_TTL_MS }.forEach { seenMsgIds.remove(it.key) }
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
            Log.d("BLE", "Scanner started (myId=${DeviceIdentity.getDeviceId(appContext)})")
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
}