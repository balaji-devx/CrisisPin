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
import java.util.UUID

class BleScanner(
    context: Context,
    private val onMessageReceived: (String) -> Unit
) {
    private val bluetoothAdapter: BluetoothAdapter =
        (context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val serviceUUID: UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
    private var isScanning = false

    // FIX 1: Dispatch to main thread — onScanResult fires on BLE binder thread.
    // Without this, mutableStateOf updates and LocalBroadcast sends race against the UI thread.
    private val mainHandler = Handler(Looper.getMainLooper())

    // Get scanner lazily — always fetched fresh so it's valid after BT toggles
    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter.bluetoothLeScanner

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            val pUuid = ParcelUuid(serviceUUID)

            record.getServiceData(pUuid)?.let { data ->
                val message = String(data).trim()
                if (message.isNotEmpty()) {
                    Log.d("BLE", "Message received: '$message' from ${result.device.address}")
                    // FIX 1: Always post to main thread before calling back
                    mainHandler.post { onMessageReceived(message) }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            Log.e("BLE", "Scan failed — error code: $errorCode")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScanning() {
        if (isScanning) return

        val activeScanner = scanner
        if (activeScanner == null) {
            Log.e("BLE", "Scanner unavailable — Bluetooth may be off")
            return
        }

        // FIX 2: Use UUID filter — NOT null. Null scans every BLE device in range
        // (headphones, watches, beacons) and fires the callback hundreds of times/second.
        // The filter means only CrisisPin packets reach onScanResult.
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            activeScanner.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
            Log.d("BLE", "Scanner started with UUID filter")
        } catch (e: Exception) {
            isScanning = false
            Log.e("BLE", "Scanner failed to start: ${e.message}")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScanning() {
        if (!isScanning) return
        try {
            scanner?.stopScan(scanCallback)
            Log.d("BLE", "Scanner stopped")
        } catch (e: Exception) {
            Log.e("BLE", "Error stopping scanner: ${e.message}")
        } finally {
            isScanning = false
        }
    }

    fun isActive(): Boolean = isScanning
}