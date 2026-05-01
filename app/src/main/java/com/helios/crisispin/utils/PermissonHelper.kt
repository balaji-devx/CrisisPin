package com.helios.crisispin.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionHelper {

    fun hasPermissions(context: Context): Boolean {
        return getRequiredPermissions(context).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Logic for required permissions:
     * Android 12+ (API 31+): BLUETOOTH_SCAN (with neverForLocation flag in manifest), 
     * BLUETOOTH_ADVERTISE, and BLUETOOTH_CONNECT. Location is NOT strictly required for scanning 
     * if the flag is present, but some devices still behave better with it.
     * Android 13+ (API 33+): POST_NOTIFICATIONS is required for Foreground Service notifications.
     */
    fun getRequiredPermissions(context: Context? = null): Array<String> {
        val p = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            p += Manifest.permission.BLUETOOTH_SCAN
            p += Manifest.permission.BLUETOOTH_ADVERTISE
            p += Manifest.permission.BLUETOOTH_CONNECT
            // We do NOT add ACCESS_FINE_LOCATION here because we use neverForLocation
        } else {
            p += Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            p += Manifest.permission.POST_NOTIFICATIONS
        }
        
        return p.toTypedArray()
    }
}
