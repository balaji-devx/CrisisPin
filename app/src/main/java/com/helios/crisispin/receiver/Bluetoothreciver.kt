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
 * Ensures that if the user turns Bluetooth ON from System Settings, 
 * CrisisPin starts its background scanning service immediately.
 */
class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return

        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
        if (state == BluetoothAdapter.STATE_ON) {
            Log.d("BluetoothReceiver", "BT ON detected in background")
            
            // Step 3D: Permission check before starting service
            if (PermissionHelper.hasPermissions(context)) {
                Log.d("BluetoothReceiver", "Permissions OK — triggering CrisisPinService")
                CrisisPinService.startService(context)
            } else {
                Log.w("BluetoothReceiver", "BT ON but permissions missing. Service won't start.")
            }
        }
    }
}
