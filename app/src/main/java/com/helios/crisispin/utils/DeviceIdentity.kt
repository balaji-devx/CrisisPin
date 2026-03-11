package com.helios.crisispin.utils

import android.content.Context
import java.util.UUID

/**
 * Provides a stable 6-character device ID for this CrisisPin installation.
 *
 * Why not use Bluetooth MAC?
 * - Android 6+ randomises BLE MAC on every scan session — useless as identity.
 *
 * Why not ANDROID_ID?
 * - Changes on factory reset and is scoped per-app on Android 8+, but still fine here.
 *
 * We generate a random UUID once and persist it. 6 chars of base36 = 2 billion combinations,
 * more than enough to avoid collisions in a local mesh of ~1000 devices.
 *
 * The ID is embedded in every BLE packet so that:
 *   1. The sender can ignore its own packets.
 *   2. Relay nodes can skip packets they've already forwarded.
 *   3. Every device that has seen the packet adds its ID to the VISITED list.
 */
object DeviceIdentity {

    private const val PREFS = "crisispin_identity"
    private const val KEY   = "device_id"

    @Volatile private var cachedId: String? = null

    fun getDeviceId(context: Context): String {
        cachedId?.let { return it }
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY, null)
        if (stored != null) {
            cachedId = stored
            return stored
        }
        // Generate a new 6-char base36 ID
        val id = UUID.randomUUID().toString()
            .replace("-", "")
            .take(8)
            .toLong(16)
            .let { Math.abs(it) % 2_176_782_336L } // 36^6
            .toString(36)
            .padStart(6, '0')
        prefs.edit().putString(KEY, id).apply()
        cachedId = id
        return id
    }
}