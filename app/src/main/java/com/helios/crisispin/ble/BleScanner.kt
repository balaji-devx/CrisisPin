package com.helios.crisispin.ble

import android.Manifest
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
    private val onMessageReceived: (message: String, deviceAddress: String) -> Unit
) {
    private val bluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    companion object {
        const val SERVICE_UUID_STRING = "0000ABCD-0000-1000-8000-00805F9B34FB"
    }

    private val serviceUUID: UUID = UUID.fromString(SERVICE_UUID_STRING)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isScanning = false

    // Filter own advertisements — set when we start advertising/relaying
    private val selfAdvertisingMessages = mutableSetOf<String>()

    fun setSelfAdvertising(message: String?) {
        selfAdvertisingMessages.clear()
        if (message != null) selfAdvertisingMessages.add(message.uppercase())
    }

    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter.bluetoothLeScanner

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            val pUuid = ParcelUuid(serviceUUID)
            val data = record.getServiceData(pUuid)
            if (data == null || data.isEmpty()) return

            val message = String(data, Charsets.UTF_8).trim()
            if (message.isBlank()) return

            if (message.uppercase() in selfAdvertisingMessages) return

            val deviceAddress = result.device.address
            Log.d("BleScanner", "RX '$message' from $deviceAddress RSSI=${result.rssi}")
            // Pass device address so service can count unique devices
            mainHandler.post { onMessageReceived(message, deviceAddress) }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            Log.e("BleScanner", "Scan FAILED errorCode=$errorCode")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScanning() {
        if (isScanning) return
        val active = scanner ?: run { Log.e("BleScanner", "Scanner null"); return }

        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        try {
            active.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
            Log.d("BleScanner", "Scanning started ✓")
        } catch (e: Exception) {
            isScanning = false
            Log.e("BleScanner", "startScan failed: ${e.message}")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScanning() {
        if (!isScanning) return
        try { scanner?.stopScan(scanCallback) } catch (e: Exception) { }
        finally { isScanning = false }
        Log.d("BleScanner", "Scanning stopped")
    }

    fun isActive() = isScanning
}