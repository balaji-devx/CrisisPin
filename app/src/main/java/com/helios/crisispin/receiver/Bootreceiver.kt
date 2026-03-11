package com.helios.crisispin.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.helios.crisispin.service.CrisisPinService
import com.helios.crisispin.utils.PermissionHelper

/**
 * Receives BOOT_COMPLETED broadcast from Android after device restarts.
 *
 * This means:
 * - User never needs to open the app for scanning to work
 * - Just having Bluetooth on is enough to receive alerts
 * - Service restarts automatically after phone reboots
 *
 * Note: On first install the user MUST open the app once to grant BLE
 * permissions. After that, this receiver handles every subsequent boot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") {
            return // Ignore anything else
        }

        Log.d("BootReceiver", "Boot complete — checking permissions before starting service")

        // Only start if BLE permissions were already granted (i.e. user opened app before)
        // Without permissions the service can't scan anyway — pointless to start
        if (PermissionHelper.hasPermissions(context)) {
            Log.d("BootReceiver", "Permissions OK — starting CrisisPinService")
            CrisisPinService.startService(context)
        } else {
            Log.d("BootReceiver", "No permissions yet — user needs to open app first")
        }
    }
}