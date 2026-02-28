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
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import java.util.UUID

class BleScanner(
    context: Context,
    private val onMessageReceived: (String) -> Unit
) {
    private val bluetoothAdapter: BluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val serviceUUID: UUID = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
    private var isScanning = false

    // Get scanner lazily — always fetched fresh so it's valid after BT toggles
    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter.bluetoothLeScanner

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            val pUuid = ParcelUuid(serviceUUID)

            // Log everything we see
            Log.d("BLE", "=== Packet from: ${result.device.address} ===")
            Log.d("BLE", "  ServiceUUIDs: ${record.serviceUuids}")
            Log.d("BLE", "  ServiceData keys: ${record.serviceData.keys}")
            Log.d("BLE", "  RSSI: ${result.rssi}")

            // Try to read our specific data
            record.getServiceData(pUuid)?.let { data ->
                val message = String(data)
                Log.d("BLE", "  >>> OUR MESSAGE: $message")
                onMessageReceived(message)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            Log.e("BLE", "Scan failed with error code: $errorCode")
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

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // more aggressive scanning
            .build()

        try {
            // NO filter — scan everything to debug
            activeScanner.startScan(null, settings, scanCallback)
            isScanning = true
            Log.d("BLE", "Scanner started successfully")
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