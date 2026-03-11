package com.helios.crisispin.receiver

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.helios.crisispin.service.CrisisPinService
import com.helios.crisispin.utils.PermissionHelper

/**
 * Manifest-registered receiver for Bluetooth state changes.
 *
 * This is the key piece that makes "BT on = scanning starts" work WITHOUT
 * opening the app. The btReceiver inside CrisisPinService only works while
 * the service is already running. This receiver works even when the service
 * is completely stopped — it wakes up the service the moment BT turns on.
 *
 * Flow:
 *   User turns BT on from quick settings → Android fires ACTION_STATE_CHANGED
 *   → This receiver wakes up → starts CrisisPinService → service starts scanning
 *
 * No app open required after first-time permission grant.
 */
class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return

        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)

        when (state) {
            BluetoothAdapter.STATE_ON -> {
                Log.d("BluetoothReceiver", "BT turned ON — starting service")
                if (PermissionHelper.hasPermissions(context)) {
                    CrisisPinService.startService(context)
                }
            }
            BluetoothAdapter.STATE_OFF -> {
                // Service handles this internally via its own btReceiver.
                // We don't stop the service here — it should keep running
                // so it can restart scanning when BT comes back on.
                Log.d("BluetoothReceiver", "BT turned OFF")
            }
        }
    }
}